package com.ulanzi.osrs;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

public class SoundTest
{
	private static final Pattern NOTE = Pattern.compile("^(\\d*)([a-gp])(#?)(\\.?)(\\d?)(\\.?)$");

	@Test
	public void everySoftTuneStaysInTheLowerOctaves()
	{
		for (Tune tune : Tune.values())
		{
			List<Integer> octaves = octaves(tune.rtttl(SoundStyle.SOFT));
			Assert.assertFalse(tune + " has no notes", octaves.isEmpty());
			for (int octave : octaves)
			{
				Assert.assertTrue(tune + " reaches octave " + octave, octave >= 4 && octave <= 5);
			}
		}
	}

	@Test
	public void everySoftTuneIsShort()
	{
		for (Tune tune : Tune.values())
		{
			int notes = octaves(tune.rtttl(SoundStyle.SOFT)).size();
			Assert.assertTrue(tune + " plays " + notes + " notes", notes <= 3);
		}
	}

	@Test
	public void classicTunesAreTheOriginals()
	{
		Assert.assertEquals("levelup:d=8,o=5,b=180:c,e,g,4c6,p,g,2c6", Tune.LEVEL_UP.rtttl(SoundStyle.CLASSIC));
		Assert.assertEquals("lowhp:d=16,o=6,b=200:c,e,g,c7", Tune.LOW_HP.rtttl(SoundStyle.CLASSIC));
		for (Tune tune : Tune.values())
		{
			octaves(tune.rtttl(SoundStyle.CLASSIC));
		}
	}

	@Test
	public void soundOffSendsNoMelody()
	{
		Assert.assertNull(AwtrixClient.rtttlFor(Tune.AFK, SoundOutput.OFF, SoundStyle.CLASSIC));
		Assert.assertEquals(Tune.AFK.rtttl(SoundStyle.CLASSIC),
			AwtrixClient.rtttlFor(Tune.AFK, SoundOutput.CLOCK, SoundStyle.CLASSIC));
		Assert.assertEquals(Tune.AFK.rtttl(SoundStyle.SOFT),
			AwtrixClient.rtttlFor(Tune.AFK, SoundOutput.CLOCK, null));
	}

	@Test
	public void alertVolumeIsReadBackOrDefaulted()
	{
		JsonObject settings = new JsonObject();
		Assert.assertEquals(100, AwtrixClient.parseAlertVolume(settings));
		settings.addProperty("alertVolume", 60);
		Assert.assertEquals(60, AwtrixClient.parseAlertVolume(settings));
		settings.addProperty("alertVolume", 250);
		Assert.assertEquals(100, AwtrixClient.parseAlertVolume(settings));
		settings.addProperty("alertVolume", "loud");
		Assert.assertEquals(100, AwtrixClient.parseAlertVolume(settings));
	}

	@Test
	public void volumeIsSentInRange()
	{
		Assert.assertEquals(40, AwtrixClient.volumeJson(40).get("alertVolume").getAsInt());
		Assert.assertEquals(100, AwtrixClient.volumeJson(400).get("alertVolume").getAsInt());
		Assert.assertEquals(0, AwtrixClient.volumeJson(-5).get("alertVolume").getAsInt());
		Assert.assertEquals(1, AwtrixClient.volumeJson(40).size());
	}

	/** The octave of every sounding note, failing on anything that is not valid RTTTL. */
	private static List<Integer> octaves(String rtttl)
	{
		String[] parts = rtttl.split(":");
		Assert.assertEquals(rtttl, 3, parts.length);
		int defaultOctave = 6;
		for (String setting : parts[1].split(","))
		{
			String[] pair = setting.split("=");
			if ("o".equals(pair[0]))
			{
				defaultOctave = Integer.parseInt(pair[1]);
			}
		}
		List<Integer> octaves = new ArrayList<>();
		for (String note : parts[2].split(","))
		{
			Matcher matcher = NOTE.matcher(note.trim());
			Assert.assertTrue(rtttl + " has a bad note: " + note, matcher.matches());
			if ("p".equals(matcher.group(2)))
			{
				continue;
			}
			octaves.add(matcher.group(5).isEmpty() ? defaultOctave : Integer.parseInt(matcher.group(5)));
		}
		return octaves;
	}
}
