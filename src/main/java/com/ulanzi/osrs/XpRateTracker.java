package com.ulanzi.osrs;

import java.util.EnumMap;
import net.runelite.api.Skill;

/**
 * XP per hour for each skill since you started training it.
 * The first drop only starts the clock, so the rate is not inflated by XP
 * earned before the session began. A break longer than {@link #BREAK_MS}
 * starts a new session.
 */
final class XpRateTracker
{
	static final long BREAK_MS = 5 * 60_000L;
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
			sessions.put(skill, new Session(nowMs));
			return;
		}
		session.gained += gained;
		session.lastMs = nowMs;
	}

	/**
	 * -1 until a second drop gives the session a length, and after a break.
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
		private long lastMs;
		private long gained;

		private Session(long startMs)
		{
			this.startMs = startMs;
			this.lastMs = startMs;
		}
	}
}
