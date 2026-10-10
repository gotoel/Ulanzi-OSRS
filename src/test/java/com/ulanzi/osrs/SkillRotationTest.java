package com.ulanzi.osrs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SkillRotationTest
{
	private static final long TURN_MS = 3_000L;

	@Test
	public void oneSkillFollowsWhatYouAreDoing()
	{
		SkillRotation rotation = new SkillRotation();
		Assert.assertEquals(SkillActivity.FISHING,
			rotation.turn(active(SkillActivity.FISHING), SkillActivity.FISHING, 0L, TURN_MS));
		// Nothing is sharing the panel, so a change is taken straight away.
		Assert.assertEquals(SkillActivity.COOKING,
			rotation.turn(active(SkillActivity.COOKING), SkillActivity.COOKING, 500L, TURN_MS));
	}

	@Test
	public void everyTurnLastsTheWholeRotateTime()
	{
		SkillRotation rotation = new SkillRotation();
		List<SkillActivity> both = active(SkillActivity.FISHING, SkillActivity.COOKING);
		SkillActivity first = rotation.turn(both, SkillActivity.FISHING, 0L, TURN_MS);

		// Drops landing for the other skill do not pull the panel over mid-turn, which
		// is what used to flicker the two back and forth.
		Assert.assertEquals(first, rotation.turn(both, SkillActivity.COOKING, 1_000L, TURN_MS));
		Assert.assertEquals(first, rotation.turn(both, SkillActivity.FISHING, 2_999L, TURN_MS));

		SkillActivity second = rotation.turn(both, SkillActivity.FISHING, 3_000L, TURN_MS);
		Assert.assertNotEquals(first, second);
		Assert.assertEquals(second, rotation.turn(both, SkillActivity.FISHING, 5_999L, TURN_MS));
		Assert.assertEquals(first, rotation.turn(both, SkillActivity.COOKING, 6_000L, TURN_MS));
	}

	@Test
	public void everythingGoingGetsAnEvenShare()
	{
		SkillRotation rotation = new SkillRotation();
		List<SkillActivity> three = active(SkillActivity.WOODCUTTING, SkillActivity.FIREMAKING,
			SkillActivity.FLETCHING);
		List<SkillActivity> seen = new ArrayList<>();
		for (long now = 0L; now < TURN_MS * 6; now += 500L)
		{
			SkillActivity turn = rotation.turn(three, SkillActivity.WOODCUTTING, now, TURN_MS);
			if (seen.isEmpty() || seen.get(seen.size() - 1) != turn)
			{
				seen.add(turn);
			}
		}
		// Two full laps, each skill once per lap and in the same order both times.
		Assert.assertEquals(6, seen.size());
		Assert.assertEquals(seen.subList(0, 3), seen.subList(3, 6));
		Assert.assertEquals(3, new LinkedHashSet<>(seen).size());
	}

	@Test
	public void aSkillJoiningWaitsItsTurn()
	{
		SkillRotation rotation = new SkillRotation();
		List<SkillActivity> both = active(SkillActivity.WOODCUTTING, SkillActivity.FLETCHING);
		SkillActivity holder = rotation.turn(both, SkillActivity.WOODCUTTING, 0L, TURN_MS);

		List<SkillActivity> three = active(SkillActivity.WOODCUTTING, SkillActivity.FIREMAKING,
			SkillActivity.FLETCHING);
		// The newcomer does not cut the turn that is running short.
		Assert.assertEquals(holder, rotation.turn(three, SkillActivity.FIREMAKING, 1_500L, TURN_MS));
	}

	@Test
	public void aSkillDroppingOutPassesThePanelOn()
	{
		SkillRotation rotation = new SkillRotation();
		List<SkillActivity> three = active(SkillActivity.WOODCUTTING, SkillActivity.FIREMAKING,
			SkillActivity.FLETCHING);
		rotation.turn(three, SkillActivity.WOODCUTTING, 0L, TURN_MS);
		SkillActivity holder = rotation.turn(three, SkillActivity.WOODCUTTING, TURN_MS, TURN_MS);

		List<SkillActivity> without = new ArrayList<>(three);
		without.remove(holder);
		SkillActivity moved = rotation.turn(without, SkillActivity.WOODCUTTING, TURN_MS + 500L, TURN_MS);
		Assert.assertNotEquals(holder, moved);
		// And the skill it moved to gets a whole turn of its own, not what was left of one.
		Assert.assertEquals(moved, rotation.turn(without, SkillActivity.WOODCUTTING, TURN_MS + 3_000L, TURN_MS));
	}

	@Test
	public void turnsAreCountedForTheLevelAndRateSwap()
	{
		SkillRotation rotation = new SkillRotation();
		List<SkillActivity> one = active(SkillActivity.FISHING);
		rotation.turn(one, SkillActivity.FISHING, 0L, TURN_MS);
		long first = rotation.turns();
		rotation.turn(one, SkillActivity.FISHING, 1_000L, TURN_MS);
		Assert.assertEquals(first, rotation.turns());
		rotation.turn(one, SkillActivity.FISHING, TURN_MS, TURN_MS);
		Assert.assertEquals(first + 1, rotation.turns());
	}

	@Test
	public void whatYouAreDoingJoinsTheRotationInOrder()
	{
		List<SkillActivity> active = new ArrayList<>(
			Arrays.asList(SkillActivity.WOODCUTTING, SkillActivity.FLETCHING));
		Collections.sort(active, (a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
		SkillRotation.insertInOrder(active, SkillActivity.FIREMAKING);
		List<SkillActivity> sorted = new ArrayList<>(active);
		Collections.sort(sorted, (a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
		Assert.assertEquals(sorted, active);
		Assert.assertTrue(active.contains(SkillActivity.FIREMAKING));

		// Already in it, so nothing is added twice.
		SkillRotation.insertInOrder(active, SkillActivity.FIREMAKING);
		Assert.assertEquals(3, active.size());
		SkillRotation.insertInOrder(active, null);
		Assert.assertEquals(3, active.size());
	}

	private static List<SkillActivity> active(SkillActivity... activities)
	{
		List<SkillActivity> list = new ArrayList<>(Arrays.asList(activities));
		Collections.sort(list, (a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
		return list;
	}
}
