package com.ulanzi.osrs;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

final class SkillActivityIcons
{
	private SkillActivityIcons()
	{
	}

	static String gif(String[] rows)
	{
		if (rows.length != 8)
		{
			throw new IllegalArgumentException("Icon must be 8 rows");
		}

		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 8; y++)
		{
			String row = rows[y];
			if (row.length() != 8)
			{
				throw new IllegalArgumentException("Icon row must be 8 pixels: " + row);
			}
			for (int x = 0; x < 8; x++)
			{
				image.setRGB(x, y, color(row.charAt(x)));
			}
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			if (!ImageIO.write(image, "gif", out))
			{
				throw new IllegalStateException("GIF writer is not available");
			}
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Failed to encode activity icon", ex);
		}

		String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
		if (encoded.length() <= 64)
		{
			throw new IllegalStateException("Activity icon must be inline image data");
		}
		return encoded;
	}

	private static int color(char pixel)
	{
		switch (pixel)
		{
			case '.':
				return 0x000000;
			case 'G':
				return 0x3CB43C;
			case 'g':
				return 0x1B5E20;
			case 'T':
				return 0x8B5A2B;
			case 't':
				return 0x5A3A1A;
			case 'L':
				return 0xC8905A;
			case 'B':
				return 0x3D7EFF;
			case 'b':
				return 0x1E3C9E;
			case 'F':
				return 0x9CC0E0;
			case 'W':
				return 0xFFFFFF;
			case 'E':
				return 0xFFF1B8;
			case 'O':
				return 0xFF8C1A;
			case 'R':
				return 0xE53935;
			case 'Y':
				return 0xFFD54A;
			case 'A':
				return 0xC8961E;
			case 'S':
				return 0xC8C8C8;
			case 's':
				return 0x707070;
			case 'M':
				return 0x8C84A8;
			default:
				throw new IllegalArgumentException("Unknown icon pixel '" + pixel + "'");
		}
	}
}
