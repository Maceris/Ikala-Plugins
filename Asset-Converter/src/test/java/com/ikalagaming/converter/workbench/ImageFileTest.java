package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.Format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        assertEquals(4, image.sourceChannels());
        assertEquals(ImageFile.Depth.UNORM8, image.depth());
        assertEquals(Format.R8G8B8A8_UNORM, image.depth().format);
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
        assertEquals(1, image.sourceChannels());
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
        assertEquals(Format.R16G16B16A16_UNORM, image.depth().format);
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
        assertEquals(Format.R16G16B16A16_SFLOAT, image.depth().format);
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
}
