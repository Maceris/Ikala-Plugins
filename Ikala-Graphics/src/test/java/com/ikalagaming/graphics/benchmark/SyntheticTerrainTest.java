package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;

import org.junit.jupiter.api.Test;

/** The benchmark's made-up world. */
class SyntheticTerrainTest {

    private static final BakeSource[] SOURCES = BlockMeshes.sources(BlockMeshes.create());

    private static SyntheticTerrain terrain(SyntheticTerrain.Recipe recipe, long seed) {
        return new SyntheticTerrain(recipe, seed, SOURCES);
    }

    @Test
    void theSameSeedMakesTheSameWorld() {
        SyntheticTerrain a = terrain(SyntheticTerrain.Recipe.TERRAIN, 7);
        SyntheticTerrain b = terrain(SyntheticTerrain.Recipe.TERRAIN, 7);
        SyntheticTerrain other = terrain(SyntheticTerrain.Recipe.TERRAIN, 8);
        int differences = 0;
        for (int x = -40; x < 40; x += 3) {
            for (int y = -20; y < 30; y += 2) {
                for (int z = -40; z < 40; z += 3) {
                    assertEquals(a.block(x, y, z), b.block(x, y, z));
                    if (a.block(x, y, z) != other.block(x, y, z)) {
                        ++differences;
                    }
                }
            }
        }
        assertNotEquals(0, differences, "Another seed makes another world");
    }

    @Test
    void skyAndBedrockSectionsAreEmpty() {
        SyntheticTerrain terrain = terrain(SyntheticTerrain.Recipe.TERRAIN, 1);
        assertEquals(0, terrain.placements(0, 10, 0).count(), "High in the sky");
        assertEquals(0, terrain.placements(0, -10, 0).count(), "Deep in solid rock");
        final int surfaceSection =
                Math.floorDiv((int) Math.floor(terrain.height(0, 0)), SyntheticTerrain.SECTION);
        assertTrue(terrain.placements(0, surfaceSection, 0).count() > 0, "At the surface");
    }

    @Test
    void placementsMatchTheirNeighborsAcrossBorders() {
        SyntheticTerrain terrain = terrain(SyntheticTerrain.Recipe.TERRAIN, 3);
        final int size = SyntheticTerrain.SECTION;
        for (int sectionY = -1; sectionY <= 1; ++sectionY) {
            SyntheticTerrain.Placements placements = terrain.placements(2, sectionY, -1);
            for (int i = 0; i < placements.count(); ++i) {
                final int x = 2 * size + SectionBaker.unpack(placements.packedPositions()[i], 0);
                final int y =
                        sectionY * size + SectionBaker.unpack(placements.packedPositions()[i], 1);
                final int z = -size + SectionBaker.unpack(placements.packedPositions()[i], 2);
                final byte block = placements.blocks()[i];
                assertEquals(terrain.block(x, y, z), block);
                // The mask from the world's own blocks, borders included
                BakeSource[] neighbors = new BakeSource[Face.ALL.length];
                for (Face face : Face.ALL) {
                    int[] step = new int[3];
                    step[face.getAxis()] = face.getSign();
                    byte neighbor = terrain.block(x + step[0], y + step[1], z + step[2]);
                    neighbors[face.ordinal()] =
                            neighbor == SyntheticTerrain.AIR ? null : SOURCES[neighbor];
                }
                final int expected = Faces.hiddenFaces(SOURCES[block], 0, neighbors, new int[6]);
                assertEquals(
                        expected,
                        placements.faceMasks()[i] & 0xFF,
                        "Mask at " + x + ", " + y + ", " + z);
                // Buried cubes are left out entirely
                assertNotEquals(0b111111, expected, "A buried cube was placed");
            }
        }
    }

    @Test
    void theCheckerboardHidesNothing() {
        SyntheticTerrain terrain = terrain(SyntheticTerrain.Recipe.CHECKERBOARD, 1);
        SyntheticTerrain.Placements placements = terrain.placements(0, 0, 0);
        final int size = SyntheticTerrain.SECTION;
        assertEquals(size * size * size / 2, placements.count());
        for (byte mask : placements.faceMasks()) {
            assertEquals(0, mask);
        }
    }
}
