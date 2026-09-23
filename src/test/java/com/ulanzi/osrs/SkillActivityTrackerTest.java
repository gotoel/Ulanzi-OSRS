package com.ulanzi.osrs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import org.junit.Assert;
import org.junit.Test;

public class SkillActivityTrackerTest
{
	@Test
	public void afkStaysUpUntilTheCharacterMoves()
	{
		Assert.assertFalse(UlanziOsrsPlugin.shouldShowAfk(false, true, false));
		Assert.assertTrue(UlanziOsrsPlugin.shouldShowAfk(true, true, false));
		Assert.assertFalse(UlanziOsrsPlugin.shouldShowAfk(true, false, false));
		Assert.assertTrue(UlanziOsrsPlugin.shouldShowAfk(true, false, true));
	}

	@Test
	public void zeroSecondAfkTriggersOnTheNextIdleTick()
	{
		Assert.assertFalse(UlanziOsrsPlugin.inputIdleLongEnough(0, 0));
		Assert.assertTrue(UlanziOsrsPlugin.inputIdleLongEnough(1, 0));
		Assert.assertTrue(UlanziOsrsPlugin.recentClickBlocksAfk(0, 0));
		Assert.assertFalse(UlanziOsrsPlugin.recentClickBlocksAfk(20, 0));
		Assert.assertTrue(UlanziOsrsPlugin.recentClickBlocksAfk(999, 30));
		Assert.assertFalse(UlanziOsrsPlugin.recentClickBlocksAfk(1000, 30));
	}

	@Test
	public void anyActionAnimationIsNotIdle()
	{
		Assert.assertFalse(UlanziOsrsPlugin.isStandingIdle(879, 808, 808, 823, 823, 819, 820, 821, 822, 824));
		Assert.assertTrue(UlanziOsrsPlugin.isStandingIdle(-1, 808, 808, 823, 823, 819, 820, 821, 822, 824));
		Assert.assertTrue(UlanziOsrsPlugin.isStandingIdle(-1, 823, 808, 823, 823, 819, 820, 821, 822, 824));
		Assert.assertFalse(UlanziOsrsPlugin.isStandingIdle(-1, 819, 808, 823, 823, 819, 820, 821, 822, 824));
		Assert.assertFalse(UlanziOsrsPlugin.isStandingIdle(-1, 824, 808, 823, 824, 819, 820, 821, 822, 824));
		Assert.assertFalse(UlanziOsrsPlugin.isCharacterIdle(true, true, false));
		Assert.assertFalse(UlanziOsrsPlugin.isCharacterIdle(true, false, true));
		Assert.assertTrue(UlanziOsrsPlugin.isCharacterIdle(true, false, false));
	}

