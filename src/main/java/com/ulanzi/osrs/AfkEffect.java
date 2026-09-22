package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AfkEffect
{
	SOLID("Solid"),
	RAINBOW_WAVE("Rainbow wave"),
	LAVA_WAVE("Lava wave"),
	OCEAN_WAVE("Ocean wave"),
	PARTY_WAVE("Party wave"),
	HEAT_WAVE("Heat wave"),
	PLASMA("Plasma"),
	LOOKING_EYES("Looking eyes");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean usesPaletteText()
	{
		switch (this)
		{
			case RAINBOW_WAVE:
			case LAVA_WAVE:
			case OCEAN_WAVE:
			case PARTY_WAVE:
			case HEAT_WAVE:
				return true;
			default:
				return false;
		}
	}

	String paletteName()
	{
		switch (this)
		{
			case RAINBOW_WAVE:
				return "Rainbow";
			case LAVA_WAVE:
				return "Lava";
			case OCEAN_WAVE:
				return "Ocean";
			case PARTY_WAVE:
				return "Party";
			case HEAT_WAVE:
				return "Heat";
			case PLASMA:
				return "Lava";
			default:
				return null;
		}
	}

	String backgroundEffect()
	{
		switch (this)
		{
			case PLASMA:
				return "Plasma";
			default:
				return null;
		}
	}

	boolean usesSideEyes()
	{
		return this == LOOKING_EYES;
	}
}
