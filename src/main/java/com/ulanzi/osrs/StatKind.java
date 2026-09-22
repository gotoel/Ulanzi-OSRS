package com.ulanzi.osrs;

import java.awt.Color;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The four orb stats, in the order they read across the panel.
 *
 * Each one keeps the colour it is always known by. That is not the colour its value is
 * drawn in: a value ramps towards red as the stat drains, so by the time it matters the
 * hue no longer says which stat it is. The identity colour is what the row of dashes
 * along the top is painted with, and it never changes.
 */
@Getter
@AllArgsConstructor
public enum StatKind
{
	HITPOINTS("Hitpoints", new Color(0x00_FF_00), 2),
	PRAYER("Prayer", new Color(0x4F_A3_FF), 2),
	ENERGY("Run energy", new Color(0xFF_D4_00), 3),
	SPEC("Special attack", new Color(0xFF_8C_00), 3);

	private final String name;
	private final Color identity;
	/** How many characters the slot holds, so a value never moves when it gains a digit. */
	private final int digits;

	@Override
	public String toString()
	{
		return name;
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
