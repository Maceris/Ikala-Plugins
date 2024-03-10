/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;

import lombok.Getter;

import java.nio.ByteBuffer;

/** An array of textures for rendering cascaded shadow maps. */
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
     * @param pixelFormat The pixel format to use for the textures for glTexImage2Ds.
     * @param textureWrap The texture wrap behavior.
     */
    public ArrayOfTextures(
            int numTextures, int width, int height, int pixelFormat, int textureWrap) {
        IDs = new int[numTextures];
        glGenTextures(IDs);

        for (int i = 0; i < numTextures; ++i) {
            glBindTexture(GL_TEXTURE_2D, IDs[i]);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_DEPTH_COMPONENT,
                    width,
                    height,
                    0,
                    pixelFormat,
                    GL_FLOAT,
                    (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, textureWrap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, textureWrap);
        }
    }

    /** Clean up all the textures. */
    public void cleanup() {
        for (int id : IDs) {
            glDeleteTextures(id);
        }
    }
}
