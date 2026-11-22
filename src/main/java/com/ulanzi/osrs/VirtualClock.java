package com.ulanzi.osrs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.time.LocalTime;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A pixel clock that lives in the plugin: it takes the same requests the plugin sends to an
 * AWTRIX NG clock and keeps the same state, so every page the plugin builds shows up in the
 * virtual window without being built twice.
 *
 * It follows the clock's documented rules where the plugin relies on them: pushed apps and
 * the active one, a notification queue where stack false replaces what is on screen and
 * hold keeps it there until it is deleted, and the brightness and app list the plugin reads
 * back. Requests come from several threads and rendering from the Swing thread, so every
 * method holds the lock; the work under it is small.
 */
@Singleton
class VirtualClock
{
	static final int MAX_QUEUE = 32;
	static final long DEFAULT_DURATION_MS = 7_000L;
	static final int DEFAULT_BRIGHTNESS = 160;
	private static final String NOTIFICATIONS = "/api/v1/notifications";
	private static final String PUSHED = "/api/v1/apps/pushed/";
	private static final String APPS = "/api/v1/apps";
	private static final int IDLE_COLOR = 0x8C8C8C;
	private static final int QUIET_COLOR = 0x3CB43C;
	private static final int WELCOME_COLOR = 0xC8C8C8;
	private static final long ICON_MS = 4_000L;
	private static final double QUIET_CYCLE_MS = 3_600.0;

	/** What the virtual overlay answers, in the shape of an HTTP reply. */
	static final class Reply
	{
		final int code;
		final String body;

		Reply(int code, String body)
		{
			this.code = code;
			this.body = body;
		}

		boolean ok()
		{
			return code >= 200 && code < 300;
		}
	}

	private static final class Notification
	{
		final String name;
		final JsonObject page;
		final boolean hold;
		final long durationMs;
		final int repeat;
		long startedMs = -1;

		Notification(String name, JsonObject page)
		{
			this.name = name;
			this.page = page;
			this.hold = PageRenderer.bool(page, "hold", false);
			this.durationMs = PageRenderer.integer(page, "durationMs", 0);
			this.repeat = PageRenderer.integer(page, "repeat", 0);
		}

		/**
		 * When it is done: held ones never are; one that repeats its scroll stays for that
		 * many runs, and at least its duration if it was given one too.
		 */
		long lengthMs()
		{
			long runs = repeat > 0 ? repeat * PageRenderer.runMs(page) : 0;
			if (runs > 0)
			{
				return Math.max(runs, durationMs);
			}
			return durationMs > 0 ? durationMs : DEFAULT_DURATION_MS;
		}
	}

	private final ChipPlayer chipPlayer;
	private final UlanziConfig config;
	private final LongSupplier clock;

	private final Map<String, JsonObject> apps = new LinkedHashMap<>();
	private final Map<String, Long> appStartedMs = new LinkedHashMap<>();
	private final List<Notification> queue = new ArrayList<>();
	private String activeApp;
	private int brightness = DEFAULT_BRIGHTNESS;
	private boolean autoBrightness = true;
	private int alertVolume = AwtrixClient.DEFAULT_ALERT_VOLUME;
	private List<String> order = new ArrayList<>();
	private boolean welcome;

	@Inject
	VirtualClock(ChipPlayer chipPlayer, UlanziConfig config)
	{
		this(chipPlayer, config, System::currentTimeMillis);
	}

	VirtualClock(ChipPlayer chipPlayer, UlanziConfig config, LongSupplier clock)
	{
		this.chipPlayer = chipPlayer;
		this.config = config;
		this.clock = clock;
	}

	/** Back to a freshly started clock: no apps, no notifications. Settings are kept. */
	synchronized void reset()
	{
		apps.clear();
		appStartedMs.clear();
		queue.clear();
		activeApp = null;
		order = new ArrayList<>();
	}

	/** Until the first login the idle page says what to do next. */
	synchronized void setWelcome(boolean welcome)
	{
		this.welcome = welcome;
	}

