package com.ikalagaming.graphics.graph;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

/**
 * An array of textures for rendering cascaded shadow maps.
 */
@Getter
public class ArrayOfTextures {

	/**
	 * The list of all texture IDs.
	 *
	 * @return The list of all texture IDs.
	 */
	private final int[] IDs;

	/**
	 * Create an array of textures.
	 *
	 * @param numTextures The number of textures to create.
	 * @param width The width of each texture in pixels.
	 * @param height The height of each texture in pixels.
	 * @param pixelFormat The pixel format to use for the textures for
	 *            glTexImage2Ds.
	 */
	public ArrayOfTextures(int numTextures, int width, int height,
		int pixelFormat) {
		this.IDs = new int[numTextures];
		GL11.glGenTextures(this.IDs);

		for (int i = 0; i < numTextures; ++i) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.IDs[i]);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT,
				width, height, 0, pixelFormat, GL11.GL_FLOAT,
				(ByteBuffer) null);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
				GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
				GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
				GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
				GL12.GL_CLAMP_TO_EDGE);
		}
	}

	/**
	 * Clean up all the textures.
	 */
	public void cleanup() {
		for (int id : this.IDs) {
			GL11.glDeleteTextures(id);
		}
	}
}
