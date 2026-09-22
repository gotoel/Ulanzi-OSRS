package com.ulanzi.osrs;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * An XP drop sliding up or down beside the icon, as an animated GIF the clock plays itself,
 * since AWTRIX only scrolls text sideways. Glyphs copy the clock's 3x5 small font,
 * resting on the same rows as the page text.
 */
final class XpDropGif
{
	static final int WIDTH = 23;
	static final int HEIGHT = 8;
	static final int MOVE_MS = 60;
	static final int HOLD_MS = 500;
	private static final int TAIL_MS = 1_000;
	private static final int REST_Y = 2;
	private static final int GLYPH_HEIGHT = 5;
	private static final int FIRST_Y = 1 - GLYPH_HEIGHT;
	private static final int LAST_Y = HEIGHT - 1;
	private static final int ADVANCE = 4;
	private static final int SPACE_ADVANCE = 2;
	private static final Map<Character, String[]> GLYPHS = new HashMap<>();

	static
	{
		glyph('0', "###", "#.#", "#.#", "#.#", "###");
		glyph('1', ".#.", "##.", ".#.", ".#.", "###");
		glyph('2', "###", "..#", "###", "#..", "###");
		glyph('3', "###", "..#", ".##", "..#", "###");
		glyph('4', "#.#", "#.#", "###", "..#", "..#");
		glyph('5', "###", "#..", "###", "..#", "###");
		glyph('6', "###", "#..", "###", "#.#", "###");
		glyph('7', "###", "..#", ".#.", ".#.", ".#.");
		glyph('8', "###", "#.#", "###", "#.#", "###");
		glyph('9', "###", "#.#", "###", "..#", "###");
		glyph('+', "...", ".#.", "###", ".#.", "...");
		glyph('x', "#.#", "#.#", ".#.", "#.#", "#.#");
		glyph('p', "##.", "#.#", "##.", "#..", "#..");
	}

	private XpDropGif()
	{
	}

	/**
	 * Same measure as the clock's small font: 4px per glyph, 2px per space, no trailing column.
	 */
	static int textWidth(String text)
	{
		int width = 0;
		for (char c : text.toCharArray())
		{
			width += c == ' ' ? SPACE_ADVANCE : ADVANCE;
		}
		return Math.max(0, width - 1);
	}

	/**
	 * Top rows of the glyphs, frame by frame: in from one edge, a pause where page text sits, out the other.
	 */
	static List<Integer> path(boolean upward)
	{
		List<Integer> path = new ArrayList<>();
		for (int step = 0; step <= LAST_Y - FIRST_Y; step++)
		{
			path.add(upward ? LAST_Y - step : FIRST_Y + step);
		}
		return path;
	}

	/**
	 * How long the text is on screen. A blank frame follows, so a clock that loops GIFs shows nothing extra.
	 */
	static long visibleMs()
	{
		return (long) (path(true).size() - 1) * MOVE_MS + HOLD_MS;
	}

	static String encode(String text, Color color, boolean upward)
	{
		byte[] reds = {0, (byte) color.getRed()};
		byte[] greens = {0, (byte) color.getGreen()};
		byte[] blues = {0, (byte) color.getBlue()};
		IndexColorModel palette = new IndexColorModel(1, 2, reds, greens, blues);
		int x = Math.max(0, (WIDTH - textWidth(text)) / 2);

		ImageWriter writer = gifWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(stream);
			writer.prepareWriteSequence(null);
			for (int y : path(upward))
			{
				BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_BINARY, palette);
				drawText(frame, text, x, y, color);
				writeFrame(writer, frame, y == REST_Y ? HOLD_MS : MOVE_MS);
			}
			writeFrame(writer, new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_BINARY, palette), TAIL_MS);
			writer.endWriteSequence();
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Failed to encode XP drop", ex);
		}
		finally
		{
			writer.dispose();
		}
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}

	private static void drawText(BufferedImage frame, String text, int x, int y, Color color)
	{
		int rgb = color.getRGB();
		for (char c : text.toCharArray())
		{
			if (c == ' ')
			{
				x += SPACE_ADVANCE;
				continue;
			}
			String[] rows = GLYPHS.get(Character.toLowerCase(c));
			if (rows != null)
			{
				for (int row = 0; row < GLYPH_HEIGHT; row++)
				{
					for (int col = 0; col < rows[row].length(); col++)
					{
						int px = x + col;
						int py = y + row;
						if (rows[row].charAt(col) == '#' && px >= 0 && px < WIDTH && py >= 0 && py < HEIGHT)
						{
							frame.setRGB(px, py, rgb);
						}
					}
				}
			}
			x += ADVANCE;
		}
	}

	private static void writeFrame(ImageWriter writer, BufferedImage frame, int delayMs) throws IOException
	{
		IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), null);
		String format = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
		IIOMetadataNode control = child(root, "GraphicControlExtension");
		control.setAttribute("disposalMethod", "none");
		control.setAttribute("userInputFlag", "FALSE");
		control.setAttribute("transparentColorFlag", "FALSE");
		control.setAttribute("transparentColorIndex", "0");
		control.setAttribute("delayTime", String.valueOf(delayMs / 10));
		metadata.setFromTree(format, root);
		writer.writeToSequence(new IIOImage(frame, null, metadata), null);
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name)
	{
		for (int i = 0; i < root.getLength(); i++)
		{
			if (name.equalsIgnoreCase(root.item(i).getNodeName()))
			{
				return (IIOMetadataNode) root.item(i);
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}

	private static ImageWriter gifWriter()
	{
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
		if (!writers.hasNext())
		{
			throw new IllegalStateException("GIF writer is not available");
		}
		return writers.next();
	}

	private static void glyph(char c, String... rows)
	{
		GLYPHS.put(c, rows);
	}
}
