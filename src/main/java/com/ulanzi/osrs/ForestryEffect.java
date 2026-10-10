package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ForestryEffect
{
	GROVE("Growing grove"),
	SPROUT("Sprouting stems"),
	MOTES("Rising anima"),
	FLASH("Flashing text");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean hasStems()
	{
		return this == GROVE || this == SPROUT;
	}

	boolean hasMotes()
	{
		return this == GROVE || this == MOTES;
	}

	boolean isAnimated()
	{
		return this != FLASH;
	}
}
