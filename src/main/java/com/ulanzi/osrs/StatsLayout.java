package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatsLayout
{
	BIG("Big"),
	COMPACT("Compact"),
	FOCUS("Focus");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
