package com.ulanzi.osrs;

import java.util.ArrayList;
import java.util.List;

/**
 * The drain orbs that stand at the two ends of the compact page.
 *
 * A bar says how much is left; an orb says it without having to be read. The glyph is
 * filled from the bottom like a vessel, so hitpoints and prayer answer a glance from
 * across the room while the digits are still there for anyone who wants the number.
 *
 * Shape carries identity here, not colour. Hitpoints ramps away from green as it drains,
 * so by the time it matters a round orb would be an anonymous red blob: the heart and the
 * diamond stay themselves at every level, which is why they are not two circles.
 *
 * Five wide and six tall. Six rows is a sixth of the stat per row, which would be coarse
 * on its own, so the row the fill stops on is lit in proportion to how far into it the
 * value reaches, the same as the bars. Rows 0-5 leave row 6 clear above the strip.
 */
enum StatOrb
{
	HITPOINTS(false, new String[] {
		".X.X.",
		"XXXXX",
		"XXXXX",
		"XXXXX",
		".XXX.",
		"..X.."
	}),
	PRAYER(true, new String[] {
		"..X..",
		".XXX.",
		"XXXXX",
		"XXXXX",
		".XXX.",
		"..X.."
	});

	static final int WIDTH = 5;
	static final int HEIGHT = 6;
	/** The top row of an orb. Row 6 is left clear so it never touches the strip. */
	static final int TOP = 0;

	private final boolean rightHand;
	/**
	 * Each row as its lit stretches, {@code {start, length}}. Drawing a run at a time
	 * rather than a pixel at a time is what keeps two orbs down to thirteen commands.
	 */
	private final int[][][] runs;

	StatOrb(boolean rightHand, String[] rows)
	{
		if (rows.length != HEIGHT)
		{
			throw new IllegalArgumentException("Orb must be " + HEIGHT + " rows");
		}
		this.rightHand = rightHand;
		this.runs = new int[HEIGHT][][];
		for (int y = 0; y < HEIGHT; y++)
		{
			this.runs[y] = runs(rows[y]);
		}
	}

	/** True for the orb that sits against the right edge, false for the left. */
	boolean isRightHand()
	{
		return rightHand;
	}

	int[][] runs(int row)
	{
		return runs[row];
	}

	private static int[][] runs(String row)
	{
		if (row.length() != WIDTH)
		{
			throw new IllegalArgumentException("Orb row must be " + WIDTH + " pixels: " + row);
		}
		List<int[]> found = new ArrayList<>();
		int start = -1;
		for (int x = 0; x <= WIDTH; x++)
		{
			boolean lit = x < WIDTH && row.charAt(x) == 'X';
			if (lit && start < 0)
			{
				start = x;
			}
			else if (!lit && start >= 0)
			{
				found.add(new int[] {start, x - start});
				start = -1;
			}
		}
		return found.toArray(new int[0][]);
	}
}
