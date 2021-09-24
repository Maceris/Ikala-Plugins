package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A texture.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Texture {

	/**
	 * The OpenGL Texture ID.
	 *
	 * @return The texture ID for this texture.
	 */
	@Getter
	private int id;

	/**
	 * The name of the texture resource.
	 *
	 * @param fileName The name of the file for the texture, including path from
	 *            resource dir.
	 */
	public Texture(String fileName) {
		File textureFile = PluginFolder.getResource(GraphicsPlugin.NAME,
			ResourceType.DATA, fileName);
		if (!textureFile.canRead()) {
			Texture.log
				.warn(SafeResourceLoader.getString("TEXTURE_ERROR_MISSING",
					GraphicsPlugin.getResourceBundle()), fileName);
			throw new TextureException();
		}
		InputStream inputStream;
		try {
			inputStream = new FileInputStream(textureFile);
		}
		catch (FileNotFoundException e) {
			Texture.log
				.warn(SafeResourceLoader.getString("TEXTURE_ERROR_MISSING",
					GraphicsPlugin.getResourceBundle()), fileName);
			throw new TextureException(e);
		}
		PNGDecoder decoder;
		ByteBuffer buffer;
		try {
			decoder = new PNGDecoder(inputStream);
			buffer = ByteBuffer
				.allocateDirect(4 * decoder.getWidth() * decoder.getHeight());
			decoder.decode(buffer, decoder.getWidth() * 4, Format.RGBA);
			buffer.flip();
		}
		catch (IOException e) {
			Texture.log
				.warn(SafeResourceLoader.getString("TEXTURE_ERROR_DECODING",
					GraphicsPlugin.getResourceBundle()), fileName);
			throw new TextureException(e);
		}

		// Create a new OpenGL texture
		this.id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.id);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		final int LEVEL_OF_DETAIL = 0;
		final int BORDER = 0;// Must be 0
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, LEVEL_OF_DETAIL, GL11.GL_RGBA,
			decoder.getWidth(), decoder.getHeight(), BORDER, GL11.GL_RGBA,
			GL11.GL_UNSIGNED_BYTE, buffer);

		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
	}

}
