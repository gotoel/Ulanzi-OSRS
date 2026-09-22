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
	public void thePulseIsFullBrightOnLandingAndFadesBack()
	{
		long start = 10_000L;
		Assert.assertEquals(UlanziOsrsPlugin.PULSE_PEAK,
			UlanziOsrsPlugin.pulseFactor(start, start), 0.0001);

		double midway = UlanziOsrsPlugin.pulseFactor(start, start + UlanziOsrsPlugin.PULSE_MS / 2);
		Assert.assertTrue(midway > 1.0 && midway < UlanziOsrsPlugin.PULSE_PEAK);

		Assert.assertEquals(1.0,
			UlanziOsrsPlugin.pulseFactor(start, start + UlanziOsrsPlugin.PULSE_MS), 0.0001);
	}

	@Test
	public void thereIsNoPulseBeforeTheFirstDrop()
	{
		Assert.assertEquals(1.0, UlanziOsrsPlugin.pulseFactor(0L, 5_000L), 0.0001);
	}

	@Test
	public void thePulseBrightensLitPixelsAndLeavesBlackAlone()
	{
		Assert.assertEquals(Color.BLACK, AwtrixClient.brighten(Color.BLACK, 1.8));
		Assert.assertEquals(new Color(180, 0, 0), AwtrixClient.brighten(new Color(100, 0, 0), 1.8));
		// Already-bright channels clamp instead of wrapping.
		Assert.assertEquals(Color.WHITE, AwtrixClient.brighten(Color.WHITE, 1.8));
		// A factor of 1 is the untouched colour.
		Assert.assertEquals(new Color(12, 34, 56), AwtrixClient.brighten(new Color(12, 34, 56), 1.0));
	}

	@Test
	public void thePulseReEncodesTheIconRatherThanDroppingIt()
	{
		String icon = PixelIcon.HITPOINTS.iconData();
		String lit = AwtrixClient.brightenIcon(icon, 1.8);
		Assert.assertNotEquals("icon should change when pulsed", icon, lit);

		// Falling back returns the original, so check it really decoded to a brighter image.
		int[] plain = iconPixels(icon);
		int[] pulsed = iconPixels(lit);
		Assert.assertEquals(plain.length, pulsed.length);
		boolean brighter = false;
		for (int i = 0; i < plain.length; i++)
		{
			Assert.assertTrue("no channel may dim", luma(pulsed[i]) >= luma(plain[i]));
			brighter |= luma(pulsed[i]) > luma(plain[i]);
		}
		Assert.assertTrue("something should be brighter", brighter);
	}

	private static int[] iconPixels(String base64)
	{
		try
		{
			java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(
				new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(base64)));
			Assert.assertNotNull("icon should decode", image);
			int[] pixels = new int[image.getWidth() * image.getHeight()];
			image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
			return pixels;
		}
		catch (java.io.IOException ex)
		{
			throw new AssertionError(ex);
		}
	}

	private static int luma(int rgb)
	{
		Color color = new Color(rgb);
		return color.getRed() + color.getGreen() + color.getBlue();
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
