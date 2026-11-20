package com.ulanzi.osrs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads an RTTTL melody ("name:d=4,o=6,b=63:c,e,8g") into notes, the same format the clock
 * plays, so a tune written once can be played by the clock or by the computer.
 */
final class Rtttl
{
	/** A pitch held for a time. A frequency of zero is a rest. */
	static final class Note
	{
		final double hz;
		final int ms;

		Note(double hz, int ms)
		{
			this.hz = hz;
			this.ms = ms;
		}
	}

	private static final int[] SEMITONES = {9, 11, 0, 2, 4, 5, 7};

	private Rtttl()
	{
	}

	/**
	 * The notes of a melody, or an empty list for anything that is not RTTTL, so a bad tune
	 * is silent rather than an error.
	 */
	static List<Note> parse(String rtttl)
	{
		if (rtttl == null)
		{
			return Collections.emptyList();
		}
		String[] parts = rtttl.split(":", -1);
		if (parts.length != 3)
		{
			return Collections.emptyList();
		}

		int duration = 4;
		int octave = 6;
		int bpm = 63;
		for (String setting : parts[1].split(","))
		{
			String[] pair = setting.trim().split("=");
			if (pair.length != 2)
			{
				continue;
			}
			int value = number(pair[1].trim(), -1);
			switch (pair[0].trim().toLowerCase(Locale.ROOT))
			{
				case "d":
					duration = value > 0 ? value : duration;
					break;
				case "o":
					octave = value >= 0 ? value : octave;
					break;
				case "b":
					bpm = value > 0 ? value : bpm;
					break;
				default:
					break;
			}
		}

		// A whole note is four beats.
		double wholeMs = 4 * 60_000.0 / bpm;
		List<Note> notes = new ArrayList<>();
		for (String raw : parts[2].split(","))
		{
			Note note = note(raw.trim().toLowerCase(Locale.ROOT), duration, octave, wholeMs);
			if (note != null)
			{
				notes.add(note);
			}
		}
		return notes;
	}

	private static Note note(String text, int defaultDuration, int defaultOctave, double wholeMs)
	{
		int i = 0;
		while (i < text.length() && Character.isDigit(text.charAt(i)))
		{
			i++;
		}
		int duration = i == 0 ? defaultDuration : number(text.substring(0, i), defaultDuration);
		if (i >= text.length() || duration <= 0)
		{
			return null;
		}

		char letter = text.charAt(i++);
		boolean rest = letter == 'p';
		if (!rest && (letter < 'a' || letter > 'g'))
		{
			return null;
		}
		int semitone = rest ? 0 : SEMITONES[letter - 'a'];
		boolean dotted = false;
		if (i < text.length() && text.charAt(i) == '#')
		{
			semitone++;
			i++;
		}
		if (i < text.length() && text.charAt(i) == '.')
		{
			dotted = true;
			i++;
		}
		int octave = defaultOctave;
		if (i < text.length() && Character.isDigit(text.charAt(i)))
		{
			octave = text.charAt(i) - '0';
			i++;
		}
		if (i < text.length() && text.charAt(i) == '.')
		{
			dotted = true;
		}

		double ms = wholeMs / duration * (dotted ? 1.5 : 1.0);
		if (rest)
		{
			return new Note(0, (int) Math.round(ms));
		}
		int midi = 12 * (octave + 1) + semitone;
		double hz = 440.0 * Math.pow(2, (midi - 69) / 12.0);
		return new Note(hz, (int) Math.round(ms));
	}

	private static int number(String text, int fallback)
	{
		try
		{
			return Integer.parseInt(text);
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}
}