	/** Handles one request the plugin would have sent to the clock. */
	synchronized Reply handle(String method, String path, String body)
	{
		long now = clock.getAsLong();
		advance(now);
		try
		{
			if ("POST".equals(method) && NOTIFICATIONS.equals(path))
			{
				return notify(parseObject(body), now);
			}
			if ("DELETE".equals(method) && path.startsWith(NOTIFICATIONS + "/"))
			{
				dismiss(path.substring(NOTIFICATIONS.length() + 1), now);
				return new Reply(200, "{\"ok\":true}");
			}
			if ("PUT".equals(method) && path.startsWith(PUSHED))
			{
				String name = path.substring(PUSHED.length());
				JsonObject page = parseObject(body);
				if (page == null)
				{
					return new Reply(422, null);
				}
				if (!apps.containsKey(name))
				{
					appStartedMs.put(name, now);
				}
				apps.put(name, page);
				return new Reply(200, "{\"ok\":true}");
			}
			if ("PUT".equals(method) && (APPS + "/active").equals(path))
			{
				JsonObject active = parseObject(body);
				String name = active == null ? null : PageRenderer.string(active, "name");
				if (name == null || !apps.containsKey(name))
				{
					return new Reply(404, null);
				}
				if (!name.equals(activeApp))
				{
					appStartedMs.put(name, now);
				}
				activeApp = name;
				return new Reply(200, "{\"ok\":true}");
			}
			if ("PUT".equals(method) && (APPS + "/order").equals(path))
			{
				JsonObject loop = parseObject(body);
				List<String> names = new ArrayList<>();
				JsonElement list = loop == null ? null : loop.get("order");
				if (list != null && list.isJsonArray())
				{
					for (JsonElement name : list.getAsJsonArray())
					{
						names.add(name.getAsString());
					}
				}
				order = names;
				return new Reply(200, "{\"ok\":true}");
			}
			if ("DELETE".equals(method) && path.startsWith(APPS + "/"))
			{
				deleteApp(path.substring(APPS.length() + 1));
				return new Reply(200, "{\"ok\":true}");
			}
			if ("GET".equals(method) && APPS.equals(path))
			{
				return new Reply(200, appList().toString());
			}
			if ("GET".equals(method) && "/api/v1/settings".equals(path))
			{
				return new Reply(200, settings().toString());
			}
			if ("PATCH".equals(method) && "/api/v1/settings".equals(path))
			{
				applySettings(parseObject(body));
				return new Reply(200, "{\"ok\":true}");
			}
			if ("GET".equals(method) && "/api/v1/device".equals(path))
			{
				JsonObject device = new JsonObject();
				device.addProperty("hostname", "the virtual overlay");
				device.addProperty("brightness", brightness);
				return new Reply(200, device.toString());
			}
		}
		catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex)
		{
			return new Reply(422, null);
		}
		return "GET".equals(method) ? new Reply(404, null) : new Reply(200, "{\"ok\":true}");
	}

	/** Draws what is on screen now into the frame. */
	synchronized void render(int[] frame)
	{
		long now = clock.getAsLong();
		advance(now);
		if (!queue.isEmpty())
		{
			Notification head = queue.get(0);
			PageRenderer.render(head.page, now - head.startedMs, now, frame);
			return;
		}
		String app = shownApp();
		if (app != null)
		{
			PageRenderer.render(apps.get(app), now - appStartedMs.getOrDefault(app, now), now, frame);
			return;
		}
		idle(frame, now);
	}

	/** How bright the panel is running, 0 to 1, for the window to light its LEDs by. */
	synchronized double brightness()
	{
		if (autoBrightness)
		{
			return 1.0;
		}
		return 0.35 + 0.65 * Math.max(0, Math.min(255, brightness)) / 255.0;
	}

	private Reply notify(JsonObject page, long now)
	{
		if (page == null)
		{
			return new Reply(422, null);
		}
		String name = PageRenderer.string(page, "name");
		Notification notification = new Notification(name, page);
		boolean stack = PageRenderer.bool(page, "stack", true);
		if (!stack && !queue.isEmpty())
		{
			// Replaces whatever is on screen and starts it over; the queue behind waits.
			queue.set(0, notification);
			begin(notification, now);
			return new Reply(200, "{\"ok\":true}");
		}
		if (queue.size() >= MAX_QUEUE)
		{
			return new Reply(507, null);
		}
		queue.add(notification);
		if (queue.size() == 1)
		{
			begin(notification, now);
		}
		return new Reply(200, "{\"ok\":true}");
	}

	private void dismiss(String name, long now)
	{
		if ("active".equals(name))
		{
			if (!queue.isEmpty())
			{
				queue.remove(0);
				startHead(now);
			}
			return;
		}
		boolean headGone = !queue.isEmpty() && name.equals(queue.get(0).name);
		queue.removeIf(n -> name.equals(n.name));
		if (headGone)
		{
			startHead(now);
		}
	}

	private void deleteApp(String name)
	{
		Iterator<String> it = apps.keySet().iterator();
		while (it.hasNext())
		{
			String app = it.next();
			if (app.equals(name) || (app.startsWith(name) && app.substring(name.length()).matches("\\d+")))
			{
				it.remove();
				appStartedMs.remove(app);
			}
		}
		if (activeApp != null && !apps.containsKey(activeApp))
		{
			activeApp = null;
		}
	}

	/** Ends notifications whose time is up, starting the next one at the moment the last ended. */
	private void advance(long now)
	{
		while (!queue.isEmpty())
		{
			Notification head = queue.get(0);
			if (head.startedMs < 0)
			{
				begin(head, now);
			}
			if (head.hold)
			{
				return;
			}
			long end = head.startedMs + head.lengthMs();
			if (now < end)
			{
				return;
			}
			queue.remove(0);
			if (!queue.isEmpty())
			{
				begin(queue.get(0), Math.min(now, end));
			}
		}
	}

	private void startHead(long now)
	{
		if (!queue.isEmpty())
		{
			begin(queue.get(0), now);
		}
	}

	/**
	 * A notification goes on screen, and its tone plays, here on the computer. When tones
	 * already go to the computer directly the virtual overlay stays quiet, so none is heard twice.
	 */
	private void begin(Notification notification, long now)
	{
		notification.startedMs = now;
		String rtttl = PageRenderer.string(notification.page, "soundRtttl");
		if (rtttl == null)
		{
			JsonElement sound = notification.page.get("sound");
			if (sound != null && sound.isJsonObject())
			{
				rtttl = PageRenderer.string(sound.getAsJsonObject(), "rtttl");
			}
		}
		SoundOutput output = config.soundOutput();
		if (rtttl != null && output.toClock() && !output.toComputer())
		{
			chipPlayer.play(rtttl);
		}
	}

	private String shownApp()
	{
		if (activeApp != null && apps.containsKey(activeApp))
		{
			return activeApp;
		}
		for (String name : order)
		{
			if (apps.containsKey(name))
			{
				return name;
			}
		}
		return apps.isEmpty() ? null : apps.keySet().iterator().next();
	}

	/** What is up with no page to show, chosen in the settings. */
	private void idle(int[] frame, long now)
	{
		Frame.fill(frame, 0);
		if (welcome)
		{
			centered(frame, "LOG IN", WELCOME_COLOR);
			return;
		}
		IdleDisplay display = config.virtualIdle();
		if (display == IdleDisplay.NOTHING)
		{
			return;
		}
		if (display == IdleDisplay.TIME)
		{
			centered(frame, formatTime(LocalTime.now(), config.virtualTimeFormat(), Locale.getDefault()), IDLE_COLOR);
			return;
		}
		if (display == IdleDisplay.ICONS)
		{
			SkillActivity[] activities = SkillActivity.values();
			SkillActivity activity = activities[(int) Math.floorMod(now / ICON_MS, (long) activities.length)];
			GifIcon icon = GifIcon.of(activity.iconData());
			if (icon != null)
			{
				icon.draw(frame, (Frame.WIDTH - icon.width) / 2, 0, now);
			}
			return;
		}
		quiet(frame, now);
	}

	/** Three dots breathing in turn: alive, and easy to ignore. */
	private static void quiet(int[] frame, long now)
	{
		for (int dot = 0; dot < 3; dot++)
		{
			double phase = now / QUIET_CYCLE_MS * 2 * Math.PI - dot * 0.9;
			int color = Frame.scale(QUIET_COLOR, 0.42 + 0.33 * Math.sin(phase));
			int x = 12 + dot * 3;
			for (int row = 3; row <= 4; row++)
			{
				Frame.set(frame, x, row, color);
				Frame.set(frame, x + 1, row, color);
			}
		}
	}

	private static void centered(int[] frame, String text, int color)
	{
		int x = (Frame.WIDTH - PixelFont.width(text)) / 2;
		PixelFont.draw(frame, text, x, PixelFont.SMALL_TOP, false, (px, i) -> color, 0, Frame.WIDTH);
	}

	/** "2:05PM" on a 12-hour clock, "14:05" on a 24-hour one. */
	static String formatTime(LocalTime time, TimeFormat format, Locale locale)
	{
		if (!(format == null ? TimeFormat.SYSTEM : format).is24Hour(locale))
		{
			int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
			return String.format(Locale.ROOT, "%d:%02d%s", hour, time.getMinute(), time.getHour() < 12 ? "AM" : "PM");
		}
		return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
	}

	private JsonArray appList()
	{
		JsonArray list = new JsonArray();
		List<String> names = new ArrayList<>(order);
		if (!names.contains("Time"))
		{
			names.add("Time");
		}
		int slot = 0;
		for (String name : names)
		{
			JsonObject app = new JsonObject();
			app.addProperty("name", name);
			app.addProperty("enabled", true);
			app.addProperty("slot", slot++);
			list.add(app);
		}
		return list;
	}

	private JsonObject settings()
	{
		JsonObject settings = new JsonObject();
		settings.addProperty("brightness", brightness);
		settings.addProperty("autoBrightness", autoBrightness);
		settings.addProperty("alertVolume", alertVolume);
		return settings;
	}

	private void applySettings(JsonObject patch)
	{
		if (patch == null)
		{
			return;
		}
		brightness = Math.max(0, Math.min(255, PageRenderer.integer(patch, "brightness", brightness)));
		autoBrightness = PageRenderer.bool(patch, "autoBrightness", autoBrightness);
		alertVolume = Math.max(0, Math.min(100, PageRenderer.integer(patch, "alertVolume", alertVolume)));
	}

	private static JsonObject parseObject(String body)
	{
		if (body == null || body.isEmpty())
		{
			return null;
		}
		JsonElement element = new JsonParser().parse(body);
		return element.isJsonObject() ? element.getAsJsonObject() : null;
	}
}
