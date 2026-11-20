package com.ulanzi.osrs;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Node;

/**
 * An icon as the virtual clock draws it: a base64 GIF (or JPEG), with or without a
 * "data:image/...;base64," prefix, decoded into whole frames and their timings. Animated
 * GIFs loop, the way the clock plays them.
 */
@Slf4j
final class GifIcon
{
	private static final int CACHE_SIZE = 160;
	private static final int DEFAULT_DELAY_MS = 100;
	private static final int MIN_DELAY_MS = 20;

	private static final Map<String, GifIcon> CACHE = new LinkedHashMap<String, GifIcon>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, GifIcon> eldest)
		{
			return size() > CACHE_SIZE;
		}
	};
	private static final GifIcon NONE = new GifIcon(new BufferedImage[0], new int[0]);

	final int width;
	final int height;
	private final BufferedImage[] frames;
	private final int[] delays;
	private final int totalMs;

	private GifIcon(BufferedImage[] frames, int[] delays)
	{
		this.frames = frames;
		this.delays = delays;
		this.width = frames.length == 0 ? 0 : frames[0].getWidth();
		this.height = frames.length == 0 ? 0 : frames[0].getHeight();
		int total = 0;
		for (int delay : delays)
		{
			total += delay;
		}
		this.totalMs = total;
	}

	/** The decoded icon, or null for anything that is not image data. */
	static GifIcon of(String data)
	{
		if (data == null || data.isEmpty())
		{
			return null;
		}
		GifIcon icon;
		synchronized (CACHE)
		{
			icon = CACHE.get(data);
		}
		if (icon == null)
		{
			icon = decode(data);
			synchronized (CACHE)
			{
				CACHE.put(data, icon);
			}
		}
		return icon == NONE ? null : icon;
	}

	/** The frame showing this far into the animation. */
	BufferedImage frameAt(long elapsedMs)
	{
		if (frames.length == 1 || totalMs <= 0)
		{
			return frames[0];
		}
		long t = Math.floorMod(elapsedMs, (long) totalMs);
		for (int i = 0; i < frames.length; i++)
		{
			t -= delays[i];
			if (t < 0)
			{
				return frames[i];
			}
		}
		return frames[frames.length - 1];
	}

	/** Draws the frame showing at this time, its top-left at (x, y). Transparent pixels are skipped. */
	void draw(int[] frame, int x, int y, long elapsedMs)
	{
		BufferedImage image = frameAt(elapsedMs);
		for (int row = 0; row < image.getHeight(); row++)
		{
			for (int col = 0; col < image.getWidth(); col++)
			{
				int argb = image.getRGB(col, row);
				if ((argb >>> 24) < 128)
				{
					continue;
				}
				Frame.set(frame, x + col, y + row, argb);
			}
		}
	}

	private static GifIcon decode(String data)
	{
		String base64 = data;
		int comma = data.indexOf(',');
		if (data.startsWith("data:") && comma > 0)
		{
			base64 = data.substring(comma + 1);
		}
		byte[] bytes;
		try
		{
			bytes = Base64.getDecoder().decode(base64.trim());
		}
		catch (IllegalArgumentException ex)
		{
			// An icon id rather than data: the virtual clock has no icon store to look it up in.
			return NONE;
		}

		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
		{
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext())
			{
				return NONE;
			}
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(input, false);
				if (!"gif".equalsIgnoreCase(reader.getFormatName()))
				{
					BufferedImage still = reader.read(0);
					return still == null ? NONE : new GifIcon(new BufferedImage[] {argb(still)}, new int[] {0});
				}
				return decodeGif(reader);
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex)
		{
			log.debug("Could not decode an icon for the virtual clock", ex);
			return NONE;
		}
	}

	/**
	 * Composites each GIF frame onto the canvas the way a player would, so frames that only
	 * redraw part of the picture still come out whole.
	 */
	private static GifIcon decodeGif(ImageReader reader) throws IOException
	{
		int count = reader.getNumImages(true);
		if (count <= 0)
		{
			return NONE;
		}
		int canvasWidth = -1;
		int canvasHeight = -1;
		IIOMetadata stream = reader.getStreamMetadata();
		if (stream != null)
		{
			Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			canvasWidth = intAttribute(screen, "logicalScreenWidth", -1);
			canvasHeight = intAttribute(screen, "logicalScreenHeight", -1);
		}
		if (canvasWidth <= 0 || canvasHeight <= 0)
		{
			BufferedImage first = reader.read(0);
			canvasWidth = first.getWidth();
			canvasHeight = first.getHeight();
		}

		BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			BufferedImage image = reader.read(i);
			Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
			Node descriptor = child(tree, "ImageDescriptor");
			Node control = child(tree, "GraphicControlExtension");
			int left = intAttribute(descriptor, "imageLeftPosition", 0);
			int top = intAttribute(descriptor, "imageTopPosition", 0);
			int delay = intAttribute(control, "delayTime", -1) * 10;
			String disposal = control == null ? "none" : attribute(control, "disposalMethod");

			BufferedImage previous = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
			Graphics2D g = canvas.createGraphics();
			g.drawImage(image, left, top, null);
			g.dispose();
			frames.add(copy(canvas));
			delays.add(delay < 0 ? DEFAULT_DELAY_MS : Math.max(MIN_DELAY_MS, delay));

			if ("restoreToBackgroundColor".equals(disposal))
			{
				Graphics2D clear = canvas.createGraphics();
				clear.setComposite(java.awt.AlphaComposite.Clear);
				clear.fillRect(left, top, image.getWidth(), image.getHeight());
				clear.dispose();
			}
			else if (previous != null)
			{
				canvas = previous;
			}
		}

		int[] delayArray = new int[delays.size()];
		for (int i = 0; i < delayArray.length; i++)
		{
			delayArray[i] = delays.get(i);
		}
		return new GifIcon(frames.toArray(new BufferedImage[0]), delayArray);
	}

	private static BufferedImage argb(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	private static BufferedImage copy(BufferedImage image)
	{
		return argb(image);
	}

	private static Node child(Node node, String name)
	{
		if (node == null)
		{
			return null;
		}
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (name.equals(child.getNodeName()))
			{
				return child;
			}
		}
		return null;
	}

	private static String attribute(Node node, String name)
	{
		if (node == null || node.getAttributes() == null)
		{
			return null;
		}
		Node value = node.getAttributes().getNamedItem(name);
		return value == null ? null : value.getNodeValue();
	}

	private static int intAttribute(Node node, String name, int fallback)
	{
		String value = attribute(node, name);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}
}
