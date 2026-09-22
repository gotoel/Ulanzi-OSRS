package com.ulanzi.osrs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class CompactLayoutTest
{
	@Test
	public void theDefaultThreeValuesFitTheBarePanel()
	{
		List<CompactLayout.Cell> cells = defaults();
		CompactLayout.fit(cells, false, StatKind.HITPOINTS);
		for (CompactLayout.Cell cell : cells)
		{
			Assert.assertEquals(cell.kind.name(), StatStyle.VALUE, cell.style);
		}
		// Two 2-digit slots and one 3-digit, a blank column between each, and the last
		// slot's own trailing column is not part of the line: 8 + 8 + 12 + 2 - 1.
		Assert.assertEquals(29, CompactLayout.lineWidth(cells));
		Assert.assertTrue(CompactLayout.fits(cells, false));
	}

	@Test
	public void anIconPushesTheRightmostValueOntoTheStrip()
	{
		List<CompactLayout.Cell> cells = defaults();
		CompactLayout.fit(cells, true, StatKind.HITPOINTS);
		Assert.assertEquals(StatStyle.VALUE, cells.get(0).style);
		Assert.assertEquals(StatStyle.VALUE, cells.get(1).style);
		Assert.assertEquals(StatStyle.BAR, cells.get(2).style);
		Assert.assertTrue(CompactLayout.fits(cells, true));
	}

	@Test
	public void theKeptStatIsTheLastToGiveUpItsDigits()
	{
		List<CompactLayout.Cell> cells = defaults();
		CompactLayout.fit(cells, true, StatKind.ENERGY);
		Assert.assertEquals(StatStyle.VALUE, cells.get(0).style);
		Assert.assertEquals(StatStyle.BAR, cells.get(1).style);
		Assert.assertEquals(StatStyle.VALUE, cells.get(2).style);
		Assert.assertTrue(CompactLayout.fits(cells, true));
	}

	@Test
	public void anAfkLabelTakesRoomFromTheValuesAndNeverFromItself()
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(CompactLayout.Cell.label("AFK", Color.WHITE));
		cells.addAll(defaults());
		CompactLayout.fit(cells, true, StatKind.HITPOINTS);
		Assert.assertEquals(StatStyle.VALUE, cells.get(0).style);
		Assert.assertEquals(StatStyle.VALUE, cells.get(1).style);
		Assert.assertEquals(StatStyle.BAR, cells.get(2).style);
		Assert.assertEquals(StatStyle.BAR, cells.get(3).style);
		Assert.assertTrue(CompactLayout.fits(cells, true));
	}

	/**
	 * The point of the whole layout: a slot is sized by what the stat can reach, so the
	 * line does not move when a number loses a digit.
	 */
	@Test
	public void aValueLosingADigitMovesNothing()
	{
		List<CompactLayout.Cell> full = defaults();
		CompactLayout.place(full, false);

		List<CompactLayout.Cell> dropped = new ArrayList<>();
		dropped.add(cell(StatKind.HITPOINTS, StatStyle.VALUE, 9, 9, 99));
		dropped.add(cell(StatKind.PRAYER, StatStyle.VALUE, 7, 10, 70));
		dropped.add(cell(StatKind.ENERGY, StatStyle.VALUE, 8, 8, 100));
		CompactLayout.place(dropped, false);

		for (int i = 0; i < full.size(); i++)
		{
			Assert.assertEquals("slot " + i, full.get(i).x, dropped.get(i).x);
			Assert.assertEquals("width " + i, full.get(i).width, dropped.get(i).width);
		}
		// The ones column holds still; only the empty leading columns are given up.
		Assert.assertEquals(full.get(0).x + CompactLayout.CHAR_WIDTH, CompactLayout.textX(dropped.get(0)));
		Assert.assertEquals(full.get(2).x + 2 * CompactLayout.CHAR_WIDTH, CompactLayout.textX(dropped.get(2)));
	}

	@Test
	public void valuesAreRightAlignedInTheirSlot()
	{
		List<CompactLayout.Cell> cells = defaults();
		CompactLayout.place(cells, false);
		Assert.assertEquals(cells.get(0).x, CompactLayout.textX(cells.get(0)));
		Assert.assertEquals(cells.get(2).x, CompactLayout.textX(cells.get(2)));
	}

	@Test
	public void barsShareTheWholeWidthBesideAnIcon()
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(cell(StatKind.HITPOINTS, StatStyle.BAR, 99, 100, 99));
		cells.add(cell(StatKind.PRAYER, StatStyle.BAR, 70, 70, 70));
		CompactLayout.place(cells, true);

		Assert.assertEquals(CompactLayout.ICON_WIDTH, cells.get(0).stripX);
		Assert.assertEquals(20, cells.get(1).stripX);
		// A blank column between the two, and the second runs to the panel edge.
		Assert.assertEquals(11, cells.get(0).stripWidth);
		Assert.assertEquals(12, cells.get(1).stripWidth);
		Assert.assertEquals(CompactLayout.PANEL_WIDTH,
			cells.get(1).stripX + cells.get(1).stripWidth);
	}

	@Test
	public void aLoneBarTakesTheWholeRow()
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(cell(StatKind.HITPOINTS, StatStyle.BAR, 50, 100, 99));
		CompactLayout.place(cells, false);
		Assert.assertEquals(0, cells.get(0).stripX);
		Assert.assertEquals(CompactLayout.PANEL_WIDTH, cells.get(0).stripWidth);
	}

	/**
	 * A stat that has given up its digits still costs nothing across the line, which is
	 * what the old right-edge columns could never do.
	 */
	@Test
	public void aBarTakesNoRoomFromTheLine()
	{
		List<CompactLayout.Cell> cells = defaults();
		int before = CompactLayout.lineWidth(cells);
		cells.get(2).style = StatStyle.BAR;
		Assert.assertEquals(before - CompactLayout.CHAR_WIDTH * 3 - 1, CompactLayout.lineWidth(cells));
	}

	@Test
	public void aBoostedLevelGetsAThirdDigit()
	{
		Assert.assertEquals(2, StatKind.HITPOINTS.digitsFor(99));
		Assert.assertEquals(3, StatKind.HITPOINTS.digitsFor(117));
		Assert.assertEquals(3, StatKind.ENERGY.digitsFor(100));
		Assert.assertEquals(3, StatKind.ENERGY.digitsFor(4));
	}

	private static List<CompactLayout.Cell> defaults()
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(cell(StatKind.HITPOINTS, StatStyle.VALUE, 99, 100, 99));
		cells.add(cell(StatKind.PRAYER, StatStyle.VALUE, 70, 100, 70));
		cells.add(cell(StatKind.ENERGY, StatStyle.VALUE, 100, 100, 100));
		return cells;
	}

	/**
	 * The ceiling is the largest the stat can read, which is what sizes the slot. It does
	 * not follow the value down, which is the whole reason the line holds still.
	 */
	private static CompactLayout.Cell cell(StatKind kind, StatStyle style, int value, int percent, int ceiling)
	{
		return new CompactLayout.Cell(kind, style, String.valueOf(value),
			kind.digitsFor(Math.max(ceiling, value)), percent, -1, Color.WHITE, kind.getIdentity());
	}
}
