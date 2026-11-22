package com.ulanzi.osrs;

import java.util.HashMap;
import java.util.Map;

/**
 * The virtual overlay's text, drawn to the same measure as the clock's own fonts so a page
 * laid out for the clock lands in the same place: every glyph three columns wide on a
 * four-pixel advance, a space two pixels, and no trailing column.
 *
 * The small font is five rows tall and sits on rows 1 to 5, like the clock's. The large one
 * is seven rows tall on rows 0 to 6, drawn here by stretching the small glyphs, which keeps
 * the two looking like one family. Lowercase is drawn as capitals, apart from the k and m
 * that shorten large numbers.
 */
final class PixelFont
{
	static final int ADVANCE = 4;
	static final int SPACE_ADVANCE = 2;
	static final int SMALL_HEIGHT = 5;
	static final int LARGE_HEIGHT = 7;
	static final int SMALL_TOP = 1;
	static final int LARGE_TOP = 0;
	/** Which small row each large row is copied from. */
	private static final int[] LARGE_ROWS = {0, 1, 1, 2, 3, 3, 4};

	private static final Map<Character, String[]> GLYPHS = new HashMap<>();
	private static final String[] UNKNOWN = {"###", "..#", ".##", "...", ".#."};

	static
	{
		glyph('0', "###", "#.#", "#.#", "#.#", "###");
		glyph('1', ".#.", "##.", ".#.", ".#.", "###");
		glyph('2', "###", "..#", "###", "#..", "###");
		glyph('3', "###", "..#", ".##", "..#", "###");
		glyph('4', "#.#", "#.#", "###", "..#", "..#");
		glyph('5', "###", "#..", "###", "..#", "###");
		glyph('6', "###", "#..", "###", "#.#", "###");
		glyph('7', "###", "..#", ".#.", ".#.", ".#.");
		glyph('8', "###", "#.#", "###", "#.#", "###");
		glyph('9', "###", "#.#", "###", "..#", "###");
		glyph('A', ".#.", "#.#", "###", "#.#", "#.#");
		glyph('B', "##.", "#.#", "##.", "#.#", "##.");
		glyph('C', ".##", "#..", "#..", "#..", ".##");
		glyph('D', "##.", "#.#", "#.#", "#.#", "##.");
		glyph('E', "###", "#..", "##.", "#..", "###");
		glyph('F', "###", "#..", "##.", "#..", "#..");
		glyph('G', ".##", "#..", "#.#", "#.#", ".##");
		glyph('H', "#.#", "#.#", "###", "#.#", "#.#");
		glyph('I', "###", ".#.", ".#.", ".#.", "###");
		glyph('J', "..#", "..#", "..#", "#.#", ".#.");
		glyph('K', "#.#", "#.#", "##.", "#.#", "#.#");
		glyph('L', "#..", "#..", "#..", "#..", "###");
		glyph('M', "#.#", "###", "###", "#.#", "#.#");
		glyph('N', "##.", "#.#", "#.#", "#.#", "#.#");
		glyph('O', ".#.", "#.#", "#.#", "#.#", ".#.");
		glyph('P', "##.", "#.#", "##.", "#..", "#..");
		glyph('Q', ".#.", "#.#", "#.#", "##.", ".##");
		glyph('R', "##.", "#.#", "##.", "#.#", "#.#");
		glyph('S', ".##", "#..", ".#.", "..#", "##.");
		glyph('T', "###", ".#.", ".#.", ".#.", ".#.");
		glyph('U', "#.#", "#.#", "#.#", "#.#", "###");
		glyph('V', "#.#", "#.#", "#.#", "#.#", ".#.");
		glyph('W', "#.#", "#.#", "###", "###", "#.#");
		glyph('X', "#.#", "#.#", ".#.", "#.#", "#.#");
		glyph('Y', "#.#", "#.#", ".#.", ".#.", ".#.");
		glyph('Z', "###", "..#", ".#.", "#..", "###");
		glyph('k', "#..", "#.#", "##.", "##.", "#.#");
		glyph('m', "...", "###", "###", "#.#", "#.#");
		glyph(' ', "", "", "", "", "");
		glyph('+', "...", ".#.", "###", ".#.", "...");
		glyph('-', "...", "...", "###", "...", "...");
		glyph('=', "...", "###", "...", "###", "...");
		glyph('.', "...", "...", "...", "...", ".#.");
		glyph(',', "...", "...", "...", ".#.", "#..");
		glyph(':', "...", ".#.", "...", ".#.", "...");
		glyph(';', "...", ".#.", "...", ".#.", "#..");
		glyph('!', ".#.", ".#.", ".#.", "...", ".#.");
		glyph('?', "###", "..#", ".##", "...", ".#.");
		glyph('/', "..#", "..#", ".#.", "#..", "#..");
		glyph('\\', "#..", "#..", ".#.", "..#", "..#");
		glyph('%', "#.#", "..#", ".#.", "#..", "#.#");
		glyph('\'', ".#.", ".#.", "...", "...", "...");
		glyph('"', "#.#", "#.#", "...", "...", "...");
		glyph('(', "..#", ".#.", ".#.", ".#.", "..#");
		glyph(')', "#..", ".#.", ".#.", ".#.", "#..");
		glyph('[', ".##", ".#.", ".#.", ".#.", ".##");
		glyph(']', "##.", ".#.", ".#.", ".#.", "##.");
		glyph('#', "#.#", "###", "#.#", "###", "#.#");
		glyph('*', "#.#", ".#.", "#.#", "...", "...");
		glyph('<', "..#", ".#.", "#..", ".#.", "..#");
		glyph('>', "#..", ".#.", "..#", ".#.", "#..");
		glyph('_', "...", "...", "...", "...", "###");
		glyph('^', ".#.", "#.#", "...", "...", "...");
		glyph('&', ".#.", "#.#", ".#.", "#.#", ".##");
		glyph('@', "###", "#.#", "#.#", "#..", "###");
		glyph('$', ".##", "##.", ".#.", ".##", "##.");
		glyph('|', ".#.", ".#.", ".#.", ".#.", ".#.");
	}

