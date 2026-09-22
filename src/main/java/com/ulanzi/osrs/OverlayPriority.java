package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OverlayPriority
{
	AFK_FIRST("AFK first"),
	ALERTS_FIRST("Alerts first"),
	ROTATE("Rotate active");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
