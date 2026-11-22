package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/** The water's surfaces, their calm near edges, and their sides. */
class WaterMeshTest {

    /** Indices a cell's surface or one side takes. */
    private static final int QUAD = 6;

    /** Vertices one side takes. */
    private static final int SIDE = 4;

    /** Still water. */
    private static final WaterBody STILL = new WaterBody(0, 0, 0);

    /**
     * How many of a mesh's vertices are on sides, facing a way.
     *
     * @param mesh The mesh.
     * @param x The side's normal along x.
     * @param z Along z.
     * @return The count.
     */
    private static int sides(WaterMesh.Mesh mesh, float x, float z) {
        int count = 0;
        for (int v = 0; v < mesh.count(); ++v) {
            final int at = v * WaterMesh.VERTEX_FLOATS;
            if (mesh.vertices()[at + WaterMesh.NORMAL_FLOAT] == x
                    && mesh.vertices()[at + WaterMesh.NORMAL_FLOAT + 1] == z
                    && (x != 0 || z != 0)) {
                ++count;
            }
        }
        return count;
    }

    /**
     * The calm factor of the surface vertex at a place.
     *
     * @param mesh The mesh.
     * @param tile Which tile.
     * @param x Its x relative to the tile.
     * @param z Its z.
     * @return The calm factor.
     */
    private static float calmAt(WaterMesh.Mesh mesh, int tile, float x, float z) {
        for (int v = 0; v < mesh.count(); ++v) {
            final int at = v * WaterMesh.VERTEX_FLOATS;
            final float[] f = mesh.vertices();
            if (Float.floatToRawIntBits(f[at + WaterMesh.TILE_FLOAT]) == tile
                    && f[at] == x
                    && f[at + 2] == z
                    && f[at + WaterMesh.NORMAL_FLOAT] == 0
                    && f[at + WaterMesh.NORMAL_FLOAT + 1] == 0) {
                return f[at + WaterMesh.CALM_FLOAT];
            }
        }
        throw new AssertionError("No surface vertex at " + x + ", " + z);
    }

    /** A lone pool: its cells, sides all round, and calm growing inward from the edge. */
    @Test
    void testLonePool() {
        final WaterTile pool = WaterTile.flat(new Vector3d(), 1, 5, 5, 0.875f, STILL);
        final WaterMesh.Mesh mesh = WaterMesh.build(List.of(pool));
        assertEquals((25 + 4 * 5) * QUAD, mesh.indexCount());
        assertEquals(6 * 6 + 4 * 5 * SIDE, mesh.count(), "corners are shared");
        assertEquals(5 * SIDE, sides(mesh, -1, 0));
        assertEquals(5 * SIDE, sides(mesh, 0, 1));
        assertEquals(0, calmAt(mesh, 0, 0, 0));
        assertEquals(0.5f, calmAt(mesh, 0, 1, 1));
        assertEquals(1, calmAt(mesh, 0, 2, 2));
        assertEquals(0.5f, calmAt(mesh, 0, 2, 1));
        // The sides go down a meter where the bottom isn't given
        float lowest = Float.MAX_VALUE;
        for (int v = 0; v < mesh.count(); ++v) {
            lowest = Math.min(lowest, mesh.vertices()[v * WaterMesh.VERTEX_FLOATS + 1]);
        }
        assertEquals(0.875f - WaterMesh.SIDE_DEPTH, lowest, 1e-6);
    }

    /**
     * Tiles side by side at the same level are one body of water: no sides between them, and calm
     * crosses the seam. A step down between them makes a side, and calms both sides of it.
     */
    @Test
    void testNeighbors() {
        final WaterTile west = WaterTile.flat(new Vector3d(0, 0, 0), 1, 4, 4, 0.875f, STILL);
        final WaterTile east = WaterTile.flat(new Vector3d(4, 0, 0), 1, 4, 4, 0.875f, STILL);
        final WaterMesh.Mesh level = WaterMesh.build(List.of(west, east));
        assertEquals(0, sides(level, 1, 0) - 4 * SIDE, "only the east tile's far edge");
        assertEquals(1, calmAt(level, 0, 4, 2), "the seam is open water");

        final WaterTile lower = WaterTile.flat(new Vector3d(4, -1, 0), 1, 4, 4, 0.875f, STILL);
        final WaterMesh.Mesh step = WaterMesh.build(List.of(west, lower));
        assertEquals(8 * SIDE, sides(step, 1, 0), "the step, and the far edge");
        assertEquals(0, calmAt(step, 0, 4, 2));
        assertEquals(0, calmAt(step, 1, 0, 2));
        // The step goes down to the lower water's surface
        float lowest = Float.MAX_VALUE;
        for (int v = 0; v < step.count(); ++v) {
            final int at = v * WaterMesh.VERTEX_FLOATS;
            if (Float.floatToRawIntBits(step.vertices()[at + WaterMesh.TILE_FLOAT]) == 0
                    && step.vertices()[at + WaterMesh.NORMAL_FLOAT] == 1) {
                lowest = Math.min(lowest, step.vertices()[at + 1]);
            }
        }
        assertEquals(0.875f - 1, lowest, 1e-6);
    }

