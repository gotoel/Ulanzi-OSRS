package com.ulanzi.osrs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String AFK_RTTTL = "afk:d=4,o=5,b=160:c6,p,c6";
	private static final String HP_RTTTL = "lowhp:d=16,o=6,b=200:c,e,g,c7";
	private static final String PRAY_RTTTL = "lowpray:d=8,o=5,b=140:e,c,e,c";

	private final OkHttpClient httpClient;
	private final UlanziConfig config;

	private final AtomicReference<String> lastStatsBody = new AtomicReference<>();
	private final AtomicReference<String> lastAfkBody = new AtomicReference<>();
	private final AtomicReference<Long> lastStatsSentMs = new AtomicReference<>(0L);
	private final AtomicReference<Boolean> loopConfigured = new AtomicReference<>(false);

	private static final long STATS_REFRESH_MS = 8_000L;

	@Inject
	AwtrixClient(OkHttpClient httpClient, UlanziConfig config)
	{
		this.httpClient = httpClient;
		this.config = config;
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
		enqueue("POST", "/api/v1/notifications", body.toString(), null);
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
			body.addProperty("textColor", toHex(config.afkTextColor()));
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
			body.addProperty("textColor", toHex(config.afkTextColor()));
		}
		else if (effect.usesPaletteText())
		{
			body.addProperty("backgroundColor", toHex(config.afkBackgroundColor()));
			body.addProperty("palette", effect.paletteName());
			body.addProperty("textColor", "palette");
			body.addProperty("paletteSpan", 16);
			body.addProperty("paletteSpeed", Math.max(0.1, config.afkWaveSpeed() / 10.0));
			body.addProperty("paletteBlend", true);
		}
		else
		{
			body.addProperty("backgroundColor", toHex(config.afkBackgroundColor()));
			body.addProperty("textColor", toHex(config.afkTextColor()));
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
		body.addProperty("textColor", toHex(textColor));
		body.addProperty("backgroundColor", toHex(background));
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
		body.addProperty("textColor", toHex(color));
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		if (background != null)
		{
			body.addProperty("backgroundColor", toHex(background));
		}

		if (progressPercent >= 0)
		{
			body.addProperty("progress", progressPercent);
			body.addProperty("progressColor", toHex(progressColor));
			body.addProperty("progressTrackColor", "#202020");
			body.addProperty("textInFront", true);
		}

		putStats(body);
	}

	void pushCompactStats(List<TextFragment> fragments, int hpPercent, Color hpColor)
	{
		pushCompactStats(fragments, hpPercent, hpColor, null, null);
	}

	void pushCompactStats(List<TextFragment> fragments, int hpPercent, Color hpColor, Color background)
	{
		pushCompactStats(fragments, hpPercent, hpColor, background, null);
	}

	void pushCompactStats(List<TextFragment> fragments, int hpPercent, Color hpColor, Color background,
		String activityIcon)
	{
		pushCompactStats(fragments, hpPercent, hpColor, background, activityIcon, -1, null, -1, null);
	}

	void pushCompactStats(List<TextFragment> fragments, int hpPercent, Color hpColor, Color background,
		String activityIcon, int energyPercent, Color energyColor)
	{
		pushCompactStats(fragments, hpPercent, hpColor, background, activityIcon, -1, null, energyPercent, energyColor);
	}

	void pushCompactStats(List<TextFragment> fragments, int hpPercent, Color hpColor, Color background,
		String activityIcon, int prayerPercent, Color prayerColor, int energyPercent, Color energyColor)
	{
		JsonObject body = baseStatsApp();
		body.addProperty("font", "small");
		body.addProperty("textCenter", true);
		body.add("scroll", scrollStatic());
		applyActivityIcon(body, activityIcon);
		int barCount = (prayerPercent >= 0 ? 1 : 0) + (energyPercent >= 0 ? 1 : 0);
		if (barCount > 0)
		{
			// Keep the centered line off the 2px columns at the right edge.
			body.addProperty("textOffsetX", -2 * barCount);
			body.add("draw", verticalColumns(prayerPercent, prayerColor, energyPercent, energyColor));
		}
		if (background != null)
		{
			body.addProperty("backgroundColor", toHex(background));
		}

		JsonArray text = new JsonArray();
		for (TextFragment fragment : fragments)
		{
			JsonObject part = new JsonObject();
			part.addProperty("text", fragment.getText());
			part.addProperty("color", toHex(fragment.getColor()));
			text.add(part);
		}
		body.add("text", text);

		if (hpPercent >= 0)
		{
			body.addProperty("progress", hpPercent);
			body.addProperty("progressColor", toHex(hpColor));
			body.addProperty("progressTrackColor", "#202020");
		}
		if (hpPercent >= 0 || prayerPercent >= 0 || energyPercent >= 0)
		{
			body.addProperty("textInFront", true);
		}

		putStats(body);
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
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_AFK, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_HP, null, null);
		enqueue("DELETE", "/api/v1/notifications/" + NOTIF_LOW_PRAY, null, null);
		enqueue("DELETE", "/api/v1/apps/" + APP_STATS, null, null);
		restoreBuiltinLoop();
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

	private void ensureStatsLoop()
	{
		if (Boolean.TRUE.equals(loopConfigured.get()))
		{
			return;
		}
		loopConfigured.set(true);

		// Keep builtins as fallback so the panel never goes blank if osrs is removed.
		JsonObject body = new JsonObject();
		JsonArray order = new JsonArray();
		order.add(APP_STATS);
		order.add("Time");
		body.add("order", order);
		body.add("disabled", new JsonArray());

		enqueue("PUT", "/api/v1/apps/order", body.toString(), null);
	}

	private void activateStats()
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", APP_STATS);
		body.addProperty("fast", true);
		enqueue("PUT", "/api/v1/apps/active", body.toString(), null);
	}

	/**
	 * How many rows of an 8px column to fill for a 0–100 energy percent.
	 * Any non-zero value shows at least one pixel.
	 */
	static int energyColumnRows(int percent)
	{
		if (percent <= 0)
		{
			return 0;
		}
		int filled = (Math.min(100, percent) * 8 + 4) / 100;
		if (filled <= 0)
		{
			return 1;
		}
		return Math.min(8, filled);
	}

	/**
	 * Prayer sits just left of energy. A single bar uses the right edge.
	 */
	private static JsonArray verticalColumns(int prayerPercent, Color prayerColor, int energyPercent, Color energyColor)
	{
		JsonArray draw = new JsonArray();
		int x = 30;
		if (energyPercent >= 0)
		{
			appendVerticalBar(draw, x, energyPercent, energyColor == null ? new Color(0xFF_D4_00) : energyColor);
			x -= 2;
		}
		if (prayerPercent >= 0)
		{
			appendVerticalBar(draw, x, prayerPercent, prayerColor == null ? new Color(0x4F_A3_FF) : prayerColor);
		}
		return draw;
	}

	private static void appendVerticalBar(JsonArray draw, int x, int percent, Color color)
	{
		int filled = energyColumnRows(percent);
		draw.add(drawCmd("rectFill", x, 0, 2, 8, "#202020"));
		if (filled > 0)
		{
			draw.add(drawCmd("rectFill", x, 8 - filled, 2, filled, toHex(color)));
		}
	}

	private static void applyActivityIcon(JsonObject body, String activityIcon)
	{
		if (activityIcon == null || activityIcon.isEmpty())
		{
			return;
		}
		body.addProperty("icon", activityIcon);
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
		String base = baseUrl();
		if (base == null)
		{
			log.debug("AWTRIX request skipped, no clock address: {} {}", method, path);
			if (done != null)
			{
				done.accept(false);
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
				if (done != null)
				{
					done.accept(false);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					boolean ok = response.isSuccessful();
					if (!ok)
					{
						log.debug("AWTRIX {} {} -> {}", method, path, response.code());
					}
					if (done != null)
					{
						done.accept(ok);
					}
				}
			}
		});
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

	static Color hpColor(int current, int max)
	{
		if (max <= 0)
		{
			return Color.GREEN;
		}
		double ratio = (double) current / (double) max;
		if (ratio > 0.5)
		{
			return Color.GREEN;
		}
		if (ratio > 0.25)
		{
			return Color.YELLOW;
		}
		return Color.RED;
	}

	@Value
	static class TextFragment
	{
		String text;
		Color color;
	}
}
