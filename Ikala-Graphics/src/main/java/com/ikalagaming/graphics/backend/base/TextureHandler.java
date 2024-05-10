package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;

import lombok.NonNull;

import java.nio.ByteBuffer;

/** Defines the methods that are required for dealing with textures in each backend. */
public interface TextureHandler {
    /**
     * Bind a texture for rendering.
     *
     * @param texture The texture to bind.
     */
    void bind(@NonNull Texture texture);

    /**
     * Clean up the texture resources.
     *
     * @param texture The texture to clean up.
     */
    void cleanup(@NonNull Texture texture);

    /**
     * Load a texture to the GPU from a byte buffer.
     *
     * @param buffer The data to load.
     * @param format The format of the image data.
     * @param width The width of the texture, in pixels.
     * @param height The height of the texture, in pixels.
     * @return The corresponding texture handle.
     */
    Texture load(
            @NonNull ByteBuffer buffer, @NonNull Format format, final int width, final int height);

    /**
     * Load a texture to the GPU from a file.
     *
     * @param texturePath The full path to the texture.
     * @return The corresponding texture handle.
     */
    Texture load(@NonNull String texturePath);
}
