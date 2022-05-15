package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A texture.
 *
 * @author Ches Burks
 *
 */
@Slf4j
@Getter
public class Texture {

	/**
	 * The OpenGL Texture ID.
	 *
	 * @return The texture ID for this texture.
	 */
	private final int id;

	/**
	 * The width of the texture.
	 *
	 * @return The width in pixels.
	 */
	private final int width;

	/**
	 * The height of the texture.
	 *
	 * @return The height in pixels.
	 */
	private final int height;

	/**
	 * Create a new texture from a memory buffer.
	 *
	 * @param imageBuffer The image data.
	 * @throws TextureException If the texture could not be loaded.
	 */
	public Texture(ByteBuffer imageBuffer) {
		try {
			ByteBuffer buffer;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer w = stack.mallocInt(1);
				IntBuffer h = stack.mallocInt(1);
				IntBuffer channels = stack.mallocInt(1);

				buffer = STBImage.stbi_load_from_memory(imageBuffer, w, h,
					channels, 4);
				if (buffer == null) {
					throw new TextureException("Image file not loaded: "
						+ STBImage.stbi_failure_reason());
				}

				this.width = w.get();
				this.height = h.get();
			}

			this.id = this.createTexture(buffer);
			STBImage.stbi_image_free(buffer);
		}
		catch (TextureException e) {
			Texture.log
				.error(SafeResourceLoader.getString("TEXTURE_ERROR_CREATION",
					GraphicsPlugin.getResourceBundle()), e);
			throw e;
		}
	}

	/**
	 * Create a new texture that is read from the provided file.
	 *
	 * @param fileName The name of the file for the texture, including path from
	 *            resource dir.
	 * @throws TextureException If the texture could not be loaded.
	 */
	public Texture(String fileName) {
		try {
			File textureFile = PluginFolder.getResource(
				GraphicsPlugin.PLUGIN_NAME, ResourceType.DATA, fileName);
			if (!textureFile.canRead()) {
				Texture.log
					.warn(SafeResourceLoader.getString("TEXTURE_ERROR_MISSING",
						GraphicsPlugin.getResourceBundle()), fileName);
				throw new TextureException();
			}
			ByteBuffer buffer;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer w = stack.mallocInt(1);
				IntBuffer h = stack.mallocInt(1);
				IntBuffer channels = stack.mallocInt(1);
				buffer = STBImage.stbi_load(textureFile.getAbsolutePath(), w, h,
					channels, 4);
				if (buffer == null) {
					throw new TextureException("Image file [" + fileName
						+ "] not loaded: " + STBImage.stbi_failure_reason());
				}

				this.width = w.get();
				this.height = h.get();
			}

			this.id = this.createTexture(buffer);
			STBImage.stbi_image_free(buffer);
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

	/**
	 * Create the a texture from a byte buffer.
	 *
	 * @param buffer The buffer that contains the texture information.
	 * @return The texture ID.
	 */
	private int createTexture(ByteBuffer buffer) {
		// Create a new OpenGL texture
		int textureID = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		// Tell OpenGL how to unpack the RGBA bytes. Each component is 1 byte
		// size
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
			GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
			GL11.GL_NEAREST);

		final int LEVEL_OF_DETAIL = 0;
		final int BORDER = 0;// Must be 0
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, LEVEL_OF_DETAIL, GL11.GL_RGBA,
			this.width, this.height, BORDER, GL11.GL_RGBA,
			GL11.GL_UNSIGNED_BYTE, buffer);

		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

		return textureID;
	}

}
