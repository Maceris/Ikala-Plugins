package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Which tile's water lies across each tile's edges, for blending their optics. */
class WaterNeighborsTest {

    /** A tile's width, in cells of a meter. */
    private static final int SIZE = 16;

    /** A river's water: muddy. */
    private static final WaterBody RIVER = WaterBody.MUDDY;

    /** The sea's water: clear. */
    private static final WaterBody SEA = WaterBody.OCEAN;

    /**
     * A flat tile.
     *
     * @param x Its corner along x.
     * @param y Its surface's height.
     * @param z Its corner along z.
     * @param body Its water.
     * @return The tile.
     */
    private static WaterTile tile(double x, float y, double z, WaterBody body) {
        return WaterTile.flat(new Vector3d(x, 0, z), 1, SIZE, SIZE, y, body);
    }

    /**
     * A river tile beside two sea tiles, one along x and one along z, sees each across its edge.
     */
    @Test
    void testDifferentWaters() {
        final List<WaterTile> tiles =
                List.of(tile(0, 1, 0, RIVER), tile(SIZE, 1, 0, SEA), tile(0, 1, SIZE, SEA));
        final int none = WaterNeighbors.NONE;
        final int[] neighbors = WaterNeighbors.find(tiles);
        assertArrayEquals(
                new int[] {none, 1, none, 2, 0, none, none, none, none, none, 0, none}, neighbors);
        assertArrayEquals(
                new boolean[] {true, true, true}, WaterNeighbors.blends(tiles, neighbors));
    }

    /**
     * Four tiles round a corner, the diagonal's water different: the tile across from it blends,
     * though both its neighbors hold its own water.
     */
    @Test
    void testDiagonal() {
        final List<WaterTile> tiles =
                List.of(
                        tile(0, 1, 0, SEA),
                        tile(SIZE, 1, 0, SEA),
                        tile(0, 1, SIZE, SEA),
                        tile(SIZE, 1, SIZE, RIVER));
        final boolean[] blends = WaterNeighbors.blends(tiles, WaterNeighbors.find(tiles));
        for (boolean blend : blends) {
            assertTrue(blend);
        }
    }

    /** The same water is a neighbor too, but needs no blending. */
    @Test
    void testSameWater() {
        final List<WaterTile> tiles = List.of(tile(0, 1, 0, SEA), tile(SIZE, 1, 0, SEA));
        final int none = WaterNeighbors.NONE;
        final int[] neighbors = WaterNeighbors.find(tiles);
        assertArrayEquals(new int[] {none, 1, none, none, 0, none, none, none}, neighbors);
        final boolean[] blends = WaterNeighbors.blends(tiles, neighbors);
        assertFalse(blends[0] || blends[1]);
        // The same water in different winds: their waves blend
        final int[] winds = {0, 1};
        final boolean[] waves = WaterNeighbors.blends(neighbors, (a, b) -> winds[a] == winds[b]);
        assertTrue(waves[0] && waves[1]);
    }

    /** A pool below a lip isn't the same water, nor is a tile with land at the edge. */
    @Test
    void testStepsAndLand() {
        final int none = WaterNeighbors.NONE;
        final List<WaterTile> step = List.of(tile(0, 3, 0, RIVER), tile(SIZE, 1, 0, SEA));
        assertArrayEquals(
                new int[] {none, none, none, none, none, none, none, none},
                WaterNeighbors.find(step));
        final boolean[] mask = new boolean[SIZE * SIZE];
        for (int z = 0; z < SIZE; ++z) {
            for (int x = 1; x < SIZE; ++x) {
                mask[z * SIZE + x] = true;
            }
        }
        final WaterTile flat = tile(SIZE, 1, 0, SEA);
        final WaterTile shore =
                new WaterTile(
                        flat.origin(),
                        flat.cellSize(),
                        SIZE,
                        SIZE,
                        flat.heights(),
                        null,
                        mask,
                        SEA);
        assertArrayEquals(
                new int[] {none, none, none, none, none, none, none, none},
                WaterNeighbors.find(List.of(tile(0, 1, 0, RIVER), shore)));
    }
}
