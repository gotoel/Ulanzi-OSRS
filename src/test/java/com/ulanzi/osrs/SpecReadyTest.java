package com.ulanzi.osrs;

import org.junit.Assert;
import org.junit.Test;

/**
 * The edge the special attack alert fires on. Everything else about it is a page, but
 * this is the part that decides whether anything happens at all.
 */
public class SpecReadyTest
{
	private static final int FULL = 100;

	@Test
	public void theBarFillingIsTheAlert()
	{
		Assert.assertTrue(UlanziOsrsPlugin.specRestored(90, 100, FULL));
	}

	@Test
	public void aBarAlreadyFullSaysNothingFurther()
	{
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(100, 100, FULL));
	}

	@Test
	public void spendingItIsNotTheAlert()
	{
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(100, 50, FULL));
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(50, 60, FULL));
	}

	/** -1 is the first reading of a session: nobody wants a chime for logging in. */
	@Test
	public void loggingInWithAFullBarIsSilent()
	{
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(-1, 100, FULL));
	}

	@Test
	public void aHalfSpecWeaponIsToldAtHalf()
	{
		Assert.assertTrue(UlanziOsrsPlugin.specRestored(40, 50, 50));
		// And only once on the way up, not again as the rest of the bar fills.
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(50, 60, 50));
		Assert.assertFalse(UlanziOsrsPlugin.specRestored(90, 100, 50));
	}

	/**
	 * Regeneration comes in tenths, but a boost can land the bar past the threshold in one
	 * step. Crossing is crossing however far it jumped.
	 */
	@Test
	public void aJumpPastTheThresholdStillCounts()
	{
		Assert.assertTrue(UlanziOsrsPlugin.specRestored(25, 100, 50));
	}
}
