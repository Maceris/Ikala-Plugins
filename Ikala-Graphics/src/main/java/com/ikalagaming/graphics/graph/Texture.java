package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A texture.
 */
@Slf4j
public class Texture {
	/**
	 * The OpenGL Texture ID.
	 */
	private int textureID;
	/**
	 * The path to the file for the texture, from the resource directory.
	 *
	 * @return The path to the file, which might be empty if the texture was
	 *         loaded from a buffer instead.
	 */
	@Getter
	private String texturePath;

	/**
	 * Load a texture directly from a byte buffer.
	 *
	 * @param width The width of the texture in pixels.
	 * @param height The height of the texture in pixels.
	 * @param buffer The buffer to load from.
	 */
	public Texture(int width, int height, @NonNull ByteBuffer buffer) {
		this.texturePath = "";
		this.generateTexture(width, height, buffer);
	}

	/**
	 * Create a new texture that is read from the provided file.
	 *
	 * @param texturePath The path to the file for the texture, including full
	 *            path.
	 * @throws RuntimeException If the texture could not be loaded.
	 */
	public Texture(@NonNull String texturePath) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			this.texturePath = texturePath;
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);

			ByteBuffer buffer =
				STBImage.stbi_load(texturePath, w, h, channels, 4);
			if (buffer == null) {
				String error =
					SafeResourceLoader.getString("TEXTURE_ERROR_LOADING",
						GraphicsPlugin.getResourceBundle());
				Texture.log.info(error, texturePath,
					STBImage.stbi_failure_reason());
				throw new TextureException(
					error.replaceFirst("\\{\\}", texturePath).replaceFirst(
						"\\{\\}", STBImage.stbi_failure_reason()));
			}

			int width = w.get();
			int height = h.get();

			this.generateTexture(width, height, buffer);

			STBImage.stbi_image_free(buffer);
		}
	}

	/**
	 * Bind the texture.
	 */
	public void bind() {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureID);
	}

	/**
	 * Clean up this texture.
	 */
	public void cleanup() {
		GL11.glDeleteTextures(this.textureID);
	}

	/**
	 * Create the a texture from a byte buffer.
	 *
	 * @param width The width of the texture in pixels.
	 * @param height The height of the texture in pixels.
	 * @param buffer The buffer that contains the texture information.
	 * @return The texture ID.
	 */
	private void generateTexture(int width, int height,
		@NonNull ByteBuffer buffer) {
		this.textureID = GL11.glGenTextures();

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureID);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
			GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
			GL11.GL_NEAREST);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
			GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
	}
}
