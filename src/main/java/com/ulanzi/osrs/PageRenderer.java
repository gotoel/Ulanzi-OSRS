package com.ulanzi.osrs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/**
 * Draws one AWTRIX NG page (an app or a notification, as the plugin sends it) into a 32x8
 * frame at a moment in time, following the clock's own layering: background, then text and
 * drawings, the progress bar, and the icons on top.
 *
 * Only what the plugin sends is drawn, which is most of the page format: text in the small
 * and large fonts, coloured fragments, palettes, blinking, sideways scrolling, every draw
 * command, the progress bar, the Plasma background, and still or animated icons.
 */
final class PageRenderer
{
	static final int ICON_SIZE = 8;
	/** Text starts one column clear of the icon, where the clock puts it. */
	static final int ICON_TEXT_X = ICON_SIZE + 1;
	static final int SCROLL_PX_PER_SEC = 21;
	static final long DEFAULT_HOLD_MS = 1_000L;
	private static final int WHITE = 0xFFFFFF;
	private static final int DEFAULT_PROGRESS = 0x00FF00;
	private static final int DEFAULT_TRACK = 0xFFFFFF;

	private PageRenderer()
	{
	}

	/**
	 * @param elapsedMs how long this page has been on screen, which drives scrolling,
	 *                  blinking, and its icon's animation
	 * @param nowMs     wall time, which drives palettes and effects, so a page that is
	 *                  re-sent every frame still flows instead of restarting
	 */
	static void render(JsonObject page, long elapsedMs, long nowMs, int[] frame)
	{
		Frame.fill(frame, 0);
		if (page == null)
		{
			return;
		}

		background(page, nowMs, frame);

		GifIcon icon = GifIcon.of(string(page, "icon"));
		boolean iconColumn = icon != null && icon.width < Frame.WIDTH;
		if (icon != null && !iconColumn)
		{
			// A GIF as wide as the panel is a background, not an icon.
			icon.draw(frame, 0, 0, elapsedMs);
		}
		int areaStart = iconColumn ? ICON_TEXT_X : 0;

		boolean textInFront = bool(page, "textInFront", false);
		if (!textInFront)
		{
			text(page, areaStart, elapsedMs, nowMs, frame);
		}
		drawCommands(page, frame);
		progress(page, iconColumn, frame);
		if (textInFront)
		{
			text(page, areaStart, elapsedMs, nowMs, frame);
		}

		if (iconColumn)
		{
			icon.draw(frame, 0, 0, elapsedMs);
		}
		JsonElement extras = page.get("icons");
		if (extras != null && extras.isJsonArray())
		{
			for (JsonElement element : extras.getAsJsonArray())
			{
				if (!element.isJsonObject())
				{
					continue;
				}
				JsonObject extra = element.getAsJsonObject();
				GifIcon image = GifIcon.of(string(extra, "icon"));
				if (image != null)
				{
					image.draw(frame, integer(extra, "x", 0), integer(extra, "y", 0), elapsedMs);
				}
			}
		}
	}

	/**
	 * How long one pass of the page's scrolling text takes, which is what "repeat" counts.
	 * Zero for a page whose text stands still.
	 */
	static long runMs(JsonObject page)
	{
		GifIcon icon = GifIcon.of(string(page, "icon"));
		int areaStart = icon != null && icon.width < Frame.WIDTH ? ICON_TEXT_X : 0;
		Text text = Text.of(page);
		Scroll scroll = Scroll.of(page, text.width, Frame.WIDTH - areaStart);
		return scroll.moving ? scroll.runMs(areaStart, text.width) : 0L;
	}

	private static void background(JsonObject page, long nowMs, int[] frame)
	{
		String effect = string(page, "effect");
		if (effect != null && "plasma".equals(effect.trim().toLowerCase(Locale.ROOT)))
		{
			int[] palette = Palettes.of(page.get("palette"));
			if (palette == null)
			{
				palette = Palettes.named("Rainbow");
			}
			double speed = Math.max(0.1, Math.min(10.0, number(page, "effectSpeed", 1.0)));
			plasma(frame, palette, nowMs / 1000.0 * speed);
			return;
		}
		Frame.fill(frame, Frame.parseColor(string(page, "backgroundColor"), 0));
	}

	static void plasma(int[] frame, int[] palette, double t)
	{
		for (int y = 0; y < Frame.HEIGHT; y++)
		{
			for (int x = 0; x < Frame.WIDTH; x++)
			{
				double v = Math.sin(x * 0.35 + t)
					+ Math.sin(y * 0.6 + t * 1.3)
					+ Math.sin((x + y) * 0.25 + t * 0.7)
					+ Math.sin(Math.sqrt((x - 16) * (x - 16) + (y - 4) * (y - 4)) * 0.4 - t);
				Frame.set(frame, x, y, Palettes.sample(palette, (v + 4) / 8.0, true, true));
			}
		}
	}

