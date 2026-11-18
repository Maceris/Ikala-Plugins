package com.ikalagaming.graphics.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.Format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Tests for reading DDS files. */
class DdsFileTest {

    /** Header flags for caps, height, width, pixel format and mip count. */
    private static final int FLAGS = 0x1 | 0x2 | 0x4 | 0x1000 | DdsFile.FLAG_MIPMAP_COUNT;

    /**
     * Make a DDS file in memory.
     *
     * @param width The width.
     * @param height The height.
     * @param mips The mip count in the header.
     * @param pixelFlags The pixel format flags.
     * @param fourCC The four character code, or 0.
     * @param bitCount Bits per pixel, for uncompressed data.
     * @param masks Red, green, blue and alpha masks, for uncompressed data.
     * @param caps2 The second caps field.
     * @param dx10 The DX10 header's five ints, or null for none.
     * @param dataBytes How many bytes of data follow.
     * @return The file, ready to read.
     */
    private static ByteBuffer file(
            int width,
            int height,
            int mips,
            int pixelFlags,
            int fourCC,
            int bitCount,
            int[] masks,
            int caps2,
            int[] dx10,
            int dataBytes) {
        final int headers = DdsFile.HEADER_SIZE + (dx10 == null ? 0 : DdsFile.DX10_HEADER_SIZE);
        ByteBuffer out = ByteBuffer.allocate(headers + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0, DdsFile.MAGIC);
        out.putInt(4, 124);
        out.putInt(8, FLAGS);
        out.putInt(12, height);
        out.putInt(16, width);
        out.putInt(28, mips);
        out.putInt(76, 32);
        out.putInt(80, pixelFlags);
        out.putInt(84, fourCC);
        out.putInt(88, bitCount);
        for (int i = 0; i < 4; ++i) {
            out.putInt(92 + i * 4, masks[i]);
        }
        out.putInt(112, caps2);
        if (dx10 != null) {
            for (int i = 0; i < dx10.length; ++i) {
                out.putInt(DdsFile.HEADER_SIZE + i * 4, dx10[i]);
            }
        }
        for (int i = headers; i < headers + dataBytes; ++i) {
            out.put(i, (byte) (i - headers));
        }
        return out;
    }

    /** No masks, for compressed formats. */
    private static final int[] NO_MASKS = {0, 0, 0, 0};

    @Test
    void testLegacyBc1WithMips() throws IOException {
        // 8x8, 4x4, 2x2 and 1x1: 4 blocks, then one block each
        ByteBuffer bytes =
                file(
                        8,
                        8,
                        4,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DXT1"),
                        0,
                        NO_MASKS,
                        0,
                        null,
                        56);
        DdsFile dds = DdsFile.parse(bytes);
        assertEquals(Format.BC1_RGBA_UNORM_BLOCK, dds.getFormat());
        assertEquals(ColorSpace.LINEAR, dds.colorSpace());
        assertEquals(4, dds.getMipLevels());
        assertEquals(0, dds.levelOffset(0));
        assertEquals(32, dds.levelOffset(1));
        assertEquals(40, dds.levelOffset(2));
        assertEquals(48, dds.levelOffset(3));
        assertEquals(8, dds.levelBytes(3));
        assertEquals(1, dds.levelWidth(3));
        assertEquals(56, dds.getData().remaining());
        // The data is copied as it is
        assertEquals(33, dds.getData().get(33));
    }

    @Test
    void testDx10Srgb() throws IOException {
        ByteBuffer bytes =
                file(
                        16,
                        4,
                        1,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DX10"),
                        0,
                        NO_MASKS,
                        0,
                        new int[] {99, DdsFile.DX10_TEXTURE_2D, 0, 1, 0},
                        64);
        DdsFile dds = DdsFile.parse(bytes);
        assertEquals(Format.BC7_SRGB_BLOCK, dds.getFormat());
        assertEquals(ColorSpace.SRGB, dds.colorSpace());
        assertEquals(1, dds.getMipLevels());
        assertEquals(64, dds.levelBytes(0));
    }

