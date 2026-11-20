package com.ulanzi.osrs;

/**
 * The virtual panel's pixels: 32 by 8, row by row, each an 0xRRGGBB colour. Writes that land
 * off the panel are dropped, the way the clock clips anything drawn past its edges.
 */
final class Frame
{
	static final int WIDTH = 32;
	static final int HEIGHT = 8;

	private Frame()
	{
	}

	static int[] blank()
	{
		return new int[WIDTH * HEIGHT];
	}

	static void set(int[] frame, int x, int y, int rgb)
	{
		if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT)
		{
			return;
		}
		frame[y * WIDTH + x] = rgb & 0xFFFFFF;
	}

	static int get(int[] frame, int x, int y)
	{
		return frame[y * WIDTH + x];
	}

	static void fill(int[] frame, int rgb)
	{
		java.util.Arrays.fill(frame, rgb & 0xFFFFFF);
	}

	/** "#RRGGBB", or the fallback for anything else. */
	static int parseColor(String text, int fallback)
	{
		if (text == null)
		{
			return fallback;
		}
		String hex = text.trim();
		if (hex.startsWith("#"))
		{
			hex = hex.substring(1);
		}
		if (hex.length() != 6)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(hex, 16);
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	static int scale(int rgb, double factor)
	{
		int r = clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor));
		int g = clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor));
		int b = clamp((int) Math.round((rgb & 0xFF) * factor));
		return (r << 16) | (g << 8) | b;
	}

	static int mix(int from, int to, double amount)
	{
		double t = Math.max(0, Math.min(1, amount));
		int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
		int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
		int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static int clamp(int channel)
	{
		return Math.max(0, Math.min(255, channel));
	}

	/** The frame as rows of '#' for lit and '.' for dark, which is how tests compare pages. */
	static String toAscii(int[] frame)
	{
		StringBuilder out = new StringBuilder();
		for (int y = 0; y < HEIGHT; y++)
		{
			for (int x = 0; x < WIDTH; x++)
			{
				out.append(get(frame, x, y) == 0 ? '.' : '#');
			}
			out.append('\n');
		}
		return out.toString();
	}
}
