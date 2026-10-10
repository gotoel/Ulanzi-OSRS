package com.ulanzi.osrs;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/*
 * Three words are used throughout and mean three different things: the clock is the
 * device, the panel is its 32x8 surface, and a page is one thing drawn on it. Every
 * description says what the setting does first; why it works that way is in the README.
 *
 * RuneLite has no way to grey out a setting that does not currently apply, so where a
 * setting only belongs to one layout or one display mode, its name says so.
 */
@ConfigGroup(UlanziConfig.GROUP)
public interface UlanziConfig extends Config
{
	String GROUP = "ulanziOsrs";

	@ConfigSection(
		name = "Connection",
		description = "Clock address, status, and how bright the panel runs.",
		position = 0
	)
	String connectionSection = "connection";

	@ConfigSection(
		name = "Stats",
		description = "Hitpoints, prayer, run energy, special attack, and the activity icon.",
		position = 1
	)
	String statsSection = "stats";

	@ConfigSection(
		name = "Training",
		description = "The skill page, XP per hour, and XP drops.",
		position = 2
	)
	String trainingSection = "training";

	@ConfigSection(
		name = "Alerts",
		description = "Low hitpoints and prayer warnings.",
		position = 3
	)
	String alertsSection = "alerts";

	@ConfigSection(
		name = "AFK",
		description = "Idle indicator.",
		position = 4,
		closedByDefault = true
	)
	String afkSection = "afk";

	@ConfigSection(
		name = "Forestry",
		description = "Call out a forestry event starting around you while you chop.",
		position = 5,
		closedByDefault = true
	)
	String forestrySection = "forestry";

	@ConfigSection(
		name = "Level ups",
		description = "Celebrate a new level on the clock.",
		position = 6,
		closedByDefault = true
	)
	String levelUpSection = "levelUps";

	@ConfigSection(
		name = "Advanced",
		description = "Settings worth changing once, if at all.",
		position = 7,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "profile",
		name = "Quick profile",
		description = "Sets the panel up for one kind of play. Combat keeps hitpoints and prayer on the line; "
			+ "Skilling puts the skill you are training and its XP/h up and keeps them through a fight; Hybrid shows "
			+ "the skill page while skilling and the combat line while fighting. Picking one "
			+ "fills in the settings below, and changing any of those puts this back to Custom.",
		position = 0
	)
	default Profile profile()
	{
		return Profile.CUSTOM;
	}

	@ConfigItem(
		keyName = "host",
		name = "Clock address",
		description = "IP or hostname of the AWTRIX NG clock, with a port if it needs one. For example 192.168.1.50. "
			+ "Nothing is sent until this is set. Hitpoints, prayer, run energy, special attack, and skill progress go there.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0,
		section = connectionSection
	)
	default String host()
	{
		return "";
	}

	@ConfigItem(
		keyName = "username",
		name = "Username",
		description = "Only if you turned on authentication in AWTRIX NG. Leave empty otherwise.",
		position = 1,
		section = connectionSection
	)
	default String username()
	{
		return "";
	}

	@ConfigItem(
		keyName = "password",
		name = "Password",
		description = "Only if you turned on authentication in AWTRIX NG.",
		position = 2,
		section = connectionSection,
		secret = true
	)
	default String password()
	{
		return "";
	}

	@ConfigItem(
		keyName = "status",
		name = "Status",
		description = "Whether the clock is answering. The plugin writes this line; anything you type here is replaced.",
		position = 3,
		section = connectionSection
	)
	default String status()
	{
		return "No clock address set.";
	}

	@ConfigItem(
		keyName = "preview",
		name = "Preview",
		description = "Put a page on the clock with sample readings, so a layout can be judged without playing. "
			+ "Works while you are logged out, and turns itself back off.",
		position = 4,
		section = connectionSection
	)
	default Preview preview()
	{
		return Preview.OFF;
	}