	private static void text(JsonObject page, int areaStart, long elapsedMs, long nowMs, int[] frame)
	{
		Text text = Text.of(page);
		if (text.chars.length() == 0)
		{
			return;
		}
		int blinkMs = integer(page, "textBlinkMs", 0);
		if (blinkMs > 0 && Math.floorMod(elapsedMs, (long) blinkMs) < blinkMs / 2)
		{
			// The first half of each blink is dark, the second lit.
			return;
		}

		int areaWidth = Frame.WIDTH - areaStart;
		Scroll scroll = Scroll.of(page, text.width, areaWidth);
		int x = scroll.x(page, areaStart, text.width, elapsedMs);
		boolean large = "large".equals(string(page, "font"));
		int y = large ? PixelFont.LARGE_TOP : PixelFont.SMALL_TOP;

		PixelFont.Paint paint;
		if (text.palette)
		{
			int[] palette = Palettes.of(page.get("palette"));
			int span = integer(page, "paletteSpan", 0);
			double speed = Math.max(0, Math.min(10, number(page, "paletteSpeed", 0)));
			boolean blend = bool(page, "paletteBlend", true);
			double shift = nowMs / 1000.0 * speed;
			int textX = x;
			int width = Math.max(1, text.width);
			paint = (px, index) -> Palettes.sample(palette,
				(span > 0 ? (px - textX) / (double) span : (px - textX) / (double) width) - shift, true, blend);
		}
		else
		{
			int[] colors = text.colors;
			paint = (px, index) -> colors[index];
		}
		PixelFont.draw(frame, text.chars, x, y, large, paint, areaStart, Frame.WIDTH);
	}

	private static void drawCommands(JsonObject page, int[] frame)
	{
		JsonElement draw = page.get("draw");
		if (draw == null || !draw.isJsonArray())
		{
			return;
		}
		int fallback = Frame.parseColor(string(page, "textColor"), WHITE);
		for (JsonElement element : draw.getAsJsonArray())
		{
			if (element.isJsonArray())
			{
				drawCommand(element.getAsJsonArray(), fallback, frame);
			}
		}
	}

	static void drawCommand(JsonArray cmd, int fallback, int[] frame)
	{
		if (cmd.size() == 0 || !cmd.get(0).isJsonPrimitive())
		{
			return;
		}
		String op = cmd.get(0).getAsString();
		try
		{
			switch (op)
			{
				case "pixel":
					Frame.set(frame, at(cmd, 1), at(cmd, 2), color(cmd, 3, fallback));
					break;
				case "pixels":
				{
					int color = cmd.size() > 1 ? Frame.parseColor(cmd.get(1).getAsString(), fallback) : fallback;
					for (int i = 2; i + 1 < cmd.size(); i += 2)
					{
						Frame.set(frame, at(cmd, i), at(cmd, i + 1), color);
					}
					break;
				}
				case "line":
					line(frame, at(cmd, 1), at(cmd, 2), at(cmd, 3), at(cmd, 4), color(cmd, 5, fallback));
					break;
				case "rect":
				{
					int x = at(cmd, 1);
					int y = at(cmd, 2);
					int w = at(cmd, 3);
					int h = at(cmd, 4);
					int color = color(cmd, 5, fallback);
					if (w > 0 && h > 0)
					{
						line(frame, x, y, x + w - 1, y, color);
						line(frame, x, y + h - 1, x + w - 1, y + h - 1, color);
						line(frame, x, y, x, y + h - 1, color);
						line(frame, x + w - 1, y, x + w - 1, y + h - 1, color);
					}
					break;
				}
				case "rectFill":
				{
					int x = at(cmd, 1);
					int y = at(cmd, 2);
					int w = at(cmd, 3);
					int h = at(cmd, 4);
					int color = color(cmd, 5, fallback);
					for (int row = 0; row < h; row++)
					{
						for (int col = 0; col < w; col++)
						{
							Frame.set(frame, x + col, y + row, color);
						}
					}
					break;
				}
				case "circle":
					circle(frame, at(cmd, 1), at(cmd, 2), at(cmd, 3), color(cmd, 4, fallback), false);
					break;
				case "circleFill":
					circle(frame, at(cmd, 1), at(cmd, 2), at(cmd, 3), color(cmd, 4, fallback), true);
					break;
				case "text":
				{
					String text = cmd.size() > 3 ? cmd.get(3).getAsString() : "";
					int color = color(cmd, 4, fallback);
					PixelFont.draw(frame, text, at(cmd, 1), at(cmd, 2), false, (px, i) -> color, 0, Frame.WIDTH);
					break;
				}
				case "bitmap":
					bitmap(frame, cmd);
					break;
				default:
					break;
			}
		}
		catch (RuntimeException ex)
		{
			// A malformed command draws nothing, the way the clock skips it.
		}
	}

