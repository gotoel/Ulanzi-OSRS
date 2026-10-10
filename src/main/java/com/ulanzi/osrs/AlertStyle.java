package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What a threshold warning does when it fires. One setting where there used to be three
 * boxes — on, flash, beep — which could be set to combinations that cancelled each other
 * out and gave no sign of having done so.
 */
@Getter
@AllArgsConstructor
public enum AlertStyle
{
	OFF("Off"),
	FLASH("Flash"),
	BEEP("Beep"),
	FLASH_AND_BEEP("Flash and beep");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean isEnabled()
	{
		return this != OFF;
	}

	boolean flashes()
	{
		return this == FLASH || this == FLASH_AND_BEEP;
	}

	boolean beeps()
	{
		return this == BEEP || this == FLASH_AND_BEEP;
	}
}
