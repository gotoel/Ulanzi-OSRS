package com.ulanzi.osrs;

/**
 * Every melody the clock is asked to play, in both styles.
 *
 * The classic tunes sit at octaves six and seven, roughly 1 to 2.6 kHz, which is about where
 * a small piezo buzzer is loudest and most piercing, and high enough to bother a pet in the
 * room. The soft ones keep the same shape (warnings fall, good news rises) two octaves down,
 * between about 260 Hz and 990 Hz, and are cut to two or three notes each.
 */
enum Tune
{
	AFK(
		"afk:d=4,o=5,b=160:c6,p,c6",
		"afk:d=8,o=4,b=120:g,p,g"),
	LOW_HP(
		"lowhp:d=16,o=6,b=200:c,e,g,c7",
		"lowhp:d=16,o=5,b=160:g,e,8c"),
	LOW_PRAYER(
		"lowpray:d=8,o=5,b=140:e,c,e,c",
		"lowpray:d=8,o=5,b=120:d,4a4"),
	SPEC_READY(
		"spec:d=16,o=5,b=180:g,c6,e6,8g6",
		"spec:d=16,o=5,b=160:c,e,8g"),
	LEVEL_UP(
		"levelup:d=8,o=5,b=180:c,e,g,4c6,p,g,2c6",
		"levelup:d=8,o=5,b=140:c,e,4g"),
	FORESTRY(
		"forestry:d=16,o=6,b=180:e,g,b,8e7,p,8b",
		"forestry:d=16,o=5,b=160:e,g,8b");

	private final String classic;
	private final String soft;

	Tune(String classic, String soft)
	{
		this.classic = classic;
		this.soft = soft;
	}

	String rtttl(SoundStyle style)
	{
		return style == SoundStyle.CLASSIC ? classic : soft;
	}
}
