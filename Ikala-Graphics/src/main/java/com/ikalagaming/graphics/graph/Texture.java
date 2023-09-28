/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
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
@Getter
public class Texture {
	/**
	 * The default texture to use if nothing is provided.
	 */
	private static final String DEFAULT_TEXTURE_PATH =
		PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME, ResourceType.DATA,
			"models/default/default_texture.png").getAbsolutePath();
	/**
	 * The default texture so that we have an easy reference.
	 */
	public static final Texture DEFAULT_TEXTURE =
		new Texture(Texture.DEFAULT_TEXTURE_PATH);

	/**
	 * The OpenGL Texture ID.
	 *
	 * @return The texture ID.
	 */
	private int textureID;

	/**
	 * The width of the texture that was loaded.
	 *
	 * @return The width in pixels.
	 */
	private int width;

	/**
	 * The height of the texture that was loaded.
	 *
	 * @return The height in pixels.
	 */
	private int height;

	/**
	 * Load a texture directly from a byte buffer.
	 *
	 * @param width The width of the texture in pixels.
	 * @param height The height of the texture in pixels.
	 * @param buffer The buffer to load from.
	 */
	public Texture(int width, int height, @NonNull ByteBuffer buffer) {
		this.width = width;
		this.height = height;
		this.generateTexture(buffer);
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

			this.width = w.get();
			this.height = h.get();

			this.generateTexture(buffer);

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
	 * @param buffer The buffer that contains the texture information.
	 */
	private void generateTexture(@NonNull ByteBuffer buffer) {
		this.textureID = GL11.glGenTextures();

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureID);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
			GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
			GL11.GL_NEAREST);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, this.width,
			this.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
	}
}
