package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Puts a page on the clock with sample readings, so a layout can be judged from the
 * settings panel instead of by logging in and draining a stat to see what happens.
 * The setting turns itself back off once the preview has been sent.
 */
@Getter
@AllArgsConstructor
public enum Preview
{
	OFF("Off"),
	CONNECTION("Connection test"),
	STATS("Stats page"),
	SKILL("Skill page"),
	LOW_HITPOINTS("Low hitpoints"),
	SPEC_READY("Special attack ready"),
	AFK("AFK page");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
