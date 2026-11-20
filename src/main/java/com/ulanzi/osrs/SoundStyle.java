package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SoundStyle
{
	SOFT("Soft"),
	CLASSIC("Classic");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
