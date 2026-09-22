package com.ulanzi.osrs;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Colour stops for the level progress bar. The bar reveals the ramp as it fills,
 * so a nearly empty bar only shows the first colour.
 */
@Getter
@AllArgsConstructor
public enum ProgressGradient
{
	SKILL("Skill color"),
	HEAT("Red to green"),
	RAINBOW("Rainbow");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	List<Color> stops(Color skillColor)
	{
		switch (this)
		{
			case HEAT:
				return Arrays.asList(Color.RED, Color.YELLOW, Color.GREEN);
			case RAINBOW:
				return Arrays.asList(Color.RED, new Color(0xFF_8C_00), Color.YELLOW, Color.GREEN,
					new Color(0x3D_7E_FF), new Color(0x9C_27_B0));
			case SKILL:
			default:
				return Arrays.asList(dim(skillColor), skillColor);
		}
	}

	private static Color dim(Color color)
	{
		return new Color(color.getRed() / 5, color.getGreen() / 5, color.getBlue() / 5);
	}
}
