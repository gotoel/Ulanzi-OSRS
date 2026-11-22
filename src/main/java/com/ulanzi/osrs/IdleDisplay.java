package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** What the virtual overlay shows when no page is up. */
@Getter
@AllArgsConstructor
public enum IdleDisplay
{
	QUIET("Quiet dots"),
	ICONS("Skill icons"),
	TIME("Time"),
	NOTHING("Nothing");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