    @Test
    void testTwentyFourBitIsWidened() throws IOException {
        ByteBuffer bytes =
                file(
                        2,
                        1,
                        1,
                        DdsFile.PIXEL_RGB,
                        0,
                        24,
                        new int[] {0xFF0000, 0xFF00, 0xFF, 0},
                        0,
                        null,
                        6);
        DdsFile dds = DdsFile.parse(bytes);
        assertEquals(Format.B8G8R8A8_UNORM, dds.getFormat());
        assertEquals(8, dds.getData().remaining());
        // Stored blue, green, red; read back as RGBA
        byte[] pixels = BlockDecoder.decode(dds, 0);
        assertEquals(2, pixels[0]);
        assertEquals(1, pixels[1]);
        assertEquals(0, pixels[2]);
        assertEquals(-1, pixels[3]);
        assertEquals(5, pixels[4]);
    }

    @Test
    void testMipCountIsClampedToTheChain() throws IOException {
        // A 4x4 texture can have only 3 levels, whatever the header says
        ByteBuffer bytes =
                file(
                        4,
                        4,
                        10,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DXT5"),
                        0,
                        NO_MASKS,
                        0,
                        null,
                        48);
        DdsFile dds = DdsFile.parse(bytes);
        assertEquals(Format.BC3_UNORM_BLOCK, dds.getFormat());
        assertEquals(3, dds.getMipLevels());
    }

    @Test
    void testRefused() {
        final int dxt1 = DdsFile.fourCC("DXT1");
        // A cube map
        assertThrows(
                IOException.class,
                () ->
                        DdsFile.parse(
                                file(
                                        4,
                                        4,
                                        1,
                                        DdsFile.PIXEL_FOURCC,
                                        dxt1,
                                        0,
                                        NO_MASKS,
                                        DdsFile.CAPS2_CUBEMAP,
                                        null,
                                        48)));
        // Too little data for the levels
        IOException truncated =
                assertThrows(
                        IOException.class,
                        () ->
                                DdsFile.parse(
                                        file(
                                                8,
                                                8,
                                                4,
                                                DdsFile.PIXEL_FOURCC,
                                                dxt1,
                                                0,
                                                NO_MASKS,
                                                0,
                                                null,
                                                40)));
        assertTrue(truncated.getMessage().startsWith("Truncated"));
        // An unknown code
        assertThrows(
                IOException.class,
                () ->
                        DdsFile.parse(
                                file(
                                        4,
                                        4,
                                        1,
                                        DdsFile.PIXEL_FOURCC,
                                        DdsFile.fourCC("NOPE"),
                                        0,
                                        NO_MASKS,
                                        0,
                                        null,
                                        8)));
        // Not a DDS file at all
        assertThrows(IOException.class, () -> DdsFile.parse(ByteBuffer.allocate(200)));
    }

    @Test
    void testByteCounts() {
        assertEquals(8, DdsFile.byteCount(Format.BC1_RGBA_UNORM_BLOCK, 1, 1));
        assertEquals(16, DdsFile.byteCount(Format.BC1_RGBA_UNORM_BLOCK, 5, 4));
        assertEquals(64, DdsFile.byteCount(Format.BC5_UNORM_BLOCK, 8, 8));
        assertEquals(4 * 3 * 8, DdsFile.byteCount(Format.R16G16B16A16_SFLOAT, 4, 3));
    }

