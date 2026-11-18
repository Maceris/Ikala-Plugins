package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.Format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;

/** Tests for the checks made before a texture is created, which need no GPU. */
class TextureLoaderVulkanTest {

    /**
     * Sizes that aren't positive are refused, with or without data.
     *
     * @param width The width.
     * @param height The height.
     */
    @ParameterizedTest
    @CsvSource({"0, 4", "4, 0", "-1, 4", "4, -2147483648", "0, 0"})
    void testSizeMustBePositive(int width, int height) {
        assertThrows(
                IllegalArgumentException.class,
                () -> TextureLoaderVulkan.checkData(null, Format.R8G8B8A8_UNORM, width, height));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TextureLoaderVulkan.checkData(
                                ByteBuffer.allocate(64), Format.R8G8B8A8_UNORM, width, height));
    }

    /** Data shorter than the image, from its position, is refused. */
    @Test
    void testShortDataIsRefused() {
        final ByteBuffer exact = ByteBuffer.allocate(4 * 4 * 4);
        assertDoesNotThrow(() -> TextureLoaderVulkan.checkData(exact, Format.R8G8B8A8_UNORM, 4, 4));
        assertDoesNotThrow(() -> TextureLoaderVulkan.checkData(null, Format.R8G8B8A8_UNORM, 4, 4));

        final ByteBuffer moved = ByteBuffer.allocate(4 * 4 * 4).position(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> TextureLoaderVulkan.checkData(moved, Format.R8G8B8A8_UNORM, 4, 4));
        // Two BC1 blocks needed, one given
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TextureLoaderVulkan.checkData(
                                ByteBuffer.allocate(8), Format.BC1_RGBA_UNORM_BLOCK, 8, 4));
    }

    /** A size whose byte count wraps an int to a small number is still refused. */
    @Test
    void testWrappingSizeIsRefused() {
        // 16384 * 16384 * 16 is exactly 2^32, which an int holds as 0
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TextureLoaderVulkan.checkData(
                                ByteBuffer.allocate(16),
                                Format.R32G32B32A32_SFLOAT,
                                16_384,
                                16_384));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TextureLoaderVulkan.checkData(
                                ByteBuffer.allocate(16),
                                Format.R8G8B8A8_UNORM,
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE));
        // Past a long, which is still an IllegalArgumentException, not an ArithmeticException
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TextureLoaderVulkan.checkData(
                                ByteBuffer.allocate(16),
                                Format.R32G32B32A32_SFLOAT,
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE));
    }
}
