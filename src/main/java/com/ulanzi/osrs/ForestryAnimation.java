package com.ulanzi.osrs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The forest that grows behind a forestry event's name.
 *
 * Stems push up from the bottom row a little after one another, so growth runs across
 * the panel rather than happening to all eight at once, and each one opens a pair of
 * leaves once it is tall enough to have earned them. Motes of anima drift up through
 * them, wandering a pixel either way so they do not rise in columns.
 *
 * Everything is drawn in the event's own colour, which is what says which event it is
 * from across a room: the name is only readable once you are close enough to read it.
 * The motes stay near white so they still read as sparks over whatever that colour is.
 */
final class ForestryAnimation
{
	static final int WIDTH = 32;
	static final int HEIGHT = 8;
	/** Fast enough for the growth to look continuous, slow enough not to flood the clock. */
	static final long FRAME_MS = 200L;

	/** One stem every four pixels, which is eight of them across the panel. */
	private static final int STEM_SPACING = 4;
	private static final int STEMS = WIDTH / STEM_SPACING;
	private static final int STEM_MAX = 6;
	/** Leaves open once the stem has this much of itself above the floor. */
	private static final int STEM_LEAVES_AT = 3;
	private static final long STEM_CYCLE_MS = 2_000L;
	private static final long STEM_STAGGER_MS = STEM_CYCLE_MS / STEMS;
	private static final double STEM_BODY_LEVEL = 0.45;
	private static final double STEM_LEAF_LEVEL = 0.75;

	private static final int MOTES = 6;
	private static final int MOTE_SPACING = 5;
	/** One pixel of rise per step. */
	private static final long MOTE_STEP_MS = 140L;
	private static final int[] MOTE_DRIFT = {0, 1, 1, 0, -1, -1, 0, 1};
	/** How far a mote is washed out towards white. Not all the way: the name is white. */
	private static final double MOTE_PALENESS = 0.6;

	private ForestryAnimation()
	{
	}

	/**
	 * One lit pixel. The panel is small enough that a frame is a handful of these,
	 * which is cheaper to check than a list of draw commands.
	 */
	static final class Dot
	{
		final int x;
		final int y;
		final Color color;

		Dot(int x, int y, Color color)
		{
			this.x = x;
			this.y = y;
			this.color = color;
		}
	}

	static List<Dot> frame(ForestryEffect effect, Color color, long elapsedMs)
	{
		List<Dot> dots = new ArrayList<>();
		long elapsed = Math.max(0L, elapsedMs);
		if (effect.hasStems())
		{
			addStems(dots, color, elapsed);
		}
		if (effect.hasMotes())
		{
			addMotes(dots, color, elapsed);
		}
		return dots;
	}

	private static void addStems(List<Dot> dots, Color color, long elapsedMs)
	{
		Color body = shade(color, STEM_BODY_LEVEL);
		Color leaf = shade(color, STEM_LEAF_LEVEL);

		for (int stem = 0; stem < STEMS; stem++)
		{
			int height = stemHeight(stem, elapsedMs);
			if (height <= 0)
			{
				continue;
			}

			int x = stem * STEM_SPACING + 1;
			int tipY = HEIGHT - height;
			for (int y = HEIGHT - 1; y > tipY; y--)
			{
				dots.add(new Dot(x, y, body));
			}
			dots.add(new Dot(x, tipY, color));

			if (height >= STEM_LEAVES_AT)
			{
				dots.add(new Dot(x - 1, tipY + 1, leaf));
				dots.add(new Dot(x + 1, tipY + 1, leaf));
			}
		}
	}

	/**
	 * A stem's darker parts. Held here rather than borrowed from the clock so that a
	 * frame is a plain function of the time, with nothing behind it to stand up first.
	 * The panel's own floor is applied later, where every other drawn colour gets it.
	 */
	private static Color shade(Color color, double level)
	{
		return new Color((int) Math.round(color.getRed() * level),
			(int) Math.round(color.getGreen() * level),
			(int) Math.round(color.getBlue() * level));
	}

	/**
	 * Up for the first half of the cycle and back down for the second, so a stem grows
	 * and withers rather than snapping back to nothing when it reaches the top.
	 */
	static int stemHeight(int stem, long elapsedMs)
	{
		long phase = Math.floorMod(elapsedMs + stem * STEM_STAGGER_MS, STEM_CYCLE_MS);
		double t = phase / (double) STEM_CYCLE_MS;
		double rise = t < 0.5 ? t * 2 : (1 - t) * 2;
		return (int) Math.round(rise * STEM_MAX);
	}

	private static void addMotes(List<Dot> dots, Color color, long elapsedMs)
	{
		Color mote = pale(color);
		for (int index = 0; index < MOTES; index++)
		{
			long steps = elapsedMs / MOTE_STEP_MS + index * 3L;
			int y = HEIGHT - 1 - (int) Math.floorMod(steps, HEIGHT);
			int x = index * MOTE_SPACING + 2
				+ MOTE_DRIFT[(int) Math.floorMod(steps + index, MOTE_DRIFT.length)];
			dots.add(new Dot(x, y, mote));
		}
	}

	/**
	 * A mote is the brightest thing in the grove, but it stays in the event's hue rather
	 * than going white, so the name written over it is still the only white on the panel.
	 */
	private static Color pale(Color color)
	{
		return new Color(wash(color.getRed()), wash(color.getGreen()), wash(color.getBlue()));
	}

	private static int wash(int channel)
	{
		return (int) Math.round(channel + (255 - channel) * MOTE_PALENESS);
	}
}
