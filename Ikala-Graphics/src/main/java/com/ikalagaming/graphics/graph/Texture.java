/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
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
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** A texture. */
@Slf4j
@Getter
public class Texture {
    /** The default texture to use if nothing is provided. */
    private static final String DEFAULT_TEXTURE_PATH =
            PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME,
                            ResourceType.DATA,
                            "models/default/default_texture.png")
                    .getAbsolutePath();

    /** The default texture so that we have an easy reference. */
    public static final Texture DEFAULT_TEXTURE = new Texture(Texture.DEFAULT_TEXTURE_PATH);

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
        generateTexture(buffer);
    }

    /**
     * Create a new texture that is read from the provided file.
     *
     * @param texturePath The path to the file for the texture, including full path.
     * @throws RuntimeException If the texture could not be loaded.
     */
    public Texture(@NonNull String texturePath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer buffer = STBImage.stbi_load(texturePath, w, h, channels, 4);
            if (buffer == null) {
                String error =
                        SafeResourceLoader.getString(
                                "TEXTURE_ERROR_LOADING", GraphicsPlugin.getResourceBundle());
                log.info(error, texturePath, STBImage.stbi_failure_reason());
                throw new TextureException(
                        error.replaceFirst("\\{\\}", texturePath)
                                .replaceFirst("\\{\\}", STBImage.stbi_failure_reason()));
            }

            width = w.get();
            height = h.get();

            generateTexture(buffer);

            STBImage.stbi_image_free(buffer);
        }
    }

    /** Bind the texture. */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    /** Clean up this texture. */
    public void cleanup() {
        glDeleteTextures(textureID);
    }

    /**
     * Create the a texture from a byte buffer.
     *
     * @param buffer The buffer that contains the texture information.
     */
    private void generateTexture(@NonNull ByteBuffer buffer) {
        textureID = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, textureID);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D);
    }
}
