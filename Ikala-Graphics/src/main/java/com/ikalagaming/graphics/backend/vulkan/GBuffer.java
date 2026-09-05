package com.ikalagaming.graphics.backend.vulkan;

import lombok.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * The gBuffer textures.
 *
 * @param textures Base color, normal, tangent, and material textures.
 * @param depth The depth buffer.
 * @param width Width of the buffers.
 * @param height Height of the buffers.
 */
public record GBuffer(
        @NonNull TextureInfo @NonNull [] textures,
        @NonNull TextureInfo depth,
        int width,
        int height) {

    @Override
    public String toString() {
        return "GBuffer{"
                + "textures="
                + Arrays.toString(textures)
                + ", depth="
                + depth
                + ", width="
                + width
                + ", height="
                + height
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GBuffer gBuffer = (GBuffer) o;
        return width == gBuffer.width
                && height == gBuffer.height
                && Objects.equals(depth, gBuffer.depth)
                && Objects.deepEquals(textures, gBuffer.textures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(textures), depth, width, height);
    }
}
