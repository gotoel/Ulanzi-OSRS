package com.ulanzi.osrs;

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
		Assert.assertFalse(UlanziOsrsPlugin.isStandingIdle(879, 808, 808, 813, 824));
		Assert.assertTrue(UlanziOsrsPlugin.isStandingIdle(-1, 808, 808, 813, 824));
		Assert.assertTrue(UlanziOsrsPlugin.isStandingIdle(-1, 813, 808, 813, 824));
		Assert.assertFalse(UlanziOsrsPlugin.isStandingIdle(-1, 819, 808, 813, 824));
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
	public void idleGapKeepsTheActivityThenClears()
	{
		SkillActivityTracker tracker = new SkillActivityTracker();
		tracker.onAnimation(SkillActivity.WOODCUTTING, 879, 1_000L);

		Assert.assertEquals(SkillActivity.WOODCUTTING,
			tracker.onAnimation(null, -1, 1_000L + SkillActivityTracker.HOLD_MS - 1));
		Assert.assertNull(tracker.onAnimation(null, -1, 1_000L + SkillActivityTracker.HOLD_MS));
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
	public void everyActivityHasAnInlineIcon()
	{
		for (SkillActivity activity : SkillActivity.values())
		{
			String icon = activity.iconData();
			Assert.assertTrue(activity.name(), icon.length() > 64);
			Assert.assertTrue(activity.name(), icon.startsWith("R0lGODlh"));
		}
	}
}
