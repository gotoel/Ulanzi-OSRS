package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Where the pages go: the clock on the network, the virtual one on screen, or both. */
@Getter
@AllArgsConstructor
public enum ClockOutput
{
	REAL("Real clock"),
	VIRTUAL("Virtual clock"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
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