    /** Shallow water calms its waves, and a masked cell is an edge. */
    @Test
    void testShallowAndMasked() {
        final float[] heights = new float[6 * 6];
        Arrays.fill(heights, 0.875f);
        final float[] bottoms = new float[6 * 6];
        Arrays.fill(bottoms, -9);
        bottoms[3 * 6 + 3] = 0.875f - 1;
        final boolean[] mask = new boolean[5 * 5];
        Arrays.fill(mask, true);
        mask[0] = false;
        final WaterTile tile =
                new WaterTile(new Vector3d(), 1, 5, 5, heights, bottoms, mask, STILL);
        final WaterMesh.Mesh mesh = WaterMesh.build(List.of(tile));
        assertEquals(1 / WaterMesh.CALM_DEPTH, calmAt(mesh, 0, 3, 3), 1e-6);
        assertEquals(0, calmAt(mesh, 0, 1, 1), "next to the masked cell");
        assertTrue(sides(mesh, -1, 0) > 0);
        // The masked corner cell takes two outer sides and leaves two facing it
        assertEquals((24 + 4 * 5 - 2 + 2) * QUAD, mesh.indexCount());
    }

    /**
     * A builder used again, with what it built given back, builds exactly what a new one does, for
     * tiles bigger and smaller than before, and its cell data matches too.
     */
    @Test
    void testReusedBuilderMatchesANewOne() {
        final WaterTile small = WaterTile.flat(new Vector3d(0, 0, 0), 1, 3, 3, 0.875f, STILL);
        final WaterTile west = WaterTile.flat(new Vector3d(0, 0, 0), 1, 6, 6, 0.875f, STILL);
        final WaterTile lower = WaterTile.flat(new Vector3d(6, -1, 0), 1, 6, 6, 0.875f, STILL);
        final List<List<WaterTile>> versions =
                List.of(List.of(small), List.of(west, lower), List.of(small), List.of(lower));
        final WaterMesh reused = new WaterMesh();
        WaterMesh.Mesh last = null;
        WaterMesh.CellData lastCells = null;
        for (List<WaterTile> tiles : versions) {
            if (last != null) {
                reused.recycle(last);
                reused.recycle(lastCells);
            }
            final List<Integer> climates = tiles.stream().map(t -> -1).toList();
            last = reused.mesh(tiles);
            lastCells = reused.cellData(tiles, climates);
            final WaterMesh.Mesh fresh = WaterMesh.build(tiles);
            assertEquals(fresh.count(), last.count());
            assertEquals(fresh.indexCount(), last.indexCount());
            assertEquals(fresh.surfaceIndexCount(), last.surfaceIndexCount());
            assertTrue(
                    Arrays.equals(
                            fresh.vertices(),
                            0,
                            fresh.count() * WaterMesh.VERTEX_FLOATS,
                            last.vertices(),
                            0,
                            last.count() * WaterMesh.VERTEX_FLOATS));
            assertTrue(
                    Arrays.equals(
                            fresh.indices(),
                            0,
                            fresh.indexCount(),
                            last.indices(),
                            0,
                            last.indexCount()));
            final WaterMesh.CellData freshCells = WaterMesh.waterCellData(tiles, climates);
            assertEquals(freshCells.size(), lastCells.size());
            for (int z = -2; z < 8; ++z) {
                for (int x = -2; x < 14; ++x) {
                    final int a = freshCells.row(x, z);
                    final int b = lastCells.row(x, z);
                    assertEquals(a < 0, b < 0, x + ", " + z);
                    for (int v = 0; a >= 0 && v < WaterMesh.CELL_DATA; ++v) {
                        assertEquals(freshCells.value(a, v), lastCells.value(b, v));
                    }
                }
            }
        }
    }

    /**
     * An open tile, water all round so its waves aren't calmed, gets coarser levels made of its own
     * corners; a tile at the water's edge keeps only its cells.
     */
    @Test
    void testLevels() {
        final int size = 16;
        final List<WaterTile> tiles = new java.util.ArrayList<>();
        for (int z = -1; z <= 1; ++z) {
            for (int x = -1; x <= 1; ++x) {
                tiles.add(
                        WaterTile.flat(
                                new Vector3d(x * size, 0, z * size), 1, size, size, 1, STILL));
            }
        }
        final WaterMesh.Mesh mesh = WaterMesh.build(tiles);
        final int middle = 4;
        final int[] levels = mesh.levels();
        for (int level = 0; level < WaterMesh.LEVELS; ++level) {
            final int cells = size >> level;
            final int at = (middle * WaterMesh.LEVELS + level) * 2;
            assertEquals(cells * cells * QUAD, levels[at + 1], "level " + level);
            // Made of the tile's own corners
            for (int i = levels[at]; i < levels[at] + levels[at + 1]; ++i) {
                final int vertex = mesh.indices()[i];
                assertEquals(
                        middle,
                        Float.floatToRawIntBits(
                                mesh.vertices()[
                                        vertex * WaterMesh.VERTEX_FLOATS + WaterMesh.TILE_FLOAT]));
            }
        }
        assertEquals(size * size * QUAD, levels[1]);
        for (int level = 1; level < WaterMesh.LEVELS; ++level) {
            assertEquals(0, levels[level * 2 + 1], "an edge tile's level " + level);
        }
        assertEquals(
                mesh.indexCount(),
                mesh.surfaceIndexCount() + mesh.sideIndexCount() + coarseIndices(levels));
    }

    /**
     * How many indices the coarser levels take.
     *
     * @param levels The mesh's levels.
     * @return Their indices.
     */
    private static int coarseIndices(int[] levels) {
        int sum = 0;
        for (int i = 0; i < levels.length; i += 2) {
            if ((i / 2) % WaterMesh.LEVELS != 0) {
                sum += levels[i + 1];
            }
        }
        return sum;
    }
}
