package com.ulanzi.osrs;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Iterator;
import net.runelite.api.Skill;

/**
 * XP per hour for each skill, either averaged over the whole session or over a
 * trailing window. A session average ignores the first drop so it is not
 * inflated by XP earned before the session began; the window counts it, because
 * there is no session length for it to distort. A break longer than
 * {@link #BREAK_MS} starts a new session.
 */
final class XpRateTracker
{
	static final long BREAK_MS = 5 * 60_000L;
	/** The longest window {@link #perHourWindow} serves. Older drops are forgotten. */
	static final long MAX_WINDOW_MS = 900_000L;
	private static final long HOUR_MS = 3_600_000L;

	private final EnumMap<Skill, Session> sessions = new EnumMap<>(Skill.class);

	void reset()
	{
		sessions.clear();
	}

	void onXp(Skill skill, int gained, long nowMs)
	{
		Session session = sessions.get(skill);
		if (session == null || nowMs - session.lastMs > BREAK_MS)
		{
			session = new Session(nowMs);
			sessions.put(skill, session);
			session.recent.addLast(new Gain(nowMs, gained));
			return;
		}
		session.gained += gained;
		session.lastMs = nowMs;
		session.recent.addLast(new Gain(nowMs, gained));
		while (nowMs - session.recent.peekFirst().atMs > MAX_WINDOW_MS)
		{
			session.recent.removeFirst();
		}
	}

	/**
	 * Averaged over the whole session: -1 until a second drop gives the session a
	 * length, and after a break.
	 */
	int perHour(Skill skill, long nowMs)
	{
		Session session = sessions.get(skill);
		if (session == null || session.gained <= 0 || nowMs - session.lastMs > BREAK_MS)
		{
			return -1;
		}
		long elapsed = Math.max(1L, nowMs - session.startMs);
		return (int) Math.min(Integer.MAX_VALUE, session.gained * HOUR_MS / elapsed);
	}

	/**
	 * Averaged over the last {@code windowMs}, always divided by the whole window
	 * rather than by however much of it has been played. The rate climbs from zero
	 * over the first window and falls back to zero once you stop, which is what
	 * makes it track what you are doing now instead of the session so far. -1
	 * before the first drop and after a break.
	 */
	int perHourWindow(Skill skill, long nowMs, long windowMs)
	{
		Session session = sessions.get(skill);
		if (session == null || nowMs - session.lastMs > BREAK_MS)
		{
			return -1;
		}
		long window = Math.max(1L, Math.min(windowMs, MAX_WINDOW_MS));
		long gained = 0L;
		// Newest first, so the walk stops at the edge of the window.
		for (Iterator<Gain> drops = session.recent.descendingIterator(); drops.hasNext(); )
		{
			Gain drop = drops.next();
			if (nowMs - drop.atMs >= window)
			{
				break;
			}
			gained += drop.xp;
		}
		return (int) Math.min(Integer.MAX_VALUE, gained * HOUR_MS / window);
	}

	/**
	 * At most four characters so the level still fits beside it: 950, 9.5k, 125k, 1.2m, 12m.
	 */
	static String format(int perHour)
	{
		if (perHour < 1_000)
		{
			return String.valueOf(Math.max(0, perHour));
		}
		if (perHour < 10_000)
		{
			return tenths(perHour / 100) + "k";
		}
		if (perHour < 1_000_000)
		{
			return (perHour / 1_000) + "k";
		}
		if (perHour < 10_000_000)
		{
			return tenths(perHour / 100_000) + "m";
		}
		return (perHour / 1_000_000) + "m";
	}

	private static String tenths(int tenths)
	{
		int whole = tenths / 10;
		int fraction = tenths % 10;
		return fraction == 0 ? String.valueOf(whole) : whole + "." + fraction;
	}

	private static final class Session
	{
		private final long startMs;
		private final ArrayDeque<Gain> recent = new ArrayDeque<>();
		private long lastMs;
		private long gained;

		private Session(long startMs)
		{
			this.startMs = startMs;
			this.lastMs = startMs;
		}
	}

	private static final class Gain
	{
		private final long atMs;
		private final int xp;

		private Gain(long atMs, int xp)
		{
			this.atMs = atMs;
			this.xp = xp;
		}
	}
}
