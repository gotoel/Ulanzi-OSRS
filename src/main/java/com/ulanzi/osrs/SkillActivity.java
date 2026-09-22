package com.ulanzi.osrs;

import java.awt.Color;

/**
 * A skilling or combat activity the clock can show an icon for.
 * Melee and ranged are chosen from the attack style while fighting.
 * Magic uses the same icon for spells and for the magic attack style.
 */
enum SkillActivity
{
	WOODCUTTING("Wood", 0x3C_B4_3C, new String[] {
		"........",
		"..GGGG..",
		".GGGGGG.",
		".GggggG.",
		"..GggG..",
		"...TT...",
		"..TTTT..",
		"........"
	}),
	MINING("Mine", 0xC8_C8_C8, new String[] {
		"........",
		".SSSS...",
		".....S..",
		"....T.S.",
		"...T..s.",
		"..T.....",
		".T......",
		"........"
	}),
	FISHING("Fish", 0x9C_C0_E0, new String[] {
		"........",
		"........",
		"..FFF.F.",
		".F.FFFF.",
		".FFFFFF.",
		"..FFF.F.",
		"........",
		"........"
	}),
	HUNTER("Hunt", 0xC8_90_5A, new String[] {
		"........",
		"..L..L..",
		".L....L.",
		"...LL...",
		"..LLLL..",
		"..LLLL..",
		"...LL...",
		"........"
	}),
	AGILITY("Agil", 0xFF_FF_FF, new String[] {
		"........",
		".....W..",
		"...WWW..",
		"..W.WWW.",
		"...WW...",
		"..W..W..",
		".W...W..",
		"........"
	}),
	THIEVING("Thief", 0x8C_84_A8, new String[] {
		"........",
		"........",
		".MMMMMM.",
		".M.MM.M.",
		".MMMMMM.",
		".MM..MM.",
		"........",
		"........"
	}),
	FARMING("Farm", 0x3C_B4_3C, new String[] {
		"........",
		"....sss.",
		".S.s..s.",
		"..SBBSS.",
		"...SSSS.",
		"...SSSS.",
		"...ssss.",
		"........"
	}),
	RUNECRAFT("RC", 0xFF_8C_1A, new String[] {
		"........",
		"..sSSs..",
		".sSOSSs.",
		".SSSRSS.",
		".SSRSSS.",
		".sSSOSs.",
		"..sSSs..",
		"........"
	}),
	CONSTRUCTION("Cons", 0xC8_C8_C8, new String[] {
		"........",
		"....TTT.",
		"....T.T.",
		"...SSTT.",
		"..SSS...",
		".SSs....",
		".s.s....",
		"........"
	}),
	COOKING("Cook", 0xFF_D5_4A, new String[] {
		"........",
		"..W..W..",
		"...W..W.",
		".AAAAAA.",
		".AYYYYA.",
		".AYYYYA.",
		"..AAAA..",
		"........"
	}),
	FIREMAKING("Fire", 0xE5_39_35, new String[] {
		"........",
		"..R..R..",
		".RYR.RR.",
		".RYYRYR.",
		"..OYYO..",
		".TT..TT.",
		"...TT...",
		"........"
	}),
	SMITHING("Smith", 0xC8_C8_C8, new String[] {
		"........",
		".SSSSSS.",
		"..SSSSs.",
		"....S...",
		"...sss..",
		"..ssss..",
		"........",
		"........"
	}),
	FLETCHING("Flet", 0x3C_B4_3C, new String[] {
		"........",
		"....SSS.",
		".....SS.",
		"....T.S.",
		".G.T....",
		".GT.....",
		".GGG....",
		"........"
	}),
	CRAFTING("Craft", 0xC8_C8_C8, new String[] {
		"........",
		".S...SS.",
		"..S.SSS.",
		"...S.S..",
		"...TT...",
		"..T..T..",
		".T....T.",
		"........"
	}),
	HERBLORE("Herb", 0x3C_B4_3C, new String[] {
		"........",
		"...g....",
		"..Gg.G..",
		".G.gGG..",
		".GGgG...",
		"..Gg....",
		"...t....",
		"........"
	}),
	PRAYER("Pray", 0xFF_F1_B8, new String[] {
		"........",
		"...W....",
		"..EWE...",
		".WWWWW..",
		"..EWE...",
		"...W....",
		"........",
		"........"
	}),
	MAGIC("Mage", 0x3D_7E_FF, new String[] {
		"........",
		".....B..",
		"....BB..",
		"...BBB..",
		"..bYYb..",
		".BBBBBB.",
		"........",
		"........"
	}),
	SAILING("Sail", 0xC8_C8_C8, new String[] {
		"........",
		"...S....",
		"..SSS...",
		"...S....",
		"...S....",
		".S.S.S..",
		"..SSS...",
		"........"
	}),
	MELEE("Melee", 0xE0_E0_E0, new String[] {
		"........",
		"......S.",
		".....S..",
		"..Y.S...",
		"...Y....",
		"..T.Y...",
		".Y......",
		"........"
	}),
	RANGED("Range", 0x8B_C3_4A, new String[] {
		"........",
		"..TT....",
		"..s.T...",
		"..s..T..",
		".GTTTTS.",
		"..s.T...",
		"..TT....",
		"........"
	});

	private final String label;
	private final Color color;
	private final String[] rows;
	private String iconData;

	SkillActivity(String label, int rgb, String[] rows)
	{
		this.label = label;
		this.color = new Color(rgb);
		this.rows = rows;
	}

	String label()
	{
		return label;
	}

	Color color()
	{
		return color;
	}

	/**
	 * Inline AWTRIX icon: base64 GIF, which the device treats as image data
	 * once the string is longer than 64 characters.
	 */
	String iconData()
	{
		if (iconData == null)
		{
			iconData = SkillActivityIcons.gif(rows);
		}
		return iconData;
	}
}
