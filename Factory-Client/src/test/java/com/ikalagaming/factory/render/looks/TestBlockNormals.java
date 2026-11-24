package com.ikalagaming.factory.render.looks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

/** Tests for making normal maps from block textures. */
class TestBlockNormals {

    /**
     * A 16x16 gray texture whose brightness one function gives.
     *
     * @param brightness Gray level 0 to 255, by column and row.
     * @return The texture.
     */
    private static BufferedImage gray(java.util.function.IntBinaryOperator brightness) {
        final BufferedImage image =
                new BufferedImage(
                        BlockNormals.TILE, BlockNormals.TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < BlockNormals.TILE; ++y) {
            for (int x = 0; x < BlockNormals.TILE; ++x) {
                final int v = brightness.applyAsInt(x, y);
                image.setRGB(x, y, 0xFF000000 | v << 16 | v << 8 | v);
            }
        }
        return image;
    }

    /** One channel of a texel, 0 to 255. */
    private static int channel(BufferedImage image, int x, int y, int shift) {
        return (image.getRGB(x, y) >> shift) & 0xFF;
    }

    /** A flat texture points straight out. */
    @Test
    void testFlat() {
        final BufferedImage map = BlockNormals.normals(gray((x, y) -> 100), null, 2);
        assertEquals(128, channel(map, 5, 5, 16), 1);
        assertEquals(128, channel(map, 5, 5, 8), 1);
        assertEquals(255, channel(map, 5, 5, 0));
    }

    /**
     * Brighter to the right tilts the normal left (red low); brighter down the image tilts it up
     * the texture (green high), the OpenGL way.
     */
    @Test
    void testSlopes() {
        final BufferedImage right = BlockNormals.normals(gray((x, y) -> x * 15), null, 2);
        assertTrue(channel(right, 7, 7, 16) < 120, "red " + channel(right, 7, 7, 16));
        assertEquals(128, channel(right, 7, 7, 8), 1);
        final BufferedImage down = BlockNormals.normals(gray((x, y) -> y * 15), null, 2);
        assertTrue(channel(down, 7, 7, 8) > 136, "green " + channel(down, 7, 7, 8));
        assertEquals(128, channel(down, 7, 7, 16), 1);
    }

    /** Tiles wrap within themselves, so the edges of a 2-tile strip don't see the other tile. */
    @Test
    void testTilesStayApart() {
        final BufferedImage strip =
                new BufferedImage(
                        2 * BlockNormals.TILE, BlockNormals.TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < BlockNormals.TILE; ++y) {
            for (int x = 0; x < 2 * BlockNormals.TILE; ++x) {
                final int v = x < BlockNormals.TILE ? 60 : 200;
                strip.setRGB(x, y, 0xFF000000 | v << 16 | v << 8 | v);
            }
        }
        final BufferedImage map = BlockNormals.normals(strip, null, 2);
        // Each tile is flat on its own, even right at the seam between them
        assertEquals(128, channel(map, BlockNormals.TILE - 1, 4, 16), 1);
        assertEquals(128, channel(map, BlockNormals.TILE, 4, 16), 1);
    }

    /** A hand-painted height map wins over the brightness, and only base colors get maps. */
    @Test
    void testHeightMapAndNames() {
        final BufferedImage map =
                BlockNormals.normals(gray((x, y) -> 100), gray((x, y) -> x * 15), 2);
        assertTrue(channel(map, 7, 7, 16) < 120);
        assertTrue(BlockNormals.isColorTexture("stone.png"));
        assertFalse(BlockNormals.isColorTexture("stone.normal.png"));
        assertFalse(BlockNormals.isColorTexture("stone.height.png"));
        assertFalse(BlockNormals.isColorTexture("stone.orm.png"));
        assertFalse(BlockNormals.isColorTexture("notes.txt"));
    }
}
