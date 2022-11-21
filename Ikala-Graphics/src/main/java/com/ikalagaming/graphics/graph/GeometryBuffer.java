/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.Window;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * A buffer for the geometry pass that is used for deferred shading.
 */
@Getter
public class GeometryBuffer {
	/**
	 * The maximum number of textures, which are used for albedo, normals,
	 * specular, and depth, respectively.
	 */
	private static final int TOTAL_TEXTURES = 4;
	/**
	 * The frame buffer ID.
	 *
	 * @return The buffer ID.
	 */
	private int gBufferId;
	/**
	 * The height in pixels.
	 *
	 * @return The buffer (window) height.
	 */
	private int height;
	/**
	 * The textures for the buffer.
	 *
	 * @return The list of texture IDs.
	 */
	private int[] textureIDs;
	/**
	 * The width in pixels.
	 *
	 * @return The buffer (window) width.
	 */
	private int width;

	/**
	 * Create a new geometry buffer for the given window.
	 *
	 * @param window Window information to pull size details from.
	 */
	public GeometryBuffer(@NonNull Window window) {
		this.gBufferId = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.gBufferId);

		this.textureIDs = new int[GeometryBuffer.TOTAL_TEXTURES];
		GL11.glGenTextures(this.textureIDs);

		this.width = window.getWidth();
		this.height = window.getHeight();

		for (int i = 0; i < GeometryBuffer.TOTAL_TEXTURES; ++i) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureIDs[i]);
			int attachmentType;
			if (i == GeometryBuffer.TOTAL_TEXTURES - 1) {
				GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,
					GL30.GL_DEPTH_COMPONENT32F, this.width, this.height, 0,
					GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
				attachmentType = GL30.GL_DEPTH_ATTACHMENT;
			}
			else {
				GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F,
					this.width, this.height, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
					(ByteBuffer) null);
				attachmentType = GL30.GL_COLOR_ATTACHMENT0 + i;
			}
			GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
				GL11.GL_NEAREST);
			GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
				GL11.GL_NEAREST);

			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachmentType,
				GL11.GL_TEXTURE_2D, this.textureIDs[i], 0);
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer intBuff = stack.mallocInt(GeometryBuffer.TOTAL_TEXTURES);
			for (int i = 0; i < GeometryBuffer.TOTAL_TEXTURES; ++i) {
				intBuff.put(i, GL30.GL_COLOR_ATTACHMENT0 + i);
			}
			GL20.glDrawBuffers(intBuff);
		}

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	/**
	 * Clean up the frame buffers and textures.
	 */
	public void cleanUp() {
		GL30.glDeleteFramebuffers(this.gBufferId);
		Arrays.stream(this.textureIDs).forEach(GL11::glDeleteTextures);
	}
}