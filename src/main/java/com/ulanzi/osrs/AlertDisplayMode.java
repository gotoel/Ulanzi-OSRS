package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlertDisplayMode
{
	FULL_PANEL("Full panel popups"),
	ON_STATS("On stats / compact view"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean usesFullPanel()
	{
		return this == FULL_PANEL || this == BOTH;
	}

	boolean usesStatsView()
	{
		return this == ON_STATS || this == BOTH;
	}
}
