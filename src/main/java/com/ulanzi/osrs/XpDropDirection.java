package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum XpDropDirection
{
	LEFT("Right to left", "left"),
	RIGHT("Left to right", "right"),
	UP("Bottom to top", null),
	DOWN("Top to bottom", null),
	IN_PLACE("In place", null);

	private final String name;
	private final String scrollDirection;

	@Override
	public String toString()
	{
		return name;
	}

	boolean isHorizontal()
	{
		return scrollDirection != null;
	}
}
