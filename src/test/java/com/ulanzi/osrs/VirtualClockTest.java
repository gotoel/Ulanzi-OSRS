package com.ulanzi.osrs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

public class VirtualClockTest
{
	private final AtomicLong now = new AtomicLong(1_000_000L);
	private final UlanziConfig config = new UlanziConfig()
	{
		@Override
		public ClockOutput clockOutput()
		{
			return ClockOutput.VIRTUAL;
		}

		@Override
		public SoundOutput soundOutput()
		{
			return SoundOutput.OFF;
		}

		@Override
		public IdleDisplay virtualIdle()
		{
			return IdleDisplay.TIME;
		}
	};
	private final ChipPlayer player = new ChipPlayer(config);
	private final VirtualClock clock = new VirtualClock(player, config, now::get);

	@Test
	public void aNotificationShowsForItsDurationThenTheAppReturns()
	{
		pushApp("#00FF00", "APP");
		notify("one", "#FF0000", "HI", 2_000, true, false);
		Assert.assertEquals(0xFF0000, litColor(frame()));
		now.addAndGet(1_999);
		Assert.assertEquals(0xFF0000, litColor(frame()));
		now.addAndGet(2);
		Assert.assertEquals(0x00FF00, litColor(frame()));
	}

	@Test
	public void stackedNotificationsPlayInTurn()
	{
		notify("one", "#FF0000", "A", 1_000, true, false);
		notify("two", "#0000FF", "B", 1_000, true, false);
		Assert.assertEquals(0xFF0000, litColor(frame()));
		now.addAndGet(1_000);
		Assert.assertEquals(0x0000FF, litColor(frame()));
	}

	@Test
	public void unstackedReplacesWhatIsOnScreen()
	{
		notify("one", "#FF0000", "A", 5_000, true, false);
		notify("two", "#0000FF", "B", 5_000, false, false);
		Assert.assertEquals(0x0000FF, litColor(frame()));
		now.addAndGet(5_001);
		// The one it replaced is gone too, so the idle time is all that is left.
		Assert.assertEquals(0x8C8C8C, litColor(frame()));
	}

	@Test
	public void heldStaysUntilDeletedAndBlocksTheQueue()
	{
		notify("afk", "#FF0000", "AFK", 0, true, true);
		notify("next", "#0000FF", "B", 1_000, true, false);
		now.addAndGet(60_000);
		Assert.assertEquals(0xFF0000, litColor(frame()));
		Assert.assertTrue(clock.handle("DELETE", "/api/v1/notifications/afk", null).ok());
		Assert.assertEquals(0x0000FF, litColor(frame()));
	}

	@Test
	public void deletingTheAppLeavesTheIdleTime()
	{
		pushApp("#00FF00", "APP");
		clock.handle("DELETE", "/api/v1/apps/osrs", null);
		int color = litColor(frame());
		Assert.assertNotEquals(0x00FF00, color);
		Assert.assertNotEquals(0, color);
	}

	@Test
	public void settingsAreReadBackAndBrightnessDims()
	{
		clock.handle("PATCH", "/api/v1/settings", "{\"autoBrightness\":false,\"brightness\":0,\"alertVolume\":30}");
		VirtualClock.Reply settings = clock.handle("GET", "/api/v1/settings", null);
		JsonObject parsed = new Gson().fromJson(settings.body, JsonObject.class);
		Assert.assertEquals(0, parsed.get("brightness").getAsInt());
		Assert.assertEquals(30, parsed.get("alertVolume").getAsInt());
		Assert.assertTrue(clock.brightness() < 0.5);
		Assert.assertEquals(404, clock.handle("GET", "/api/v1/nothing", null).code);
	}

	@Test
	public void thePluginsOwnAlertDrawsOnTheVirtualClock()
	{
		AwtrixClient client = new AwtrixClient(new OkHttpClient(), config, new Gson(), player, clock);
		client.showLowHpAlert(7, false, false, true);
		int[] frame = frame();
		boolean red = false;
		for (int rgb : frame)
		{
			red |= rgb == 0xFF0000;
		}
		Assert.assertTrue(Frame.toAscii(frame), red);
	}

	@Test
	public void theCompactPageLandsWhereTheLayoutPutsIt()
	{
		JsonArray draw = new JsonArray();
		JsonArray text = new JsonArray();
		text.add("text");
		text.add(12);
		text.add(1);
		text.add("99");
		text.add("#FFFFFF");
		draw.add(text);
		JsonObject page = new JsonObject();
		page.addProperty("text", "");
		page.add("draw", draw);
		int[] frame = Frame.blank();
		PageRenderer.render(page, 0, 0, frame);
		// "99": two glyphs, three columns each with a gap, from x 12, on rows 1 to 5.
		Assert.assertEquals(7, PixelFont.width("99"));
		Assert.assertEquals(0, count(frame, 0, 0, 12, 8));
		Assert.assertEquals(0, count(frame, 19, 0, 13, 8));
		Assert.assertEquals(0, count(frame, 0, 0, 32, 1));
		Assert.assertEquals(0, count(frame, 0, 6, 32, 2));
		Assert.assertTrue(count(frame, 12, 1, 7, 5) > 10);
	}

