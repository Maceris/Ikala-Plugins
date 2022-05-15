package com.ikalagaming.graphics.graph;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Used to dynamically generate a texture that contains characters for a font.
 *
 * @author Ches Burks
 *
 */
public class FontTexture {
	/**
	 * Information about where a specific character appears in the texture.
	 */
	@Getter
	@AllArgsConstructor
	public static class CharInfo {
		/**
		 * The start x position.
		 *
		 * @param startX The x coordinate of where the character starts.
		 * @return The x coordinate of where the character starts.
		 */
		private final int startX;
		/**
		 * The width of the character.
		 *
		 * @param width The width of the character.
		 * @return The width of the character.
		 */
		private final int width;
	}

	/**
	 * The file format of the image.
	 */
	private static final String IMAGE_FORMAT = "png";

	/**
	 * How many pixels to pad the characters by.
	 */
	private static final int CHAR_PADDING = 2;

	/**
	 * The font to use.
	 */
	private final Font font;

	/**
	 * The name of the character set.
	 */
	private final String charSetName;

	/**
	 * Stores information about each character so we can find each letter in
	 * non-monospace fonts.
	 */
	private final Map<Character, CharInfo> charMap;

	/**
	 * The generated texture.
	 *
	 * @return The generated texture.
	 */
	@Getter
	private Texture texture;

	/**
	 * The standard height of a line of text in this font.
	 *
	 * @return The standard height of a line of text in this font.
	 */
	@Getter
	private int height;

	/**
	 * The total width of this line of text.
	 *
	 * @return The width of the generated line of text.
	 */
	@Getter
	private int width;

	/**
	 * Create a new texture for the given font and character set.
	 *
	 * @param font The font to use.
	 * @param charSetName The character set to use, which the list of available
	 *            characters is pulled from.
	 * @throws IOException If there is an error generating the texture.
	 */
	public FontTexture(Font font, String charSetName) throws IOException {
		this.font = font;
		this.charSetName = charSetName;
		this.charMap = new HashMap<>();

		this.buildTexture();
	}

	/**
	 * Build the texture based on the font and character set.
	 *
	 * @throws IOException If there is an error generating the texture.
	 */
	private void buildTexture() throws IOException {
		/*
		 * Get the font metrics for each character for the selected font using
		 * an image
		 */
		BufferedImage image =
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2D = image.createGraphics();
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		g2D.setFont(this.font);
		FontMetrics fontMetrics = g2D.getFontMetrics();

		String allChars = this.getAllAvailableChars(this.charSetName);
		this.width = 0;
		this.height = fontMetrics.getHeight();
		for (char c : allChars.toCharArray()) {
			// Get the size for each character and update global image size
			CharInfo charInfo =
				new CharInfo(this.width, fontMetrics.charWidth(c));
			this.charMap.put(c, charInfo);
			this.width += charInfo.getWidth() + FontTexture.CHAR_PADDING;
		}
		g2D.dispose();

		// Create the image associated to the charset
		image = new BufferedImage(this.width, this.height,
			BufferedImage.TYPE_INT_ARGB);
		g2D = image.createGraphics();
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		g2D.setFont(this.font);
		fontMetrics = g2D.getFontMetrics();
		g2D.setColor(Color.WHITE);
		int startX = 0;
		for (char c : allChars.toCharArray()) {
			CharInfo charInfo = this.charMap.get(c);
			g2D.drawString("" + c, startX, fontMetrics.getAscent());
			startX += charInfo.getWidth() + FontTexture.CHAR_PADDING;
		}
		g2D.dispose();

		ByteBuffer buf = null;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, FontTexture.IMAGE_FORMAT, out);
			out.flush();
			byte[] data = out.toByteArray();
			buf = ByteBuffer.allocateDirect(data.length);
			buf.put(data, 0, data.length);
			buf.flip();
		}
		this.texture = new Texture(buf);
	}

	/**
	 * Grab a list of all available characters for the given character set.
	 *
	 * @param charsetName The name of the character set.
	 * @return The list of all characters for the given set.
	 */
	private String getAllAvailableChars(String charsetName) {
		CharsetEncoder ce = Charset.forName(charsetName).newEncoder();
		StringBuilder result = new StringBuilder();
		for (char c = 0; c < Character.MAX_VALUE; c++) {
			if (ce.canEncode(c)) {
				result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Get information about where to find the given character in the texture.
	 *
	 * @param c The character we are looking for.
	 * @return Information about the location and size of the given character.
	 */
	public CharInfo getCharInfo(char c) {
		return this.charMap.get(c);
	}
}
