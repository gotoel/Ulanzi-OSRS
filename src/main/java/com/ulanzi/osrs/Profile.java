package com.ulanzi.osrs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One setting for what the panel is about, so a fight and a skilling trip do not need the
 * same dozen boxes changed between them.
 *
 * A profile writes its settings into the boxes below rather than quietly winning over
 * them, so what the panel does is always what the panel is showing you. Change one of the
 * settings it wrote and the profile goes back to Custom, since it is your own panel again.
 */
@Getter
@AllArgsConstructor
public enum Profile
{
	CUSTOM("Custom"),
	COMBAT("Combat"),
	SKILLING("Skilling"),
	HYBRID("Hybrid");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * The settings this profile stands for, as they are stored: enums by name, booleans
	 * as words. Custom stands for nothing and owns no setting.
	 */
	Map<String, String> settings()
	{
		Map<String, String> settings = new LinkedHashMap<>();
		switch (this)
		{
			case COMBAT:
				// What is being spent on staying alive is what the line is for. Run
				// energy is the first thing off it, and the skill page has no business
				// covering your health while something is hitting you.
				settings.put("statsEnabled", "true");
				settings.put("hitpointsStyle", StatStyle.VALUE.name());
				settings.put("prayerStyle", StatStyle.VALUE.name());
				settings.put("energyStyle", StatStyle.OFF.name());
				settings.put("skillProgress", SkillProgressMode.OFF.name());
				settings.put("skillProgressInCombat", "false");
				// The one alert that is only worth having in a fight, so only the
				// profiles that fight turn it on.
				settings.put("specRestored", AlertStyle.FLASH_AND_BEEP.name());
				break;

			case HYBRID:
				// Each half of the trip gets the page it would have had on its own: the
				// fight gets the combat line, and the skill page takes over again once
				// you are back to training something that is not a fight.
				settings.put("statsEnabled", "true");
				settings.put("hitpointsStyle", StatStyle.VALUE.name());
				settings.put("prayerStyle", StatStyle.VALUE.name());
				settings.put("energyStyle", StatStyle.OFF.name());
				settings.put("showActivity", "true");
				settings.put("activityInCombat", "true");
				settings.put("skillProgress", SkillProgressMode.BOTH.name());
				settings.put("skillProgressInCombat", "false");
				settings.put("specRestored", AlertStyle.FLASH_AND_BEEP.name());
				break;

			case SKILLING:
				// The skill and its rate keep the panel, through a fight as well, where
				// the combat skill is the one being trained anyway. Both icon settings go
				// on: without them the skill page has no activity to hang itself on.
				settings.put("statsEnabled", "true");
				settings.put("showActivity", "true");
				settings.put("activityInCombat", "true");
				settings.put("skillProgress", SkillProgressMode.BOTH.name());
				settings.put("skillProgressInCombat", "true");
				break;

			default:
				break;
		}
		return Collections.unmodifiableMap(settings);
	}

	/**
	 * What this profile asks of one setting, or null if it has no opinion about it.
	 * A stored value that no longer matches is what tells us the profile has been left.
	 */
	String valueFor(String key)
	{
		return settings().get(key);
	}
}