	private static void bitmap(int[] frame, JsonArray cmd)
	{
		int x = at(cmd, 1);
		int y = at(cmd, 2);
		int w = at(cmd, 3);
		int h = at(cmd, 4);
		if (w <= 0 || h <= 0 || cmd.size() < 6)
		{
			return;
		}
		JsonElement data = cmd.get(5);
		if (data.isJsonArray())
		{
			JsonArray colors = data.getAsJsonArray();
			for (int i = 0; i < Math.min(colors.size(), w * h); i++)
			{
				JsonElement c = colors.get(i);
				int rgb = c.isJsonPrimitive() && c.getAsJsonPrimitive().isNumber()
					? c.getAsInt() : Frame.parseColor(c.getAsString(), 0);
				Frame.set(frame, x + i % w, y + i / w, rgb);
			}
		}
		else if (data.isJsonPrimitive())
		{
			byte[] bytes = java.util.Base64.getDecoder().decode(data.getAsString());
			for (int i = 0; i < w * h && i * 3 + 2 < bytes.length; i++)
			{
				int rgb = ((bytes[i * 3] & 0xFF) << 16) | ((bytes[i * 3 + 1] & 0xFF) << 8) | (bytes[i * 3 + 2] & 0xFF);
				Frame.set(frame, x + i % w, y + i / w, rgb);
			}
		}
	}

	private static void line(int[] frame, int x0, int y0, int x1, int y1, int color)
	{
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		int x = x0;
		int y = y0;
		for (int guard = 0; guard < 512; guard++)
		{
			Frame.set(frame, x, y, color);
			if (x == x1 && y == y1)
			{
				return;
			}
			int e2 = 2 * err;
			if (e2 >= dy)
			{
				err += dy;
				x += sx;
			}
			if (e2 <= dx)
			{
				err += dx;
				y += sy;
			}
		}
	}

	private static void circle(int[] frame, int cx, int cy, int r, int color, boolean fill)
	{
		if (r < 0)
		{
			return;
		}
		for (int y = -r; y <= r; y++)
		{
			for (int x = -r; x <= r; x++)
			{
				double d = Math.sqrt(x * x + y * y);
				boolean inside = d <= r + 0.5;
				boolean edge = inside && d > r - 0.5;
				if (fill ? inside : edge)
				{
					Frame.set(frame, cx + x, cy + y, color);
				}
			}
		}
	}

	private static void progress(JsonObject page, boolean iconColumn, int[] frame)
	{
		int percent = integer(page, "progress", -1);
		if (percent < 0)
		{
			return;
		}
		percent = Math.min(100, percent);
		int start = iconColumn ? ICON_SIZE : 0;
		int width = Frame.WIDTH - start;
		int filled = percent * width / 100;
		int track = Frame.parseColor(string(page, "progressTrackColor"), DEFAULT_TRACK);
		String fillName = string(page, "progressColor");
		boolean palette = "palette".equals(fillName);
		int[] colors = palette ? Palettes.of(page.get("palette")) : null;
		int fill = palette ? WHITE : Frame.parseColor(fillName, DEFAULT_PROGRESS);
		int row = Frame.HEIGHT - 1;
		for (int i = 0; i < width; i++)
		{
			int color = track;
			if (i < filled)
			{
				color = palette ? Palettes.sample(colors, width <= 1 ? 0 : i / (double) (width - 1), false, true) : fill;
			}
			Frame.set(frame, start + i, row, color);
		}
	}

	/** The page's text as characters and a colour for each, from a string or from fragments. */
	private static final class Text
	{
		final String chars;
		final int[] colors;
		final boolean palette;
		final int width;

		private Text(String chars, int[] colors, boolean palette)
		{
			this.chars = chars;
			this.colors = colors;
			this.palette = palette;
			this.width = PixelFont.width(chars);
		}

