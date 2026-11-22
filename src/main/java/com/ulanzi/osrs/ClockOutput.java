package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Where the pages go. Automatic is the default: the virtual overlay, until a real clock has
 * answered at the saved address, and from then on the real one alone.
 */
@Getter
@AllArgsConstructor
public enum ClockOutput
{
	AUTO("Automatic"),
	VIRTUAL("Virtual overlay"),
	REAL("Real clock (TC001)"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	/** What this setting means right now. Only Automatic depends on the clock. */
	ClockOutput resolve(boolean hasAddress, boolean connected)
	{
		if (this != AUTO)
		{
			return this;
		}
		return hasAddress && connected ? REAL : VIRTUAL;
	}

	boolean toReal()
	{
		return this == REAL || this == BOTH;
	}

	boolean toVirtual()
	{
		return this == VIRTUAL || this == BOTH;
	}
}
