package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;

import org.junit.jupiter.api.Test;

/** Tests for the test blocks' shapes. */
class BlockMeshesTest {

    /** The blocks' bake sources. */
    private static final BakeSource[] SOURCES = BlockMeshes.sources(BlockMeshes.create());

    /**
     * Stairs cover their whole bottom and back, so they hide the faces of full blocks there, but
     * not their top or front, which a full block's face still shows past.
     */
    @Test
    void testStairsCoverTheirBottomAndBack() {
        final BakeSource stairs = SOURCES[SyntheticTerrain.STAIRS];
        final BakeSource stone = SOURCES[SyntheticTerrain.STONE];
        // Stone under the stairs: its top is hidden; stone above: its bottom isn't
        assertTrue(Faces.hides(stone, 0, Face.POS_Y, stairs, 0), "Stone under stairs");
        assertFalse(Faces.hides(stone, 0, Face.NEG_Y, stairs, 0), "Stone over stairs");
        // Stone behind the stairs (+z) is hidden, stone in front isn't
        assertTrue(Faces.hides(stone, 0, Face.NEG_Z, stairs, 0), "Stone behind stairs");
        assertFalse(Faces.hides(stone, 0, Face.POS_Z, stairs, 0), "Stone in front of stairs");
        // The stairs' own bottom and back are hidden by stone, their front by nothing partial
        assertTrue(Faces.hides(stairs, 0, Face.NEG_Y, stone, 0), "Stairs on stone");
        assertTrue(Faces.hides(stairs, 0, Face.NEG_Z, stone, 0), "Stairs front against stone");
    }

    /** The jumble's stairs turn, flip and cut their step back to a corner. */
    @Test
    void testJumbleStairsTurnAndFlip() {
        final BakeSource stone = SOURCES[SyntheticTerrain.STONE];
        final BakeSource straight =
                SOURCES[SyntheticTerrain.stairs(SyntheticTerrain.STAIRS_STRAIGHT, false, 0)];
        assertTrue(Faces.hides(stone, 0, Face.NEG_Z, straight, 0), "Straight, back at +z");
        assertFalse(Faces.hides(stone, 0, Face.POS_Z, straight, 0), "Straight, front at -z");
        // A quarter turn puts the back at -x
        final BakeSource turned =
                SOURCES[SyntheticTerrain.stairs(SyntheticTerrain.STAIRS_STRAIGHT, false, 1)];
        assertTrue(Faces.hides(stone, 0, Face.POS_X, turned, 0), "Turned, back at -x");
        assertFalse(Faces.hides(stone, 0, Face.NEG_Z, turned, 0), "Turned, +z open");
        // Upside down, the slab hides the stone above rather than below
        final BakeSource flipped =
                SOURCES[SyntheticTerrain.stairs(SyntheticTerrain.STAIRS_STRAIGHT, true, 0)];
        assertTrue(Faces.hides(stone, 0, Face.NEG_Y, flipped, 0), "Flipped, stone above");
        assertFalse(Faces.hides(stone, 0, Face.POS_Y, flipped, 0), "Flipped, stone below");
        // An inner corner fills its back and right, an outer corner neither
        final BakeSource inner =
                SOURCES[SyntheticTerrain.stairs(SyntheticTerrain.STAIRS_INNER, false, 0)];
        assertTrue(Faces.hides(stone, 0, Face.NEG_Z, inner, 0), "Inner, back");
        assertTrue(Faces.hides(stone, 0, Face.NEG_X, inner, 0), "Inner, right");
        assertFalse(Faces.hides(stone, 0, Face.POS_X, inner, 0), "Inner, left");
        final BakeSource outer =
                SOURCES[SyntheticTerrain.stairs(SyntheticTerrain.STAIRS_OUTER, false, 0)];
        assertFalse(Faces.hides(stone, 0, Face.NEG_Z, outer, 0), "Outer, back");
        assertTrue(Faces.hides(stone, 0, Face.POS_Y, outer, 0), "Outer, bottom");
    }

    /**
     * Chests and lecterns sit flat on what is under them, but a chest's gap and a lectern's open
     * sides hide nothing else, and neither is hidden whole by its neighbors.
     */
    @Test
    void testChestsAndLecternsOnlyCoverTheirBottom() {
        final BakeSource stone = SOURCES[SyntheticTerrain.STONE];
        for (int facing = 0; facing < SyntheticTerrain.FACINGS; ++facing) {
            final BakeSource chest = SOURCES[SyntheticTerrain.CHEST_FIRST + facing];
            final BakeSource lectern = SOURCES[SyntheticTerrain.LECTERN_FIRST + facing];
            assertTrue(Faces.hides(stone, 0, Face.POS_Y, lectern, 0), "Stone under a lectern");
            assertTrue(Faces.hides(lectern, 0, Face.NEG_Y, stone, 0), "Lectern on stone");
            // A chest's bottom is inset too, so stone under it still shows round its edge
            assertFalse(Faces.hides(stone, 0, Face.POS_Y, chest, 0), "Stone under a chest");
            assertTrue(Faces.hides(chest, 0, Face.NEG_Y, stone, 0), "Chest on stone");
            for (Face face : new Face[] {Face.POS_X, Face.NEG_X, Face.POS_Z, Face.NEG_Z}) {
                assertFalse(Faces.hides(stone, 0, face, chest, 0), "Beside a chest, " + face);
                assertFalse(Faces.hides(stone, 0, face, lectern, 0), "Beside a lectern, " + face);
            }
            assertFalse(Faces.hides(stone, 0, Face.NEG_Y, lectern, 0), "Over a lectern");
        }
    }

    /** Panes hide nothing, being thin and see-through. */
    @Test
    void testPanesHideNothing() {
        final BakeSource stone = SOURCES[SyntheticTerrain.STONE];
        for (byte pane :
                new byte[] {
                    SyntheticTerrain.PANE_X, SyntheticTerrain.PANE_Z, SyntheticTerrain.PANE_CROSS
                }) {
            for (Face face : Face.ALL) {
                assertFalse(Faces.hides(stone, 0, face, SOURCES[pane], 0), "Pane " + face);
            }
        }
    }

    /** Faces are textured by where they are in the cell, so a slab's side shows the lower half. */
    @Test
    void testTextureCoordinatesFollowPosition() {
        // A side face's v runs down from the top of the cell
        assertArrayEquals(new float[] {0, 1}, BlockMeshes.uv(0, -1, new float[] {0, 0, 0}));
        assertArrayEquals(new float[] {1, 0.5f}, BlockMeshes.uv(0, -1, new float[] {0, 0.5f, 1}));
        // Seen from outside, u runs left to right on opposite faces
        assertArrayEquals(new float[] {1, 1}, BlockMeshes.uv(0, 1, new float[] {1, 0, 0}));
        assertArrayEquals(new float[] {0, 1}, BlockMeshes.uv(2, 1, new float[] {0, 0, 1}));
        assertArrayEquals(new float[] {1, 1}, BlockMeshes.uv(2, -1, new float[] {0, 0, 0}));
        // The top follows x and z
        assertArrayEquals(
                new float[] {0.25f, 0.75f}, BlockMeshes.uv(1, 1, new float[] {0.25f, 1, 0.75f}));
    }
}
