package com.ikalagaming.factory.render.looks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

/** Tests for reading look files and finding their files. */
class TestBlockLook {

    /** A look with only {@code all} puts it on every face, with the default surface. */
    @Test
    void testAllFaces() {
        BlockLook look =
                BlockLook.parse("{textures:{all:\"lotomation:block/stone\"}}").orElseThrow();
        assertEquals(BlockLook.DEFAULT_MODEL, look.model());
        assertEquals("lotomation:block/stone", look.side());
        assertEquals("lotomation:block/stone", look.top());
        assertEquals("lotomation:block/stone", look.bottom());
        assertEquals(BlockLook.DEFAULT_ROUGHNESS, look.roughness());
        assertEquals(0, look.metallic());
        assertEquals(BlockLook.Transparency.OPAQUE, look.transparency());
    }

    /** Top, side and bottom override all, and surface values are read. */
    @Test
    void testFacesAndSurface() {
        BlockLook look =
                BlockLook.parse(
                                "{textures:{all:\"a\", top:\"t\", bottom:\"b\"},"
                                        + " roughness:0.3F, metallic:1.0F, emission:2.0F,"
                                        + " transparency:\"cutout\"}")
                        .orElseThrow();
        assertEquals("a", look.side());
        assertEquals("t", look.top());
        assertEquals("b", look.bottom());
        assertEquals(0.3f, look.roughness(), 1e-6);
        assertEquals(1f, look.metallic(), 1e-6);
        assertEquals(2f, look.emission(), 1e-6);
        assertEquals(BlockLook.Transparency.CUTOUT, look.transparency());
    }

    /** Fluids are translucent unless they say otherwise, and a look with no textures has none. */
    @Test
    void testFluid() {
        BlockLook look = BlockLook.parse("{model:\"fluid\"}").orElseThrow();
        assertTrue(look.isFluid());
        assertEquals(BlockLook.Transparency.TRANSLUCENT, look.transparency());
        assertNull(look.side());
    }

    /** Unknown models and values of the wrong kind are errors. */
    @Test
    void testBadValues() {
        assertThrows(IllegalArgumentException.class, () -> BlockLook.parse("{model:1}"));
        assertThrows(IllegalArgumentException.class, () -> BlockLook.parse("{roughness:\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> BlockLook.parse("{textures:\"x\"}"));
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

    /** The starter textures survive a trip through an image. */
    @Test
    void testImageRoundTrip() {
        byte[] art =
                com.ikalagaming.graphics.benchmark.BlockTextures.art(
                        com.ikalagaming.graphics.benchmark.BlockTextures.Tile.LEAVES);
        BufferedImage image = StarterTextures.image(art);
        assertEquals(
                java.util.Arrays.toString(art), java.util.Arrays.toString(BlockLooks.rgba(image)));
    }
}
