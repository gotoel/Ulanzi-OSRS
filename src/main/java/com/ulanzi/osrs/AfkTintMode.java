package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AfkTintMode
{
	OFF("Off"),
	VALUES("Values & health bar"),
	BACKGROUND("Background"),
	BOTH("Values and background");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean tintsValues()
	{
		return this == VALUES || this == BOTH;
	}

	boolean tintsBackground()
	{
		return this == BACKGROUND || this == BOTH;
	}
}