	@Test
	public void centredTextSitsBesideTheIcon()
	{
		JsonObject page = new JsonObject();
		page.addProperty("text", "50");
		page.addProperty("icon", SkillActivity.WOODCUTTING.iconData());
		JsonObject scroll = new JsonObject();
		scroll.addProperty("mode", "static");
		page.add("scroll", scroll);
		int[] frame = Frame.blank();
		PageRenderer.render(page, 0, 0, frame);
		// Area x 9 to 31 is 23 wide; "50" is 7 wide, so it starts at 9 + 8 = 17.
		Assert.assertTrue(count(frame, 0, 0, 8, 8) > 0);
		Assert.assertEquals(0, count(frame, 9, 1, 8, 5));
		Assert.assertTrue(count(frame, 17, 1, 7, 5) > 0);
		Assert.assertEquals(0, count(frame, 24, 1, 8, 5));
	}

	@Test
	public void progressFillsTheBottomRowRightOfTheIcon()
	{
		JsonObject page = new JsonObject();
		page.addProperty("text", "");
		page.addProperty("progress", 50);
		page.addProperty("progressColor", "#FF0000");
		page.addProperty("progressTrackColor", "#000000");
		int[] frame = Frame.blank();
		PageRenderer.render(page, 0, 0, frame);
		Assert.assertEquals(16, count(frame, 0, 7, 32, 1));
		Assert.assertEquals(0xFF0000, Frame.get(frame, 15, 7));
		Assert.assertEquals(0, Frame.get(frame, 16, 7));
	}

	@Test
	public void blinkingTextIsDarkForTheFirstHalf()
	{
		JsonObject page = new JsonObject();
		page.addProperty("text", "HP");
		page.addProperty("textBlinkMs", 400);
		int[] frame = Frame.blank();
		PageRenderer.render(page, 100, 0, frame);
		Assert.assertEquals(0, count(frame));
		PageRenderer.render(page, 300, 0, frame);
		Assert.assertTrue(count(frame) > 0);
	}

	@Test
	public void scrollingTextCrossesThePanelAndRepeatEndsIt()
	{
		JsonObject page = new JsonObject();
		page.addProperty("text", "+120");
		JsonObject scroll = new JsonObject();
		scroll.addProperty("mode", "wrap");
		scroll.addProperty("entry", "offscreen");
		scroll.addProperty("whenFits", "scroll");
		scroll.addProperty("speed", 150);
		scroll.addProperty("holdMs", 0);
		page.add("scroll", scroll);
		page.addProperty("repeat", 1);
		page.addProperty("name", "xp");

		int[] frame = Frame.blank();
		PageRenderer.render(page, 0, 0, frame);
		Assert.assertEquals("starts off the right edge", 0, count(frame));
		long run = PageRenderer.runMs(page);
		Assert.assertEquals(AwtrixClient.xpDropFlightMs("+120", false), run, 400);
		PageRenderer.render(page, run / 2, 0, frame);
		Assert.assertTrue(count(frame) > 0);

		clock.handle("POST", "/api/v1/notifications", page.toString());
		now.addAndGet(run / 2);
		Assert.assertEquals(0xFFFFFF, litColor(frame()));
		now.addAndGet(run / 2 + 1);
		// One run and it is gone, leaving the idle time in grey.
		Assert.assertEquals(0x8C8C8C, litColor(frame()));
	}

	@Test
	public void iconsDecodeFromThePluginsOwnGifs()
	{
		GifIcon still = GifIcon.of(SkillActivity.MINING.iconData());
		Assert.assertNotNull(still);
		Assert.assertEquals(8, still.width);
		GifIcon drop = GifIcon.of(XpDropGif.encode("+50", Color.WHITE, true));
		Assert.assertNotNull(drop);
		Assert.assertEquals(XpDropGif.WIDTH, drop.width);
		Assert.assertNotSame(drop.frameAt(0), drop.frameAt(XpDropGif.visibleMs() / 2));
		Assert.assertNotNull(GifIcon.of("data:image/gif;base64," + SkillActivity.MINING.iconData()));
		Assert.assertNull(GifIcon.of("an-icon-id"));
	}

	@Test
	public void plasmaAndPalettesFillThePanel()
	{
		JsonObject page = new JsonObject();
		page.addProperty("effect", "Plasma");
		page.addProperty("palette", "Lava");
		page.addProperty("text", "");
		int[] frame = Frame.blank();
		PageRenderer.render(page, 0, 1234, frame);
		Assert.assertTrue(count(frame) > 100);
		Assert.assertEquals(0xFF0000, Palettes.sample(new int[] {0xFF0000, 0x0000FF}, 0, false, true));
		Assert.assertEquals(0x0000FF, Palettes.sample(new int[] {0xFF0000, 0x0000FF}, 1, false, true));
		Assert.assertNotNull(Palettes.named("rainbow"));
	}

