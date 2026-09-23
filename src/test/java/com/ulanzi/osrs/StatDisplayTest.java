package com.ulanzi.osrs;

import java.awt.Color;
import org.junit.Assert;
import org.junit.Test;

public class StatDisplayTest
{
	@Test
	public void aFullStatKeepsItsOwnColour()
	{
		Assert.assertEquals(Color.BLUE, StatRamp.drain(Color.BLUE, 100));
		Assert.assertEquals(Color.BLUE, StatRamp.drain(Color.BLUE, 50));
	}

	@Test
	public void aDrainingStatRampsThroughAmberToRed()
	{
		Color base = StatKind.PRAYER.getIdentity();
		Color half = StatRamp.drain(base, 50);
		Color quarter = StatRamp.drain(base, 25);
		Color empty = StatRamp.drain(base, 0);

		Assert.assertEquals(base, half);
		// Amber at a quarter, whatever the stat started as.
		Assert.assertEquals(StatRamp.drain(StatKind.ENERGY.getIdentity(), 25), quarter);
		Assert.assertTrue(empty.getRed() > empty.getGreen());
		Assert.assertTrue(empty.getGreen() < quarter.getGreen());
	}

	@Test
	public void theRampIsContinuous()
	{
		Color base = StatKind.HITPOINTS.getIdentity();
		Color previous = StatRamp.drain(base, 100);
		for (int percent = 99; percent >= 0; percent--)
		{
			Color next = StatRamp.drain(base, percent);
			int step = Math.abs(next.getRed() - previous.getRed())
				+ Math.abs(next.getGreen() - previous.getGreen())
				+ Math.abs(next.getBlue() - previous.getBlue());
			Assert.assertTrue("jump at " + percent + " of " + step, step <= 24);
			previous = next;
		}
	}

	@Test
	public void hitpointsStillReadGreenFullAndRedNearlyGone()
	{
		Color base = StatKind.HITPOINTS.getIdentity();
		Color full = StatRamp.drain(base, AwtrixClient.percentOf(99, 99));
		Color gone = StatRamp.drain(base, AwtrixClient.percentOf(1, 99));
		Assert.assertTrue(full.getGreen() > full.getRed());
		Assert.assertTrue(gone.getRed() > gone.getGreen());
		Assert.assertEquals(0, AwtrixClient.percentOf(0, 0));
		Assert.assertEquals(50, AwtrixClient.percentOf(50, 100));
		Assert.assertEquals(100, AwtrixClient.percentOf(120, 100));
	}

	/**
	 * The clock scales every channel again for the room it is in, so a colour already close
	 * to black lands under what an LED can show once the lights go out.
	 */
	@Test
	public void aLitColourIsNeverSentCloseToBlack()
	{
		Color faint = AwtrixClient.lift(new Color(8, 4, 0), 40);
		Assert.assertEquals(40, Math.max(faint.getRed(), Math.max(faint.getGreen(), faint.getBlue())));
		// The hue survives being lifted, so a track still says which stat it belongs to.
		Assert.assertEquals(8.0 / 4.0, faint.getRed() / (double) faint.getGreen(), 0.3);
	}

	@Test
	public void blackStaysBlackSoBackgroundsAndIconsDoNotGlow()
	{
		Assert.assertEquals(Color.BLACK, AwtrixClient.lift(Color.BLACK, 40));
	}

	@Test
	public void alreadyBrightColoursAreLeftAlone()
	{
		Assert.assertEquals(Color.WHITE, AwtrixClient.lift(Color.WHITE, 40));
		Color base = StatKind.PRAYER.getIdentity();
		Assert.assertEquals(base, AwtrixClient.lift(base, 40));
	}

