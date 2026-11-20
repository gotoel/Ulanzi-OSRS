package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LedStyle
{
	ROUND("Round"),
	SQUARE("Square"),
	GLOW("Glow");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
