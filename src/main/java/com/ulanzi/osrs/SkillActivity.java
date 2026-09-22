package com.ulanzi.osrs;

import java.awt.Color;

/**
 * A skilling activity the clock can show an icon for.
 * Combat skills are intentionally absent: their animations are not skilling.
 */
enum SkillActivity
{
	WOODCUTTING("Wood", 0x3C_B4_3C, new String[] {
		"..GGGG..",
		".GggggG.",
		"GggggggG",
		".GGGGGG.",
		"...TT...",
		"...TT...",
		"...TT...",
		"..TTTT.."
	}),
	MINING("Mine", 0xC8_C8_C8, new String[] {
		"......SS",
		".....SS.",
		"....SS..",
		"...TT...",
		"..TT....",
		".TT.....",
		"TT......",
		"........"
	}),
	FISHING("Fish", 0x3D_7E_FF, new String[] {
		"........",
		"...BBBB.",
		"..BBBBWB",
		".BBBBBBB",
		"..BBBBBB",
		"...BB...",
		"........",
		"........"
	}),
	HUNTER("Hunt", 0x8B_5A_2B, new String[] {
		"TTT..TTT",
		"T......T",
		"T.Y..Y.T",
		"T......T",
		"T......T",
		"T.Y..Y.T",
		"T......T",
		"TTT..TTT"
	}),
	AGILITY("Agil", 0xFF_D5_4A, new String[] {
		"....YY..",
		"...YY...",
		"..YY....",
		".YYYYYY.",
		"....YY..",
		"...YY...",
		"..YY....",
		".YY....."
	}),
	THIEVING("Thief", 0xFF_C1_07, new String[] {
		"..AAAA..",
		".A....A.",
		"A..AA..A",
		"A.AAAA.A",
		"A.AAAA.A",
		"A..AA..A",
		".A....A.",
		"..AAAA.."
	}),
	FARMING("Farm", 0x3C_B4_3C, new String[] {
		"...gG...",
		"..G.G...",
		".G.GG.G.",
		"...TT...",
		"...TT...",
		"..TTTT..",
		".TTTTTT.",
		"........"
	}),
	RUNECRAFT("RC", 0x26_C6_DA, new String[] {
		".CCCCCC.",
		"C......C",
		"C.CCCC.C",
		"C.C..C.C",
		"C.CCCC.C",
		"C......C",
		".CCCCCC.",
		"........"
	}),
	CONSTRUCTION("Cons", 0xC8_C8_C8, new String[] {
		"..SSSS..",
		"...SS...",
		"...TT...",
		"...TT...",
		"...TT...",
		"...TT...",
		"..TTTT..",
		"........"
	}),
	COOKING("Cook", 0xFF_8C_1A, new String[] {
		"...YY...",
		"..YRR...",
		"...YY...",
		"..OOOO..",
		".OOOOOO.",
		".OOOOOO.",
		"..OOOO..",
		"........"
	}),
	FIREMAKING("Fire", 0xE5_39_35, new String[] {
		"...RR...",
		"..ROOR..",
		".RYYYR..",
		"..YYYY..",
		".RYYYYR.",
		"..RRRR..",
		"...RR...",
		"........"
	}),
	SMITHING("Smith", 0xC8_C8_C8, new String[] {
		"........",
		".SSSS...",
		"SSSSSS..",
		".SSSSS..",
		"..TTT...",
		"..TTT...",
		".TTTTT..",
		"........"
	}),
	FLETCHING("Flet", 0xFF_D5_4A, new String[] {
		".....YY.",
		"....YYYY",
		"...TT...",
		"...TT...",
		"...TT...",
		"...TT...",
		"...AA...",
		"...AA..."
	}),
	CRAFTING("Craft", 0x9C_27_B0, new String[] {
		"...PP...",
		"..PPPP..",
		".PPWWPP.",
		"PPPPPPPP",
		".PPPPPP.",
		"..PPPP..",
		"...PP...",
		"........"
	}),
	HERBLORE("Herb", 0x3C_B4_3C, new String[] {
		"...WW...",
		"...GG...",
		"..GGGG..",
		"..GGGG..",
		"..GGGG..",
		"..GGGG..",
		"...GG...",
		"..GGGG.."
	}),
	PRAYER("Pray", 0xFF_D5_4A, new String[] {
		"...YY...",
		"...YY...",
		".YYYYYY.",
		"..YYYY..",
		"...YY...",
		"..Y..Y..",
		".Y....Y.",
		"........"
	}),
	MAGIC("Mage", 0x3D_7E_FF, new String[] {
		"...BB...",
		"..B..B..",
		".B.WW.B.",
		"..B..B..",
		"...BB...",
		"..B..B..",
		".B....B.",
		"........"
	}),
	SAILING("Sail", 0x3D_7E_FF, new String[] {
		"...WW...",
		"...WW...",
		"...WW...",
		".BBBBBB.",
		"BBBBBBBB",
		".BBBBBB.",
		"..BBBB..",
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