	/**
	 * Track, trail and fill have to stay three different things at the pulse's resting dim,
	 * which is where they were all being squeezed towards black at once.
	 */
	@Test
	public void theBarsThreeLevelsStayApartAtTheRestingDim()
	{
		Color color = StatKind.PRAYER.getIdentity();
		int track = peak(AwtrixClient.lift(AwtrixClient.brighten(
			AwtrixClient.brighten(color, 0.22), UlanziOsrsPlugin.PULSE_REST), 40));
		int ghost = peak(AwtrixClient.lift(AwtrixClient.brighten(
			AwtrixClient.brighten(color, 0.5), UlanziOsrsPlugin.PULSE_REST), 40));
		int fill = peak(AwtrixClient.lift(AwtrixClient.brighten(color, UlanziOsrsPlugin.PULSE_REST), 40));

		Assert.assertTrue("track " + track + " under the floor", track >= 40);
		Assert.assertTrue("trail " + ghost + " not above track " + track, ghost > track);
		Assert.assertTrue("fill " + fill + " not above trail " + ghost, fill > ghost);
	}

	private static int peak(Color color)
	{
		return Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
	}

	/**
	 * The bar answers a change of a percent or two instead of waiting to earn a whole
	 * pixel, which is what the old eight-row column made it do.
	 */
	@Test
	public void aBarFillsBelowAWholePixel()
	{
		Assert.assertEquals(0, AwtrixClient.barFullPixels(0, 12));
		Assert.assertEquals(0, AwtrixClient.barPartialPercent(0, 12));

		// Under one pixel of a 12px bar, so nothing is solid but the first pixel is lit.
		Assert.assertEquals(0, AwtrixClient.barFullPixels(4, 12));
		Assert.assertEquals(48, AwtrixClient.barPartialPercent(4, 12));

		Assert.assertEquals(6, AwtrixClient.barFullPixels(50, 12));
		Assert.assertEquals(0, AwtrixClient.barPartialPercent(50, 12));

		Assert.assertEquals(12, AwtrixClient.barFullPixels(100, 12));
		Assert.assertEquals(0, AwtrixClient.barPartialPercent(100, 12));
	}

	@Test
	public void aOnePercentTrickleStillShows()
	{
		Assert.assertEquals(0, AwtrixClient.barFullPixels(1, 8));
		Assert.assertTrue(AwtrixClient.barPartialPercent(1, 8) > 0);
	}

	/**
	 * Two readings a single percent apart have to light the panel differently, or the
	 * sub-pixel fill is not buying anything.
	 */
	@Test
	public void neighbouringPercentsDifferOnAWideBar()
	{
		int differences = 0;
		int previousFull = -1;
		int previousPartial = -1;
		for (int percent = 0; percent <= 100; percent++)
		{
			int full = AwtrixClient.barFullPixels(percent, 24);
			int partial = AwtrixClient.barPartialPercent(percent, 24);
			if (full != previousFull || partial != previousPartial)
			{
				differences++;
			}
			previousFull = full;
			previousPartial = partial;
		}
		Assert.assertEquals(101, differences);
	}

	@Test
	public void theFloorFollowsThePanelAndLeavesABrightOneAlone()
	{
		// Nothing moves at an ordinary brightness, so a lit room sees no change at all.
		Assert.assertEquals(40, AwtrixClient.visibleFloor(255));
		Assert.assertEquals(40, AwtrixClient.visibleFloor(AwtrixClient.DEFAULT_BRIGHTNESS));
		Assert.assertEquals(40, AwtrixClient.visibleFloor(64));
		Assert.assertTrue(AwtrixClient.visibleFloor(17) > 40);
		Assert.assertTrue(AwtrixClient.visibleFloor(8) > AwtrixClient.visibleFloor(17));
		// Never so far that the whole page flattens into one shade.
		Assert.assertTrue(AwtrixClient.visibleFloor(1) <= 140);
	}

	/**
	 * What the clock itself showed: at the bottom of its range a green sent at peak 180 was
	 * still green, while the same green sent at peak 40 was not there at all. Whether a
	 * pixel exists is decided by its brightest channel once the clock has scaled it, so the
	 * faintest thing on the page has to be worked backwards from the level it runs at.
	 */
	@Test
	public void theFaintestLitColourSurvivesThePanelItIsSentTo()
	{
		Color gradientLowStop = new Color(13, 40, 13);
		for (int brightness : new int[] {8, 12, 17, 32, 64, 120, 255})
		{
			Color held = AwtrixClient.lift(gradientLowStop, AwtrixClient.visibleFloor(brightness));
			int lands = peak(held) * (brightness + 1) / 256;
			Assert.assertTrue("at brightness " + brightness + " it lands on " + lands, lands >= 3);
		}
	}

