package com.ulanzi.osrs;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the clock's tunes through the computer's speakers.
 *
 * The voice is deliberately nothing like a piezo: a sine with a touch of its octave for
 * warmth, eased in and out so no note starts or ends with a click. The audio is built in
 * memory as a WAV and handed to RuneLite's audio player from one background thread, so a
 * tune never holds up the game.
 */
@Slf4j
@Singleton
class ChipPlayer
{
	static final int SAMPLE_RATE = 44_100;
	private static final int ATTACK_MS = 10;
	private static final int MAX_RELEASE_MS = 60;
	/** A short silence after every note, so repeated pitches are heard as separate notes. */
	private static final int GAP_MS = 12;
	/** Full volume stays well short of clipping, and leaves room for the octave above. */
	private static final double PEAK = 0.45;
	private static final double OCTAVE_SHARE = 0.15;
	private static final int WAV_HEADER_BYTES = 44;

	private final UlanziConfig config;
	private final AudioPlayer audioPlayer;
	private final AtomicInteger generation = new AtomicInteger();
	private ExecutorService executor;

	@Inject
	ChipPlayer(UlanziConfig config, AudioPlayer audioPlayer)
	{
		this.config = config;
		this.audioPlayer = audioPlayer;
	}

	void play(String rtttl)
	{
		int volume = Math.max(0, Math.min(100, config.computerVolume()));
		if (volume == 0)
		{
			return;
		}
		List<Rtttl.Note> notes = Rtttl.parse(rtttl);
		if (notes.isEmpty())
		{
			return;
		}
		byte[] wav = wav(render(notes, volume));
		int mine = generation.incrementAndGet();
		executor().execute(() -> playNow(wav, mine));
	}

	/** Drops any tune that has not started yet and lets the sound thread go. */
	synchronized void stop()
	{
		generation.incrementAndGet();
		if (executor != null)
		{
			executor.shutdown();
			executor = null;
		}
	}

	private synchronized ExecutorService executor()
	{
		if (executor == null)
		{
			executor = Executors.newSingleThreadExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "ulanzi-osrs-sound");
				thread.setDaemon(true);
				return thread;
			});
		}
		return executor;
	}

	private void playNow(byte[] wav, int mine)
	{
		if (generation.get() != mine)
		{
			return;
		}
		try
		{
			// The volume is already in the samples, so the player adds no gain of its own.
			audioPlayer.play(new ByteArrayInputStream(wav), 0f);
		}
		catch (Exception ex)
		{
			log.debug("No sound output for the computer", ex);
		}
	}

	/** Wraps 16-bit mono samples in the WAV header the audio player needs to read them. */
	static byte[] wav(byte[] pcm)
	{
		ByteBuffer out = ByteBuffer.allocate(WAV_HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
		out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		out.putInt(WAV_HEADER_BYTES - 8 + pcm.length);
		out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
		out.putInt(16);
		out.putShort((short) 1);
		out.putShort((short) 1);
		out.putInt(SAMPLE_RATE);
		out.putInt(SAMPLE_RATE * 2);
		out.putShort((short) 2);
		out.putShort((short) 16);
		out.put("data".getBytes(StandardCharsets.US_ASCII));
		out.putInt(pcm.length);
		out.put(pcm);
		return out.array();
	}

	/**
	 * The whole tune as 16-bit little-endian mono samples. Volume follows a square law, which
	 * is closer to how loud a level sounds than a straight line is.
	 */
	static byte[] render(List<Rtttl.Note> notes, int volume)
	{
		double level = Math.pow(Math.max(0, Math.min(100, volume)) / 100.0, 2) * PEAK;
		int total = 0;
		for (Rtttl.Note note : notes)
		{
			total += samples(note.ms + GAP_MS);
		}
		byte[] pcm = new byte[total * 2];
		int at = 0;
		for (Rtttl.Note note : notes)
		{
			int sounding = samples(note.ms);
			if (note.hz > 0)
			{
				int attack = Math.min(samples(ATTACK_MS), sounding / 4);
				int release = Math.min(samples(MAX_RELEASE_MS), sounding * 2 / 5);
				for (int i = 0; i < sounding; i++)
				{
					double t = (double) i / SAMPLE_RATE;
					double envelope = 1.0;
					if (i < attack)
					{
						envelope = (double) i / attack;
					}
					else if (i >= sounding - release)
					{
						envelope = (double) (sounding - i) / release;
					}
					// A smooth curve rather than a straight ramp, which is what keeps it soft.
					envelope = envelope * envelope * (3 - 2 * envelope);
					double wave = Math.sin(2 * Math.PI * note.hz * t)
						+ OCTAVE_SHARE * Math.sin(4 * Math.PI * note.hz * t);
					short sample = (short) Math.round(wave / (1 + OCTAVE_SHARE) * envelope * level * Short.MAX_VALUE);
					pcm[(at + i) * 2] = (byte) sample;
					pcm[(at + i) * 2 + 1] = (byte) (sample >> 8);
				}
			}
			at += samples(note.ms + GAP_MS);
		}
		return pcm;
	}

	private static int samples(int ms)
	{
		return (int) ((long) ms * SAMPLE_RATE / 1000);
	}
}
