/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterf;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

import com.ikalagaming.graphics.Window;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/** A buffer for the geometry pass that is used for deferred shading. */
@Getter
public class GBuffer {
    /**
     * The maximum number of textures, which are used for albedo, normals, specular, and depth,
     * respectively.
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
    public GBuffer(@NonNull Window window) {
        gBufferId = glGenFramebuffers();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, gBufferId);

        textureIDs = new int[GBuffer.TOTAL_TEXTURES];
        glGenTextures(textureIDs);

        width = window.getWidth();
        height = window.getHeight();

        for (int i = 0; i < GBuffer.TOTAL_TEXTURES; ++i) {
            glBindTexture(GL_TEXTURE_2D, textureIDs[i]);
            int attachmentType;
            if (i == GBuffer.TOTAL_TEXTURES - 1) {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_DEPTH_COMPONENT32F,
                        width,
                        height,
                        0,
                        GL_DEPTH_COMPONENT,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_DEPTH_ATTACHMENT;
            } else {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA32F,
                        width,
                        height,
                        0,
                        GL_RGBA,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_COLOR_ATTACHMENT0 + i;
            }
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

            glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentType, GL_TEXTURE_2D, textureIDs[i], 0);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer intBuff = stack.mallocInt(GBuffer.TOTAL_TEXTURES - 1);
            for (int i = 0; i < GBuffer.TOTAL_TEXTURES - 1; ++i) {
                intBuff.put(i, GL_COLOR_ATTACHMENT0 + i);
            }
            glDrawBuffers(intBuff);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Clean up the frame buffers and textures. */
    public void cleanUp() {
        glDeleteFramebuffers(gBufferId);
        Arrays.stream(textureIDs).forEach(GL11::glDeleteTextures);
    }
}
