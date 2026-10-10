package com.ulanzi.osrs;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class ForestryTest
{
	private static final Color GREEN = new Color(0x4F, 0xE0, 0x8A);
	/** Long enough to cover a whole stem cycle and several turns of the motes. */
	private static final long RUN_MS = 12_000L;

	@Test
	public void everyFrameStaysOnThePanel()
	{
		for (ForestryEffect effect : ForestryEffect.values())
		{
			for (long elapsed = 0; elapsed < RUN_MS; elapsed += 20)
			{
				for (ForestryAnimation.Dot dot : ForestryAnimation.frame(effect, GREEN, elapsed))
				{
					Assert.assertTrue(effect + " at " + elapsed + " drew x " + dot.x,
						dot.x >= 0 && dot.x < ForestryAnimation.WIDTH);
					Assert.assertTrue(effect + " at " + elapsed + " drew y " + dot.y,
						dot.y >= 0 && dot.y < ForestryAnimation.HEIGHT);
				}
			}
		}
	}

	@Test
	public void flashingDrawsNothing()
	{
		Assert.assertTrue(ForestryAnimation.frame(ForestryEffect.FLASH, GREEN, 500L).isEmpty());
	}

	@Test
	public void bothPartsOfTheGroveAreThere()
	{
		// Stems are drawn in the event's colour and motes are not, which is how the two
		// halves of the grove are told apart once they are in the same list.
		List<ForestryAnimation.Dot> grove = ForestryAnimation.frame(ForestryEffect.GROVE, GREEN, 700L);
		Assert.assertTrue(grove.size()
			> ForestryAnimation.frame(ForestryEffect.SPROUT, GREEN, 700L).size());
		Assert.assertTrue(grove.size()
			> ForestryAnimation.frame(ForestryEffect.MOTES, GREEN, 700L).size());
	}

	@Test
	public void aStemGrowsAndWithersRatherThanSnappingBack()
	{
		int start = ForestryAnimation.stemHeight(0, 0L);
		int middle = ForestryAnimation.stemHeight(0, 1_000L);
		int quarter = ForestryAnimation.stemHeight(0, 500L);
		int threeQuarters = ForestryAnimation.stemHeight(0, 1_500L);

		Assert.assertEquals(0, start);
		Assert.assertTrue("grows", quarter > start);
		Assert.assertTrue("peaks in the middle", middle > quarter);
		Assert.assertEquals("withers back the way it came", quarter, threeQuarters);
	}

	@Test
	public void stemsDoNotAllGrowAtOnce()
	{
		Set<Integer> heights = new HashSet<>();
		for (int stem = 0; stem < ForestryAnimation.WIDTH / 4; stem++)
		{
			heights.add(ForestryAnimation.stemHeight(stem, 0L));
		}
		Assert.assertTrue("stems are staggered across the panel", heights.size() > 1);
	}

	@Test
	public void noIdBelongsToTwoEvents()
	{
		Set<Integer> npcs = new HashSet<>();
		Set<Integer> objects = new HashSet<>();
		for (ForestryEvent event : ForestryEvent.values())
		{
			int seen = 0;
			for (int npc = 0; npc < 65_536; npc++)
			{
				if (ForestryEvent.fromNpc(npc) == event)
				{
					Assert.assertTrue("npc " + npc + " twice", npcs.add(npc));
					seen++;
				}
			}
			for (int object = 0; object < 65_536; object++)
			{
				if (ForestryEvent.fromObject(object) == event)
				{
					Assert.assertTrue("object " + object + " twice", objects.add(object));
					seen++;
				}
			}
			Assert.assertTrue(event + " has nothing to recognise it by", seen > 0);
		}
	}

	@Test
	public void everyLabelFitsAcrossThePanel()
	{
		for (ForestryEvent event : ForestryEvent.values())
		{
			// The small font is four pixels a character, so eight of them span 32 pixels.
			Assert.assertTrue(event + " label is too long: " + event.label(),
				event.label().length() <= 8);
		}
	}

	@Test
	public void theFirstThingAnEventSpawnsIsTheOneThatAnnouncesIt()
	{
		ForestryTracker tracker = new ForestryTracker();
		Assert.assertEquals(ForestryEvent.POACHERS,
			tracker.onSpawn(ForestryEvent.POACHERS, 1L, 1_000L));
		Assert.assertNull(tracker.onSpawn(ForestryEvent.POACHERS, 2L, 1_200L));
		Assert.assertNull(tracker.onSpawn(ForestryEvent.POACHERS, 1L, 1_400L));
	}

	@Test
	public void aDifferentEventAnnouncesItself()
	{
		ForestryTracker tracker = new ForestryTracker();
		tracker.onSpawn(ForestryEvent.POACHERS, 1L, 1_000L);
		Assert.assertEquals(ForestryEvent.BEEHIVE,
			tracker.onSpawn(ForestryEvent.BEEHIVE, 2L, 1_100L));
	}

	@Test
	public void anEventThatComesStraightBackIsTheSameEvent()
	{
		// What a region load or a world hop looks like: everything goes, everything returns.
		ForestryTracker tracker = new ForestryTracker();
		tracker.onSpawn(ForestryEvent.RISING_ROOTS, 1L, 1_000L);
		tracker.onDespawn(ForestryEvent.RISING_ROOTS, 1L, 2_000L);
		Assert.assertNull(tracker.onSpawn(ForestryEvent.RISING_ROOTS, 1L, 2_600L));
		Assert.assertTrue(tracker.isRunning(ForestryEvent.RISING_ROOTS));
	}

	@Test
	public void anEventThatStaysGoneAnnouncesItselfAgain()
	{
		ForestryTracker tracker = new ForestryTracker();
		tracker.onSpawn(ForestryEvent.RISING_ROOTS, 1L, 1_000L);
		tracker.onDespawn(ForestryEvent.RISING_ROOTS, 1L, 2_000L);
		Assert.assertFalse(tracker.isRunning(ForestryEvent.RISING_ROOTS));
		Assert.assertEquals(ForestryEvent.RISING_ROOTS,
			tracker.onSpawn(ForestryEvent.RISING_ROOTS, 1L, 2_000L + ForestryTracker.REARM_MS));
	}

	@Test
	public void oneThingLeavingIsNotTheEventEnding()
	{
		ForestryTracker tracker = new ForestryTracker();
		tracker.onSpawn(ForestryEvent.PHEASANT_CONTROL, 1L, 1_000L);
		tracker.onSpawn(ForestryEvent.PHEASANT_CONTROL, 2L, 1_100L);
		tracker.onDespawn(ForestryEvent.PHEASANT_CONTROL, 1L, 1_200L);
		Assert.assertTrue(tracker.isRunning(ForestryEvent.PHEASANT_CONTROL));
		Assert.assertNull(tracker.onSpawn(ForestryEvent.PHEASANT_CONTROL, 3L, 1_300L));
	}

	@Test
	public void nothingIsTrackedForSomethingThatIsNotAnEvent()
	{
		ForestryTracker tracker = new ForestryTracker();
		Assert.assertNull(tracker.onSpawn(null, 1L, 1_000L));
		tracker.onDespawn(null, 1L, 1_000L);
	}
}