		static Text of(JsonObject page)
		{
			String textColor = string(page, "textColor");
			boolean palette = "palette".equals(textColor);
			int base = palette ? WHITE : Frame.parseColor(textColor, WHITE);
			boolean upper = !"asTyped".equals(string(page, "textCase"));

			JsonElement text = page.get("text");
			StringBuilder chars = new StringBuilder();
			java.util.List<Integer> colors = new java.util.ArrayList<>();
			if (text != null && text.isJsonArray())
			{
				for (JsonElement element : text.getAsJsonArray())
				{
					if (!element.isJsonObject())
					{
						continue;
					}
					JsonObject part = element.getAsJsonObject();
					String piece = string(part, "text");
					if (piece == null)
					{
						continue;
					}
					int color = Frame.parseColor(string(part, "color"), WHITE);
					for (char c : piece.toCharArray())
					{
						chars.append(c);
						colors.add(color);
					}
				}
			}
			else if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString())
			{
				for (char c : text.getAsString().toCharArray())
				{
					chars.append(c);
					colors.add(base);
				}
			}
			String out = upper ? chars.toString().toUpperCase(Locale.ROOT) : chars.toString();
			int[] colorArray = new int[colors.size()];
			for (int i = 0; i < colorArray.length; i++)
			{
				colorArray[i] = colors.get(i);
			}
			return new Text(out, colorArray, palette);
		}
	}

	/** Where the text sits at a moment, by the page's scroll settings. */
	private static final class Scroll
	{
		final boolean moving;
		final boolean rightward;
		final boolean offscreen;
		final double pxPerSec;
		final long holdMs;

		private Scroll(boolean moving, boolean rightward, boolean offscreen, double pxPerSec, long holdMs)
		{
			this.moving = moving;
			this.rightward = rightward;
			this.offscreen = offscreen;
			this.pxPerSec = pxPerSec;
			this.holdMs = holdMs;
		}

		static Scroll of(JsonObject page, int textWidth, int areaWidth)
		{
			JsonElement element = page.get("scroll");
			JsonObject scroll = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
			String mode = element != null && element.isJsonPrimitive() ? element.getAsString() : string(scroll, "mode");
			if (mode == null)
			{
				mode = "wrap";
			}
			int speed = integer(scroll, "speed", 100);
			boolean fits = textWidth <= areaWidth;
			boolean scrollWhenFits = "scroll".equals(string(scroll, "whenFits"));
			boolean moving = !"static".equals(mode) && speed > 0 && (!fits || scrollWhenFits);
			return new Scroll(moving,
				"right".equals(string(scroll, "direction")),
				"offscreen".equals(string(scroll, "entry")),
				SCROLL_PX_PER_SEC * speed / 100.0,
				integer(scroll, "holdMs", (int) DEFAULT_HOLD_MS));
		}

		int x(JsonObject page, int areaStart, int textWidth, long elapsedMs)
		{
			int areaWidth = Frame.WIDTH - areaStart;
			int offset = integer(page, "textOffsetX", 0);
			if (!moving)
			{
				String align = string(page, "textAlign");
				boolean center = align == null ? !Boolean.FALSE.equals(boolOrNull(page, "textCenter")) : "center".equals(align);
				if (textWidth > areaWidth || "start".equals(align) || !center)
				{
					return areaStart + offset;
				}
				if ("end".equals(align))
				{
					return Frame.WIDTH - textWidth + offset;
				}
				return areaStart + (areaWidth - textWidth) / 2 + offset;
			}

			int start = start(areaStart, textWidth);
			int travel = travel(areaStart, textWidth);
			long run = runMs(areaStart, textWidth);
			long t = Math.floorMod(elapsedMs, Math.max(1L, run));
			double moved = t < holdMs ? 0 : (t - holdMs) * pxPerSec / 1000.0;
			moved = Math.min(travel, moved);
			int x = rightward ? (int) Math.round(start + moved) : (int) Math.round(start - moved);
			return x + offset;
		}

		private int start(int areaStart, int textWidth)
		{
			if (rightward)
			{
				return offscreen ? areaStart - textWidth : areaStart;
			}
			return offscreen ? Frame.WIDTH : areaStart;
		}

		private int travel(int areaStart, int textWidth)
		{
			int end = rightward ? Frame.WIDTH : areaStart - textWidth;
			return Math.abs(end - start(areaStart, textWidth));
		}

		long runMs(int areaStart, int textWidth)
		{
			return holdMs + (long) Math.ceil(travel(areaStart, textWidth) * 1000.0 / pxPerSec);
		}
	}

	private static int at(JsonArray cmd, int index)
	{
		return cmd.get(index).getAsInt();
	}

	private static int color(JsonArray cmd, int index, int fallback)
	{
		if (cmd.size() <= index || !cmd.get(index).isJsonPrimitive())
		{
			return fallback;
		}
		return Frame.parseColor(cmd.get(index).getAsString(), fallback);
	}

	static String string(JsonObject object, String key)
	{
		JsonElement value = object == null ? null : object.get(key);
		return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
	}

	static int integer(JsonObject object, String key, int fallback)
	{
		JsonElement value = object == null ? null : object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
		{
			return fallback;
		}
		return (int) Math.round(value.getAsDouble());
	}

	private static double number(JsonObject object, String key, double fallback)
	{
		JsonElement value = object == null ? null : object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
		{
			return fallback;
		}
		return value.getAsDouble();
	}

	static boolean bool(JsonObject object, String key, boolean fallback)
	{
		Boolean value = boolOrNull(object, key);
		return value == null ? fallback : value;
	}

	private static Boolean boolOrNull(JsonObject object, String key)
	{
		JsonElement value = object == null ? null : object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
		{
			return null;
		}
		return value.getAsBoolean();
	}
}
