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
}
