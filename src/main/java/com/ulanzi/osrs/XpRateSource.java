package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum XpRateSource
{
	CLOCK("Session"),
	SLIDING_WINDOW("Sliding window"),
	XP_TRACKER("RuneLite XP Tracker");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
