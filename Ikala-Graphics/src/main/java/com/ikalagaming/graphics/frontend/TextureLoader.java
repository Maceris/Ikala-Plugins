package com.ikalagaming.graphics.frontend;

import lombok.NonNull;

import java.nio.ByteBuffer;

/** Defines the methods that are required for dealing with textures in each backend. */
public interface TextureLoader {
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
