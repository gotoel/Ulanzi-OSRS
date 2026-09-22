package com.ulanzi.osrs;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class SkillProgressTest
{
	@Test
	public void rateStartsOnTheSecondDrop()
	{
		XpRateTracker rates = new XpRateTracker();
		rates.onXp(Skill.WOODCUTTING, 175, 0L);
		Assert.assertEquals(-1, rates.perHour(Skill.WOODCUTTING, 1_000L));

		rates.onXp(Skill.WOODCUTTING, 100, 60_000L);
		Assert.assertEquals(6_000, rates.perHour(Skill.WOODCUTTING, 60_000L));
		Assert.assertEquals(-1, rates.perHour(Skill.MINING, 60_000L));
	}

	@Test
	public void aLongBreakStartsANewSession()
	{
		XpRateTracker rates = new XpRateTracker();
		rates.onXp(Skill.FISHING, 50, 0L);
		rates.onXp(Skill.FISHING, 50, 10_000L);
		Assert.assertEquals(-1, rates.perHour(Skill.FISHING, 10_000L + XpRateTracker.BREAK_MS + 1));

		long back = 20_000L + XpRateTracker.BREAK_MS;
		rates.onXp(Skill.FISHING, 50, back);
		Assert.assertEquals(-1, rates.perHour(Skill.FISHING, back));
	}

	@Test
	public void theWindowDividesByTheWholeWindowNotTheTimePlayed()
	{
		XpRateTracker rates = new XpRateTracker();
		// One 100 XP drop, ten seconds in, read against a 60 second window:
		// 100 * 3600 / 60 = 6000, not the 36000 the ten seconds alone would give.
		rates.onXp(Skill.WOODCUTTING, 100, 10_000L);
		Assert.assertEquals(6_000, rates.perHourWindow(Skill.WOODCUTTING, 10_000L, 60_000L));
		Assert.assertEquals(-1, rates.perHourWindow(Skill.MINING, 10_000L, 60_000L));
	}

	@Test
	public void theWindowCountsTheFirstDropUnlikeTheSessionAverage()
	{
		XpRateTracker rates = new XpRateTracker();
		rates.onXp(Skill.FISHING, 100, 0L);
		Assert.assertEquals(-1, rates.perHour(Skill.FISHING, 0L));
		Assert.assertEquals(6_000, rates.perHourWindow(Skill.FISHING, 0L, 60_000L));
	}

	@Test
	public void theWindowDecaysToZeroWhenDropsFallOutOfIt()
	{
		XpRateTracker rates = new XpRateTracker();
		rates.onXp(Skill.MINING, 100, 0L);
		rates.onXp(Skill.MINING, 100, 1_000L);
		Assert.assertEquals(12_000, rates.perHourWindow(Skill.MINING, 1_000L, 60_000L));

		// Both drops are now older than the window, but the break has not elapsed.
		Assert.assertEquals(0, rates.perHourWindow(Skill.MINING, 61_000L, 60_000L));
		Assert.assertEquals(-1, rates.perHourWindow(Skill.MINING, 1_000L + XpRateTracker.BREAK_MS + 1, 60_000L));
	}

	@Test
	public void ratesStayFourCharactersWide()
	{
		Assert.assertEquals("950", XpRateTracker.format(950));
		Assert.assertEquals("9k", XpRateTracker.format(9_000));
		Assert.assertEquals("9.5k", XpRateTracker.format(9_540));
		Assert.assertEquals("125k", XpRateTracker.format(125_400));
		Assert.assertEquals("1.2m", XpRateTracker.format(1_250_000));
		Assert.assertEquals("12m", XpRateTracker.format(12_000_000));
	}

	@Test
	public void progressRunsFromThisLevelToTheNext()
	{
		Assert.assertEquals(0, UlanziOsrsPlugin.levelProgress(0));
		Assert.assertEquals(49, UlanziOsrsPlugin.levelProgress(41));
		Assert.assertEquals(0, UlanziOsrsPlugin.levelProgress(Experience.getXpForLevel(70)));
		Assert.assertEquals(100, UlanziOsrsPlugin.levelProgress(Experience.getXpForLevel(99)));
		Assert.assertEquals(100, UlanziOsrsPlugin.levelProgress(200_000_000));
	}

	@Test
	public void levelAndRateShareTheLineWhenTheyFit()
	{
		List<AwtrixClient.TextFragment> text = UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.BOTH, 45, 45_200, false, Color.GREEN);
		Assert.assertEquals(3, text.size());
		Assert.assertEquals("45", text.get(0).getText());
		Assert.assertEquals("45k", text.get(2).getText());
	}

	@Test
	public void levelAndRateTakeTurnsWhenTheyDoNotFit()
	{
		List<AwtrixClient.TextFragment> level = UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.BOTH, 99, 125_000, false, Color.GREEN);
		List<AwtrixClient.TextFragment> rate = UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.BOTH, 99, 125_000, true, Color.GREEN);
		Assert.assertEquals("99", level.get(0).getText());
		Assert.assertEquals("125k/h", rate.get(0).getText());
		Assert.assertEquals(1, rate.size());
	}

	@Test
	public void rateModeShowsTheLevelUntilThereIsARate()
	{
		Assert.assertEquals("70", UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.XP_RATE, 70, -1, true, Color.GREEN).get(0).getText());
		Assert.assertEquals("9.5k/h", UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.XP_RATE, 70, 9_500, false, Color.GREEN).get(0).getText());
		Assert.assertEquals("70", UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.LEVEL, 70, 9_500, true, Color.GREEN).get(0).getText());
	}

	@Test
	public void controlledStyleKeepsTheCurrentMeleeSkill()
	{
		Map<Skill, Integer> gains = new EnumMap<>(Skill.class);
		gains.put(Skill.ATTACK, 20);
		gains.put(Skill.STRENGTH, 20);
		gains.put(Skill.DEFENCE, 20);
		gains.put(Skill.HITPOINTS, 26);
		Assert.assertEquals(Skill.STRENGTH, UlanziOsrsPlugin.meleeSkill(gains, Skill.STRENGTH));
		Assert.assertEquals(Skill.DEFENCE, UlanziOsrsPlugin.meleeSkill(gains, Skill.DEFENCE));

		gains.put(Skill.STRENGTH, 40);
		Assert.assertEquals(Skill.STRENGTH, UlanziOsrsPlugin.meleeSkill(gains, Skill.ATTACK));

		Map<Skill, Integer> ranged = new EnumMap<>(Skill.class);
		ranged.put(Skill.RANGED, 40);
		Assert.assertEquals(Skill.ATTACK, UlanziOsrsPlugin.meleeSkill(ranged, Skill.ATTACK));
	}

	@Test
	public void hitpointsOnlyNamesADropOnItsOwn()
	{
		Map<Skill, Integer> gains = new EnumMap<>(Skill.class);
		gains.put(Skill.HITPOINTS, 53);
		Assert.assertEquals(Skill.HITPOINTS, UlanziOsrsPlugin.dropSkill(gains));
		gains.put(Skill.RANGED, 40);
		Assert.assertEquals(Skill.RANGED, UlanziOsrsPlugin.dropSkill(gains));
		Assert.assertNull(UlanziOsrsPlugin.dropSkill(new EnumMap<>(Skill.class)));
	}

	@Test
	public void everyActivityTrainsASkill()
	{
		for (SkillActivity activity : SkillActivity.values())
		{
			Assert.assertNotNull(activity.name(), SkillActivities.skillFor(activity));
		}
		Assert.assertEquals(Skill.WOODCUTTING, SkillActivities.skillFor(SkillActivity.WOODCUTTING));
		Assert.assertEquals(Skill.MAGIC, SkillActivities.skillFor(SkillActivity.MAGIC));
	}

	@Test
	public void anXpDropFliesByInAboutASecondAndAHalf()
	{
		String text = UlanziOsrsPlugin.xpDropText(175);
		Assert.assertEquals("+175 xp", text);
		long flightMs = AwtrixClient.xpDropFlightMs(text, true);
		Assert.assertTrue(String.valueOf(flightMs), flightMs > 1_000L && flightMs < 2_500L);
	}

	@Test
	public void verticalDropsCrossThePanelAndStayWellUnderTheBodyLimit() throws Exception
	{
		for (boolean upward : new boolean[] {true, false})
		{
			List<Integer> path = XpDropGif.path(upward);
			Assert.assertEquals(upward ? 7 : -4, (int) path.get(0));
			Assert.assertEquals(upward ? -4 : 7, (int) path.get(path.size() - 1));
			Assert.assertTrue(path.contains(2));

			String gif = XpDropGif.encode("+175xp", Color.WHITE, upward);
			Assert.assertTrue(gif.startsWith("R0lGOD"));
			Assert.assertTrue(String.valueOf(gif.length()), gif.length() < 4_000);

			javax.imageio.ImageReader reader = javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(javax.imageio.ImageIO.createImageInputStream(
				new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(gif))));
			Assert.assertEquals(path.size() + 1, reader.getNumImages(true));
			Assert.assertEquals(XpDropGif.WIDTH, reader.read(0).getWidth());
			Assert.assertEquals(XpDropGif.HEIGHT, reader.read(0).getHeight());
		}
	}

	@Test
	public void stillDropsShrinkTheirUnitToFitBesideTheIcon()
	{
		Assert.assertEquals("+175xp", UlanziOsrsPlugin.fitXpDropText(175));
		Assert.assertEquals("+12 xp", UlanziOsrsPlugin.fitXpDropText(12));
		Assert.assertEquals("+12345", UlanziOsrsPlugin.fitXpDropText(12_345));
	}

	@Test
	public void everyGradientHasStops()
	{
		for (ProgressGradient gradient : ProgressGradient.values())
		{
			Assert.assertTrue(gradient.name(), gradient.stops(Color.GREEN).size() >= 2);
		}
	}
}
