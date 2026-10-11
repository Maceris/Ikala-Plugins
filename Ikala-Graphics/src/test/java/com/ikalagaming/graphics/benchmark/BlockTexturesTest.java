package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** Tests for the block textures made in code. */
class BlockTexturesTest {

    /** Where alpha is in a pixel. */
    private static final int ALPHA = 3;

    /** Every tile comes out the same each time, at the full size. */
    @Test
    void testDeterministic() {
        for (BlockTextures.Tile tile : BlockTextures.Tile.values()) {
            final byte[] once = BlockTextures.pixels(tile);
            assertEquals(
                    BlockTextures.SIZE * BlockTextures.SIZE * BlockTextures.CHANNELS, once.length);
            assertArrayEquals(once, BlockTextures.pixels(tile), tile.name());
        }
    }

    /** Tiles differ from each other, and aren't one flat color. */
    @Test
    void testTilesDiffer() {
        final BlockTextures.Tile[] tiles = BlockTextures.Tile.values();
        for (int i = 0; i < tiles.length; ++i) {
            final byte[] pixels = BlockTextures.pixels(tiles[i]);
            boolean varied = false;
            for (int at = BlockTextures.CHANNELS; at < pixels.length; ++at) {
                varied |= pixels[at] != pixels[at % BlockTextures.CHANNELS];
            }
            assertTrue(varied, tiles[i] + " is one flat color");
            for (int j = i + 1; j < tiles.length; ++j) {
                assertFalse(
                        Arrays.equals(pixels, BlockTextures.pixels(tiles[j])),
                        tiles[i] + " and " + tiles[j]);
            }
        }
    }

    /** Leaves have holes, water is partly see-through, and the rest is opaque. */
    @Test
    void testAlpha() {
        for (BlockTextures.Tile tile : BlockTextures.Tile.values()) {
            final byte[] pixels = BlockTextures.pixels(tile);
            int clear = 0;
            int partial = 0;
            for (int at = ALPHA; at < pixels.length; at += BlockTextures.CHANNELS) {
                final int alpha = pixels[at] & 0xFF;
                clear += alpha == 0 ? 1 : 0;
                partial += alpha > 0 && alpha < 0xFF ? 1 : 0;
            }
            switch (tile) {
                case LEAVES -> assertTrue(clear > 0 && partial == 0, "Leaves have holes");
                case WATER -> assertEquals(pixels.length / BlockTextures.CHANNELS, partial);
                default -> assertTrue(clear == 0 && partial == 0, tile + " is opaque");
            }
        }
    }

    /** A grass block's side has grass along its top row and dirt along its bottom. */
    @Test
    void testGrassSideHasGrassOnTop() {
        final byte[] side = BlockTextures.pixels(BlockTextures.Tile.GRASS_SIDE);
        final int topGreen = side[1] & 0xFF;
        final int topRed = side[0] & 0xFF;
        final int last = (BlockTextures.SIZE * BlockTextures.SIZE - 1) * BlockTextures.CHANNELS;
        assertTrue(topGreen > topRed, "Green on top");
        assertTrue((side[last] & 0xFF) > (side[last + 1] & 0xFF), "Brown at the bottom");
    }
}
