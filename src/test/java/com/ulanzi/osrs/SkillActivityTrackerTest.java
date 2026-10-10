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
import net.runelite.api.HitsplatID;
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
	public void wallMinePauseDoesNotCountAsIdle()
	{
		int wall = AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL;
		int rock = AnimationID.HUMAN_MINING_RUNE_PICKAXE;
		Assert.assertTrue(SkillActivities.isWallMining(wall));
		Assert.assertFalse(SkillActivities.isWallMining(rock));
		Assert.assertEquals(SkillActivity.MINING, SkillActivities.fromAnimation(wall));

		long started = UlanziOsrsPlugin.wallMineIdleSince(-1, wall, 1_000L, 0L);
		Assert.assertEquals(1_000L, started);
		Assert.assertTrue(UlanziOsrsPlugin.inWallMineGap(1_000L, started));
		Assert.assertTrue(UlanziOsrsPlugin.inWallMineGap(1_000L + UlanziOsrsPlugin.WALL_MINE_GAP_MS - 1, started));
		Assert.assertFalse(UlanziOsrsPlugin.inWallMineGap(1_000L + UlanziOsrsPlugin.WALL_MINE_GAP_MS, started));

		Assert.assertEquals(started, UlanziOsrsPlugin.wallMineIdleSince(-1, -1, 1_600L, started));
		Assert.assertEquals(0L, UlanziOsrsPlugin.wallMineIdleSince(wall, -1, 1_600L, started));
		Assert.assertEquals(0L, UlanziOsrsPlugin.wallMineIdleSince(-1, rock, 1_000L, 0L));
		Assert.assertFalse(UlanziOsrsPlugin.inWallMineGap(1_000L, 0L));
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
	public void aFightIsOnUntilTheGraceRunsOut()
	{
		Assert.assertFalse(UlanziOsrsPlugin.isInCombat(0L, 5_000L, 10_000L));
		Assert.assertTrue(UlanziOsrsPlugin.isInCombat(5_000L, 5_000L, 10_000L));
		Assert.assertTrue(UlanziOsrsPlugin.isInCombat(5_000L, 14_999L, 10_000L));
		Assert.assertFalse(UlanziOsrsPlugin.isInCombat(5_000L, 15_000L, 10_000L));
	}

	@Test
	public void poisonIsNotAFight()
	{
		Assert.assertTrue(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.DAMAGE_ME));
		Assert.assertTrue(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.BLOCK_OTHER));
		Assert.assertTrue(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.DAMAGE_MAX_ME));
		Assert.assertFalse(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.POISON));
		Assert.assertFalse(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.VENOM));
		Assert.assertFalse(UlanziOsrsPlugin.isFightHitsplat(HitsplatID.HEAL));
	}

	@Test
	public void everySkillPayingXpIsTraining()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		tracker.onXp(SkillActivity.FISHING, 1_000L);
		tracker.onXp(SkillActivity.COOKING, 2_000L);
		Assert.assertEquals(Arrays.asList(SkillActivity.FISHING, SkillActivity.COOKING),
			tracker.training(2_000L, SkillActivityTracker.TRAINING_MS));
		// The order is the same whoever paid last, so a rotation keeps its place.
		tracker.onXp(SkillActivity.FISHING, 3_000L);
		Assert.assertEquals(Arrays.asList(SkillActivity.FISHING, SkillActivity.COOKING),
			tracker.training(3_000L, SkillActivityTracker.TRAINING_MS));
		// Cooking stops, and drops out of the rotation a window later.
		Assert.assertEquals(Collections.singletonList(SkillActivity.FISHING),
			tracker.training(2_000L + SkillActivityTracker.TRAINING_MS, SkillActivityTracker.TRAINING_MS));
		tracker.reset();
		Assert.assertTrue(tracker.training(3_000L, SkillActivityTracker.TRAINING_MS).isEmpty());
	}

	@Test
	public void aSwapDipsAndComesBackUp()
	{
		Assert.assertEquals(1.0, UlanziOsrsPlugin.fadeFactor(0L, 5_000L), 0.0001);
		Assert.assertEquals(1.0, UlanziOsrsPlugin.fadeFactor(1_000L, 1_000L), 0.0001);
		Assert.assertEquals(UlanziOsrsPlugin.FADE_DIP,
			UlanziOsrsPlugin.fadeFactor(1_000L, 1_000L + UlanziOsrsPlugin.FADE_MS / 2), 0.0001);
		Assert.assertEquals(1.0,
			UlanziOsrsPlugin.fadeFactor(1_000L, 1_000L + UlanziOsrsPlugin.FADE_MS), 0.0001);
		double quarter = UlanziOsrsPlugin.fadeFactor(1_000L, 1_000L + UlanziOsrsPlugin.FADE_MS / 4);
		Assert.assertTrue(quarter > UlanziOsrsPlugin.FADE_DIP && quarter < 1.0);
	}

	@Test
	public void aProfileWritesTheSettingsItStandsFor()
	{
		// Custom is not a preset and owns nothing, so nothing it touches sends it back
		// to itself.
		Assert.assertTrue(Profile.CUSTOM.settings().isEmpty());
		Assert.assertNull(Profile.CUSTOM.valueFor("skillProgress"));

		// Combat spends the line on staying alive: run energy off, skill page off.
		Assert.assertEquals(StatStyle.VALUE.name(), Profile.COMBAT.valueFor("hitpointsStyle"));
		Assert.assertEquals(StatStyle.VALUE.name(), Profile.COMBAT.valueFor("prayerStyle"));
		Assert.assertEquals(StatStyle.OFF.name(), Profile.COMBAT.valueFor("energyStyle"));
		Assert.assertEquals(SkillProgressMode.OFF.name(), Profile.COMBAT.valueFor("skillProgress"));
		Assert.assertEquals("false", Profile.COMBAT.valueFor("skillProgressInCombat"));

		// Skilling keeps the skill page through a fight, and turns on both icon settings
		// with it: the page used to need them and said so nowhere.
		Assert.assertEquals(SkillProgressMode.BOTH.name(), Profile.SKILLING.valueFor("skillProgress"));
		Assert.assertEquals("true", Profile.SKILLING.valueFor("skillProgressInCombat"));
		Assert.assertEquals("true", Profile.SKILLING.valueFor("showActivity"));
		Assert.assertEquals("true", Profile.SKILLING.valueFor("activityInCombat"));

		// Hybrid has the skill page while skilling and hands the panel to the combat
		// line for a fight.
		Assert.assertEquals(SkillProgressMode.BOTH.name(), Profile.HYBRID.valueFor("skillProgress"));
		Assert.assertEquals("false", Profile.HYBRID.valueFor("skillProgressInCombat"));
		Assert.assertEquals("true", Profile.HYBRID.valueFor("showActivity"));
		Assert.assertEquals(StatStyle.VALUE.name(), Profile.HYBRID.valueFor("hitpointsStyle"));
		Assert.assertEquals(StatStyle.OFF.name(), Profile.HYBRID.valueFor("energyStyle"));

		// A setting a profile has no opinion about is nobody's business but yours.
		Assert.assertNull(Profile.COMBAT.valueFor("specStyle"));
		Assert.assertNull(Profile.SKILLING.valueFor("statsLayout"));
	}

	@Test
	public void theOldAlertBoxesFoldIntoOneStyle()
	{
		Assert.assertEquals(AlertStyle.FLASH_AND_BEEP, UlanziOsrsPlugin.alertStyle(true, true, true));
		Assert.assertEquals(AlertStyle.FLASH, UlanziOsrsPlugin.alertStyle(true, true, false));
		Assert.assertEquals(AlertStyle.BEEP, UlanziOsrsPlugin.alertStyle(true, false, true));
		// Switched on but doing neither was a warning that never arrived, and switched
		// off with both ticked was the same thing said the other way about.
		Assert.assertEquals(AlertStyle.OFF, UlanziOsrsPlugin.alertStyle(true, false, false));
		Assert.assertEquals(AlertStyle.OFF, UlanziOsrsPlugin.alertStyle(false, true, true));
	}

	@Test
	public void theAfkLabelJoinsWhateverTheTintWas()
	{
		Assert.assertEquals(AfkTintMode.VALUES_AND_LABEL, UlanziOsrsPlugin.withLabel("VALUES"));
		Assert.assertEquals(AfkTintMode.LABEL, UlanziOsrsPlugin.withLabel("OFF"));
		Assert.assertEquals(AfkTintMode.BACKGROUND, UlanziOsrsPlugin.withLabel("BACKGROUND"));
		Assert.assertEquals(AfkTintMode.VALUES_AND_LABEL, UlanziOsrsPlugin.withLabel("NOT_A_MODE"));
	}

	@Test
	public void aTintModeKnowsWhatItPaints()
	{
		Assert.assertTrue(AfkTintMode.VALUES_AND_LABEL.tintsValues());
		Assert.assertTrue(AfkTintMode.VALUES_AND_LABEL.showsLabel());
		Assert.assertFalse(AfkTintMode.VALUES_AND_LABEL.tintsBackground());
		Assert.assertTrue(AfkTintMode.LABEL.showsLabel());
		Assert.assertFalse(AfkTintMode.LABEL.tintsValues());
		Assert.assertTrue(AfkTintMode.BOTH.tintsValues());
		Assert.assertTrue(AfkTintMode.BOTH.tintsBackground());
		Assert.assertFalse(AfkTintMode.BOTH.showsLabel());
		Assert.assertFalse(AfkTintMode.OFF.tintsValues());
	}

	@Test
	public void edgeColumnsStandAgainstTheRightEdge()
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(new CompactLayout.Cell(StatKind.HITPOINTS, StatStyle.BAR, "50", 2, 50, -1,
			Color.GREEN, Color.GREEN));
		cells.add(new CompactLayout.Cell(StatKind.PRAYER, StatStyle.BAR, "43", 2, 43, -1,
			Color.BLUE, Color.BLUE));
		Assert.assertEquals(2, CompactLayout.placeEdge(cells));
		// Hitpoints keeps its place whether prayer is beside it or not, and a blank column
		// sits between the two.
		Assert.assertEquals(CompactLayout.PANEL_WIDTH - 3, cells.get(0).edgeX);
		Assert.assertEquals(CompactLayout.PANEL_WIDTH - 1, cells.get(1).edgeX);
		Assert.assertEquals(0, CompactLayout.edgeReserve(0));
		Assert.assertEquals(4, CompactLayout.edgeReserve(2));
	}

	@Test
	public void edgeColumnsTakeTheirRoomOffTheWords()
	{
		// 45 and 45k share the line on a page with nothing at its edge, and stop sharing it
		// once two columns have taken their four pixels.
		Assert.assertEquals(3, UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.BOTH, 45, 45_200, false, Color.GREEN, 24).size());
		Assert.assertEquals(1, UlanziOsrsPlugin.skillProgressText(
			SkillProgressMode.BOTH, 45, 45_200, false, Color.GREEN, 24 - CompactLayout.edgeReserve(2)).size());
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
	public void bloodwoodFellingMapsToWoodcutting()
	{
		Assert.assertEquals(SkillActivity.WOODCUTTING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_FELLING_CHARGE_SLOW01));
		Assert.assertEquals(SkillActivity.WOODCUTTING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_FELLING_SWING01));
		Assert.assertEquals(SkillActivity.WOODCUTTING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_FELLING_BOTCH01));
		Assert.assertEquals(SkillActivity.WOODCUTTING,
			SkillActivities.fromAnimation(AnimationID.HUMAN_FELLING_TAP01));
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
