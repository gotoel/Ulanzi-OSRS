package com.ulanzi.osrs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which skilling activity is current.
 * A mapped animation wins immediately. After it stops, the same activity can
 * stay for a configurable hold so running to the next tree or rock does not
 * flicker the icon off. An unmapped animation (combat, emotes) clears it,
 * unless that skill just gained XP, which covers actions whose animation is
 * not in the map yet.
 */
final class SkillActivityTracker
{
	static final long UNKNOWN_ANIM_MS = 2_000L;

	/** How long a skill counts as still being trained after its last XP. */
	static final long TRAINING_MS = 30_000L;

	private SkillActivity current;
	private long lastConfirmMs;
	private SkillActivity lastXpActivity;
	private long lastXpMs;
	/** Every skill paying XP at the moment, so several at once can take turns. */
	private final Map<SkillActivity, Long> trainingXpMs = new EnumMap<>(SkillActivity.class);

	void reset()
	{
		current = null;
		lastConfirmMs = 0L;
		lastXpActivity = null;
		lastXpMs = 0L;
		trainingXpMs.clear();
	}

	void onXp(SkillActivity activity, long nowMs)
	{
		if (activity == null)
		{
			return;
		}
		lastXpActivity = activity;
		lastXpMs = nowMs;
		trainingXpMs.put(activity, nowMs);
		current = activity;
		lastConfirmMs = nowMs;
	}

	/**
	 * The skills still paying XP, in a fixed order so a rotation between them keeps its
	 * place as the drops land rather than reshuffling under whoever paid last.
	 */
	List<SkillActivity> training(long nowMs, long windowMs)
	{
		List<SkillActivity> training = new ArrayList<>();
		for (Map.Entry<SkillActivity, Long> entry : trainingXpMs.entrySet())
		{
			if (nowMs - entry.getValue() < windowMs)
			{
				training.add(entry.getKey());
			}
		}
		return training;
	}

	SkillActivity onAnimation(SkillActivity fromAnimation, int animationId, long nowMs)
	{
		return onAnimation(fromAnimation, animationId, nowMs, 0L);
	}

	SkillActivity onAnimation(SkillActivity fromAnimation, int animationId, long nowMs, long holdMs)
	{
		if (fromAnimation != null)
		{
			current = fromAnimation;
			lastConfirmMs = nowMs;
			return current;
		}

		if (animationId == -1)
		{
			if (holdMs > 0L)
			{
				if (current != null && nowMs - lastConfirmMs < holdMs)
				{
					return current;
				}
				if (lastXpActivity != null && nowMs - lastXpMs < holdMs)
				{
					current = lastXpActivity;
					return current;
				}
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
