package com.ulanzi.osrs;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(UlanziConfig.GROUP)
public interface UlanziConfig extends Config
{
	String GROUP = "ulanziOsrs";

	@ConfigSection(
		name = "Connection",
		description = "AWTRIX NG device address and credentials.",
		position = 0
	)
	String connectionSection = "connection";

	@ConfigSection(
		name = "AFK",
		description = "Idle indicator.",
		position = 1
	)
	String afkSection = "afk";

	@ConfigSection(
		name = "Stats",
		description = "Hitpoints, prayer, energy, special attack, and the activity icon.",
		position = 2
	)
	String statsSection = "stats";

	@ConfigSection(
		name = "Skill progress",
		description = "Level, progress to the next level, XP per hour, and XP drops while you train.",
		position = 3
	)
	String skillProgressSection = "skillProgress";

	@ConfigSection(
		name = "Alerts",
		description = "Low hitpoints and prayer warnings, and what wins when several are active.",
		position = 4
	)
	String alertsSection = "alerts";

	@ConfigSection(
		name = "Level ups",
		description = "Celebrate a new level on the clock.",
		position = 5
	)
	String levelUpSection = "levelUps";

	@ConfigItem(
		keyName = "host",
		name = "Clock address",
		description = "IP or hostname of the AWTRIX NG clock. Include a port if needed, e.g. 192.168.1.50 or 192.168.1.50:80.",
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
		description = "Optional HTTP basic-auth username if you enabled authentication on AWTRIX NG.",
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
		description = "Optional HTTP basic-auth password.",
		position = 2,
		section = connectionSection,
		secret = true
	)
	default String password()
	{
		return "";
	}

