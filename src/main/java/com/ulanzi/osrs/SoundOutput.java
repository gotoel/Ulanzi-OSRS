package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Where a tone plays: the clock's buzzer, the computer's speakers, both, or nowhere.
 */
@Getter
@AllArgsConstructor
public enum SoundOutput
{
	CLOCK("Clock"),
	COMPUTER("Computer"),
	BOTH("Both"),
	OFF("Off");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean toClock()
	{
		return this == CLOCK || this == BOTH;
	}

	boolean toComputer()
	{
		return this == COMPUTER || this == BOTH;
	}
}
