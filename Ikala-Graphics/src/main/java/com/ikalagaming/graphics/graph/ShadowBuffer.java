/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;

/** The buffer shadows are rendered onto. */
public class ShadowBuffer {
    /** The width of the shadow map in pixels. */
    public static final int SHADOW_MAP_WIDTH = 4096;

    /** The height of the shadow map in pixels. */
    public static final int SHADOW_MAP_HEIGHT = ShadowBuffer.SHADOW_MAP_WIDTH;

    /**
     * The actual depth map textures used for the Frame Buffer Object.
     *
     * @return The shadow map textures we render to.
     */
    @Getter private final ArrayOfTextures depthMap;

    /**
     * The Frame Buffer Object ID.
     *
     * @return The FBO.
     */
    @Getter private final int depthMapFBO;

    /**
     * Create a shadow buffer.
     *
     * @throws RenderException if there is a problem setting up frame buffers.
     */
    public ShadowBuffer() {
        // Create a FBO to render the depth map
        depthMapFBO = glGenFramebuffers();

        // Create the depth map textures
        depthMap =
                new ArrayOfTextures(
                        CascadeShadow.SHADOW_MAP_CASCADE_COUNT,
                        ShadowBuffer.SHADOW_MAP_WIDTH,
                        ShadowBuffer.SHADOW_MAP_HEIGHT,
                        GL_DEPTH_COMPONENT,
                        GL_CLAMP_TO_BORDER);

        // Attach the the depth map texture to the FBO
        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMap.getIDs()[0], 0);

        // Set only depth
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RenderException(
                    SafeResourceLoader.getString(
                            "FRAME_BUFFER_CREATION_FAIL", GraphicsPlugin.getResourceBundle()));
        }

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Bind the textures for the shadow maps.
     *
     * @param start The start value to set the active texture.
     */
    public void bindTextures(int start) {
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glActiveTexture(start + i);
            glBindTexture(GL_TEXTURE_2D, depthMap.getIDs()[i]);
        }
    }

    /** Clean up the associated buffers. */
    public void cleanup() {
        glDeleteFramebuffers(depthMapFBO);
        depthMap.cleanup();
    }
}
