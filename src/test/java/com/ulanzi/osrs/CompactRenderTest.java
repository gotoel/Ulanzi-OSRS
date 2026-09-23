package com.ulanzi.osrs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * The compact page is nothing but draw commands now, so what it is worth checking is that
 * every one of them lands on the panel, and clear of the icon when there is one.
 */
public class CompactRenderTest
{
	private static final int ICON = CompactLayout.ICON_WIDTH;
	/** AWTRIX's small font: capitals are five pixels tall. */
	private static final int CAP_HEIGHT = 5;

	@Test
	public void everyCommandStaysOnThePanelBesideAnIcon()
	{
		assertInBounds(render(page(true)), true);
	}

	@Test
	public void everyCommandStaysOnThePanelWithoutAnIcon()
	{
		assertInBounds(render(page(false)), false);
	}

	@Test
	public void theStripReachesBothEdges()
	{
		List<CompactLayout.Cell> cells = page(false);
		int left = CompactLayout.PANEL_WIDTH;
		int right = 0;
		for (CompactLayout.Cell cell : cells)
		{
			if (cell.showsBar())
			{
				left = Math.min(left, cell.stripX);
				right = Math.max(right, cell.stripX + cell.stripWidth);
			}
		}
		Assert.assertEquals(0, left);
		Assert.assertEquals(CompactLayout.PANEL_WIDTH, right);
	}

	@Test
	public void aLostReadingLeavesATrailAndAFullOneDoesNot()
	{
		Assert.assertTrue(hasGhostPixels(withGhost(40, 90)));
		Assert.assertFalse(hasGhostPixels(withGhost(40, -1)));
		// A trail is only ever behind the value, never ahead of it.
		Assert.assertFalse(hasGhostPixels(withGhost(90, 40)));
	}

	private boolean hasGhostPixels(List<CompactLayout.Cell> cells)
	{
		JsonArray draw = render(cells);
		Color dim = AwtrixClient.brighten(Color.WHITE, 0.5);
		String hex = AwtrixClient.toHex(dim);
		for (JsonElement element : draw)
		{
			JsonArray command = element.getAsJsonArray();
			String color = command.get(command.size() - 1).getAsString();
			if (hex.equals(color))
			{
				return true;
			}
		}
		return false;
	}

	private static List<CompactLayout.Cell> withGhost(int percent, int ghost)
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(new CompactLayout.Cell(StatKind.HITPOINTS, StatStyle.BOTH, "50", 2, percent, ghost,
			Color.WHITE, Color.WHITE));
		CompactLayout.place(cells, false);
		return cells;
	}

	/** The four stats as someone running them all would have them. */
	private static List<CompactLayout.Cell> page(boolean icon)
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(new CompactLayout.Cell(StatKind.HITPOINTS, StatStyle.BOTH, "99", 2, 100, -1,
			Color.GREEN, StatKind.HITPOINTS.getIdentity()));
		cells.add(new CompactLayout.Cell(StatKind.PRAYER, StatStyle.BOTH, "70", 2, 70, 85,
			Color.BLUE, StatKind.PRAYER.getIdentity()));
		cells.add(new CompactLayout.Cell(StatKind.ENERGY, StatStyle.BOTH, "100", 3, 100, -1,
			Color.YELLOW, StatKind.ENERGY.getIdentity()));
		cells.add(new CompactLayout.Cell(StatKind.SPEC, StatStyle.BAR, "50", 3, 50, -1,
			Color.ORANGE, StatKind.SPEC.getIdentity()));
		CompactLayout.fit(cells, icon, StatKind.HITPOINTS);
		CompactLayout.place(cells, icon);
		return cells;
	}

	private static void assertInBounds(JsonArray draw, boolean icon)
	{
		Assert.assertTrue("nothing was drawn", draw.size() > 0);
		int floor = icon ? ICON : 0;
		for (JsonElement element : draw)
		{
			JsonArray command = element.getAsJsonArray();
			String op = command.get(0).getAsString();
			int x = command.get(1).getAsInt();
			int y = command.get(2).getAsInt();
			Assert.assertTrue(op + " starts at " + x, x >= floor);
			Assert.assertTrue(op + " row " + y, y >= 0 && y < 8);
			if ("rectFill".equals(op))
			{
				int right = x + command.get(3).getAsInt();
				int bottom = y + command.get(4).getAsInt();
				Assert.assertTrue(op + " ends at " + right, right <= CompactLayout.PANEL_WIDTH);
				Assert.assertTrue(op + " ends on row " + bottom, bottom <= 8);
			}
			if ("text".equals(op))
			{
				// y is the top of the glyph, so five pixel capitals have to finish above
				// the strip. Getting this the wrong way up ran the digits off the panel.
				Assert.assertTrue("text top " + y, y >= 0);
				Assert.assertTrue("text bottom " + (y + CAP_HEIGHT), y + CAP_HEIGHT <= CompactLayout.STRIP_ROW);
			}
			if ("pixel".equals(op))
			{
				Assert.assertTrue(op + " at " + x, x < CompactLayout.PANEL_WIDTH);
			}
		}
	}

	private static JsonArray render(List<CompactLayout.Cell> cells)
	{
		AwtrixClient client = new AwtrixClient(new OkHttpClient(), new UlanziConfig()
		{
		}, new Gson());
		return client.compactDraw(cells);
	}
}
