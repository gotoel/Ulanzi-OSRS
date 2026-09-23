package com.ulanzi.osrs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Base64;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class AwtrixClient
{
	static final String APP_STATS = "osrs";
	static final String NOTIF_AFK = "osrs-afk";
	static final String NOTIF_LOW_HP = "osrs-low-hp";
	static final String NOTIF_LOW_PRAY = "osrs-low-pray";
	static final String NOTIF_TEST = "osrs-test";
	static final String NOTIF_XP_DROP = "osrs-xp";
	static final long LEVEL_UP_MS = 5_000L;
	static final int XP_DROP_SPEED = 150;
	static final long XP_DROP_IN_PLACE_MS = 1_800L;
	private static final int SCROLL_PX_PER_SEC = 21;
	private static final int PANEL_WIDTH = 32;
	private static final int ICON_COLUMN = 9;
	private static final int SMALL_CHAR_WIDTH = 4;

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String AFK_RTTTL = "afk:d=4,o=5,b=160:c6,p,c6";
	private static final String HP_RTTTL = "lowhp:d=16,o=6,b=200:c,e,g,c7";
	private static final String PRAY_RTTTL = "lowpray:d=8,o=5,b=140:e,c,e,c";
	private static final String LEVEL_RTTTL = "levelup:d=8,o=5,b=180:c,e,g,4c6,p,g,2c6";

	private final OkHttpClient httpClient;
	private final UlanziConfig config;
	private final Gson gson;

	private final AtomicReference<String> lastStatsBody = new AtomicReference<>();
	private final AtomicReference<String> lastAfkBody = new AtomicReference<>();
	private final AtomicReference<Long> lastStatsSentMs = new AtomicReference<>(0L);
	private final AtomicReference<Boolean> loopConfigured = new AtomicReference<>(false);
	private final AtomicReference<AppLoop> savedLoop = new AtomicReference<>();
	private final AtomicBoolean loopSnapshotTried = new AtomicBoolean(false);
	private final AtomicBoolean loopChanged = new AtomicBoolean(false);
	private final AtomicReference<Brightness> savedBrightness = new AtomicReference<>();
	private final AtomicBoolean brightnessSnapshotTried = new AtomicBoolean(false);
	private final AtomicReference<String> appliedBrightness = new AtomicReference<>();
	private final AtomicReference<Boolean> reachable = new AtomicReference<>();
	private volatile Consumer<String> messages = message -> { };
	private volatile double pulse = 1.0;
	private final Map<String, String> pulseIcons = new ConcurrentHashMap<>();

	private static final long STATS_REFRESH_MS = 8_000L;
	private static final double MIN_PULSE = 0.2;
	private static final double MAX_PULSE = 3.0;
	private static final int PULSE_ICON_CACHE = 64;
	/**
	 * How far down a reading that has just been lost is left showing. It has to clear the
	 * floor even after the pulse's resting dim has halved it, or a trail and the track it
	 * sits on come out the same colour.
	 */
	private static final double GHOST_LEVEL = 0.5;
	/** A part-filled pixel never falls below this, so a trickle still shows. */
	private static final int MIN_PARTIAL = 12;
	/** The dashes naming the values are a label, so they sit under them rather than beside. */
	private static final double TICK_LEVEL = 0.45;
	/** The unfilled part of a bar, tinted with the stat's own colour. */
	private static final double TRACK_LEVEL = 0.22;
	/**
	 * Nothing lit is sent below this. The clock scales every channel again for the room it
	 * is in, so a colour already taken most of the way to black lands under what an LED can
	 * show once the lights go out: the bar tracks and the trail stopped existing in a dark
	 * room, and the pulse's resting dim halved them again on the way. Holding a floor costs
	 * a little contrast on a bright panel and keeps the page readable on a dim one.
	 */
	private static final int VISIBLE_FLOOR = 40;
	private static final Color TRACK = new Color(48, 48, 48);

	@Inject
	AwtrixClient(OkHttpClient httpClient, UlanziConfig config, Gson gson)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.gson = gson;
	}

	/**
	 * Where connection results go. Called from OkHttp threads.
	 */
	void setMessageSink(Consumer<String> sink)
	{
		messages = sink == null ? message -> { } : sink;
		reachable.set(null);
	}

	/**
	 * Brightness applied to everything sent from here until it is set back, which is
	 * how an XP drop lifts the whole panel at once instead of moving text across it.
	 * Below 1 it dims, which is what gives white somewhere to brighten from: white is
	 * already at full, so multiplying it up does nothing. Black stays black either
	 * way, so the background never washes out.
	 */
	void setPulse(double factor)
	{
		pulse = Math.max(MIN_PULSE, Math.min(MAX_PULSE, factor));
	}

	private String hex(Color color)
	{
		return toHex(lift(brighten(color, pulse), VISIBLE_FLOOR));
	}

	/**
	 * A background is allowed to be as near black as it likes, so it skips the floor.
	 */
	private String hexBackground(Color color)
	{
		return toHex(brighten(color, pulse));
	}

	/**
	 * Lifts a lit colour until its brightest channel clears the floor, holding its hue and
	 * so its identity. Black is left alone: an unlit pixel has to stay unlit, or the
	 * background would come up grey and every icon would glow.
	 */
	static Color lift(Color color, int minimum)
	{
		int peak = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
		if (peak == 0 || peak >= minimum)
		{
			return color;
		}
		return new Color(lift(color.getRed(), peak, minimum), lift(color.getGreen(), peak, minimum),
			lift(color.getBlue(), peak, minimum));
	}

	private static int lift(int channel, int peak, int minimum)
	{
		return Math.max(0, Math.min(255, (int) Math.round(channel * (double) minimum / peak)));
	}

	static Color brighten(Color color, double factor)
	{
		if (factor == 1.0)
		{
			return color;
		}
		return new Color(channel(color.getRed(), factor), channel(color.getGreen(), factor),
			channel(color.getBlue(), factor));
	}

	private static int channel(int value, double factor)
	{
		return Math.max(0, Math.min(255, (int) Math.round(value * factor)));
	}

	/**
	 * Icons arrive already encoded, so they are decoded and re-encoded to pulse with
	 * the rest. Quantised to whole percent so a pulse reuses a handful of encodings.
	 */
	private String pulseIcon(String icon)
	{
		double factor = pulse;
		if (icon == null || icon.isEmpty() || factor == 1.0)
		{
			return icon;
		}
		int step = (int) Math.round(factor * 100);
		if (pulseIcons.size() > PULSE_ICON_CACHE)
		{
			pulseIcons.clear();
		}
		return pulseIcons.computeIfAbsent(step + ":" + icon, key -> brightenIcon(icon, step / 100.0));
	}

	static String brightenIcon(String icon, double factor)
	{
		try
		{
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(icon)));
			if (image == null)
			{
				return icon;
			}
			BufferedImage lit = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					lit.setRGB(x, y, brighten(new Color(image.getRGB(x, y)), factor).getRGB());
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(lit, "gif", out))
			{
				return icon;
			}
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
		catch (IOException | IllegalArgumentException ex)
		{
			log.debug("Could not pulse icon", ex);
			return icon;
		}
	}

	void clearCache()
	{
		lastStatsBody.set(null);
		lastAfkBody.set(null);
		lastStatsSentMs.set(0L);
		loopConfigured.set(false);
	}

	void testConnection()
	{
		if (baseUrl() == null)
		{
			messages.accept("Set the clock address first.");
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("name", NOTIF_TEST);
		body.addProperty("text", "OK");
		body.addProperty("textColor", "#00FF00");
		body.addProperty("backgroundColor", "#001100");
		body.addProperty("font", "large");
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		body.addProperty("durationMs", 2000);
		body.addProperty("hold", false);
		body.addProperty("stack", false);
		send("POST", "/api/v1/notifications", body.toString(), true, result ->
		{
			if (result.ok)
			{
				send("GET", "/api/v1/device", null, true, device ->
				{
					String name = device.ok ? hostname(device.body) : null;
					messages.accept(name == null ? "Connected to the clock." : "Connected to " + name + ".");
				});
			}
			else if (result.reachable)
			{
				messages.accept("The clock answered with " + result.problem + ". Is it running AWTRIX NG?");
			}
			else
			{
				messages.accept("Couldn't reach the clock: " + result.problem + ".");
			}
		});
	}

	private String hostname(String deviceJson)
	{
		if (deviceJson == null)
		{
			return null;
		}
		try
		{
			JsonObject device = gson.fromJson(deviceJson, JsonObject.class);
			String name = device == null ? null : stringOrNull(device, "hostname");
			return name == null || name.trim().isEmpty() ? null : name.trim();
		}
		catch (JsonParseException ex)
		{
			return null;
		}
	}

	/**
	 * A new level with the skill icon, the number painted in a moving rainbow.
	 * Stacked so several level-ups at once play one after another.
	 */
	void showLevelUp(String skillName, String icon, int level, boolean sound)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", "osrs-level-" + skillName.toLowerCase());
		body.addProperty("text", String.valueOf(level));
		body.addProperty("font", "large");
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		body.addProperty("backgroundColor", "#000000");
		body.addProperty("palette", "Rainbow");
		body.addProperty("textColor", "palette");
		body.addProperty("paletteSpan", 16);
		body.addProperty("paletteSpeed", 1.5);
		body.addProperty("durationMs", LEVEL_UP_MS);
		body.addProperty("hold", false);
		body.addProperty("stack", true);
		body.addProperty("wakeup", true);
		if (icon != null)
		{
			body.addProperty("icon", icon);
			body.addProperty("iconMode", "fixed");
		}
		if (sound)
		{
			body.addProperty("soundRtttl", LEVEL_RTTTL);
		}
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
	}

	/**
	 * Shows the gain for about as long as its animation lasts, then the page ends.
	 * Returns that time, so the next drop can wait for this one.
	 */
	long showXpDrop(String text, Color color, String icon, XpDropDirection direction)
	{
		switch (direction)
		{
			case UP:
			case DOWN:
				return showVerticalXpDrop(text, color, icon, direction == XpDropDirection.UP);
			case IN_PLACE:
				return showStillXpDrop(text, color, icon);
			default:
				return showScrollingXpDrop(text, color, icon, direction);
		}
	}

	private long showVerticalXpDrop(String text, Color color, String icon, boolean upward)
	{
		JsonObject body = xpDropBody("", color, icon);
		body.add("scroll", scrollStatic());
		JsonArray icons = new JsonArray();
		JsonObject slide = new JsonObject();
		slide.addProperty("icon", XpDropGif.encode(text, color, upward));
		slide.addProperty("x", icon != null ? ICON_COLUMN : (PANEL_WIDTH - XpDropGif.WIDTH) / 2);
		slide.addProperty("y", 0);
		icons.add(slide);
		body.add("icons", icons);
		long visibleMs = XpDropGif.visibleMs();
		body.addProperty("durationMs", visibleMs + XpDropGif.MOVE_MS);
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
		return visibleMs;
	}

	private long showStillXpDrop(String text, Color color, String icon)
	{
		JsonObject body = xpDropBody(text, color, icon);
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		body.addProperty("durationMs", XP_DROP_IN_PLACE_MS);
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
		return XP_DROP_IN_PLACE_MS;
	}

	/**
	 * Scrolls the gain in from one edge and off the other once.
	 */
	private long showScrollingXpDrop(String text, Color color, String icon, XpDropDirection direction)
	{
		JsonObject body = xpDropBody(text, color, icon);
		JsonObject scroll = new JsonObject();
		scroll.addProperty("mode", "wrap");
		scroll.addProperty("direction", direction.getScrollDirection());
		scroll.addProperty("entry", "offscreen");
		scroll.addProperty("whenFits", "scroll");
		scroll.addProperty("speed", XP_DROP_SPEED);
		scroll.addProperty("holdMs", 0);
		body.add("scroll", scroll);
		body.addProperty("repeat", 1);
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
		return xpDropFlightMs(text, icon != null);
	}

	private JsonObject xpDropBody(String text, Color color, String icon)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", NOTIF_XP_DROP);
		body.addProperty("text", text);
		body.addProperty("textColor", hex(color));
		body.addProperty("font", "small");
		body.addProperty("textCase", "asTyped");
		body.addProperty("backgroundColor", "#000000");
		body.addProperty("hold", false);
		body.addProperty("stack", true);
		applyActivityIcon(body, icon);
		return body;
	}

	/**
	 * The text travels the width of the text area plus its own width at 21 px/s per 100% speed.
	 */
	static long xpDropFlightMs(String text, boolean icon)
	{
		int area = icon ? PANEL_WIDTH - ICON_COLUMN : PANEL_WIDTH;
		int distance = area + (text == null ? 0 : text.length()) * SMALL_CHAR_WIDTH;
		return distance * 1000L * 100L / ((long) SCROLL_PX_PER_SEC * XP_DROP_SPEED);
	}

	void showAfk(boolean playSound)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", NOTIF_AFK);
		body.addProperty("hold", true);
		body.addProperty("stack", false);
		body.addProperty("wakeup", true);
		applyAfkVisuals(body);

		if (playSound && config.afkSound())
		{
			body.addProperty("soundRtttl", AFK_RTTTL);
		}

		String json = body.toString();
		lastAfkBody.set(json);
		enqueue("POST", "/api/v1/notifications", json, null);
	}

	/**
	 * Show AFK on the stats pushed-app slot (no popup takeover).
	 */
	void pushAfkOnStats(boolean playSound)
	{
		JsonObject body = baseStatsApp();
		applyAfkVisuals(body);
		body.addProperty("durationMs", 60_000);
		if (playSound && config.afkSound())
		{
			body.addProperty("soundRtttl", AFK_RTTTL);
		}
		putStats(body);
	}

	private void applyAfkVisuals(JsonObject body)
	{
		body.addProperty("text", truncate(config.afkText(), 8));
		body.addProperty("font", "large");
		body.addProperty("textCenter", true);
		body.addProperty("textCase", "asTyped");
		body.add("scroll", scrollStatic());

		AfkEffect effect = config.afkEffect();
		String bgEffect = effect.backgroundEffect();
		if (effect.usesSideEyes())
		{
			body.addProperty("backgroundColor", "#000000");
			body.addProperty("textColor", hex(config.afkTextColor()));
			body.addProperty("textInFront", true);
			body.add("draw", buildSideEyesDraw());
		}
		else if (bgEffect != null)
		{
			body.addProperty("effect", bgEffect);
			body.addProperty("effectSpeed", 1.2);
			if (effect.paletteName() != null)
			{
				body.addProperty("palette", effect.paletteName());
			}
			body.addProperty("textColor", hex(config.afkTextColor()));
		}
		else if (effect.usesPaletteText())
		{
			body.addProperty("backgroundColor", hexBackground(config.afkBackgroundColor()));
			body.addProperty("palette", effect.paletteName());
			body.addProperty("textColor", "palette");
			body.addProperty("paletteSpan", 16);
			body.addProperty("paletteSpeed", Math.max(0.1, config.afkWaveSpeed() / 10.0));
			body.addProperty("paletteBlend", true);
		}
		else
		{
			body.addProperty("backgroundColor", hexBackground(config.afkBackgroundColor()));
			body.addProperty("textColor", hex(config.afkTextColor()));
			if (config.afkBlink())
			{
				body.addProperty("textBlinkMs", config.afkBlinkMs());
			}
		}
	}

	void beep(String rtttl)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", "osrs-beep");
		body.addProperty("text", "");
		body.addProperty("durationMs", 50);
		body.addProperty("hold", false);
		body.addProperty("stack", false);
		body.addProperty("soundRtttl", rtttl);
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
	}

	void beepLowHp()
	{
		beep(HP_RTTTL);
	}

	void beepLowPrayer()
	{
		beep(PRAY_RTTTL);
	}

	void beepAfk()
	{
		beep(AFK_RTTTL);
	}

	/**
	 * Two eyes on the panel edges (not under the text). Pupils glance and blink over time.
	 * Left eye occupies x 0-5, right eye x 26-31, leaving the center for AFK.
	 */
	private JsonArray buildSideEyesDraw()
	{
		long phase = (System.currentTimeMillis() / 700) % 8;
		int pupilDx;
		int pupilDy;
		boolean blink;
		switch ((int) phase)
		{
			case 0:
				pupilDx = -1;
				pupilDy = 0;
				blink = false;
				break;
			case 1:
				pupilDx = 0;
				pupilDy = 0;
				blink = false;
				break;
			case 2:
				pupilDx = 1;
				pupilDy = 0;
				blink = false;
				break;
			case 3:
				pupilDx = 0;
				pupilDy = 1;
				blink = false;
				break;
			case 4:
				pupilDx = 0;
				pupilDy = 0;
				blink = true;
				break;
			case 5:
				pupilDx = -1;
				pupilDy = -1;
				blink = false;
				break;
			case 6:
				pupilDx = 1;
				pupilDy = -1;
				blink = false;
				break;
			default:
				pupilDx = 0;
				pupilDy = 0;
				blink = false;
				break;
		}

		JsonArray draw = new JsonArray();
		addEye(draw, 0, blink, pupilDx, pupilDy);
		addEye(draw, 26, blink, pupilDx, pupilDy);
		return draw;
	}

	private static void addEye(JsonArray draw, int originX, boolean blink, int pupilDx, int pupilDy)
	{
		if (blink)
		{
			// Closed eyelid across the middle of the eye slot.
			draw.add(drawCmd("rectFill", originX, 3, 6, 2, "#FFFFFF"));
			return;
		}

		// Sclera
		draw.add(drawCmd("rectFill", originX + 1, 1, 4, 6, "#FFFFFF"));
		draw.add(drawCmd("rectFill", originX, 2, 6, 4, "#FFFFFF"));

		int pupilX = originX + 2 + pupilDx;
		int pupilY = 2 + pupilDy;
		pupilX = Math.max(originX + 1, Math.min(originX + 3, pupilX));
		pupilY = Math.max(1, Math.min(4, pupilY));

		draw.add(drawCmd("rectFill", pupilX, pupilY, 2, 2, "#1020FF"));
		draw.add(drawCmd("pixel", pupilX + 1, pupilY + 1, "#000000"));
	}

	private static JsonArray drawCmd(String op, Object... args)
	{
		JsonArray cmd = new JsonArray();
		cmd.add(op);
		for (Object arg : args)
		{
			if (arg instanceof String)
			{
				cmd.add((String) arg);
			}
			else if (arg instanceof Number)
			{
				cmd.add((Number) arg);
			}
			else
			{
				cmd.add(String.valueOf(arg));
			}
		}
		return cmd;
	}

	void dismissAfk()
	{
		lastAfkBody.set(null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_AFK, null, null);
	}

	void showLowHpAlert(int hitpoints, boolean flash, boolean beep, boolean hold)
	{
		showThresholdAlert(NOTIF_LOW_HP, "HP " + hitpoints, Color.RED, new Color(40, 0, 0),
			flash, beep, hold, HP_RTTTL);
	}

	void showLowPrayerAlert(int prayer, boolean flash, boolean beep, boolean hold)
	{
		showThresholdAlert(NOTIF_LOW_PRAY, "PRAY " + prayer, new Color(0x4F, 0xA3, 0xFF), new Color(0, 0, 40),
			flash, beep, hold, PRAY_RTTTL);
	}

	void dismissLowAlerts()
	{
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_HP, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_PRAY, null, null);
	}

	void dismissLowHp()
	{
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_HP, null, null);
	}

	void dismissLowPrayer()
	{
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_PRAY, null, null);
	}

	private void showThresholdAlert(String name, String text, Color textColor, Color background,
		boolean flash, boolean beep, boolean hold, String rtttl)
	{
		if (!flash && !beep && !hold)
		{
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("text", text);
		body.addProperty("textColor", hex(textColor));
		body.addProperty("backgroundColor", hexBackground(background));
		body.addProperty("font", "large");
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		body.addProperty("stack", false);
		body.addProperty("wakeup", true);

		if (hold)
		{
			body.addProperty("hold", true);
		}
		else
		{
			body.addProperty("hold", false);
			body.addProperty("durationMs", 2500);
		}

		if (flash)
		{
			body.addProperty("textBlinkMs", 350);
		}

		if (beep)
		{
			body.addProperty("soundRtttl", rtttl);
		}

		enqueue("POST", "/api/v1/notifications", body.toString(), null);
	}

	void pushBigStat(String label, String value, Color color, int progressPercent, Color progressColor)
	{
		pushBigStat(label, value, color, progressPercent, progressColor, null, null);
	}

	void pushBigStat(String label, String value, Color color, int progressPercent, Color progressColor,
		Color background)
	{
		pushBigStat(label, value, color, progressPercent, progressColor, background, null);
	}

	void pushBigStat(String label, String value, Color color, int progressPercent, Color progressColor,
		Color background, String activityIcon)
	{
		JsonObject body = baseStatsApp();
		// The icon column takes the left 8px. Three large glyphs no longer fit beside it.
		boolean shrink = activityIcon != null && value != null && value.length() >= 3;
		body.addProperty("font", shrink ? "small" : "large");
		body.addProperty("text", value);
		body.addProperty("textColor", hex(color));
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		if (background != null)
		{
			body.addProperty("backgroundColor", hexBackground(background));
		}

		if (progressPercent >= 0)
		{
			body.addProperty("progress", progressPercent);
			body.addProperty("progressColor", hex(progressColor));
			body.addProperty("progressTrackColor", hex(TRACK));
			body.addProperty("textInFront", true);
		}

		putStats(body);
	}

	/**
	 * The compact page, drawn pixel by pixel rather than handed over as one centred run of
	 * text. Centred text reflows every time a number gains or loses a digit, which on 32
	 * pixels is the most visible thing about the old page; drawing each value at a settled
	 * x is what stops it. Nothing here uses the app's own text, so the firmware never gets
	 * to lay anything out.
	 */
	void pushCompact(List<CompactLayout.Cell> cells, Color background, String activityIcon)
	{
		JsonObject body = baseStatsApp();
		body.addProperty("font", "small");
		body.addProperty("text", "");
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		if (background != null)
		{
			body.addProperty("backgroundColor", hexBackground(background));
		}
		body.add("draw", compactDraw(cells));
		putStats(body);
	}

	/**
	 * The focus page: one stat large in the middle, the rest of them along the bottom row.
	 * The big value is left to the firmware to centre rather than drawn, since the large
	 * font's widths are not ours to know, and one value has nothing to line up with anyway.
	 */
	void pushFocusStat(String value, Color color, List<CompactLayout.Cell> cells, Color background,
		String activityIcon)
	{
		JsonObject body = baseStatsApp();
		body.addProperty("font", "large");
		body.addProperty("text", value);
		body.addProperty("textColor", hex(color));
		body.addProperty("textCenter", true);
		body.addProperty("textInFront", true);
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		if (background != null)
		{
			body.addProperty("backgroundColor", hexBackground(background));
		}
		body.add("draw", stripDraw(cells));
		putStats(body);
	}

	/**
	 * Dashes first, then values, then the strip, so a value drawn tight against its slot
	 * edge lands on top of its own dash rather than under the next one.
	 */
	JsonArray compactDraw(List<CompactLayout.Cell> cells)
	{
		JsonArray draw = new JsonArray();
		for (CompactLayout.Cell cell : cells)
		{
			if (cell.showsValue() && cell.identity != null && cell.x >= 0)
			{
				draw.add(drawCmd("rectFill", cell.x, CompactLayout.TICK_ROW,
					CompactLayout.tickWidth(cell), 1, hex(brighten(cell.identity, TICK_LEVEL))));
			}
		}
		for (CompactLayout.Cell cell : cells)
		{
			if (cell.showsValue() && cell.x >= 0)
			{
				draw.add(drawCmd("text", CompactLayout.textX(cell), CompactLayout.TEXT_TOP,
					cell.text, hex(cell.color)));
			}
		}
		appendStrip(draw, cells);
		return draw;
	}

	private JsonArray stripDraw(List<CompactLayout.Cell> cells)
	{
		JsonArray draw = new JsonArray();
		appendStrip(draw, cells);
		return draw;
	}

	private void appendStrip(JsonArray draw, List<CompactLayout.Cell> cells)
	{
		for (CompactLayout.Cell cell : cells)
		{
			if (cell.showsBar() && cell.stripX >= 0)
			{
				appendStripSegment(draw, cell);
			}
		}
	}

	/**
	 * One stat's share of the bottom row. The pixel the fill stops on is lit in proportion
	 * to how far into it the value reaches, so the bar answers a change of a percent or two
	 * instead of sitting still until it has earned a whole pixel.
	 */
	private void appendStripSegment(JsonArray draw, CompactLayout.Cell cell)
	{
		int row = CompactLayout.STRIP_ROW;
		int width = cell.stripWidth;
		Color color = cell.color == null ? Color.WHITE : cell.color;
		// Tinted rather than grey, so an empty segment still says which stat it belongs to.
		draw.add(drawCmd("rectFill", cell.stripX, row, width, 1, hex(brighten(color, TRACK_LEVEL))));

		// What the stat was a moment ago, left behind so a drop is visible as it happens.
		if (cell.ghostPercent > cell.percent)
		{
			int ghost = barFullPixels(cell.ghostPercent, width);
			int from = barFullPixels(cell.percent, width);
			if (ghost > from)
			{
				draw.add(drawCmd("rectFill", cell.stripX + from, row, ghost - from, 1,
					hex(brighten(color, GHOST_LEVEL))));
			}
		}

		int full = barFullPixels(cell.percent, width);
		if (full > 0)
		{
			draw.add(drawCmd("rectFill", cell.stripX, row, full, 1, hex(color)));
		}
		int partial = barPartialPercent(cell.percent, width);
		if (partial > 0 && full < width)
		{
			draw.add(drawCmd("pixel", cell.stripX + full, row, hex(brighten(color, partial / 100.0))));
		}
	}

	/**
	 * Whole pixels a 0-100 percent fills across a bar of this width.
	 */
	static int barFullPixels(int percent, int width)
	{
		if (percent <= 0 || width <= 0)
		{
			return 0;
		}
		return Math.min(width, Math.min(100, percent) * width / 100);
	}

	/**
	 * How far into the next pixel the value reaches, 0-99, which is the brightness that
	 * pixel is lit at. Anything above zero keeps at least a trace of a pixel showing.
	 */
	static int barPartialPercent(int percent, int width)
	{
		if (percent <= 0 || width <= 0)
		{
			return 0;
		}
		int remainder = (Math.min(100, percent) * width) % 100;
		if (remainder == 0)
		{
			return 0;
		}
		return Math.max(MIN_PARTIAL, remainder);
	}

	/**
	 * Text beside the activity icon, with the bottom row painted from a gradient up to the percent.
	 */
	void pushSkillProgress(List<TextFragment> fragments, int progressPercent, List<Color> gradient, String activityIcon)
	{
		JsonObject body = baseStatsApp();
		body.addProperty("font", "small");
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		body.add("text", fragmentsJson(fragments));
		if (progressPercent >= 0)
		{
			JsonArray palette = new JsonArray();
			for (Color stop : gradient)
			{
				palette.add(hex(stop));
			}
			body.add("palette", palette);
			body.addProperty("progress", progressPercent);
			body.addProperty("progressColor", "palette");
			body.addProperty("progressTrackColor", hex(TRACK));
			body.addProperty("textInFront", true);
		}
		putStats(body);
	}

	private JsonArray fragmentsJson(List<TextFragment> fragments)
	{
		JsonArray text = new JsonArray();
		for (TextFragment fragment : fragments)
		{
			JsonObject part = new JsonObject();
			part.addProperty("text", fragment.getText());
			part.addProperty("color", hex(fragment.getColor()));
			text.add(part);
		}
		return text;
	}

	void clearStats()
	{
		lastStatsBody.set(null);
		lastStatsSentMs.set(0L);
		enqueue("DELETE", "/api/v1/apps/" + APP_STATS, null, null);
	}

	void clearAll()
	{
		clearCache();
		restoreBrightness();
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_AFK, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_HP, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_PRAY, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_XP_DROP, null, null);
		enqueue("DELETE", "/api/v1/apps/" + APP_STATS, null, null);
		restoreLoop();
	}

	/**
	 * Put the rotation back the way it was before the stats app took it over.
	 * Nothing is sent if the plugin never changed it.
	 */
	private void restoreLoop()
	{
		loopSnapshotTried.set(false);
		if (!loopChanged.getAndSet(false))
		{
			return;
		}
		AppLoop saved = savedLoop.getAndSet(null);
		if (saved == null || saved.order.isEmpty())
		{
			restoreBuiltinLoop();
			return;
		}
		enqueue("PUT", "/api/v1/apps/order", saved.toJson().toString(), null);
	}

	private void restoreBuiltinLoop()
	{
		JsonObject body = new JsonObject();
		JsonArray order = new JsonArray();
		order.add("Time");
		order.add("Date");
		order.add("Temperature");
		order.add("Humidity");
		order.add("Battery");
		body.add("order", order);
		body.add("disabled", new JsonArray());
		enqueue("PUT", "/api/v1/apps/order", body.toString(), null);
	}

	private void putStats(JsonObject body)
	{
		body.addProperty("durationMs", 60_000);
		String json = body.toString();
		long now = System.currentTimeMillis();
		Long lastSent = lastStatsSentMs.get();
		boolean identical = json.equals(lastStatsBody.get());
		boolean stale = lastSent == null || now - lastSent >= STATS_REFRESH_MS;
		if (identical && !stale)
		{
			return;
		}

		lastStatsBody.set(json);
		lastStatsSentMs.set(now);
		enqueue("PUT", "/api/v1/apps/pushed/" + APP_STATS, json, ok ->
		{
			if (!ok)
			{
				lastStatsBody.set(null);
				lastStatsSentMs.set(0L);
				return;
			}
			ensureStatsLoop();
			activateStats();
		});
	}

	/**
	 * Holds the panel where the config asks, if it asks at all.
	 *
	 * The clock's light sensor takes a dark room a long way down and has no floor under it,
	 * so the dimmer half of a page stops being legible once the lights go out. What the
	 * clock was set to is read once and put back on the way out, the same as the rotation.
	 */
	void applyBrightness()
	{
		if (config.brightnessMode() != BrightnessMode.FIXED)
		{
			restoreBrightness();
			return;
		}

		JsonObject wanted = new JsonObject();
		wanted.addProperty("autoBrightness", false);
		wanted.addProperty("brightness", Math.max(1, Math.min(255, config.brightness())));
		String json = wanted.toString();
		if (json.equals(appliedBrightness.get()))
		{
			return;
		}

		if (savedBrightness.get() != null || brightnessSnapshotTried.getAndSet(true))
		{
			putBrightness(json);
			return;
		}
		send("GET", "/api/v1/settings", null, false, result ->
		{
			if (result.ok && result.body != null)
			{
				try
				{
					JsonObject settings = gson.fromJson(result.body, JsonObject.class);
					if (settings != null)
					{
						savedBrightness.compareAndSet(null, parseBrightness(settings));
					}
				}
				catch (JsonParseException ex)
				{
					log.debug("Could not read the clock's settings", ex);
				}
			}
			putBrightness(json);
		});
	}

	private void putBrightness(String json)
	{
		appliedBrightness.set(json);
		enqueue("PATCH", "/api/v1/settings", json, ok ->
		{
			if (!ok)
			{
				// Forgotten, so the next refresh tries again and the clock is not put
				// back to a setting it never left.
				appliedBrightness.set(null);
			}
		});
	}

	/**
	 * Puts the clock's own brightness back. Nothing is sent if we never changed it.
	 */
	private void restoreBrightness()
	{
		brightnessSnapshotTried.set(false);
		if (appliedBrightness.getAndSet(null) == null)
		{
			return;
		}
		Brightness saved = savedBrightness.getAndSet(null);
		if (saved == null)
		{
			return;
		}
		enqueue("PATCH", "/api/v1/settings", saved.toJson().toString(), null);
	}

	static Brightness parseBrightness(JsonObject settings)
	{
		JsonElement auto = settings.get("autoBrightness");
		JsonElement level = settings.get("brightness");
		return new Brightness(
			auto != null && auto.isJsonPrimitive() && auto.getAsBoolean(),
			level != null && level.isJsonPrimitive() ? level.getAsInt() : 120);
	}

	private void ensureStatsLoop()
	{
		if (Boolean.TRUE.equals(loopConfigured.get()))
		{
			return;
		}
		loopConfigured.set(true);

		if (savedLoop.get() != null || loopSnapshotTried.getAndSet(true))
		{
			putStatsLoop();
			return;
		}
		send("GET", "/api/v1/apps", null, false, result ->
		{
			if (result.ok && result.body != null)
			{
				try
				{
					JsonArray apps = gson.fromJson(result.body, JsonArray.class);
					if (apps != null)
					{
						savedLoop.compareAndSet(null, parseAppLoop(apps));
					}
				}
				catch (JsonParseException ex)
				{
					log.debug("Could not read the clock's app list", ex);
				}
			}
			putStatsLoop();
		});
	}

	private void putStatsLoop()
	{
		// Keep Time as a fallback so the panel never goes blank if osrs is removed.
		JsonObject body = new JsonObject();
		JsonArray order = new JsonArray();
		order.add(APP_STATS);
		order.add("Time");
		body.add("order", order);
		body.add("disabled", new JsonArray());

		loopChanged.set(true);
		enqueue("PUT", "/api/v1/apps/order", body.toString(), null);
	}

	/**
	 * The rotation from GET /api/v1/apps: apps with a slot in slot order, plus the switched-off ones.
	 * The stats app itself is left out, since it is deleted before the rotation is restored.
	 */
	static AppLoop parseAppLoop(JsonArray apps)
	{
		List<JsonObject> slotted = new ArrayList<>();
		List<String> disabled = new ArrayList<>();
		for (JsonElement element : apps)
		{
			if (!element.isJsonObject())
			{
				continue;
			}
			JsonObject app = element.getAsJsonObject();
			String name = stringOrNull(app, "name");
			if (name == null || APP_STATS.equals(name))
			{
				continue;
			}
			if (app.has("enabled") && !app.get("enabled").isJsonNull() && !app.get("enabled").getAsBoolean())
			{
				disabled.add(name);
			}
			else if (app.has("slot") && app.get("slot").isJsonPrimitive())
			{
				slotted.add(app);
			}
		}
		slotted.sort(Comparator.comparingInt(app -> app.get("slot").getAsInt()));
		List<String> order = new ArrayList<>();
		for (JsonObject app : slotted)
		{
			order.add(app.get("name").getAsString());
		}
		return new AppLoop(order, disabled);
	}

	private static String stringOrNull(JsonObject object, String key)
	{
		JsonElement value = object.get(key);
		return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
	}

	private void activateStats()
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", APP_STATS);
		body.addProperty("fast", true);
		enqueue("PUT", "/api/v1/apps/active", body.toString(), null);
	}

	private void applyActivityIcon(JsonObject body, String activityIcon)
	{
		if (activityIcon == null || activityIcon.isEmpty())
		{
			return;
		}
		body.addProperty("icon", pulseIcon(activityIcon));
		body.addProperty("iconMode", "fixed");
	}

	private JsonObject baseStatsApp()
	{
		JsonObject body = new JsonObject();
		body.addProperty("backgroundColor", "#000000");
		body.addProperty("textCase", "asTyped");
		body.addProperty("lifetimeMs", 0);
		return body;
	}

	private static JsonObject scrollStatic()
	{
		JsonObject scroll = new JsonObject();
		scroll.addProperty("mode", "static");
		return scroll;
	}

	private void enqueue(String method, String path, String jsonBody, Consumer<Boolean> done)
	{
		send(method, path, jsonBody, false, done == null ? null : result -> done.accept(result.ok));
	}

	/**
	 * The clock counts as reachable whenever it answers, even with a 404 for a notification
	 * that already expired. Only no answer at all, or a rejected login, counts as unreachable.
	 * Background requests report a change in the game chat; a test reports its own result.
	 */
	private void send(String method, String path, String jsonBody, boolean test, Consumer<Result> done)
	{
		String base = baseUrl();
		if (base == null)
		{
			log.debug("AWTRIX request skipped, no clock address: {} {}", method, path);
			if (done != null)
			{
				done.accept(Result.failed("no clock address"));
			}
			return;
		}

		Request.Builder builder = new Request.Builder().url(base + path);
		String username = config.username() == null ? "" : config.username().trim();
		String password = config.password() == null ? "" : config.password();
		if (!username.isEmpty())
		{
			builder.header("Authorization", Credentials.basic(username, password));
		}

		if (jsonBody != null)
		{
			builder.header("Content-Type", "application/json");
			builder.method(method, RequestBody.create(JSON, jsonBody));
		}
		else
		{
			builder.method(method, null);
		}

		httpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("AWTRIX request failed: {} {}", method, path, e);
				finish(Result.failed(describe(e)));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				Result result;
				try (Response ignored = response)
				{
					int code = response.code();
					if (response.isSuccessful())
					{
						String body = null;
						if ("GET".equals(method) && response.body() != null)
						{
							body = response.body().string();
						}
						result = Result.ok(body);
					}
					else
					{
						log.debug("AWTRIX {} {} -> {}", method, path, code);
						result = code == 401 || code == 403
							? Result.failed("the clock rejected the login (HTTP " + code + "), check the username and password")
							: Result.answered("HTTP " + code);
					}
				}
				catch (IOException ex)
				{
					log.debug("AWTRIX response failed: {} {}", method, path, ex);
					result = Result.failed(describe(ex));
				}
				finish(result);
			}

			private void finish(Result result)
			{
				Boolean before = reachable.getAndSet(result.reachable);
				if (!test && before != null && before != result.reachable)
				{
					messages.accept(result.reachable
						? "Connected to the clock again."
						: "Lost the clock: " + result.problem + ".");
				}
				else if (!test && before == null && !result.reachable)
				{
					messages.accept("Couldn't reach the clock: " + result.problem + ".");
				}
				if (done != null)
				{
					done.accept(result);
				}
			}
		});
	}

	static String describe(IOException e)
	{
		if (e instanceof UnknownHostException)
		{
			return "unknown host, check the address";
		}
		if (e instanceof SocketTimeoutException || e instanceof ConnectException || e instanceof NoRouteToHostException)
		{
			return "no answer, check the address and that the clock is on the same network";
		}
		// OkHttp's messages carry the host and port, which should not end up in the chat log.
		return "the connection failed (" + e.getClass().getSimpleName() + ")";
	}

	private String baseUrl()
	{
		String host = config.host() == null ? "" : config.host().trim();
		if (host.isEmpty())
		{
			return null;
		}
		if (!host.startsWith("http://") && !host.startsWith("https://"))
		{
			host = "http://" + host;
		}
		while (host.endsWith("/"))
		{
			host = host.substring(0, host.length() - 1);
		}
		return host;
	}

	static String toHex(Color color)
	{
		return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}

	static String truncate(String text, int maxChars)
	{
		if (text == null || text.isEmpty())
		{
			return "AFK";
		}
		String trimmed = text.trim();
		if (trimmed.length() <= maxChars)
		{
			return trimmed;
		}
		return trimmed.substring(0, maxChars);
	}

	static int percentOf(int current, int max)
	{
		if (max <= 0)
		{
			return 0;
		}
		return Math.max(0, Math.min(100, (int) ((current * 100L) / max)));
	}

	@Value
	static class TextFragment
	{
		String text;
		Color color;
	}

	static final class Brightness
	{
		final boolean automatic;
		final int level;

		Brightness(boolean automatic, int level)
		{
			this.automatic = automatic;
			this.level = level;
		}

		JsonObject toJson()
		{
			JsonObject body = new JsonObject();
			body.addProperty("autoBrightness", automatic);
			body.addProperty("brightness", level);
			return body;
		}
	}

	static final class AppLoop
	{
		final List<String> order;
		final List<String> disabled;

		AppLoop(List<String> order, List<String> disabled)
		{
			this.order = order;
			this.disabled = disabled;
		}

		JsonObject toJson()
		{
			JsonObject body = new JsonObject();
			JsonArray orderJson = new JsonArray();
			order.forEach(orderJson::add);
			JsonArray disabledJson = new JsonArray();
			disabled.forEach(disabledJson::add);
			body.add("order", orderJson);
			body.add("disabled", disabledJson);
			return body;
		}
	}

	private static final class Result
	{
		final boolean ok;
		final boolean reachable;
		final String problem;
		final String body;

		private Result(boolean ok, boolean reachable, String problem, String body)
		{
			this.ok = ok;
			this.reachable = reachable;
			this.problem = problem;
			this.body = body;
		}

		static Result ok(String body)
		{
			return new Result(true, true, null, body);
		}

		static Result answered(String problem)
		{
			return new Result(false, true, problem, null);
		}

		static Result failed(String problem)
		{
			return new Result(false, false, problem, null);
		}
	}
}
