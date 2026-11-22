package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LedStyle
{
	SQUARE("Square"),
	ROUND("Round");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
