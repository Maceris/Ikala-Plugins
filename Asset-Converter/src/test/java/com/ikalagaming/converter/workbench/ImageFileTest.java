package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.Format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Tests for decoding image files. */
class ImageFileTest {

    /** Where test images are written. */
    @TempDir Path folder;

    @Test
    void testPngWithAlpha() throws IOException {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80FF0000);
        source.setRGB(2, 1, 0xFF00FF7F);
        Path file = folder.resolve("alpha.png");
        ImageIO.write(source, "png", file.toFile());

        ImageFile image = ImageFile.read(file);
        assertEquals(3, image.width());
        assertEquals(2, image.height());
        assertEquals("4 (RGBA)", image.channels());
        assertEquals(ImageFile.Depth.UNORM8, image.depth());
        assertEquals(Format.R8G8B8A8_UNORM, image.format());
        assertEquals(3 * 2 * 4, image.pixels().remaining());

        float[] pixel = image.pixel(0, 0, new float[4]);
        assertEquals(1, pixel[0], 0.001f);
        assertEquals(0, pixel[1], 0.001f);
        assertEquals(0x80 / 255.0f, pixel[3], 0.001f);
        // Rows from the top, so the bottom right is the last pixel
        image.pixel(2, 1, pixel);
        assertEquals(0, pixel[0], 0.001f);
        assertEquals(1, pixel[1], 0.001f);
        assertEquals(0x7F / 255.0f, pixel[2], 0.001f);
        assertEquals(1, pixel[3], 0.001f);
    }

    @Test
    void testGrayIsExpandedToRgba() throws IOException {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        source.getRaster().setSample(1, 0, 0, 200);
        Path file = folder.resolve("gray.png");
        ImageIO.write(source, "png", file.toFile());

        ImageFile image = ImageFile.read(file);
        assertEquals("1 (gray)", image.channels());
        float[] pixel = image.pixel(1, 0, new float[4]);
        for (int c = 0; c < 3; ++c) {
            assertEquals(200 / 255.0f, pixel[c], 0.001f);
        }
        assertEquals(1, pixel[3], 0.001f);
    }

    @Test
    void testSixteenBitPng() throws IOException {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_GRAY);
        source.getRaster().setSample(0, 0, 0, 1000);
        Path file = folder.resolve("deep.png");
        ImageIO.write(source, "png", file.toFile());

        ImageFile image = ImageFile.read(file);
        assertEquals(ImageFile.Depth.UNORM16, image.depth());
        assertEquals(Format.R16G16B16A16_UNORM, image.format());
        assertEquals(1000 / 65_535.0f, image.pixel(0, 0, new float[4])[0], 0.000_01f);
    }

    @Test
    void testHdr() throws IOException {
        // Flat (not run length encoded) RGBE: mantissas / 256 * 2^(exponent - 128)
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(
                "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 1 +X 2\n"
                        .getBytes(StandardCharsets.US_ASCII));
        bytes.writeBytes(new byte[] {(byte) 128, 64, 32, (byte) 129});
        bytes.writeBytes(new byte[] {(byte) 128, (byte) 128, (byte) 128, (byte) 132});
        Path file = folder.resolve("light.hdr");
        Files.write(file, bytes.toByteArray());

        ImageFile image = ImageFile.read(file);
        assertEquals(ImageFile.Depth.HALF, image.depth());
        assertEquals(Format.R16G16B16A16_SFLOAT, image.format());
        assertEquals(2 * 4 * Short.BYTES, image.pixels().remaining());
        float[] pixel = image.pixel(0, 0, new float[4]);
        assertEquals(1, pixel[0], 0.001f);
        assertEquals(0.5f, pixel[1], 0.001f);
        assertEquals(0.25f, pixel[2], 0.001f);
        assertEquals(1, pixel[3], 0.001f);
        // Brighter than 1, which is the point of HDR
        assertEquals(8, image.pixel(1, 0, pixel)[0], 0.01f);
    }

    @Test
    void testNotAnImage() throws IOException {
        Path file = folder.resolve("fake.png");
        Files.writeString(file, "not a png");
        assertThrows(IOException.class, () -> ImageFile.read(file));
    }

    @Test
    void testPixelOutside() throws IOException {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Path file = folder.resolve("one.png");
        ImageIO.write(source, "png", file.toFile());
        ImageFile image = ImageFile.read(file);
        assertThrows(IndexOutOfBoundsException.class, () -> image.pixel(1, 0, new float[4]));
    }

    /**
     * Write a one level 4x4 DDS file holding one block.
     *
     * @param file Where to write it.
     * @param fourCC The four character code.
     * @param block The block's bytes.
     * @throws IOException If it can't be written.
     */
    private static void writeDds(Path file, String fourCC, byte[] block) throws IOException {
        ByteBuffer out = ByteBuffer.allocate(128 + block.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0, 0x20534444);
        out.putInt(4, 124);
        out.putInt(8, 0x1 | 0x2 | 0x4 | 0x1000);
        out.putInt(12, 4);
        out.putInt(16, 4);
        out.putInt(76, 32);
        out.putInt(80, 0x4);
        out.put(84, fourCC.getBytes(StandardCharsets.US_ASCII));
        out.put(128, block);
        Files.write(file, out.array());
    }

    @Test
    void testDds() throws IOException {
        // BC1, red and blue end points, every pixel index 0, so all red
        Path file = folder.resolve("Brick_BaseColor.dds");
        writeDds(file, "DXT1", new byte[] {0x00, (byte) 0xF8, 0x1F, 0x00, 0, 0, 0, 0});
        ImageFile image = ImageFile.read(file);
        assertNotNull(image.dds());
        assertEquals(Format.BC1_RGBA_UNORM_BLOCK, image.format());
        assertEquals(1, image.mipLevels());
        assertEquals(8, image.dataBytes());
        assertEquals(ColorSpace.SRGB, image.defaultColorSpace());
        float[] pixel = image.pixel(3, 3, new float[4]);
        assertEquals(1, pixel[0], 0.001f);
        assertEquals(0, pixel[2], 0.001f);
    }

    @Test
    void testDdsWithoutReadBack() throws IOException {
        // BC5 normal maps decode; their format can't be sRGB, so they load linear
        Path normal = folder.resolve("Brick_Normal.dds");
        writeDds(normal, "ATI2", new byte[16]);
        ImageFile image = ImageFile.read(normal);
        assertEquals(ColorSpace.LINEAR, image.defaultColorSpace());
        assertNotNull(image.pixel(0, 0, new float[4]));
    }

    @Test
    void testColorSpaceGuess() {
        assertTrue(ImageFile.looksLikeData("Antenna_Metal_Specular.dds"));
        assertTrue(ImageFile.looksLikeData("rock_nrm.png"));
        assertTrue(ImageFile.looksLikeData("wall_roughness.png"));
        assertFalse(ImageFile.looksLikeData("Antenna_Metal_BaseColor.dds"));
        assertFalse(ImageFile.looksLikeData("sky.png"));
        assertTrue(ImageFile.looksLikeNormalMap("Antenna_Metal_Normal.dds"));
        assertEquals(
                java.util.List.of("wood", "base", "color", "2k"),
                ImageFile.words("Wood_BaseColor-2k.dds"));
    }
}
