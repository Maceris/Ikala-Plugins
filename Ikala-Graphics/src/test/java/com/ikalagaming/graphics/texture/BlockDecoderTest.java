package com.ikalagaming.graphics.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.Format;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Tests for decoding block compressed textures on the CPU, and for color space twins. */
class BlockDecoderTest {

    /**
     * Put bytes in a little endian buffer.
     *
     * @param bytes The bytes, as ints.
     * @return The buffer.
     */
    private static ByteBuffer bytes(int... bytes) {
        ByteBuffer out = ByteBuffer.allocate(bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int b : bytes) {
            out.put((byte) b);
        }
        return out.flip();
    }

    /**
     * Read one decoded pixel.
     *
     * @param pixels The pixels.
     * @param index Which pixel.
     * @return Its red, green, blue and alpha, from 0 to 255.
     */
    private static int[] pixel(byte[] pixels, int index) {
        int[] out = new int[4];
        for (int c = 0; c < 4; ++c) {
            out[c] = Byte.toUnsignedInt(pixels[index * 4 + c]);
        }
        return out;
    }

    @Test
    void testBc1FourColors() {
        // Red then blue end points, red > blue as 5:6:5, so four colors; the first row's indices
        // are 0, 1, 2, 3
        byte[] pixels =
                BlockDecoder.decode(
                        Format.BC1_RGBA_UNORM_BLOCK,
                        bytes(0x00, 0xF8, 0x1F, 0x00, 0xE4, 0, 0, 0),
                        0,
                        4,
                        4);
        assertArrayEquals(new int[] {255, 0, 0, 255}, pixel(pixels, 0));
        assertArrayEquals(new int[] {0, 0, 255, 255}, pixel(pixels, 1));
        assertArrayEquals(new int[] {170, 0, 85, 255}, pixel(pixels, 2));
        assertArrayEquals(new int[] {85, 0, 170, 255}, pixel(pixels, 3));
        // The rest are index 0
        assertArrayEquals(new int[] {255, 0, 0, 255}, pixel(pixels, 15));
    }

    @Test
    void testBc1PunchThrough() {
        // Blue then red, so three colors and transparent black at index 3
        final ByteBuffer block = bytes(0x1F, 0x00, 0x00, 0xF8, 0xE4, 0, 0, 0);
        byte[] rgba = BlockDecoder.decode(Format.BC1_RGBA_UNORM_BLOCK, block, 0, 4, 4);
        assertArrayEquals(new int[] {127, 0, 127, 255}, pixel(rgba, 2));
        assertArrayEquals(new int[] {0, 0, 0, 0}, pixel(rgba, 3));
        // Without alpha, it is opaque black
        byte[] rgb = BlockDecoder.decode(Format.BC1_RGB_UNORM_BLOCK, block, 0, 4, 4);
        assertArrayEquals(new int[] {0, 0, 0, 255}, pixel(rgb, 3));
    }

    @Test
    void testBc3Alpha() {
        // Alpha end points 255 and 0, eight values; the first pixels use indices 0, 1 and 2. The
        // color block is all white.
        byte[] pixels =
                BlockDecoder.decode(
                        Format.BC3_UNORM_BLOCK,
                        bytes(255, 0, 136, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0),
                        0,
                        4,
                        4);
        assertArrayEquals(new int[] {255, 255, 255, 255}, pixel(pixels, 0));
        assertArrayEquals(new int[] {255, 255, 255, 0}, pixel(pixels, 1));
        assertArrayEquals(new int[] {255, 255, 255, 219}, pixel(pixels, 2));
    }

    @Test
    void testBc5() {
        // Red is 200 everywhere; green's end points 10 and 20 give six values, 0 and 255, and
        // index 7 is 255
        byte[] pixels =
                BlockDecoder.decode(
                        Format.BC5_UNORM_BLOCK,
                        bytes(200, 100, 0, 0, 0, 0, 0, 0, 10, 20, 255, 255, 255, 255, 255, 255),
                        0,
                        4,
                        4);
        assertArrayEquals(new int[] {200, 255, 0, 255}, pixel(pixels, 5));
    }

    @Test
    void testPartialBlocksAndUnsupported() {
        // A 2x2 image is still one whole block
        byte[] pixels =
                BlockDecoder.decode(
                        Format.BC4_UNORM_BLOCK, bytes(77, 0, 0, 0, 0, 0, 0, 0), 0, 2, 2);
        assertArrayEquals(new int[] {77, 0, 0, 255}, pixel(pixels, 3));
        assertFalse(BlockDecoder.canDecode(Format.BC7_UNORM_BLOCK));
        assertNull(BlockDecoder.decode(Format.BC7_UNORM_BLOCK, bytes(new int[16]), 0, 4, 4));
    }

    @Test
    void testColorSpaceTwins() {
        assertArrayEquals(
                new Object[] {Format.BC1_RGBA_SRGB_BLOCK, Format.BC3_UNORM_BLOCK},
                new Object[] {
                    ColorSpace.SRGB.apply(Format.BC1_RGBA_UNORM_BLOCK),
                    ColorSpace.LINEAR.apply(Format.BC3_SRGB_BLOCK)
                });
        // No sRGB version, so always linear
        assertArrayEquals(
                new Object[] {Format.BC5_UNORM_BLOCK, ColorSpace.LINEAR, false},
                new Object[] {
                    ColorSpace.SRGB.apply(Format.BC5_UNORM_BLOCK),
                    ColorSpace.of(Format.R16G16B16A16_SFLOAT),
                    ColorSpace.canSwitch(Format.BC5_UNORM_BLOCK)
                });
    }
}
