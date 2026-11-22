package com.ulanzi.osrs;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
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
	public void outputsRouteToTheRightPlaces()
	{
		Assert.assertTrue(SoundOutput.CLOCK.toClock());
		Assert.assertFalse(SoundOutput.CLOCK.toComputer());
		Assert.assertFalse(SoundOutput.COMPUTER.toClock());
		Assert.assertTrue(SoundOutput.COMPUTER.toComputer());
		Assert.assertTrue(SoundOutput.BOTH.toClock() && SoundOutput.BOTH.toComputer());
		Assert.assertFalse(SoundOutput.OFF.toClock() || SoundOutput.OFF.toComputer());
	}

	@Test
	public void rtttlNotesHaveTheirPitchAndLength()
	{
		// b=120 makes a quarter note 500 ms.
		List<Rtttl.Note> notes = Rtttl.parse("t:d=4,o=4,b=120:a,8a5,p,4c.,c#");
		Assert.assertEquals(5, notes.size());
		Assert.assertEquals(440.0, notes.get(0).hz, 0.01);
		Assert.assertEquals(500, notes.get(0).ms);
		Assert.assertEquals(880.0, notes.get(1).hz, 0.01);
		Assert.assertEquals(250, notes.get(1).ms);
		Assert.assertEquals(0.0, notes.get(2).hz, 0.0);
		Assert.assertEquals(261.63, notes.get(3).hz, 0.01);
		Assert.assertEquals(750, notes.get(3).ms);
		Assert.assertEquals(277.18, notes.get(4).hz, 0.01);
	}

	@Test
	public void badMelodiesAreSilentNotErrors()
	{
		Assert.assertTrue(Rtttl.parse(null).isEmpty());
		Assert.assertTrue(Rtttl.parse("nonsense").isEmpty());
		Assert.assertTrue(Rtttl.parse("x:d=4,o=5,b=100:zz,9").isEmpty());
	}

	@Test
	public void everyTuneRendersSoftlyAndQuietly()
	{
		for (Tune tune : Tune.values())
		{
			for (SoundStyle style : SoundStyle.values())
			{
				List<Rtttl.Note> notes = Rtttl.parse(tune.rtttl(style));
				Assert.assertFalse(tune + " " + style, notes.isEmpty());
				byte[] pcm = ChipPlayer.render(notes, 100);
				Assert.assertTrue(pcm.length > 0);
				int peak = 0;
				for (int i = 0; i < pcm.length; i += 2)
				{
					short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
					peak = Math.max(peak, Math.abs(sample));
				}
				// Full volume stays under half scale, and every note eases in from silence.
				Assert.assertTrue(tune + " peaks at " + peak, peak > 0 && peak <= Short.MAX_VALUE / 2);
				Assert.assertEquals(0, (short) ((pcm[0] & 0xFF) | (pcm[1] << 8)));
			}
		}
		Assert.assertEquals(0, peakOf(ChipPlayer.render(Rtttl.parse(Tune.AFK.rtttl(SoundStyle.SOFT)), 0)));
	}

	@Test
	public void tunesAreWrappedInAWavTheAudioPlayerCanRead() throws Exception
	{
		byte[] pcm = ChipPlayer.render(Rtttl.parse(Tune.LEVEL_UP.rtttl(SoundStyle.SOFT)), 40);
		try (AudioInputStream wav = AudioSystem.getAudioInputStream(new ByteArrayInputStream(ChipPlayer.wav(pcm))))
		{
			Assert.assertEquals(ChipPlayer.SAMPLE_RATE, wav.getFormat().getSampleRate(), 0f);
			Assert.assertEquals(16, wav.getFormat().getSampleSizeInBits());
			Assert.assertEquals(1, wav.getFormat().getChannels());
			Assert.assertEquals(pcm.length / 2, wav.getFrameLength());
		}
	}

	private static int peakOf(byte[] pcm)
	{
		int peak = 0;
		for (int i = 0; i < pcm.length; i += 2)
		{
			peak = Math.max(peak, Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8))));
		}
		return peak;
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
