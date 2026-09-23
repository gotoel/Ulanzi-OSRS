package com.ulanzi.osrs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on the 32x8 panel in the compact layout.
 *
 * The panel is read in three tiers. Row 0 carries a short dash per value in the colour
 * that stat is always known by, so a number stays identifiable once its own colour has
 * ramped away towards red. Rows 1-5 carry the values, each in a slot wide enough for the
 * largest number that stat can reach and right aligned inside it, so a digit appearing or
 * disappearing changes that number in place instead of sliding the whole line sideways.
 * Row 7 is a strip of bars sharing the full width.
 *
 * Bars living on their own row is what makes the line fit: a value demoted to a bar gives
 * up its whole slot and costs nothing horizontally, where the old right-edge columns took
 * 2px off the text no matter what.
 */
final class CompactLayout
{
	static final int PANEL_WIDTH = 32;
	static final int ICON_WIDTH = 8;
	/** The small font advances 4px per character, the last column of which is blank. */
	static final int CHAR_WIDTH = 4;
	/** Dashes naming each value. */
	static final int TICK_ROW = 0;
	/**
	 * The top of the glyph, not its baseline: the draw list places text from here down,
	 * which is the opposite of what the scripting API's text() does. Five pixel capitals
	 * from row 1 leave row 0 for the dashes and row 6 blank above the strip.
	 */
	static final int TEXT_TOP = 1;
	/** The bar strip, and for the focus layout the only row the large font leaves free. */
	static final int STRIP_ROW = 7;
	/** Two blank columns between values: the slot's own trailing column, and this. */
	private static final int GAP = 1;

	private CompactLayout()
	{
	}

	/**
	 * One stat on the panel, or a label with no stat behind it.
	 *
	 * The style is settled by {@link #fit}, and the pixel fields by {@link #place}.
	 */
	static final class Cell
	{
		final StatKind kind;
		StatStyle style;
		final String text;
		final int digits;
		final int percent;
		/** A higher reading still fading out behind this one, or -1 for none. */
		final int ghostPercent;
		final Color color;
		final Color identity;

		int x = -1;
		int width;
		int stripX = -1;
		int stripWidth;

		Cell(StatKind kind, StatStyle style, String text, int digits, int percent, int ghostPercent,
			Color color, Color identity)
		{
			this.kind = kind;
			this.style = style;
			this.text = text;
			this.digits = digits;
			this.percent = percent;
			this.ghostPercent = ghostPercent;
			this.color = color;
			this.identity = identity;
		}

		/** A label has no stat behind it, so it can never be demoted to a bar. */
		static Cell label(String text, Color color)
		{
			return new Cell(null, StatStyle.VALUE, text, text.length(), -1, -1, color, null);
		}

		boolean showsValue()
		{
			return style.showsValue() && text != null && !text.isEmpty();
		}

		boolean showsBar()
		{
			return style.showsBar() && percent >= 0;
		}
	}

	static int available(boolean icon)
	{
		return PANEL_WIDTH - (icon ? ICON_WIDTH : 0);
	}

	/**
	 * Width of the values in lit pixels. Each slot ends in a blank column, so the one
	 * after the last value is not part of the line.
	 */
	static int lineWidth(List<Cell> cells)
	{
		int slots = 0;
		int width = 0;
		for (Cell cell : cells)
		{
			if (!cell.showsValue())
			{
				continue;
			}
			width += cell.digits * CHAR_WIDTH;
			slots++;
		}
		if (slots == 0)
		{
			return 0;
		}
		return width + (slots - 1) * GAP - 1;
	}

	static boolean fits(List<Cell> cells, boolean icon)
	{
		return lineWidth(cells) <= available(icon);
	}

	/**
	 * Turns values into bars until the line fits, giving up the least wanted stat first.
	 * Whichever stat {@code keep} names goes last, so the one you chose is the one that
	 * survives a narrow panel. A bar is not a loss the way it used to be: the stat keeps
	 * a full share of the bottom strip either way.
	 */
	static void fit(List<Cell> cells, boolean icon, StatKind keep)
	{
		for (int index : demotionOrder(cells, keep))
		{
			if (fits(cells, icon))
			{
				return;
			}
			cells.get(index).style = StatStyle.BAR;
		}
	}

	/**
	 * Rightmost stat first, so the line gives up what is furthest from the eye, with the
	 * kept stat held back to the end. Labels are not in the order: they have no bar to
	 * fall back to.
	 */
	private static List<Integer> demotionOrder(List<Cell> cells, StatKind keep)
	{
		List<Integer> order = new ArrayList<>();
		int kept = -1;
		for (int i = cells.size() - 1; i >= 0; i--)
		{
			Cell cell = cells.get(i);
			if (cell.kind == null || !cell.style.showsValue())
			{
				continue;
			}
			if (cell.kind == keep)
			{
				kept = i;
			}
			else
			{
				order.add(i);
			}
		}
		if (kept >= 0)
		{
			order.add(kept);
		}
		return order;
	}

	/**
	 * Settles the pixels: the values as one centred group, and the bars sharing the whole
	 * width beneath them in the same left to right order.
	 */
	static void place(List<Cell> cells, boolean icon)
	{
		int origin = icon ? ICON_WIDTH : 0;
		placeValues(cells, origin, available(icon));
		placeBars(cells, origin, available(icon));
	}

	private static void placeValues(List<Cell> cells, int origin, int available)
	{
		int x = origin + Math.max(0, (available - lineWidth(cells)) / 2);
		for (Cell cell : cells)
		{
			if (!cell.showsValue())
			{
				continue;
			}
			cell.width = cell.digits * CHAR_WIDTH;
			cell.x = x;
			x += cell.width + GAP;
		}
	}

	/**
	 * Equal shares of the full width, which is what gives a bar the room to say anything:
	 * a segment squeezed in beside a number would only have a pixel or two to move across.
	 */
	private static void placeBars(List<Cell> cells, int origin, int available)
	{
		List<Cell> bars = new ArrayList<>();
		for (Cell cell : cells)
		{
			if (cell.showsBar())
			{
				bars.add(cell);
			}
		}
		for (int i = 0; i < bars.size(); i++)
		{
			int start = origin + (available * i) / bars.size();
			int end = origin + (available * (i + 1)) / bars.size();
			Cell cell = bars.get(i);
			cell.stripX = start;
			// A blank column between segments, so two that are both dark still read apart.
			cell.stripWidth = Math.max(1, end - start - (i < bars.size() - 1 ? 1 : 0));
		}
	}

	/**
	 * The dash naming a value runs the lit width of its slot.
	 */
	static int tickWidth(Cell cell)
	{
		return Math.max(1, cell.width - 1);
	}

	/**
	 * Right aligned in the slot, so the ones column of a number never moves.
	 */
	static int textX(Cell cell)
	{
		return cell.x + Math.max(0, cell.digits - cell.text.length()) * CHAR_WIDTH;
	}
}
