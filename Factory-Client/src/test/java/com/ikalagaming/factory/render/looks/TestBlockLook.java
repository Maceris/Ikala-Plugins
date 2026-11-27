package com.ikalagaming.factory.render.looks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

/** Tests for reading look files and finding their files. */
class TestBlockLook {

    /** A look names its model, texture and material; a missing model is the plain cube. */
    @Test
    void testNames() {
        BlockLook look =
                BlockLook.parse(
                                "{model:\"lotomation:block/cube_bottom_top\","
                                        + " texture:\"lotomation:block/grass\","
                                        + " material:\"lotomation:block/grass\"}")
                        .orElseThrow();
        assertEquals("lotomation:block/cube_bottom_top", look.model());
        assertEquals("lotomation:block/grass", look.texture());
        assertEquals("lotomation:block/grass", look.material());
        BlockLook bare = BlockLook.parse("{}").orElseThrow();
        assertEquals(BlockLook.DEFAULT_MODEL, bare.model());
        assertNull(bare.texture());
        assertNull(bare.material());
    }

    /** A fluid's model is recognized. */
    @Test
    void testFluid() {
        assertTrue(BlockLook.parse("{model:\"fluid\"}").orElseThrow().isFluid());
    }

    /** Values of the wrong kind are errors. */
    @Test
    void testBadValues() {
        assertThrows(IllegalArgumentException.class, () -> BlockLook.parse("{model:1}"));
        assertThrows(IllegalArgumentException.class, () -> BlockLook.parse("{material:1}"));
        assertThrows(
                IllegalArgumentException.class, () -> BlockLook.parse("{material:{all:\"a\"}}"));
        assertThrows(
                IllegalArgumentException.class, () -> BlockLook.parse("{texture:{all:\"a\"}}"));
    }

    /**
     * Names get the default namespace when they have none, and names that could leave a folder are
     * refused.
     */
    @Test
    void testQualified() {
        assertEquals("lotomation:stone", BlockLooks.qualified("lotomation:stone"));
        assertEquals("lotomation:block/grass", BlockLooks.qualified("block/grass"));
        assertEquals("mymod:block/x", BlockLooks.qualified("mymod:block/x"));
        assertNull(BlockLooks.qualified("lotomation:../secret"));
        assertNull(BlockLooks.qualified("lotomation:/abs"));
        assertNull(BlockLooks.qualified("Lotomation:stone"));
        assertNull(BlockLooks.qualified(":stone"));
    }

    /** Tiles survive a trip through an image, and strips put tiles side by side. */
    @Test
    void testImageRoundTrip() {
        byte[] art =
                com.ikalagaming.graphics.benchmark.BlockTextures.art(
                        com.ikalagaming.graphics.benchmark.BlockTextures.Tile.LEAVES);
        BufferedImage image = image(java.util.List.of(art));
        assertEquals(
                java.util.Arrays.toString(art), java.util.Arrays.toString(BlockLooks.rgba(image)));
        byte[] dirt =
                com.ikalagaming.graphics.benchmark.BlockTextures.art(
                        com.ikalagaming.graphics.benchmark.BlockTextures.Tile.DIRT);
        BufferedImage strip = image(java.util.List.of(art, dirt));
        assertEquals(2 * image.getWidth(), strip.getWidth());
        assertEquals(image.getRGB(3, 5), strip.getRGB(3, 5));
        assertEquals(
                image(java.util.List.of(dirt)).getRGB(3, 5), strip.getRGB(image.getWidth() + 3, 5));
    }

    /**
     * 16x16 RGBA tiles side by side in one image.
     *
     * @param tiles Each tile's pixels, rows from the top, red, green, blue and alpha bytes each.
     * @return The image.
     */
    private static BufferedImage image(java.util.List<byte[]> tiles) {
        final int size = 16;
        BufferedImage image =
                new BufferedImage(size * tiles.size(), size, BufferedImage.TYPE_INT_ARGB);
        for (int tile = 0; tile < tiles.size(); ++tile) {
            byte[] pixels = tiles.get(tile);
            for (int y = 0; y < size; ++y) {
                for (int x = 0; x < size; ++x) {
                    int at = (y * size + x) * 4;
                    int argb =
                            (pixels[at + 3] & 0xFF) << 24
                                    | (pixels[at] & 0xFF) << 16
                                    | (pixels[at + 1] & 0xFF) << 8
                                    | pixels[at + 2] & 0xFF;
                    image.setRGB(tile * size + x, y, argb);
                }
            }
        }
        return image;
    }
}
