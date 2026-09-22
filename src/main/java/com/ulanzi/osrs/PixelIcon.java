package com.ulanzi.osrs;

/**
 * Icons that are not activities: the stat orbs shown in the big layout, and
 * the skills that only need an icon for a level-up.
 */
enum PixelIcon
{
	HITPOINTS(new String[] {
		"........",
		".HH..HH.",
		".HWHHHH.",
		".HHHHHh.",
		"..HHHh..",
		"...Hh...",
		"........",
		"........"
	}),
	RUN_ENERGY(new String[] {
		"........",
		"..TTT...",
		"..TLT...",
		"..TLT...",
		"..TLLTT.",
		".TLLLLT.",
		".tttttt.",
		"........"
	}),
	SPECIAL_ATTACK(new String[] {
		"........",
		".S....S.",
		"..S..S..",
		"...SS...",
		"...SS...",
		"..Y..Y..",
		".Y....Y.",
		"........"
	}),
	STRENGTH(new String[] {
		"........",
		"..LLLL..",
		".LtLtLL.",
		".LLLLLL.",
		".LtLLLL.",
		"..LLLL..",
		"..LLL...",
		"........"
	}),
	DEFENCE(new String[] {
		"........",
		".SSSSSS.",
		".SSKKSS.",
		".SKKKKS.",
		".SSKKSS.",
		"..SKKS..",
		"...SS...",
		"........"
	}),
	SLAYER(new String[] {
		"........",
		"..WWWW..",
		".WWWWWW.",
		".W.WW.W.",
		".WWWWWW.",
		"..WWWW..",
		"..W.WW..",
		"........"
	});

	private final String[] rows;
	private String iconData;

	PixelIcon(String[] rows)
	{
		this.rows = rows;
	}

	String iconData()
	{
		if (iconData == null)
		{
			iconData = SkillActivityIcons.gif(rows);
		}
		return iconData;
	}
}