	@ConfigItem(
		keyName = "brightness",
		name = "Brightness",
		description = "How bright to hold the panel, on the clock's own scale. 0 leaves it to the clock's light "
			+ "sensor. Anything from 1 to 7 is raised to 8, below which the panel has no colour left to draw with.",
		position = 5,
		section = connectionSection
	)
	@Range(min = 0, max = 255)
	default int brightness()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "statsEnabled",
		name = "Show stats",
		description = "Put a stats page on the panel while you are logged in and nothing has taken it over.",
		position = 0,
		section = statsSection
	)
	default boolean statsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "statsLayout",
		name = "Layout",
		description = "Big shows one large stat at a time, rotating. Compact shows every stat on one line over a "
			+ "strip of bars. Focus shows whichever stat changed last in large digits and keeps the rest on the strip.",
		position = 1,
		section = statsSection
	)
	default StatsLayout statsLayout()
	{
		return StatsLayout.COMPACT;
	}

	@ConfigItem(
		keyName = "hitpointsStyle",
		name = "Hitpoints",
		description = "Value prints the number, Bar draws a bar, Both shows the two.",
		position = 2,
		section = statsSection
	)
	default StatStyle hitpointsStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "prayerStyle",
		name = "Prayer",
		description = "Value prints the number, Bar draws a bar, Both shows the two.",
		position = 3,
		section = statsSection
	)
	default StatStyle prayerStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "energyStyle",
		name = "Run energy",
		description = "Value prints the number, Bar draws a bar, Both shows the two.",
		position = 4,
		section = statsSection
	)
	default StatStyle energyStyle()
	{
		return StatStyle.BAR;
	}

	@ConfigItem(
		keyName = "specStyle",
		name = "Special attack",
		description = "Value prints the number, Bar draws a bar, Both shows the two.",
		position = 5,
		section = statsSection
	)
	default StatStyle specStyle()
	{
		return StatStyle.BAR;
	}

	@ConfigItem(
		keyName = "statOrbs",
		name = "Compact: drain orbs",
		description = "A heart at the left edge and a prayer diamond at the right, each emptying from the top as the "
			+ "stat drains. They take six columns each, which usually pushes run energy and spec onto the bottom strip.",
		position = 6,
		section = statsSection
	)
	default boolean statOrbs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "compactPriority",
		name = "Compact: never drop",
		description = "When the numbers no longer fit on the line, every other stat gives up its digits for a bar on "
			+ "the bottom strip before this one does.",
		position = 7,
		section = statsSection
	)
	default StatKind compactPriority()
	{
		return StatKind.HITPOINTS;
	}

	@ConfigItem(
		keyName = "statsRotateSeconds",
		name = "Big: rotate every",
		description = "How long each stat holds the panel in the Big layout. Nothing else uses this.",
		position = 8,
		section = statsSection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int statsRotateSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "showActivity",
		name = "Skilling icon",
		description = "Draw an icon for the skill you are training. Turning it off only takes the icon away; the "
			+ "skill page under Training works either way.",
		position = 9,
		section = statsSection
	)
	default boolean showActivity()
	{
		return true;
	}

	@ConfigItem(
		keyName = "activityInCombat",
		name = "Combat style icon",
		description = "Draw a melee, ranged, or magic icon while you are fighting. Off leaves the whole line to your "
			+ "stats; the skilling icon is unaffected.",
		position = 10,
		section = statsSection
	)
	default boolean activityInCombat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "skillProgress",
		name = "Skill page",
		description = "Replace the stats with the skill you are training: its level, its XP/h, or both. The bottom "
			+ "row fills toward the next level.",
		position = 0,
		section = trainingSection
	)
	default SkillProgressMode skillProgress()
	{
		return SkillProgressMode.BOTH;
	}

	@ConfigItem(
		keyName = "hideMaxedSkill",
		name = "Hide skill page at 99",
		description = "Leave the stats up when the skill you are training is already 99. A skill still climbing keeps "
			+ "the page, and the activity icon stays on the stats line.",
		position = 1,
		section = trainingSection
	)
	default boolean hideMaxedSkill()
	{
		return true;
	}

	@ConfigItem(
		keyName = "skillProgressInCombat",
		name = "Keep skill page in combat",
		description = "Let the skill page hold the panel while you fight. Off hands it back to hitpoints and prayer.",
		position = 2,
		section = trainingSection
	)
	default boolean skillProgressInCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "skillProgressBars",
		name = "Skill page: stat bars",
		description = "Keep the stats on the skill page as thin columns down the right edge, one for each stat set to "
			+ "anything but Off under Stats. The words shift left to make room.",
		position = 3,
		section = trainingSection
	)
	default boolean skillProgressBars()
	{
		return false;
	}

	@ConfigItem(
		keyName = "progressGradient",
		name = "Progress bar",
		description = "Colours of the bar toward the next level. The gradient is revealed as the bar fills.",
		position = 4,
		section = trainingSection
	)
	default ProgressGradient progressGradient()
	{
		return ProgressGradient.SKILL;
	}

	@ConfigItem(
		keyName = "xpRateSource",
		name = "XP/h source",
		description = "Sliding window follows what you are doing now. Session averages the skill from your second XP "
			+ "drop. RuneLite XP Tracker matches its panel and globes, falling back to the session average.",
		position = 5,
		section = trainingSection
	)
	default XpRateSource xpRateSource()
	{
		return XpRateSource.CLOCK;
	}

	@ConfigItem(
		keyName = "alternateSkills",
		name = "Alternate skills",
		description = "When more than one skill is paying XP, give each the panel in turn instead of letting every "
			+ "drop pull the page to whichever skill paid last.",
		position = 6,
		section = trainingSection
	)
	default boolean alternateSkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "skillRotateSeconds",
		name = "Alternate every",
		description = "How long each skill holds the panel when several are paying XP, and how often level and XP/h "
			+ "swap over.",
		position = 7,
		section = trainingSection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int skillRotateSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "xpDrops",
		name = "XP drops",
		description = "Show each XP gain as it lands. Gains that arrive while one is still showing are added to the next.",
		position = 8,
		section = trainingSection
	)
	default boolean xpDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "xpDropDirection",
		name = "XP drop style",
		description = "Which way a drop travels across the panel. In place swaps the text without moving it. Pulse "
			+ "writes nothing and snaps the whole page to full colour as XP lands, resting dimmed between drops.",
		position = 9,
		section = trainingSection
	)
	default XpDropDirection xpDropDirection()
	{
		return XpDropDirection.PULSE;
	}

	@ConfigItem(
		keyName = "alertDisplayMode",
		name = "Alert display",
		description = "Full panel takes the display with a flashing popup. On the stats page flashes the value in "
			+ "place. Both does the two.",
		position = 0,
		section = alertsSection
	)
	default AlertDisplayMode alertDisplayMode()
	{
		return AlertDisplayMode.FULL_PANEL;
	}

	@ConfigItem(
		keyName = "thresholdUnit",
		name = "Thresholds in",
		description = "Percent of max scales with your level, so 25 means a quarter of your hitpoints. Points "
			+ "compares the number directly.",
		position = 1,
		section = alertsSection
	)
	default ThresholdUnit thresholdUnit()
	{
		return ThresholdUnit.PERCENT;
	}

	@ConfigItem(
		keyName = "lowHitpoints",
		name = "Low hitpoints",
		description = "What happens when hitpoints reach the threshold below. Flash follows Alert display; Beep is "
			+ "the clock's buzzer.",
		position = 2,
		section = alertsSection
	)
	default AlertStyle lowHitpoints()
	{
		return AlertStyle.FLASH;
	}

	@ConfigItem(
		keyName = "lowHitpointsThreshold",
		name = "Hitpoints threshold",
		description = "Warn at or below this, in whatever Thresholds in is set to.",
		position = 3,
		section = alertsSection
	)
	@Range(min = 1, max = 99)
	default int lowHitpointsThreshold()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "lowPrayer",
		name = "Low prayer",
		description = "What happens when prayer reaches the threshold below. Flash follows Alert display; Beep is "
			+ "the clock's buzzer.",
		position = 4,
		section = alertsSection
	)
	default AlertStyle lowPrayer()
	{
		return AlertStyle.FLASH;
	}

	@ConfigItem(
		keyName = "lowPrayerThreshold",
		name = "Prayer threshold",
		description = "Warn at or below this, in whatever Thresholds in is set to.",
		position = 5,
		section = alertsSection
	)
	@Range(min = 1, max = 99)
	default int lowPrayerThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "specRestored",
		name = "Special attack ready",
		description = "What happens when the special attack bar comes back up to the amount below. Flash follows "
			+ "Alert display; Beep is the clock's buzzer.",
		position = 6,
		section = alertsSection
	)
	default AlertStyle specRestored()
	{
		return AlertStyle.OFF;
	}

	@ConfigItem(
		keyName = "specReadyThreshold",
		name = "Special attack ready at",
		description = "How much of the bar counts as ready. Leave it at 100 to be told only when it is full; drop "
			+ "it to 50 for a weapon that specs at half.",
		position = 7,
		section = alertsSection
	)
	@Range(min = 25, max = 100)
	@Units(Units.PERCENT)
	default int specReadyThreshold()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "realertOnDrop",
		name = "Alert again on further drop",
		description = "While still under the threshold, fire again each time hitpoints or prayer fall lower.",
		position = 8,
		section = alertsSection
	)
	default boolean realertOnDrop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayPriority",
		name = "AFK vs. alert priority",
		description = "What shows when AFK and a warning are active together. A forestry event wins over both, "
			+ "except against low hitpoints.",
		position = 9,
		section = alertsSection
	)
	default OverlayPriority overlayPriority()
	{
		return OverlayPriority.ROTATE;
	}

	@ConfigItem(
		keyName = "overlayRotateSeconds",
		name = "Rotate overlays every",
		description = "How long each one holds the panel when the priority above is Rotate.",
		position = 10,
		section = alertsSection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int overlayRotateSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "afkEnabled",
		name = "AFK indicator",
		description = "Show AFK when you go idle. Off by default. Walking, running, or clicking to move clears it; "
			+ "moving the mouse does not. A pause between swings on a wall rock, such as a Motherlode vein, "
			+ "does not count.",
		position = 0,
		section = afkSection
	)
	default boolean afkEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkDisplay",
		name = "AFK display",
		description = "Full panel takes the display with the AFK text and effect. On the stats page keeps your stats "
			+ "up and marks them instead.",
		position = 1,
		section = afkSection
	)
	default AlertDisplayMode afkDisplay()
	{
		return AlertDisplayMode.FULL_PANEL;
	}

	@ConfigItem(
		keyName = "afkSeconds",
		name = "AFK after",
		description = "Seconds without mouse or keyboard input before showing AFK. 0 shows it on the next tick after "
			+ "input stops.",
		position = 2,
		section = afkSection
	)
	@Range(min = 0, max = 600)
	@Units(Units.SECONDS)
	default int afkSeconds()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "afkInCombat",
		name = "AFK in combat",
		description = "Let AFK show while you are fighting. Off by default: waiting for the next attack looks exactly "
			+ "like being idle, so the page would flash up and off all through a fight.",
		position = 3,
		section = afkSection
	)
	default boolean afkInCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkText",
		name = "Full panel: text",
		description = "Text shown on the full panel while AFK. Keep it short for the large font.",
		position = 4,
		section = afkSection
	)
	default String afkText()
	{
		return "AFK ";
	}

	@ConfigItem(
		keyName = "afkEffect",
		name = "Full panel: effect",
		description = "The wave effects scroll colour across the text. Plasma and Looking eyes draw animated "
			+ "backgrounds of their own.",
		position = 5,
		section = afkSection
	)
	default AfkEffect afkEffect()
	{
		return AfkEffect.SOLID;
	}

	@ConfigItem(
		keyName = "afkTextColor",
		name = "Full panel: text colour",
		description = "Used by Solid, Plasma, and Looking eyes. The wave effects colour the text themselves.",
		position = 6,
		section = afkSection
	)
	default Color afkTextColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "afkBackgroundColor",
		name = "Full panel: background",
		description = "Used by Solid and the wave effects. Plasma and Looking eyes draw their own.",
		position = 7,
		section = afkSection
	)
	default Color afkBackgroundColor()
	{
		return new Color(0, 0, 40);
	}

	@ConfigItem(
		keyName = "afkWaveSpeed",
		name = "Full panel: wave speed",
		description = "How fast the colours travel across the text. Wave effects only.",
		position = 8,
		section = afkSection
	)
	@Range(min = 1, max = 50)
	default int afkWaveSpeed()
	{
		return 21;
	}

	@ConfigItem(
		keyName = "afkBlinkMs",
		name = "Full panel: blink period",
		description = "Blink the AFK text this often. 0 does not blink. Solid effect only.",
		position = 9,
		section = afkSection
	)
	@Range(min = 0, max = 5000)
	@Units(Units.MILLISECONDS)
	default int afkBlinkMs()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "afkSound",
		name = "AFK buzz",
		description = "Play a short tone on the clock's buzzer when AFK starts.",
		position = 10,
		section = afkSection
	)
	default boolean afkSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkTintMode",
		name = "Stats page: mark as",
		description = "How the stats page says you are away, when AFK display leaves it up.",
		position = 11,
		section = afkSection
	)
	default AfkTintMode afkTintMode()
	{
		return AfkTintMode.OFF;
	}

	@ConfigItem(
		keyName = "afkTintColor",
		name = "Stats page: tint colour",
		description = "Colour for the tint above. Grey works well as an idle signal.",
		position = 12,
		section = afkSection
	)
	default Color afkTintColor()
	{
		return new Color(145, 145, 145);
	}

	@ConfigItem(
		keyName = "forestryEnabled",
		name = "Forestry event alert",
		description = "Call out rising roots, a struggling sapling, poachers and the rest when one starts around you. "
			+ "Off by default.",
		position = 0,
		section = forestrySection
	)
	default boolean forestryEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "forestryWhileChopping",
		name = "Only while chopping",
		description = "Only call an event when you have been cutting a tree in the last minute, so walking past "
			+ "someone else's does not take the panel.",
		position = 1,
		section = forestrySection
	)
	default boolean forestryWhileChopping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "forestryDisplay",
		name = "Forestry display",
		description = "Full panel takes the display with the event. On the stats page puts it in the stats slot "
			+ "instead.",
		position = 2,
		section = forestrySection
	)
	default AlertDisplayMode forestryDisplay()
	{
		return AlertDisplayMode.FULL_PANEL;
	}

	@ConfigItem(
		keyName = "forestryEffect",
		name = "Forestry effect",
		description = "What grows behind the event's name. Each event has its own colour, so which one it is reads "
			+ "before the name does.",
		position = 3,
		section = forestrySection
	)
	default ForestryEffect forestryEffect()
	{
		return ForestryEffect.GROVE;
	}

	@ConfigItem(
		keyName = "forestrySeconds",
		name = "Show event for",
		description = "How long the event holds the panel before it goes back to what it was showing.",
		position = 4,
		section = forestrySection
	)
	@Range(min = 2, max = 60)
	@Units(Units.SECONDS)
	default int forestrySeconds()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "forestryBeep",
		name = "Forestry chime",
		description = "Play a short chime on the clock's buzzer when an event starts. Off by default.",
		position = 5,
		section = forestrySection
	)
	default boolean forestryBeep()
	{
		return false;
	}

	@ConfigItem(
		keyName = "levelUpEnabled",
		name = "Level-up celebration",
		description = "Show the skill icon and your new level for a few seconds when you level up.",
		position = 0,
		section = levelUpSection
	)
	default boolean levelUpEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "levelUpSound",
		name = "Level-up jingle",
		description = "Play a short fanfare on the clock's buzzer with the celebration. Off by default: it is loud.",
		position = 1,
		section = levelUpSection
	)
	default boolean levelUpSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minBrightness",
		name = "Minimum brightness",
		description = "Never let the panel sit below this, whoever set it there. Under about 17 the clock has so few "
			+ "steps of colour left that hues collapse together and thin bars stop being drawn. 0 turns this off.",
		position = 0,
		section = advancedSection
	)
	@Range(min = 0, max = 255)
	default int minBrightness()
	{
		return 17;
	}

	@ConfigItem(
		keyName = "connectionMessages",
		name = "Connection messages",
		description = "Also post a line in the game chat when the clock stops answering and when it comes back. "
			+ "Status, under Connection, says the same thing without the chat.",
		position = 1,
		section = advancedSection
	)
	default boolean connectionMessages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "xpRateWindowSeconds",
		name = "Sliding window",
		description = "How far back the sliding XP/h window looks. Short suits steady XP like alching, long suits "
			+ "bursty XP like combat. Only used by the sliding window source.",
		position = 2,
		section = advancedSection
	)
	@Range(min = 5, max = 900)
	@Units(Units.SECONDS)
	default int xpRateWindowSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "activityHoldSeconds",
		name = "Activity hold",
		description = "Seconds to keep the activity icon after the action stops. Raise it if walking between trees "
			+ "or rocks makes the icon flicker.",
		position = 3,
		section = advancedSection
	)
	@Range(min = 0, max = 30)
	@Units(Units.SECONDS)
	default int activityHoldSeconds()
	{
		return 5;
	}
}
