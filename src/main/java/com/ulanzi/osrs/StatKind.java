package com.ulanzi.osrs;

import java.awt.Color;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The four orb stats, in the order they read across the panel.
 *
 * Each one keeps the colour it is always known by. That is not always the colour its value
 * is drawn in: a stat that ramps goes over towards red as it drains, so by the time it
 * matters the hue no longer says which stat it is. The identity colour is what the bar
 * tracks and the unfilled part of a drain orb are painted with, and it never changes.
 */
@Getter
@AllArgsConstructor
public enum StatKind
{
	HITPOINTS("Hitpoints", new Color(0x00_FF_00), 2, true, StatOrb.HITPOINTS),
	PRAYER("Prayer", new Color(0x4F_A3_FF), 2, false, StatOrb.PRAYER),
	ENERGY("Run energy", new Color(0xFF_D4_00), 3, true, null),
	SPEC("Special attack", new Color(0xFF_8C_00), 3, true, null);

	private final String name;
	private final Color identity;
	/** How many characters the slot holds, so a value never moves when it gains a digit. */
	private final int digits;
	/** Whether this stat's colour answers its own value, or holds still at every level. */
	private final boolean ramps;
	/** The glyph this stat fills at the edge of the compact page, or null for none. */
	private final StatOrb orb;

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * The colour this stat is drawn in at this percent.
	 *
	 * Hitpoints, run energy and special attack ramp: a glance says one of them is running
	 * out before any of the digits have been read, and red is the right word for all three.
	 * Prayer does not. Running out of prayer is not the emergency running out of hitpoints
	 * is, and blue is the only thing on the panel that says prayer at all - an amber one
	 * beside an amber hitpoints reads as two alarms where there is one.
	 */
	Color drained(int percent)
	{
		return ramps ? StatRamp.drain(identity, percent) : identity;
	}

	/**
	 * Digits for a stat that can be boosted past its usual ceiling, so a brewed-up
	 * hitpoints level still has somewhere to print its third digit.
	 */
	int digitsFor(int ceiling)
	{
		int needed = String.valueOf(Math.max(0, ceiling)).length();
		return Math.max(digits, needed);
	}
}
