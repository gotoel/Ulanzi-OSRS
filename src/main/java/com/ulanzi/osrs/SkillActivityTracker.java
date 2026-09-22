package com.ulanzi.osrs;

/**
 * Decides which skilling activity is current.
 * A mapped animation wins immediately. After it stops, the same activity stays
 * for a few seconds so running to the next tree or rock does not flicker the icon off.
 * An unmapped animation (combat, emotes) clears it, unless that skill just gained XP,
 * which covers actions whose animation is not in the map yet.
 */
final class SkillActivityTracker
{
	static final long HOLD_MS = 5_000L;
	static final long UNKNOWN_ANIM_MS = 2_000L;

	private SkillActivity current;
	private long lastConfirmMs;
	private SkillActivity lastXpActivity;
	private long lastXpMs;

	void reset()
	{
		current = null;
		lastConfirmMs = 0L;
		lastXpActivity = null;
		lastXpMs = 0L;
	}

	void onXp(SkillActivity activity, long nowMs)
	{
		if (activity == null)
		{
			return;
		}
		lastXpActivity = activity;
		lastXpMs = nowMs;
		current = activity;
		lastConfirmMs = nowMs;
	}

	SkillActivity onAnimation(SkillActivity fromAnimation, int animationId, long nowMs)
	{
		if (fromAnimation != null)
		{
			current = fromAnimation;
			lastConfirmMs = nowMs;
			return current;
		}

		if (animationId == -1)
		{
			if (current != null && nowMs - lastConfirmMs < HOLD_MS)
			{
				return current;
			}
			if (lastXpActivity != null && nowMs - lastXpMs < HOLD_MS)
			{
				current = lastXpActivity;
				return current;
			}
			current = null;
			return null;
		}

		if (lastXpActivity != null && nowMs - lastXpMs <= UNKNOWN_ANIM_MS)
		{
			current = lastXpActivity;
			lastConfirmMs = nowMs;
			return current;
		}

		current = null;
		lastXpActivity = null;
		lastConfirmMs = 0L;
		return null;
	}
}