	@Test
	public void choppingAnimationShowsWoodcuttingImmediately()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		SkillActivity activity = tracker.onAnimation(SkillActivity.WOODCUTTING, 879, 1_000L);
		Assert.assertEquals(SkillActivity.WOODCUTTING, activity);
	}

	@Test
	public void zeroHoldClearsAsSoonAsTheAnimationStops()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		tracker.onAnimation(SkillActivity.WOODCUTTING, 879, 1_000L, 0L);

		Assert.assertNull(tracker.onAnimation(null, -1, 1_001L, 0L));
	}

	@Test
	public void configuredHoldKeepsTheActivityThenClears()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		long holdMs = 5_000L;
		tracker.onAnimation(SkillActivity.WOODCUTTING, 879, 1_000L, holdMs);

		Assert.assertEquals(SkillActivity.WOODCUTTING,
			tracker.onAnimation(null, -1, 1_000L + holdMs - 1, holdMs));
		Assert.assertNull(tracker.onAnimation(null, -1, 1_000L + holdMs, holdMs));
	}

	@Test
	public void zeroHoldKeepsCombatWhileTargeted()
	{
		Assert.assertTrue(UlanziOsrsPlugin.inCombatHold(true, 1_000L, 20_000L, 0L));
		Assert.assertFalse(UlanziOsrsPlugin.inCombatHold(false, 1_000L, 1_001L, 0L));
		Assert.assertTrue(UlanziOsrsPlugin.inCombatHold(true, 1_000L, 1_000L + 4_999L, 5_000L));
		Assert.assertFalse(UlanziOsrsPlugin.inCombatHold(true, 1_000L, 1_000L + 5_000L, 5_000L));
	}

	@Test
	public void otherAnimationClearsTheActivity()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		tracker.onAnimation(SkillActivity.MINING, 6752, 1_000L);
		Assert.assertNull(tracker.onAnimation(null, 422, 1_500L));
		Assert.assertNull(tracker.onAnimation(null, -1, 2_000L));
	}

	@Test
	public void recentXpKeepsAnUnmappedAnimation()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		tracker.onXp(SkillActivity.AGILITY, 5_000L);
		Assert.assertEquals(SkillActivity.AGILITY, tracker.onAnimation(null, 99999, 5_000L + SkillActivityTracker.UNKNOWN_ANIM_MS));
		Assert.assertNull(tracker.onAnimation(null, 99999, 5_000L + SkillActivityTracker.UNKNOWN_ANIM_MS + 1));
	}

	@Test
	public void woodcuttingAxeMapsToWoodcutting()
	{
		Assert.assertEquals(SkillActivity.WOODCUTTING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE));
		Assert.assertEquals(SkillActivity.MINING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_MINING_RUNE_PICKAXE));
		Assert.assertEquals(SkillActivity.FISHING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_HARPOON));
		Assert.assertNull(SkillActivities.fromAnimation(-1));
	}

	@Test
	public void combatXpDoesNotStartAnActivity()
	{
		Assert.assertNull(SkillActivities.fromSkillXp(Skill.ATTACK));
		Assert.assertNull(SkillActivities.fromSkillXp(Skill.PRAYER));
		Assert.assertNull(SkillActivities.fromSkillXp(Skill.MAGIC));
		Assert.assertEquals(SkillActivity.WOODCUTTING, SkillActivities.fromSkillXp(Skill.WOODCUTTING));
		Assert.assertEquals(SkillActivity.SAILING, SkillActivities.fromSkillXp(Skill.SAILING));
	}

	@Test
	public void everyActivityHasAnInlineIcon() throws Exception
	{
		for (SkillActivity activity : SkillActivity.values())
		{
			String icon = activity.iconData();
			Assert.assertTrue(activity.name(), icon.length() > 64);
			Assert.assertTrue(activity.name(), icon.startsWith("R0lGODlh"));
			assertIconFits(activity, icon);
		}
	}

	@Test
	public void attackStyleMapsToMeleeRangeOrMage()
	{
		Assert.assertEquals(SkillActivity.MELEE, CombatStyles.activityForStyleName("Accurate"));
		Assert.assertEquals(SkillActivity.MELEE, CombatStyles.activityForStyleName("Controlled"));
		Assert.assertEquals(SkillActivity.RANGED, CombatStyles.activityForStyleName("Ranging"));
		Assert.assertEquals(SkillActivity.RANGED, CombatStyles.activityForStyleName("Longrange"));
		Assert.assertEquals(SkillActivity.MAGIC, CombatStyles.activityForStyleName("Casting"));
		Assert.assertEquals(SkillActivity.MAGIC, CombatStyles.activityForStyleName("Defensive Casting"));
		Assert.assertNull(CombatStyles.activityForStyleName("Other"));

		String[] staff = {"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive Casting"};
		Assert.assertEquals(SkillActivity.MAGIC, CombatStyles.activityFor(staff, 4, 0));
		Assert.assertEquals(SkillActivity.MAGIC, CombatStyles.activityFor(staff, 4, 1));
		Assert.assertEquals(SkillActivity.MELEE, CombatStyles.activityFor(staff, 0, 0));

		String[] bow = {"Accurate", "Ranging", "Longrange"};
		Assert.assertEquals(SkillActivity.RANGED, CombatStyles.activityFor(bow, 0, 0));
		Assert.assertEquals(SkillActivity.RANGED, CombatStyles.activityFor(bow, 1, 0));
	}

	@Test
	public void bothStyleShowsTheNumberAndTheBar()
	{
		Assert.assertTrue(StatStyle.BOTH.showsValue());
		Assert.assertTrue(StatStyle.BOTH.showsBar());
		Assert.assertFalse(StatStyle.BAR.showsValue());
		Assert.assertFalse(StatStyle.VALUE.showsBar());
		Assert.assertFalse(StatStyle.OFF.isShown());
		Assert.assertFalse(StatStyle.OFF.showsValue());
		Assert.assertFalse(StatStyle.OFF.showsBar());
	}

	@Test
	public void percentThresholdScalesWithTheRealLevel()
	{
		Assert.assertTrue(ThresholdUnit.PERCENT.isLow(24, 99, 25));
		Assert.assertFalse(ThresholdUnit.PERCENT.isLow(25, 99, 25));
		Assert.assertFalse(ThresholdUnit.PERCENT.isLow(10, 10, 25));
		Assert.assertTrue(ThresholdUnit.PERCENT.isLow(2, 10, 25));
		Assert.assertTrue(ThresholdUnit.POINTS.isLow(20, 99, 20));
		Assert.assertFalse(ThresholdUnit.POINTS.isLow(21, 99, 20));
	}

	@Test
	public void theClocksOwnBrightnessIsReadBackSoItCanBePutAgain()
	{
		JsonObject settings = new Gson().fromJson(
			"{\"autoBrightness\":true,\"brightness\":37,\"somethingElse\":1}", JsonObject.class);
		AwtrixClient.Brightness saved = AwtrixClient.parseBrightness(settings);
		Assert.assertTrue(saved.automatic);
		Assert.assertEquals(37, saved.level);
		Assert.assertEquals("{\"autoBrightness\":true,\"brightness\":37}", saved.toJson().toString());
	}

	@Test
	public void aClockThatNamesNoBrightnessFallsBackToTheFirmwareDefault()
	{
		AwtrixClient.Brightness saved = AwtrixClient.parseBrightness(
			new Gson().fromJson("{}", JsonObject.class));
		Assert.assertFalse(saved.automatic);
		Assert.assertEquals(120, saved.level);
	}

	@Test
	public void appLoopKeepsTheUsersOrderAndSwitchedOffApps()
	{
		String json = "["
			+ "{\"name\":\"Date\",\"enabled\":true,\"inLoop\":true,\"slot\":1},"
			+ "{\"name\":\"osrs\",\"enabled\":true,\"inLoop\":true,\"slot\":0},"
			+ "{\"name\":\"Time\",\"enabled\":true,\"inLoop\":true,\"slot\":2},"
			+ "{\"name\":\"weather\",\"enabled\":true,\"inLoop\":true,\"slot\":3},"
			+ "{\"name\":\"Battery\",\"enabled\":false,\"inLoop\":false,\"slot\":null},"
			+ "{\"name\":\"mymodule\",\"origin\":\"module\"}"
			+ "]";
		AwtrixClient.AppLoop loop = AwtrixClient.parseAppLoop(new Gson().fromJson(json, JsonArray.class));
		Assert.assertEquals(Arrays.asList("Date", "Time", "weather"), loop.order);
		Assert.assertEquals(Collections.singletonList("Battery"), loop.disabled);
		Assert.assertEquals("{\"order\":[\"Date\",\"Time\",\"weather\"],\"disabled\":[\"Battery\"]}", loop.toJson().toString());
	}

	@Test
	public void connectionErrorsNeverMentionTheAddress()
	{
		String[] problems = {
			AwtrixClient.describe(new java.net.ConnectException("Failed to connect to /192.168.1.50:80")),
			AwtrixClient.describe(new java.net.UnknownHostException("clock.local")),
			AwtrixClient.describe(new java.io.IOException("unexpected end of stream on http://192.168.1.50/..."))
		};
		for (String problem : problems)
		{
			Assert.assertFalse(problem, problem.contains("192.168"));
			Assert.assertFalse(problem, problem.contains("clock.local"));
		}
	}

	@Test
	public void onlyARiseFromAKnownLevelIsALevelUp()
	{
		Assert.assertFalse(UlanziOsrsPlugin.isLevelUp(null, 70));
		Assert.assertFalse(UlanziOsrsPlugin.isLevelUp(70, 70));
		Assert.assertTrue(UlanziOsrsPlugin.isLevelUp(69, 70));
	}

	@Test
	public void combatSkillsHaveLevelUpIcons()
	{
		for (Skill skill : new Skill[] {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS,
			Skill.RANGED, Skill.MAGIC, Skill.PRAYER, Skill.SLAYER, Skill.WOODCUTTING, Skill.SAILING})
		{
			Assert.assertNotNull(skill.name(), SkillActivities.levelUpIcon(skill));
		}
	}

	@Test
	public void everyPixelIconFits() throws Exception
	{
		for (PixelIcon icon : PixelIcon.values())
		{
			assertIconFits(icon.name(), icon.iconData());
		}
	}

	private static void assertIconFits(SkillActivity activity, String icon) throws Exception
	{
		assertIconFits(activity.name(), icon);
	}

	private static void assertIconFits(String name, String icon) throws Exception
	{
		byte[] gif = Base64.getDecoder().decode(icon);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(gif));
		Assert.assertNotNull(name, image);
		Assert.assertEquals(name, 8, image.getWidth());
		Assert.assertEquals(name, 8, image.getHeight());
		for (int i = 0; i < 8; i++)
		{
			Assert.assertEquals(name + " top", 0, image.getRGB(i, 0) & 0xFFFFFF);
			Assert.assertEquals(name + " bottom", 0, image.getRGB(i, 7) & 0xFFFFFF);
			Assert.assertEquals(name + " left", 0, image.getRGB(0, i) & 0xFFFFFF);
			Assert.assertEquals(name + " right", 0, image.getRGB(7, i) & 0xFFFFFF);
		}
	}
}