	@Test
	public void everyCharacterThePluginWritesHasAGlyph()
	{
		for (char c : "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ +-.,:!?/%'()#".toCharArray())
		{
			Assert.assertTrue("no glyph for " + c, PixelFont.known(c));
		}
		Assert.assertEquals(XpDropGif.textWidth("+1 234"), PixelFont.width("+1 234"));
	}

	@Test
	public void automaticIsVirtualUntilAClockHasAnswered()
	{
		Assert.assertEquals(ClockOutput.VIRTUAL, ClockOutput.AUTO.resolve(false, false));
		Assert.assertEquals(ClockOutput.VIRTUAL, ClockOutput.AUTO.resolve(true, false));
		Assert.assertEquals(ClockOutput.REAL, ClockOutput.AUTO.resolve(true, true));
		Assert.assertEquals(ClockOutput.BOTH, ClockOutput.BOTH.resolve(true, true));
		Assert.assertEquals(ClockOutput.VIRTUAL, ClockOutput.VIRTUAL.resolve(true, true));
		Assert.assertEquals(ClockOutput.AUTO, new UlanziConfig()
		{
		}.clockOutput());
	}

	@Test
	public void timeReadsInTwelveOrTwentyFourHours()
	{
		java.time.LocalTime afternoon = java.time.LocalTime.of(14, 5);
		java.time.LocalTime midnight = java.time.LocalTime.of(0, 7);
		Assert.assertEquals("2:05PM", VirtualClock.formatTime(afternoon, TimeFormat.H12, java.util.Locale.UK));
		Assert.assertEquals("12:07AM", VirtualClock.formatTime(midnight, TimeFormat.H12, java.util.Locale.UK));
		Assert.assertEquals("14:05", VirtualClock.formatTime(afternoon, TimeFormat.H24, java.util.Locale.US));
		Assert.assertTrue(PixelFont.width("12:59PM") <= Frame.WIDTH);
	}

	@Test
	public void resizingSnapsToWholeLedsAndFindsTheEdges()
	{
		Dimension ten = VirtualClockWindow.LedPanel.sizeFor(10);
		Assert.assertEquals(10, VirtualClockWindow.pitchForLength(ten.width, Frame.WIDTH));
		Assert.assertEquals(10, VirtualClockWindow.pitchForLength(ten.height, Frame.HEIGHT));
		Assert.assertEquals(VirtualClockWindow.MIN_PITCH, VirtualClockWindow.pitchForLength(1, Frame.WIDTH));
		Assert.assertEquals(VirtualClockWindow.MAX_PITCH, VirtualClockWindow.pitchForLength(99_999, Frame.WIDTH));
		Assert.assertEquals(VirtualClockWindow.LEFT | VirtualClockWindow.TOP,
			VirtualClockWindow.edgesAt(1, 1, 300, 80));
		Assert.assertEquals(VirtualClockWindow.RIGHT, VirtualClockWindow.edgesAt(298, 40, 300, 80));
		Assert.assertEquals(0, VirtualClockWindow.edgesAt(150, 40, 300, 80));
	}

	@Test
	public void lowercaseKeepsItsNumberSuffixes()
	{
		Assert.assertTrue(PixelFont.known('k'));
		int[] lower = Frame.blank();
		int[] upper = Frame.blank();
		PixelFont.draw(lower, "k", 0, 1, false, (x, i) -> 1, 0, Frame.WIDTH);
		PixelFont.draw(upper, "K", 0, 1, false, (x, i) -> 1, 0, Frame.WIDTH);
		Assert.assertFalse(java.util.Arrays.equals(lower, upper));
	}

	private void pushApp(String color, String text)
	{
		JsonObject page = new JsonObject();
		page.addProperty("text", text);
		page.addProperty("textColor", color);
		clock.handle("PUT", "/api/v1/apps/pushed/osrs", page.toString());
		clock.handle("PUT", "/api/v1/apps/active", "{\"name\":\"osrs\"}");
	}

	private void notify(String name, String color, String text, int durationMs, boolean stack, boolean hold)
	{
		JsonObject page = new JsonObject();
		page.addProperty("name", name);
		page.addProperty("text", text);
		page.addProperty("textColor", color);
		page.addProperty("durationMs", durationMs);
		page.addProperty("stack", stack);
		page.addProperty("hold", hold);
		Assert.assertTrue(clock.handle("POST", "/api/v1/notifications", page.toString()).ok());
	}

	private int[] frame()
	{
		int[] frame = Frame.blank();
		clock.render(frame);
		return frame;
	}

	/** The colour of the first lit pixel, or 0 when the panel is dark. */
	private static int litColor(int[] frame)
	{
		for (int rgb : frame)
		{
			if (rgb != 0)
			{
				return rgb;
			}
		}
		return 0;
	}

	private static int count(int[] frame)
	{
		return count(frame, 0, 0, Frame.WIDTH, Frame.HEIGHT);
	}

	private static int count(int[] frame, int x, int y, int w, int h)
	{
		int lit = 0;
		for (int row = y; row < y + h; row++)
		{
			for (int col = x; col < x + w; col++)
			{
				if (Frame.get(frame, col, row) != 0)
				{
					lit++;
				}
			}
		}
		return lit;
	}
}
