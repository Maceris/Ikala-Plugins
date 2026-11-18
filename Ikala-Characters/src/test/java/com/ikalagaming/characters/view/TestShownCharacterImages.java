package com.ikalagaming.characters.view;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Tests for decoding a character's images: sizes are read from the header and checked before any
 * pixels are decoded.
 *
 * @author Ches Burks
 */
class TestShownCharacterImages {
    /**
     * A PNG of only a gray 8 bit header, with a valid CRC, claiming a size; it has no pixel data.
     *
     * @param width The claimed width.
     * @param height The claimed height.
     * @return The file's bytes.
     */
    private static byte[] forgedPng(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(4 + 13).order(ByteOrder.BIG_ENDIAN);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII));
        ihdr.putInt(width).putInt(height).put((byte) 8).put(new byte[4]);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4 + 12).order(ByteOrder.BIG_ENDIAN);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        crc.reset();
        crc.update("IEND".getBytes(StandardCharsets.US_ASCII));
        png.putInt(0).put("IEND".getBytes(StandardCharsets.US_ASCII)).putInt((int) crc.getValue());
        return png.array();
    }

    /** Headers claiming huge sizes are refused with an exception, not an out of memory error. */
    @Test
    void testForgedSizesRefused() {
        int[][] sizes = {
            {100_000, 100_000},
            {ShownCharacter.MAX_IMAGE_SIDE + 1, 1},
            {1, ShownCharacter.MAX_IMAGE_SIDE + 1},
            // Each side fits, the total doesn't
            {ShownCharacter.MAX_IMAGE_SIDE, ShownCharacter.MAX_IMAGE_SIDE}
        };
        for (int[] size : sizes) {
            byte[] png = forgedPng(size[0], size[1]);
            IOException thrown =
                    Assertions.assertThrows(
                            IOException.class, () -> ShownCharacter.readImage(png));
            Assertions.assertTrue(thrown.getMessage().contains("size limit"), thrown.getMessage());
        }
    }

    /**
     * Ordinary images still decode, and bytes no reader knows give null.
     *
     * @throws IOException If the test image can't be written.
     */
    @Test
    void testSmallImageDecodes() throws IOException {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 1, 0xFF00FF00);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(source, "png", out);
        BufferedImage decoded = ShownCharacter.readImage(out.toByteArray());
        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(3, decoded.getWidth());
        Assertions.assertEquals(2, decoded.getHeight());
        Assertions.assertEquals(0xFF00FF00, decoded.getRGB(1, 1));
        Assertions.assertNull(ShownCharacter.readImage(new byte[] {1, 2, 3}));
    }
}
