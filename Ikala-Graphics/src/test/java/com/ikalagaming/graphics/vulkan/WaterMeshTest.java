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
}
