package com.ulanzi.osrs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Colour palettes for the virtual overlay: the clock's eight named ones, or a list of colours
 * sent with the page. The clock does not publish the colours behind its names, so these are
 * close matches drawn from the descriptions in its documentation.
 */
final class Palettes
{
	private static final Map<String, int[]> NAMED = new HashMap<>();

	static
	{
		NAMED.put("rainbow", new int[] {
			0xFF0000, 0xD52A00, 0xAB5500, 0xAB7F00, 0xABAB00, 0x56D500, 0x00FF00, 0x00D52A,
			0x00AB55, 0x0056AA, 0x0000FF, 0x2A00D5, 0x5500AB, 0x7F0081, 0xAB0055, 0xD5002B});
		NAMED.put("lava", new int[] {
			0x000000, 0x800000, 0x000000, 0x800000, 0x8B0000, 0x800000, 0x8B0000, 0x8B0000,
			0x8B0000, 0xFF0000, 0xFFA500, 0xFFFFFF, 0xFFA500, 0xFF0000, 0x8B0000, 0x000000});
		NAMED.put("ocean", new int[] {
			0x191970, 0x00008B, 0x191970, 0x000080, 0x00008B, 0x0000CD, 0x2E8B57, 0x008080,
			0x5F9EA0, 0x0000FF, 0x008B8B, 0x6495ED, 0x7FFFD4, 0x2E8B57, 0x00FFFF, 0x87CEFA});
		NAMED.put("forest", new int[] {
			0x006400, 0x006400, 0x556B2F, 0x006400, 0x008000, 0x228B22, 0x6B8E23, 0x008000,
			0x2E8B57, 0x66CDAA, 0x32CD32, 0x9ACD32, 0x90EE90, 0x7CFC00, 0x66CDAA, 0x228B22});
		NAMED.put("cloud", new int[] {
			0x0000FF, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B,
			0x0000FF, 0x00008B, 0x87CEEB, 0x87CEEB, 0xADD8E6, 0xFFFFFF, 0xADD8E6, 0x87CEEB});
		NAMED.put("party", new int[] {
			0x5500AB, 0x84007C, 0xB5004B, 0xE5001B, 0xE81700, 0xB84700, 0xAB7700, 0xABAB00,
			0xAB5500, 0xDD2200, 0xF2000E, 0xC2003E, 0x8F0071, 0x5F00A1, 0x2F00D0, 0x0007F9});
		NAMED.put("heat", new int[] {
			0x000000, 0x330000, 0x660000, 0x990000, 0xCC0000, 0xFF0000, 0xFF3300, 0xFF6600,
			0xFF9900, 0xFFCC00, 0xFFFF00, 0xFFFF33, 0xFFFF66, 0xFFFF99, 0xFFFFCC, 0xFFFFFF});
		NAMED.put("stripe", new int[] {
			0xFF0000, 0x000000, 0xFFA500, 0x000000, 0xFFFF00, 0x000000, 0x00FF00, 0x000000,
			0x00FFFF, 0x000000, 0x0000FF, 0x000000, 0x8A2BE2, 0x000000, 0xFF00FF, 0x000000});
	}

	private Palettes()
	{
	}

	static int[] named(String name)
	{
		return name == null ? null : NAMED.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * The palette a page asks for: a name, a list of "#RRGGBB" colours, or a list of
	 * {color, pos} stops. Null when there is none or it cannot be read.
	 */
	static int[] of(JsonElement palette)
	{
		if (palette == null || palette.isJsonNull())
		{
			return null;
		}
		if (palette.isJsonPrimitive())
		{
			return named(palette.getAsString());
		}
		if (!palette.isJsonArray())
		{
			return null;
		}
		JsonArray array = palette.getAsJsonArray();
		List<Integer> colors = new ArrayList<>();
		for (JsonElement element : array)
		{
			if (element.isJsonPrimitive())
			{
				colors.add(Frame.parseColor(element.getAsString(), 0xFFFFFF));
			}
			else if (element.isJsonObject())
			{
				JsonObject stop = element.getAsJsonObject();
				JsonElement color = stop.get("color");
				if (color != null && color.isJsonPrimitive())
				{
					colors.add(Frame.parseColor(color.getAsString(), 0xFFFFFF));
				}
			}
		}
		if (colors.isEmpty())
		{
			return null;
		}
		int[] out = new int[colors.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = colors.get(i);
		}
		return out;
	}

	/**
	 * The colour at a position. With wrap the palette is a loop, so 1.0 is back at the start
	 * and moving text flows through it; without, 0 is the first colour and 1 the last, which
	 * is what a bar filling up wants.
	 */
	static int sample(int[] palette, double position, boolean wrap, boolean blend)
	{
		if (palette == null || palette.length == 0)
		{
			return 0xFFFFFF;
		}
		if (palette.length == 1)
		{
			return palette[0];
		}
		int n = palette.length;
		double scaled;
		if (wrap)
		{
			double p = position - Math.floor(position);
			scaled = p * n;
		}
		else
		{
			double p = Math.max(0, Math.min(1, position));
			scaled = p * (n - 1);
		}
		int index = (int) Math.floor(scaled);
		if (!blend)
		{
			return palette[Math.min(n - 1, index)];
		}
		int next = wrap ? (index + 1) % n : Math.min(n - 1, index + 1);
		return Frame.mix(palette[index % n], palette[next], scaled - index);
	}
}
