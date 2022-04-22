package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
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
	 * Load a texture from file name.
	 *
	 * @param fileName The name of the file for the texture, including path from
	 *            resource dir.
	 * @return The ID of the newly created texture.
	 * @throws TextureException If there was a problem loading the texture.
	 */
	private static int loadTexture(String fileName) {
		File textureFile = PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
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
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		final int LEVEL_OF_DETAIL = 0;
		final int BORDER = 0;// Must be 0
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, LEVEL_OF_DETAIL, GL11.GL_RGBA,
			decoder.getWidth(), decoder.getHeight(), BORDER, GL11.GL_RGBA,
			GL11.GL_UNSIGNED_BYTE, buffer);

		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		return id;
	}

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
	 * @throws TextureException If the texture could not be loaded.
	 */
	public Texture(String fileName) {
		try {
			this.id = Texture.loadTexture(fileName);
		}
		catch (TextureException e) {
			Texture.log
				.error(SafeResourceLoader.getString("TEXTURE_ERROR_CREATION",
					GraphicsPlugin.getResourceBundle()), e);
			throw e;
		}
	}

	/**
	 * Bind the texture.
	 */
	public void bind() {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.id);
	}

	/**
	 * Clean up this texture.
	 */
	public void cleanup() {
		GL11.glDeleteTextures(this.id);
	}

}
