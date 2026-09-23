package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Who decides how bright the panel is.
 *
 * The clock's own light sensor takes a dark room a long way down, far enough that the
 * dimmer half of a page stops being legible at all. Pinning the brightness is the only
 * way to stop that, since the firmware has no floor under its automatic setting.
 */
@Getter
@AllArgsConstructor
public enum BrightnessMode
{
	DEVICE("Leave to the clock"),
	FIXED("Fixed");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