    /** Sizes that would wrap an int are worked out in full. */
    @Test
    void testByteCountsDoNotWrap() {
        // Exactly 2^32, which an int holds as 0
        assertEquals(1L << 32, DdsFile.byteCount(Format.R32G32B32A32_SFLOAT, 16384, 16384));
        assertEquals(
                (long) Integer.MAX_VALUE * Integer.MAX_VALUE,
                DdsFile.byteCount(Format.R8_UNORM, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(
                ArithmeticException.class,
                () ->
                        DdsFile.byteCount(
                                Format.R32G32B32A32_SFLOAT, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /**
     * A DX10 header for an uncompressed format with a forged size and almost no data is refused
     * with an IOException, never wrapped to a small size or let out as another exception.
     *
     * @param width The width in the header.
     * @param height The height in the header.
     * @param dxgi The DXGI format.
     * @param mips The mip count in the header.
     */
    @ParameterizedTest
    @CsvSource({
        // 16384 x 16384 RGBA32F is exactly 2^32 bytes
        "16384, 16384, 2, 1",
        "16384, 16384, 2, 2147483647",
        "16384, 16384, 28, 15",
        // Past the dimension cap
        "16385, 1, 28, 1",
        "1, 2147483647, 28, 1",
        "2147483647, 2147483647, 2, 1",
        // Not positive
        "0, 4, 28, 1",
        "4, -4, 28, 1",
        "-2147483648, -2147483648, 2, 1"
    })
    void testForgedSizesAreRefused(int width, int height, int dxgi, int mips) {
        ByteBuffer bytes =
                file(
                        width,
                        height,
                        mips,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DX10"),
                        0,
                        NO_MASKS,
                        0,
                        new int[] {dxgi, DdsFile.DX10_TEXTURE_2D, 0, 1, 0},
                        64);
        assertThrows(IOException.class, () -> DdsFile.parse(bytes));
    }

    /** A legacy 24 bit header with a forged size is refused before the widened copy. */
    @Test
    void testForgedTwentyFourBitSizeIsRefused() {
        ByteBuffer bytes =
                file(
                        16384,
                        16384,
                        1,
                        DdsFile.PIXEL_RGB,
                        0,
                        24,
                        new int[] {0xFF0000, 0xFF00, 0xFF, 0},
                        0,
                        null,
                        12);
        IOException e = assertThrows(IOException.class, () -> DdsFile.parse(bytes));
        assertTrue(e.getMessage().startsWith("Truncated"), e.getMessage());
    }

    /** The largest allowed size still reads when the data is there, with its whole mip chain. */
    @Test
    void testLargestSizeReads() throws IOException {
        // BC1 at 16384 x 4: 4096 blocks of 8 bytes on top, then halving down to 1 x 1
        long expected = 0;
        for (int level = 0; level < 15; ++level) {
            expected +=
                    DdsFile.byteCount(
                            Format.BC1_RGBA_UNORM_BLOCK,
                            DdsFile.levelSize(16384, level),
                            DdsFile.levelSize(4, level));
        }
        ByteBuffer bytes =
                file(
                        16384,
                        4,
                        99,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DXT1"),
                        0,
                        NO_MASKS,
                        0,
                        null,
                        (int) expected);
        DdsFile dds = DdsFile.parse(bytes);
        assertEquals(15, dds.getMipLevels());
        assertEquals(expected, dds.getData().remaining());
        assertEquals(expected - 8, dds.levelOffset(14));
    }

    /**
     * Arrays and cube maps in a DX10 header are refused, whatever count they claim; the count is
     * unsigned, so -1 is a huge array, not a single texture.
     *
     * @param misc The misc flags.
     * @param arraySize The array size.
     */
    @ParameterizedTest
    @CsvSource({"0, 2", "0, 2147483647", "0, -1", "4, 1"})
    void testDx10ArraysAreRefused(int misc, int arraySize) {
        ByteBuffer bytes =
                file(
                        4,
                        4,
                        1,
                        DdsFile.PIXEL_FOURCC,
                        DdsFile.fourCC("DX10"),
                        0,
                        NO_MASKS,
                        0,
                        new int[] {71, DdsFile.DX10_TEXTURE_2D, misc, arraySize, 0},
                        8);
        // The data is enough for one 4 x 4 BC1 image, so only the count can refuse it
        assertThrows(IOException.class, () -> DdsFile.parse(bytes));
    }
}
