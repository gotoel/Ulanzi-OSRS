package com.ulanzi.osrs;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
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
	public void energyColumnFillsFromTheBottom()
	{
		Assert.assertEquals(0, AwtrixClient.energyColumnRows(0));
		Assert.assertEquals(1, AwtrixClient.energyColumnRows(1));
		Assert.assertEquals(4, AwtrixClient.energyColumnRows(50));
		Assert.assertEquals(8, AwtrixClient.energyColumnRows(100));
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
	}

	private static void assertIconFits(SkillActivity activity, String icon) throws Exception
	{
		byte[] gif = Base64.getDecoder().decode(icon);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(gif));
		Assert.assertNotNull(activity.name(), image);
		Assert.assertEquals(activity.name(), 8, image.getWidth());
		Assert.assertEquals(activity.name(), 8, image.getHeight());
		for (int i = 0; i < 8; i++)
		{
			Assert.assertEquals(activity.name() + " top", 0, image.getRGB(i, 0) & 0xFFFFFF);
			Assert.assertEquals(activity.name() + " bottom", 0, image.getRGB(i, 7) & 0xFFFFFF);
			Assert.assertEquals(activity.name() + " left", 0, image.getRGB(0, i) & 0xFFFFFF);
			Assert.assertEquals(activity.name() + " right", 0, image.getRGB(7, i) & 0xFFFFFF);
		}
	}
}
