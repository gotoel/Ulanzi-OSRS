package com.ulanzi.osrs;

import java.util.List;

/**
 * Whose turn it is when several skills are being trained at once.
 *
 * Each one holds the panel for a whole turn and keeps it for the whole of that turn:
 * a drop landing for another skill, one joining the rotation or one falling out of it
 * no longer cuts a turn short. Turns used to be counted off the clock and handed out
 * by position in the list, so every change to what you had going reshuffled it under
 * you and two skills ended up flickering back and forth under a stream of drops.
 *
 * Turns are taken in the skills' own order and passed to whoever comes after the one
 * holding the panel, so the rotation walks evenly around everything you have going
 * and keeps its place whatever joins or drops out.
 */
final class SkillRotation
{
	private SkillActivity holder;
	private long turnEndsMs;
	private long turns;

	void reset()
	{
		holder = null;
		turnEndsMs = 0L;
		turns = 0L;
	}

	/**
	 * @param active every skill trained recently, in their own order.
	 * @param current what is happening right now, which takes the panel on its own
	 *                while nothing is sharing it.
	 * @param turnMs how long one turn lasts.
	 * @return the skill whose turn it is.
	 */
	SkillActivity turn(List<SkillActivity> active, SkillActivity current, long nowMs, long turnMs)
	{
		if (nowMs >= turnEndsMs)
		{
			startTurn(active.size() < 2 ? current : next(active, holder), nowMs, turnMs);
		}
		else if (active.size() < 2)
		{
			// Nothing to share with, so the panel follows what you are doing without
			// waiting for a turn to run out.
			holder = current;
		}
		else if (holder == null || !active.contains(holder))
		{
			// Whoever was holding it stopped being trained: the panel passes on down
			// the order rather than back to whichever skill paid last.
			startTurn(next(active, holder), nowMs, turnMs);
		}
		return holder;
	}

	/** How many turns have been handed out, which is what paces the level and rate swap. */
	long turns()
	{
		return turns;
	}

	private void startTurn(SkillActivity activity, long nowMs, long turnMs)
	{
		holder = activity;
		turnEndsMs = nowMs + turnMs;
		turns++;
	}

	/**
	 * The first skill past the one holding the panel, wrapping round at the end. Going
	 * by order rather than by index is what lets a skill join or drop out without
	 * moving anybody else's turn.
	 */
	private static SkillActivity next(List<SkillActivity> active, SkillActivity after)
	{
		if (active.isEmpty())
		{
			return null;
		}
		if (after != null)
		{
			for (SkillActivity activity : active)
			{
				if (activity.ordinal() > after.ordinal())
				{
					return activity;
				}
			}
		}
		return active.get(0);
	}

	/** Keeps the list in the skills' own order, so the rotation reads the same each lap. */
	static void insertInOrder(List<SkillActivity> active, SkillActivity activity)
	{
		if (activity == null || active.contains(activity))
		{
			return;
		}
		for (int i = 0; i < active.size(); i++)
		{
			if (active.get(i).ordinal() > activity.ordinal())
			{
				active.add(i, activity);
				return;
			}
		}
		active.add(activity);
	}
}
