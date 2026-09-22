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
		description = "Full-panel AFK indicator.",
		position = 1
	)
	String afkSection = "afk";

	@ConfigSection(
		name = "Stats",
		description = "Hitpoints, prayer, energy, special attack, and an optional skilling icon.",
		position = 2
	)
	String statsSection = "stats";

	@ConfigSection(
		name = "Alerts",
		description = "Low hitpoints and prayer warnings.",
		position = 3
	)
	String alertsSection = "alerts";

	@ConfigSection(
		name = "Priority",
		description = "What wins when AFK and low HP/prayer overlap.",
		position = 4
	)
	String prioritySection = "priority";

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
		description = "Turn this on to send a short OK notification to the clock. It turns itself off afterwards.",
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
		description = "Take over the whole display when you go idle. Walking, running, or clicking to move clears it. Moving the mouse does not.",
		position = 0,
		section = afkSection
	)
	default boolean afkEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkSeconds",
		name = "AFK after",
		description = "Seconds without mouse or keyboard input before showing AFK. 0 shows it on the next client tick after input stops. Hovering afterward leaves it up until your character is active again.",
		position = 1,
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
		description = "Text shown on the panel while AFK. Keep it short for the large font.",
		position = 2,
		section = afkSection
	)
	default String afkText()
	{
		return "AFK ";
	}

	@ConfigItem(
		keyName = "afkEffect",
		name = "AFK effect",
		description = "Visual style. Rainbow/Lava/Ocean/Party/Heat waves scroll color across the text. Plasma and Looking eyes use animated backgrounds.",
		position = 3,
		section = afkSection
	)
	default AfkEffect afkEffect()
	{
		return AfkEffect.SOLID;
	}

	@ConfigItem(
		keyName = "afkTextColor",
		name = "AFK text color",
		description = "Used when AFK effect is Solid.",
		position = 4,
		section = afkSection
	)
	default Color afkTextColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "afkBackgroundColor",
		name = "AFK background",
		description = "Solid background when the effect is not a full-panel animation.",
		position = 5,
		section = afkSection
	)
	default Color afkBackgroundColor()
	{
		return new Color(0, 0, 40);
	}

	@ConfigItem(
		keyName = "afkWaveSpeed",
		name = "Wave speed",
		description = "How fast palette colors travel across AFK text (passes per second).",
		position = 6,
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
		description = "Blink AFK text. Ignored for palette wave effects.",
		position = 7,
		section = afkSection
	)
	default boolean afkBlink()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkBlinkMs",
		name = "Blink period",
		description = "Blink period in milliseconds when blink is enabled.",
		position = 8,
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
		description = "Play a short RTTTL tone on the buzzer when AFK starts.",
		position = 9,
		section = afkSection
	)
	default boolean afkSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "afkTintMode",
		name = "AFK tint mode",
		description = "On stats/compact alert mode: recolor all values and the health bar, fill the panel background, both, or off.",
		position = 10,
		section = afkSection
	)
	default AfkTintMode afkTintMode()
	{
		return AfkTintMode.OFF;
	}

	@ConfigItem(
		keyName = "afkTintColor",
		name = "AFK tint color",
		description = "Color used for AFK tint mode (values/health bar and/or background). Gray works well as an idle signal.",
		position = 11,
		section = afkSection
	)
	default Color afkTintColor()
	{
		return new Color(145, 145, 145);
	}

	@ConfigItem(
		keyName = "afkCompactLabel",
		name = "Show AFK label on compact",
		description = "Also prepend a flashing AFK label on the compact stats line. Leave off if tint alone is enough.",
		position = 12,
		section = afkSection
	)
	default boolean afkCompactLabel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "statsEnabled",
		name = "Show stats",
		description = "Push a rotating stats page to the clock while you are logged in and no overlay is taking over.",
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
		description = "Big shows one large stat at a time. Compact shows each stat as a number or a vertical bar.",
		position = 1,
		section = statsSection
	)
	default StatsLayout statsLayout()
	{
		return StatsLayout.COMPACT;
	}

	@ConfigItem(
		keyName = "showHitpoints",
		name = "Hitpoints",
		description = "Include hitpoints on the stats page.",
		position = 2,
		section = statsSection
	)
	default boolean showHitpoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hitpointsStyle",
		name = "Hitpoints as",
		description = "Compact layout: Value prints the number, Bar draws a vertical bar, and Both shows the number and the bar.",
		position = 3,
		section = statsSection
	)
	default StatStyle hitpointsStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "showPrayer",
		name = "Prayer",
		description = "Include prayer on the stats page.",
		position = 4,
		section = statsSection
	)
	default boolean showPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerStyle",
		name = "Prayer as",
		description = "Compact layout: Value prints the number, Bar draws a vertical bar, and Both shows the number and the bar.",
		position = 5,
		section = statsSection
	)
	default StatStyle prayerStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "showEnergy",
		name = "Run energy",
		description = "Include run energy on the stats page.",
		position = 6,
		section = statsSection
	)
	default boolean showEnergy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "energyStyle",
		name = "Run energy as",
		description = "Compact layout: Value prints the number, Bar draws a vertical bar, and Both shows the number and the bar.",
		position = 7,
		section = statsSection
	)
	default StatStyle energyStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "showSpec",
		name = "Special attack",
		description = "Include special-attack percent on the stats page.",
		position = 8,
		section = statsSection
	)
	default boolean showSpec()
	{
		return false;
	}

	@ConfigItem(
		keyName = "specStyle",
		name = "Special attack as",
		description = "Compact layout: Value prints the number, Bar draws a vertical bar, and Both shows the number and the bar.",
		position = 9,
		section = statsSection
	)
	default StatStyle specStyle()
	{
		return StatStyle.VALUE;
	}

	@ConfigItem(
		keyName = "statsRotateSeconds",
		name = "Rotate every",
		description = "How long each big-layout stat stays on screen before rotating.",
		position = 10,
		section = statsSection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int statsRotateSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "compactFlashLowHp",
		name = "Always flash low HP on compact",
		description = "When using compact layout, flash HP digits while low even if Alert display is Full panel only.",
		position = 11,
		section = statsSection
	)
	default boolean compactFlashLowHp()
	{
		return true;
	}

	@ConfigItem(
		keyName = "compactFlashLowPrayer",
		name = "Always flash low prayer on compact",
		description = "When using compact layout, flash prayer while low even if Alert display is Full panel only.",
		position = 12,
		section = statsSection
	)
	default boolean compactFlashLowPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showActivity",
		name = "Activity icon",
		description = "Show an icon while skilling, and a melee, ranged, or magic icon while you are attacking with that style.",
		position = 13,
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
		position = 14,
		section = statsSection
	)
	@Range(min = 0, max = 30)
	@Units(Units.SECONDS)
	default int activityHoldSeconds()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "alertDisplayMode",
		name = "Alert display",
		description = "Full panel uses popup takeovers. On stats keeps the compact/stats page and flashes or shows AFK there. Both does popups and inline.",
		position = 0,
		section = alertsSection
	)
	default AlertDisplayMode alertDisplayMode()
	{
		return AlertDisplayMode.ON_STATS;
	}

	@ConfigItem(
		keyName = "lowHitpointsEnabled",
		name = "Low hitpoints alert",
		description = "Warn when hitpoints are at or below the threshold.",
		position = 1,
		section = alertsSection
	)
	default boolean lowHitpointsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHitpointsThreshold",
		name = "Hitpoints threshold",
		description = "Alert when boosted hitpoints are at or below this value.",
		position = 2,
		section = alertsSection
	)
	@Range(min = 1, max = 99)
	default int lowHitpointsThreshold()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "lowHpFlash",
		name = "Flash low hitpoints",
		description = "Flash HP (on the stats page and/or as a full-panel popup, depending on Alert display).",
		position = 3,
		section = alertsSection
	)
	default boolean lowHpFlash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHpBeep",
		name = "Beep low hitpoints",
		description = "Buzz the TC001 when low HP triggers or drops further.",
		position = 4,
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
		position = 5,
		section = alertsSection
	)
	default boolean lowPrayerEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerThreshold",
		name = "Prayer threshold",
		description = "Alert when boosted prayer is at or below this value.",
		position = 6,
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
		description = "Flash prayer (on the stats page and/or as a full-panel popup, depending on Alert display).",
		position = 7,
		section = alertsSection
	)
	default boolean lowPrayerFlash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerBeep",
		name = "Beep low prayer",
		description = "Buzz the TC001 when low prayer triggers or drops further.",
		position = 8,
		section = alertsSection
	)
	default boolean lowPrayerBeep()
	{
		return false;
	}

	@ConfigItem(
		keyName = "realertOnDrop",
		name = "Flash again on further drop",
		description = "While still under the threshold, flash/beep again each time HP or prayer falls lower.",
		position = 9,
		section = alertsSection
	)
	default boolean realertOnDrop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayPriority",
		name = "When overlapping",
		description = "AFK first keeps AFK on top. Alerts first lets low HP/prayer take the panel. Rotate cycles through whatever is active.",
		position = 0,
		section = prioritySection
	)
	default OverlayPriority overlayPriority()
	{
		return OverlayPriority.ROTATE;
	}

	@ConfigItem(
		keyName = "overlayRotateSeconds",
		name = "Rotate every",
		description = "How long each active overlay stays when priority is Rotate active.",
		position = 1,
		section = prioritySection
	)
	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	default int overlayRotateSeconds()
	{
		return 3;
	}
}
