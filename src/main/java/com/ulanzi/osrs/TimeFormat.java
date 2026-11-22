package com.ulanzi.osrs;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TimeFormat
{
	SYSTEM("System"),
	H12("12-hour"),
	H24("24-hour");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}

	boolean is24Hour(Locale locale)
	{
		if (this != SYSTEM)
		{
			return this == H24;
		}
		DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
		return format instanceof SimpleDateFormat && !((SimpleDateFormat) format).toPattern().contains("a");
	}
}
