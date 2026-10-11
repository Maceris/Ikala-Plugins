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
