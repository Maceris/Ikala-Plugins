package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * The buffer shadows are rendered onto.
 */
public class ShadowBuffer {
	/**
	 * The width of the shadow map in pixels.
	 */
	public static final int SHADOW_MAP_WIDTH = 4096;
	/**
	 * The height of the shadow map in pixels.
	 */
	public static final int SHADOW_MAP_HEIGHT = ShadowBuffer.SHADOW_MAP_WIDTH;
	/**
	 * The actual depth map textures used for the Frame Buffer Object.
	 *
	 * @return The shadow map textures we render to.
	 */
	@Getter
	private final ArrayOfTextures depthMap;
	/**
	 * The Frame Buffer Object ID.
	 *
	 * @return The FBO.
	 */
	@Getter
	private final int depthMapFBO;

	/**
	 * Create a shadow buffer.
	 *
	 * @throws RenderException if there is a problem setting up frame buffers.
	 */
	public ShadowBuffer() {
		// Create a FBO to render the depth map
		this.depthMapFBO = GL30.glGenFramebuffers();

		// Create the depth map textures
		this.depthMap =
			new ArrayOfTextures(CascadeShadow.SHADOW_MAP_CASCADE_COUNT,
				ShadowBuffer.SHADOW_MAP_WIDTH, ShadowBuffer.SHADOW_MAP_HEIGHT,
				GL11.GL_DEPTH_COMPONENT);

		// Attach the the depth map texture to the FBO
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.depthMapFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
			GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
			this.depthMap.getIDs()[0], 0);

		// Set only depth
		GL11.glDrawBuffer(GL11.GL_NONE);
		GL11.glReadBuffer(GL11.GL_NONE);

		if (GL30.glCheckFramebufferStatus(
			GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
			throw new RenderException(
				SafeResourceLoader.getString("FRAME_BUFFER_CREATION_FAIL",
					GraphicsPlugin.getResourceBundle()));
		}

		// Unbind
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	/**
	 * Bind the textures for the shadow maps.
	 *
	 * @param start The start value to set the active texture.
	 */
	public void bindTextures(int start) {
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL13.glActiveTexture(start + i);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.depthMap.getIDs()[i]);
		}
	}

	/**
	 * Clean up the associated buffers.
	 */
	public void cleanup() {
		GL30.glDeleteFramebuffers(this.depthMapFBO);
		this.depthMap.cleanup();
	}
}