	@Test
	public void anIconGivesUpItsShadingOnlyOnceThePanelIsDim()
	{
		Assert.assertEquals(0.0, AwtrixClient.iconOpenAmount(AwtrixClient.DEFAULT_BRIGHTNESS), 0.001);
		Assert.assertEquals(0.0, AwtrixClient.iconOpenAmount(64), 0.001);
		Assert.assertEquals(1.0, AwtrixClient.iconOpenAmount(8), 0.001);
		Assert.assertEquals(1.0, AwtrixClient.iconOpenAmount(1), 0.001);
		Assert.assertTrue(AwtrixClient.iconOpenAmount(17) > 0.5);
	}

	/**
	 * The tree trunk, which is the colour that was collapsing into the same red as
	 * everything else warm on the panel.
	 */
	@Test
	public void theTrunkKeepsMoreOfItsBrownOnADimPanel()
	{
		Color trunk = new Color(0x8B_5A_2B);
		int brightness = 17;
		int[] before = onPanel(trunk, brightness);
		int[] after = onPanel(AwtrixClient.openUp(trunk, AwtrixClient.iconOpenAmount(brightness)), brightness);

		// Blue is the first thing to go, and it is what separates a brown from a red.
		Assert.assertTrue("blue " + after[2] + " is no better than " + before[2], after[2] > before[2]);
		// And the gap red holds over green is what is left of the hue.
		Assert.assertTrue(after[0] - after[1] > before[0] - before[1]);
	}

	@Test
	public void openingUpHoldsTheHueAndLeavesBlackAlone()
	{
		Color opened = AwtrixClient.openUp(new Color(0x8B_5A_2B), 1.0);
		Assert.assertEquals(255, peak(opened));
		Assert.assertEquals(90.0 / 139.0, opened.getGreen() / (double) opened.getRed(), 0.02);
		Assert.assertEquals(43.0 / 139.0, opened.getBlue() / (double) opened.getRed(), 0.02);

		// An icon's background has to stay off, and white has nowhere to go.
		Assert.assertEquals(Color.BLACK, AwtrixClient.openUp(Color.BLACK, 1.0));
		Assert.assertEquals(Color.WHITE, AwtrixClient.openUp(Color.WHITE, 1.0));
	}

	private static int[] onPanel(Color color, int brightness)
	{
		return new int[] {
			color.getRed() * (brightness + 1) / 256,
			color.getGreen() * (brightness + 1) / 256,
			color.getBlue() * (brightness + 1) / 256};
	}

	@Test
	public void theFloorIsHeldOnlyWhenTheClockIsUnderIt()
	{
		Assert.assertTrue(AwtrixClient.shouldHoldFloor(8, 17));
		Assert.assertFalse(AwtrixClient.shouldHoldFloor(17, 17));
		Assert.assertFalse(AwtrixClient.shouldHoldFloor(120, 17));
		// Switched off.
		Assert.assertFalse(AwtrixClient.shouldHoldFloor(8, 0));
		// Nothing read back yet is not the same as a dark panel.
		Assert.assertFalse(AwtrixClient.shouldHoldFloor(0, 17));
	}

	@Test
	public void onlyASensorDrivenPanelIsHandedBack()
	{
		// The room has brightened past where it was when the floor was taken up.
		Assert.assertTrue(AwtrixClient.shouldRelease(true, 5, 3));
		Assert.assertFalse(AwtrixClient.shouldRelease(true, 2, 3));
		// A level someone set by hand is held, which is the point of asking for a floor.
		Assert.assertFalse(AwtrixClient.shouldRelease(false, 500, 3));
	}
}
