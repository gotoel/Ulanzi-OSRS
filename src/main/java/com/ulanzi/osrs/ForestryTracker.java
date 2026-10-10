package com.ulanzi.osrs;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns the stream of spawns and despawns around you into "an event just started".
 *
 * An event puts several things in the scene at once and keeps replacing them while it
 * runs, so the first one to appear is the news and the rest are not. Walking back into
 * a region, or hopping worlds, despawns and respawns everything that was already there,
 * which is why an event that comes straight back is treated as the one you were already
 * standing in rather than a new one.
 */
final class ForestryTracker
{
	/** How long an event has to stay gone before it counts as a new one. */
	static final long REARM_MS = 30_000L;

	private final Map<ForestryEvent, Set<Long>> present = new EnumMap<>(ForestryEvent.class);
	private final Map<ForestryEvent, Long> goneSinceMs = new EnumMap<>(ForestryEvent.class);

	/**
	 * @return the event if this spawn started it, or null if it was already running.
	 */
	ForestryEvent onSpawn(ForestryEvent event, long key, long nowMs)
	{
		if (event == null)
		{
			return null;
		}

		Set<Long> keys = present.computeIfAbsent(event, ignored -> new HashSet<>());
		boolean wasEmpty = keys.isEmpty();
		if (!keys.add(key) || !wasEmpty)
		{
			return null;
		}

		Long goneSince = goneSinceMs.remove(event);
		if (goneSince != null && nowMs - goneSince < REARM_MS)
		{
			return null;
		}
		return event;
	}

	void onDespawn(ForestryEvent event, long key, long nowMs)
	{
		if (event == null)
		{
			return;
		}

		Set<Long> keys = present.get(event);
		if (keys == null || !keys.remove(key) || !keys.isEmpty())
		{
			return;
		}
		goneSinceMs.put(event, nowMs);
	}

	boolean isRunning(ForestryEvent event)
	{
		Set<Long> keys = present.get(event);
		return keys != null && !keys.isEmpty();
	}

	void reset()
	{
		present.clear();
		goneSinceMs.clear();
	}
}
