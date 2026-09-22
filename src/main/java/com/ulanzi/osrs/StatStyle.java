package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatStyle
{
	VALUE("Value"),
	BAR("Bar"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean showsValue()
	{
		return this != BAR;
	}

	boolean showsBar()
	{
		return this != VALUE;
	}
}