	@ConfigItem(
		keyName = "testConnection",
		name = "Test connection",
		description = "Turn this on to send a short OK to the clock. The result is posted in the game chat, and the box turns itself off.",
		position = 3,
		section = connectionSection
	)
	default boolean testConnection()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkEnabled",
		name = "AFK indicator",
		description = "Show AFK when you go idle. Walking, running, or clicking to move clears it. Moving the mouse does not.",
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
		description = "Full panel takes over the whole display with the AFK text and effect. On the stats page keeps your stats up and marks them with the tint or label below.",
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
		description = "Seconds without mouse or keyboard input before showing AFK. 0 shows it on the next client tick after input stops. Hovering afterward leaves it up until your character is active again.",
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
		keyName = "afkText",
		name = "AFK text",
		description = "Text shown on the full panel while AFK. Keep it short for the large font.",
		position = 3,
		section = afkSection
	)
	default String afkText()
	{
		return "AFK ";
	}

	@ConfigItem(
		keyName = "afkEffect",
		name = "AFK effect",
		description = "Full panel style. The wave effects scroll color across the text. Plasma and Looking eyes use animated backgrounds.",
		position = 4,
		section = afkSection
	)
	default AfkEffect afkEffect()
	{
		return AfkEffect.SOLID;
	}

	@ConfigItem(
		keyName = "afkTextColor",
		name = "AFK text color",
		description = "Used by Solid, Plasma, and Looking eyes. The wave effects color the text themselves.",
		position = 5,
		section = afkSection
	)
	default Color afkTextColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "afkBackgroundColor",
		name = "AFK background",
		description = "Used by Solid and the wave effects. Plasma and Looking eyes draw their own background.",
		position = 6,
		section = afkSection
	)
	default Color afkBackgroundColor()
	{
		return new Color(0, 0, 40);
	}

	@ConfigItem(
		keyName = "afkWaveSpeed",
		name = "Wave speed",
		description = "Wave effects only. How fast the colors travel across the text.",
		position = 7,
		section = afkSection
	)
	@Range(min = 1, max = 50)
	default int afkWaveSpeed()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "afkBlink",
		name = "Blink AFK text",
		description = "Solid effect only.",
		position = 8,
		section = afkSection
	)
	default boolean afkBlink()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkBlinkMs",
		name = "Blink period",
		description = "Blink period in milliseconds when blink is on.",
		position = 9,
		section = afkSection
	)
	@Range(min = 100, max = 5000)
	@Units(Units.MILLISECONDS)
	default int afkBlinkMs()
	{
		return 600;
	}

	@ConfigItem(
		keyName = "afkSound",
		name = "AFK buzz",
		description = "Play a short tone on the buzzer when AFK starts.",
		position = 10,
		section = afkSection
	)
	default boolean afkSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkTintMode",
		name = "AFK tint on stats",
		description = "When AFK display is On the stats page: recolor the values and bars, fill the background, both, or off.",
		position = 11,
		section = afkSection
	)
	default AfkTintMode afkTintMode()
	{
		return AfkTintMode.VALUES;
	}

	@ConfigItem(
		keyName = "afkTintColor",
		name = "AFK tint color",
		description = "Color for the AFK tint on the stats page. Gray works well as an idle signal.",
		position = 12,
		section = afkSection
	)
	default Color afkTintColor()
	{
		return new Color(145, 145, 145);
	}

	@ConfigItem(
		keyName = "afkCompactLabel",
		name = "AFK label on stats",
		description = "When AFK display is On the stats page, also put a flashing AFK label in front of the values.",
		position = 13,
		section = afkSection
	)
	default boolean afkCompactLabel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "statsEnabled",
		name = "Show stats",
		description = "Push a stats page to the clock while you are logged in and no overlay is taking over.",
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
		description = "Big shows one large stat at a time with its orb icon. Compact shows every stat on one line.",
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
		description = "Value prints the number, Bar draws a bar, and Both shows the two. Compact bars are vertical on the right edge; Big bars run along the bottom.",
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
		description = "Value prints the number, Bar draws a bar, and Both shows the two. Compact bars are vertical on the right edge; Big bars run along the bottom.",
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
		description = "Value prints the number, Bar draws a bar, and Both shows the two. Compact bars are vertical on the right edge; Big bars run along the bottom.",
		position = 4,
		section = statsSection
	)
	default StatStyle energyStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "specStyle",
		name = "Special attack",
		description = "Value prints the number, Bar draws a bar, and Both shows the two. Compact bars are vertical on the right edge; Big bars run along the bottom.",
		position = 5,
		section = statsSection
	)
	default StatStyle specStyle()
	{
		return StatStyle.OFF;
	}

	@ConfigItem(
		keyName = "statsRotateSeconds",
		name = "Rotate every",
		description = "Big layout: how long each stat stays on screen before rotating.",
		position = 6,
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
		name = "Activity icon",
		description = "Show an icon while skilling, and a melee, ranged, or magic icon while you are attacking with that style. If the compact line no longer fits beside it, the last values switch to bars.",
		position = 7,
		section = statsSection
	)
	default boolean showActivity()
	{
		return true;
	}

	@ConfigItem(
		keyName = "activityHoldSeconds",
		name = "Activity hold",
		description = "Seconds to keep the icon after the action stops. 0 clears it on the next tick. Increase it if walking between trees or rocks makes the icon flicker.",
		position = 8,
		section = statsSection
	)
	@Range(min = 0, max = 30)
	@Units(Units.SECONDS)
	default int activityHoldSeconds()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "skillProgress",
		name = "While training",
		description = "Replace the stats with the skill you are training while the activity icon is up. The bottom row fills toward the next level. Level and XP/h shows both when they fit and alternates when they do not. Needs Activity icon on.",
		position = 0,
		section = skillProgressSection
	)
	default SkillProgressMode skillProgress()
	{
		return SkillProgressMode.OFF;
	}

	@ConfigItem(
		keyName = "xpRateSource",
		name = "XP/h source",
		description = "Session averages each skill from your second XP drop. Sliding window averages only the last few seconds, so the rate follows what you are doing now instead of the session so far. Both start over after 5 minutes without XP. RuneLite XP Tracker matches its panel and globes, including its pause and reset settings; while it has no rate, the session average is used.",
		position = 1,
		section = skillProgressSection
	)
	default XpRateSource xpRateSource()
	{
		return XpRateSource.SLIDING_WINDOW;
	}

	@ConfigItem(
		keyName = "xpRateWindowSeconds",
		name = "Sliding window",
		description = "How far back the sliding window looks. The rate is always divided by the whole window, so it climbs from zero over the first one and falls back to zero when you stop. Short suits steady XP like alching, long suits bursty XP like combat. Only used by the sliding window source.",
		position = 2,
		section = skillProgressSection
	)
	@Range(min = 5, max = 900)
	@Units(Units.SECONDS)
	default int xpRateWindowSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "progressGradient",
		name = "Progress bar",
		description = "Colors of the bar toward the next level. The gradient is revealed as the bar fills.",
		position = 3,
		section = skillProgressSection
	)
	default ProgressGradient progressGradient()
	{
		return ProgressGradient.SKILL;
	}

	@ConfigItem(
		keyName = "skillProgressInCombat",
		name = "Also in combat",
		description = "Show the combat skill's progress while fighting too. Off keeps hitpoints and prayer up in combat.",
		position = 4,
		section = skillProgressSection
	)
	default boolean skillProgressInCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "xpDrops",
		name = "XP drops",
		description = "Fly each XP gain, like +175 xp, across the panel. Gains that land while one is still flying are added to the next.",
		position = 5,
		section = skillProgressSection
	)
	default boolean xpDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "xpDropDirection",
		name = "XP drop direction",
		description = "Which way XP drops travel across the panel. In place does not move: on the skill progress page it briefly replaces the level and XP/h, elsewhere it shows still for a moment.",
		position = 6,
		section = skillProgressSection
	)
	default XpDropDirection xpDropDirection()
	{
		return XpDropDirection.LEFT;
	}

	@ConfigItem(
		keyName = "alertDisplayMode",
		name = "Alert display",
		description = "Full panel takes over the display with a flashing popup. On the stats page flashes the value in place. Both does the two.",
		position = 0,
		section = alertsSection
	)
	default AlertDisplayMode alertDisplayMode()
	{
		return AlertDisplayMode.ON_STATS;
	}

	@ConfigItem(
		keyName = "thresholdUnit",
		name = "Thresholds in",
		description = "Percent of max scales with your level, so 25 means a quarter of your hitpoints. Points compares the number directly.",
		position = 1,
		section = alertsSection
	)
	default ThresholdUnit thresholdUnit()
	{
		return ThresholdUnit.PERCENT;
	}

	@ConfigItem(
		keyName = "lowHitpointsEnabled",
		name = "Low hitpoints alert",
		description = "Warn when hitpoints are at or below the threshold.",
		position = 2,
		section = alertsSection
	)
	default boolean lowHitpointsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHitpointsThreshold",
		name = "Hitpoints threshold",
		description = "Alert when hitpoints are at or below this, in the unit chosen by Thresholds in.",
		position = 3,
		section = alertsSection
	)
	@Range(min = 1, max = 99)
	default int lowHitpointsThreshold()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "lowHpFlash",
		name = "Flash low hitpoints",
		description = "Flash HP on the stats page or as a popup, depending on Alert display.",
		position = 4,
		section = alertsSection
	)
	default boolean lowHpFlash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHpBeep",
		name = "Beep low hitpoints",
		description = "Buzz the clock when low HP triggers or drops further.",
		position = 5,
		section = alertsSection
	)
	default boolean lowHpBeep()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerEnabled",
		name = "Low prayer alert",
		description = "Warn when prayer is at or below the threshold.",
		position = 6,
		section = alertsSection
	)
	default boolean lowPrayerEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerThreshold",
		name = "Prayer threshold",
		description = "Alert when prayer is at or below this, in the unit chosen by Thresholds in.",
		position = 7,
		section = alertsSection
	)
	@Range(min = 1, max = 99)
	default int lowPrayerThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "lowPrayerFlash",
		name = "Flash low prayer",
		description = "Flash prayer on the stats page or as a popup, depending on Alert display.",
		position = 8,
		section = alertsSection
	)
	default boolean lowPrayerFlash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerBeep",
		name = "Beep low prayer",
		description = "Buzz the clock when low prayer triggers or drops further.",
		position = 9,
		section = alertsSection
	)
	default boolean lowPrayerBeep()
	{
		return false;
	}

	@ConfigItem(
		keyName = "realertOnDrop",
		name = "Flash again on further drop",
		description = "While still under the threshold, flash and beep again each time HP or prayer falls lower.",
		position = 10,
		section = alertsSection
	)
	default boolean realertOnDrop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayPriority",
		name = "When overlapping",
		description = "What shows when AFK and low HP or prayer are active together. AFK first keeps AFK on top, Alerts first lets the warning win, and Rotate cycles through them.",
		position = 11,
		section = alertsSection
	)
	default OverlayPriority overlayPriority()
	{
		return OverlayPriority.ROTATE;
	}

	@ConfigItem(
		keyName = "overlayRotateSeconds",
		name = "Rotate overlays every",
		description = "How long each active overlay stays when When overlapping is Rotate active.",
		position = 12,
		section = alertsSection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int overlayRotateSeconds()
	{
		return 3;
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
		description = "Play a short fanfare on the buzzer with the celebration.",
		position = 1,
		section = levelUpSection
	)
	default boolean levelUpSound()
	{
		return true;
	}
}
