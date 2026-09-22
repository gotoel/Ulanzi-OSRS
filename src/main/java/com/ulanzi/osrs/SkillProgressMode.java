package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SkillProgressMode
{
	OFF("Off"),
	LEVEL("Level"),
	XP_RATE("XP per hour"),
	BOTH("Level and XP/h");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean isShown()
	{
		return this != OFF;
	}
}