	/** Colours one glyph pixel, given the panel column it lands on and which character it is. */
	interface Paint
	{
		int at(int x, int index);
	}

	private PixelFont()
	{
	}

	private static void glyph(char c, String... rows)
	{
		GLYPHS.put(c, rows);
	}

	static int advance(char c)
	{
		return c == ' ' ? SPACE_ADVANCE : ADVANCE;
	}

	static int width(CharSequence text)
	{
		int width = 0;
		for (int i = 0; i < text.length(); i++)
		{
			width += advance(text.charAt(i));
		}
		return Math.max(0, width - 1);
	}

	static int height(boolean large)
	{
		return large ? LARGE_HEIGHT : SMALL_HEIGHT;
	}

	static boolean known(char c)
	{
		return GLYPHS.containsKey(Character.toUpperCase(c));
	}

	private static String[] rows(char c)
	{
		String[] rows = GLYPHS.get(c);
		if (rows == null)
		{
			rows = GLYPHS.get(Character.toUpperCase(c));
		}
		return rows == null ? UNKNOWN : rows;
	}

	/**
	 * Draws text with its top-left at (x, y), keeping to columns clipLeft up to but not
	 * including clipRight.
	 */
	static void draw(int[] frame, CharSequence text, int x, int y, boolean large, Paint paint,
		int clipLeft, int clipRight)
	{
		int penX = x;
		for (int index = 0; index < text.length(); index++)
		{
			char c = text.charAt(index);
			if (c != ' ')
			{
				String[] rows = rows(c);
				int height = height(large);
				for (int row = 0; row < height; row++)
				{
					String line = rows[large ? LARGE_ROWS[row] : row];
					for (int col = 0; col < line.length(); col++)
					{
						if (line.charAt(col) != '#')
						{
							continue;
						}
						int px = penX + col;
						if (px < clipLeft || px >= clipRight)
						{
							continue;
						}
						Frame.set(frame, px, y + row, paint.at(px, index));
					}
				}
			}
			penX += advance(c);
		}
	}
}
