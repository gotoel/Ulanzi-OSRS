package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Where a sound goes. Only the clock for now; the computer joins this once the plugin can
 * play a tune itself.
 */
@Getter
@AllArgsConstructor
public enum SoundOutput
{
	CLOCK("Clock"),
	OFF("Off");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
