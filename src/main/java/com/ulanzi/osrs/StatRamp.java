package com.ulanzi.osrs;

import java.awt.Color;

/**
 * How a stat's colour answers its own value.
 *
 * Hitpoints used to be the only stat that changed colour, and only in three steps, while
 * prayer, run energy and special attack stayed the same shade whether they were full or
 * nearly gone. Every stat ramps now, and smoothly: a glance says something is running out
 * before any of the digits have been read.
 */
final class StatRamp
{
	/** Full colour down to here. */
	private static final int HIGH = 50;
	/** All the way over to amber by here. */
	private static final int LOW = 25;
	private static final Color AMBER = new Color(0xFF_A0_00);
	private static final Color EMPTY = new Color(0xFF_20_00);

	private StatRamp()
	{
	}

	static Color drain(Color base, int percent)
	{
		int value = Math.max(0, Math.min(100, percent));
		if (value >= HIGH)
		{
			return base;
		}
		if (value >= LOW)
		{
			return blend(AMBER, base, (value - LOW) / (double) (HIGH - LOW));
		}
		return blend(EMPTY, AMBER, value / (double) LOW);
	}

	/**
	 * {@code towards} at 1, {@code from} at 0.
	 */
	static Color blend(Color from, Color towards, double amount)
	{
		double t = Math.max(0.0, Math.min(1.0, amount));
		return new Color(
			mix(from.getRed(), towards.getRed(), t),
			mix(from.getGreen(), towards.getGreen(), t),
			mix(from.getBlue(), towards.getBlue(), t));
	}

	private static int mix(int from, int towards, double amount)
	{
		return Math.max(0, Math.min(255, (int) Math.round(from + (towards - from) * amount)));
	}
}
