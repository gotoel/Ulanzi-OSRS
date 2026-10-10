package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * How the stats page says you are away, when the AFK display is left on it. The label
 * used to be a separate checkbox, which left the two settings to be read together to
 * work out what the page would actually look like.
 */
@Getter
@AllArgsConstructor
public enum AfkTintMode
{
	OFF("Off"),
	LABEL("AFK label"),
	VALUES("Dim the values"),
	VALUES_AND_LABEL("Dim the values, with label"),
	BACKGROUND("Tint the background"),
	BOTH("Values and background");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean tintsValues()
	{
		return this == VALUES || this == VALUES_AND_LABEL || this == BOTH;
	}

	boolean tintsBackground()
	{
		return this == BACKGROUND || this == BOTH;
	}

	boolean showsLabel()
	{
		return this == LABEL || this == VALUES_AND_LABEL;
	}
}
