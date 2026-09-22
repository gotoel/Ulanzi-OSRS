package com.ulanzi.osrs;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ThresholdUnit
{
	PERCENT("Percent of max"),
	POINTS("Points");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * At or below the threshold. A percent threshold scales with the real level,
	 * so the same setting works on a 10 HP account and a 99 HP one.
	 */
	boolean isLow(int current, int max, int threshold)
	{
		if (this == POINTS || max <= 0)
		{
			return current <= threshold;
		}
		return current * 100 <= threshold * max;
	}
}
