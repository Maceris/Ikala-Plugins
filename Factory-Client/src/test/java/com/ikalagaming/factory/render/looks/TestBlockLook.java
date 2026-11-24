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

    /** Names become paths in the asset layout, and names that could leave it don't. */
    @Test
    void testRelativePath() {
        assertEquals(
                "assets/lotomation/looks/block/stone.kvt",
                BlockLooks.relativePath("lotomation:stone", "looks/block", ".kvt"));
        assertEquals(
                "assets/lotomation/textures/block/grass_top.png",
                BlockLooks.relativePath("block/grass_top", "textures", ".png"));
        assertEquals(
                "assets/mymod/textures/block/x.png",
                BlockLooks.relativePath("mymod:block/x", "textures", ".png"));
        assertNull(BlockLooks.relativePath("lotomation:../secret", "textures", ".png"));
        assertNull(BlockLooks.relativePath("lotomation:/abs", "textures", ".png"));
        assertNull(BlockLooks.relativePath("Lotomation:stone", "textures", ".png"));
        assertNull(BlockLooks.relativePath(":stone", "textures", ".png"));
    }

    /** The starter textures survive a trip through an image, and strips put tiles side by side. */
    @Test
    void testImageRoundTrip() {
        byte[] art =
                com.ikalagaming.graphics.benchmark.BlockTextures.art(
                        com.ikalagaming.graphics.benchmark.BlockTextures.Tile.LEAVES);
        BufferedImage image = StarterTextures.image(java.util.List.of(art));
        assertEquals(
                java.util.Arrays.toString(art), java.util.Arrays.toString(BlockLooks.rgba(image)));
        byte[] dirt =
                com.ikalagaming.graphics.benchmark.BlockTextures.art(
                        com.ikalagaming.graphics.benchmark.BlockTextures.Tile.DIRT);
        BufferedImage strip = StarterTextures.image(java.util.List.of(art, dirt));
        assertEquals(2 * image.getWidth(), strip.getWidth());
        assertEquals(image.getRGB(3, 5), strip.getRGB(3, 5));
        assertEquals(
                StarterTextures.image(java.util.List.of(dirt)).getRGB(3, 5),
                strip.getRGB(image.getWidth() + 3, 5));
    }
}
