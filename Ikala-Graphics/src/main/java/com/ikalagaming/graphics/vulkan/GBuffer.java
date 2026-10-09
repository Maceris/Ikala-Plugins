package com.ikalagaming.graphics.vulkan;

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
        @NonNull TextureInfoVulkan @NonNull [] textures,
        @NonNull TextureInfoVulkan depth,
        int width,
        int height) {

    /** Index of the base color texture. */
    public static final int BASE_COLOR = 0;

    /** Index of the normal texture. */
    public static final int NORMAL = 1;

    /** Index of the tangent texture. */
    public static final int TANGENT = 2;

    /** Index of the material index texture. */
    public static final int MATERIAL = 3;

    /** The number of textures, not counting depth. */
    public static final int TEXTURE_COUNT = 4;

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
