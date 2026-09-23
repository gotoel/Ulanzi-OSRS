package com.ulanzi.osrs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on the 32x8 panel in the compact layout.
 *
 * Rows 1-5 carry the values, each in a slot wide enough for the largest number that stat
 * can reach and right aligned inside it, so a digit appearing or disappearing changes that
 * number in place instead of sliding the whole line sideways. Rows 0 and 6 are left clear
 * so nothing crowds them. Row 7 is a strip of bars sharing the full width, each one tracked
 * in the colour its stat is always known by, so an empty bar still says whose it is after
 * the fill has ramped away towards red.
 *
 * Bars living on their own row is what makes the line fit: a value demoted to a bar gives
 * up its whole slot and costs nothing horizontally, where the old right-edge columns took
 * 2px off the text no matter what.
 *
 * A {@link StatOrb} stands at either end of whatever room is left, the left one first and
 * the right one hard against the panel edge. They cost the values six columns apiece, which
 * is most of a three digit slot, so switching them on is what pushes run energy and special
 * attack down onto the strip. The strip itself is not shortened: the orbs stop at row 5,
 * and bars are free to run underneath them.
 */
final class CompactLayout
{
	static final int PANEL_WIDTH = 32;
	static final int ICON_WIDTH = 8;
	/** The small font advances 4px per character, the last column of which is blank. */
	static final int CHAR_WIDTH = 4;
	/**
	 * The top of the glyph, not its baseline: the draw list places text from here down,
	 * which is the opposite of what the scripting API's text() does.
	 *
	 * Five pixel digits from row 1 leave row 0 clear above them and row 6 clear below,
	 * which is what makes them readable. There is no room for anything else up here: a
	 * marker row, a blank, the digits, a blank and the strip is nine rows on a panel with
	 * eight, and a marker sitting straight on top of a number merges into it.
	 */
	static final int TEXT_TOP = 1;
	/** The bar strip, and for the focus layout the only row the large font leaves free. */
	static final int STRIP_ROW = 7;
	/** Two blank columns between values: the slot's own trailing column, and this. */
	private static final int GAP = 1;
	/** A blank column between an orb and the values, on top of the slot's own. */
	private static final int ORB_GAP = 1;

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
		/** The glyph this stat fills at the edge of the page, or null if it has none. */
		final StatOrb orb;

		int x = -1;
		int width;
		int stripX = -1;
		int stripWidth;
		int orbX = -1;

		Cell(StatKind kind, StatStyle style, String text, int digits, int percent, int ghostPercent,
			Color color, Color identity)
		{
			this(kind, style, text, digits, percent, ghostPercent, color, identity, null);
		}

		Cell(StatKind kind, StatStyle style, String text, int digits, int percent, int ghostPercent,
			Color color, Color identity, StatOrb orb)
		{
			this.kind = kind;
			this.style = style;
			this.text = text;
			this.digits = digits;
			this.percent = percent;
			this.ghostPercent = ghostPercent;
			this.color = color;
			this.identity = identity;
			this.orb = orb;
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

		/**
		 * An orb is drawn whatever style the stat is in: it is the reading itself, not a
		 * fallback for a value that would not fit. It does need a reading to draw.
		 */
		boolean showsOrb()
		{
			return orb != null && percent >= 0;
		}
	}

	/**
	 * Room for the values: the panel, less the activity icon, less whichever orbs are up.
	 */
	static int available(List<Cell> cells, boolean icon)
	{
		return PANEL_WIDTH - (icon ? ICON_WIDTH : 0) - orbReserve(cells);
	}

	private static Cell orb(List<Cell> cells, boolean rightHand)
	{
		for (Cell cell : cells)
		{
			if (cell.showsOrb() && cell.orb.isRightHand() == rightHand)
			{
				return cell;
			}
		}
		return null;
	}

	/**
	 * What the orbs take off the line. Each side is counted on its own, so one orb alone
	 * does not cost the values the other one's columns as well.
	 */
	private static int orbReserve(List<Cell> cells)
	{
		int reserve = 0;
		if (orb(cells, false) != null)
		{
			reserve += StatOrb.WIDTH + ORB_GAP;
		}
		if (orb(cells, true) != null)
		{
			reserve += StatOrb.WIDTH + ORB_GAP;
		}
		return reserve;
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
		return lineWidth(cells) <= available(cells, icon);
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
		Cell left = orb(cells, false);
		Cell right = orb(cells, true);
		if (left != null)
		{
			left.orbX = origin;
		}
		if (right != null)
		{
			right.orbX = PANEL_WIDTH - StatOrb.WIDTH;
		}
		int textOrigin = origin + (left != null ? StatOrb.WIDTH + ORB_GAP : 0);
		placeValues(cells, textOrigin, available(cells, icon));
		// The full width, not the room the values were left: the orbs stop above the strip,
		// so shortening the bars to clear them would give away pixels for nothing.
		placeBars(cells, origin, PANEL_WIDTH - origin);
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
	 * Right aligned in the slot, so the ones column of a number never moves.
	 */
	static int textX(Cell cell)
	{
		return cell.x + Math.max(0, cell.digits - cell.text.length()) * CHAR_WIDTH;
	}
}
