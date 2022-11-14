package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A texture.
 */
@Slf4j
@Getter
public class Texture {

	/**
	 * The OpenGL Texture ID.
	 *
	 * @return The texture ID for this texture.
	 */
	private int id;
	/**
	 * The path to the file for the texture, from the resource directory.
	 * 
	 * @return The path to the file, which might be empty if the texture was
	 *         loaded from a buffer instead.
	 */
	private final String texturePath;

	/**
	 * Load a texture directly from a byte buffer.
	 * 
	 * @param width The width of the texture in pixels.
	 * @param height The height of the texture in pixels.
	 * @param buf The buffer to load from.
	 */
	public Texture(int width, int height, ByteBuffer buf) {
		this.texturePath = "";
		createTexture(width, height, buf);
	}

	/**
	 * Create a new texture that is read from the provided file.
	 *
	 * @param texturePath The path to the file for the texture, including full
	 *            path.
	 * @throws TextureException If the texture could not be loaded.
	 */
	public Texture(String texturePath) {
		this.texturePath = texturePath;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);
			ByteBuffer buffer =
				STBImage.stbi_load(texturePath, w, h, channels, 4);
			if (buffer == null) {
				throw new TextureException("Image file [" + texturePath
					+ "] not loaded: " + STBImage.stbi_failure_reason());
			}

			int width = w.get();
			int height = h.get();
			this.id = this.createTexture(width, height, buffer);
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
		glBindTexture(GL_TEXTURE_2D, this.id);
	}

	/**
	 * Clean up this texture.
	 */
	public void cleanup() {
		glDeleteTextures(this.id);
	}

	/**
	 * Create the a texture from a byte buffer.
	 * 
	 * @param width The width of the texture in pixels.
	 * @param height The height of the texture in pixels.
	 * @param buffer The buffer that contains the texture information.
	 * @return The texture ID.
	 */
	private int createTexture(int width, int height, ByteBuffer buffer) {
		// Create a new OpenGL texture
		int textureID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureID);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

		// Tell OpenGL how to unpack the RGBA bytes. Each component is 1 byte
		// size
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		final int LEVEL_OF_DETAIL = 0;
		final int BORDER = 0;// Must be 0
		glTexImage2D(GL_TEXTURE_2D, LEVEL_OF_DETAIL, GL_RGBA, width, height,
			BORDER, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		glGenerateMipmap(GL_TEXTURE_2D);

		return textureID;
	}

}
