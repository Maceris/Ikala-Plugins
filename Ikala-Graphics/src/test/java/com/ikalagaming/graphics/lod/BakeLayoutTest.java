package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for laying clusters out in bake tiles. */
class BakeLayoutTest {

    /** The tile's side, in texels. */
    private static final int TILE = 64;

    /**
     * The triangles of a unit cube's faces, facing out.
     *
     * @return The corners.
     */
    private static float[] cube() {
        final List<float[]> triangles = new ArrayList<>();
        final float[][] v = new float[8][];
        for (int i = 0; i < 8; ++i) {
            v[i] = new float[] {i & 1, (i >> 1) & 1, (i >> 2) & 1};
        }
        final int[][] quads = {
            {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}
        };
        for (int[] q : quads) {
            triangles.add(join(v[q[0]], v[q[1]], v[q[2]]));
            triangles.add(join(v[q[0]], v[q[2]], v[q[3]]));
        }
        return flatten(triangles);
    }

    /**
     * Half a cylinder's side, radius 1 and height 2, in strips around its axis.
     *
     * @param strips Strips around.
     * @return The corners.
     */
    private static float[] halfCylinder(int strips) {
        final List<float[]> triangles = new ArrayList<>();
        for (int s = 0; s < strips; ++s) {
            final double a = Math.PI * s / strips;
            final double b = Math.PI * (s + 1) / strips;
            final float[] p0 = {(float) Math.cos(a), 0, (float) Math.sin(a)};
            final float[] p1 = {(float) Math.cos(b), 0, (float) Math.sin(b)};
            final float[] p2 = {(float) Math.cos(b), 2, (float) Math.sin(b)};
            final float[] p3 = {(float) Math.cos(a), 2, (float) Math.sin(a)};
            triangles.add(join(p0, p2, p1));
            triangles.add(join(p0, p3, p2));
        }
        return flatten(triangles);
    }

    /**
     * Three corners in one array.
     *
     * @param a The first.
     * @param b The second.
     * @param c The third.
     * @return Nine floats.
     */
    private static float[] join(float[] a, float[] b, float[] c) {
        return new float[] {a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]};
    }

    /**
     * Triangles in one array.
     *
     * @param triangles The triangles, nine floats each.
     * @return Their corners together.
     */
    private static float[] flatten(List<float[]> triangles) {
        final float[] out = new float[triangles.size() * 9];
        for (int i = 0; i < triangles.size(); ++i) {
            System.arraycopy(triangles.get(i), 0, out, i * 9, 9);
        }
        return out;
    }

    /**
     * Check what every layout must be: inside the tile, clear of its edge by the gap, no two
     * triangles overlapping, and every triangle at the layout's texels per world unit, less only
     * what laying a tilted triangle flat on its chart's plane takes off.
     *
     * @param corners The triangles.
     * @param layout Their layout.
     */
    private static void assertSound(float[] corners, BakeLayout layout) {
        final float[] uv = layout.uvs();
        final int triangles = corners.length / 9;
        final double margin = (BakeLayout.GUTTER - 1e-3) / TILE;
        for (float value : uv) {
            assertTrue(value >= margin && value <= 1 - margin, "Out of the tile: " + value);
        }
        final double[] flat = new double[uv.length];
        for (int i = 0; i < uv.length; ++i) {
            flat[i] = uv[i];
        }
        for (int a = 0; a < triangles; ++a) {
            for (int b = a + 1; b < triangles; ++b) {
                assertFalse(BakeLayout.overlap(flat, a * 6, flat, b * 6), a + " overlaps " + b);
            }
        }
        final double texels = 1 / layout.texelSize();
        for (int t = 0; t < triangles; ++t) {
            final double[] a = new double[3];
            final double[] b = new double[3];
            final double[] c = new double[3];
            for (int axis = 0; axis < 3; ++axis) {
                a[axis] = corners[t * 9 + axis];
                b[axis] = corners[t * 9 + 3 + axis];
                c[axis] = corners[t * 9 + 6 + axis];
            }
            final double world = Geometry.area(a, b, c);
            final double inTile =
                    Math.abs(
                                    (uv[t * 6 + 2] - uv[t * 6]) * (uv[t * 6 + 5] - uv[t * 6 + 1])
                                            - (uv[t * 6 + 4] - uv[t * 6])
                                                    * (uv[t * 6 + 3] - uv[t * 6 + 1]))
                            / 2
                            * TILE
                            * TILE;
            final double full = world * texels * texels;
            final double tilted = Math.cos(Math.toRadians(BakeLayout.CHART_DEGREES)) * full;
            assertTrue(
                    inTile <= full * (1 + 1e-3) + 1e-3 && inTile >= tilted * (1 - 1e-3) - 1e-3,
                    "Triangle " + t + " covers " + inTile + " of " + full + " texels");
        }
    }

    @Test
    void testCubeFacesAreCharts() {
        final float[] corners = cube();
        final BakeLayout layout = BakeLayout.of(corners, TILE);
        assertEquals(6, layout.chartCount());
        // Each face's two triangles share a chart
        for (int face = 0; face < 6; ++face) {
            assertEquals(layout.charts()[face * 2], layout.charts()[face * 2 + 1]);
        }
        assertSound(corners, layout);
        // Six unit squares with their gaps fill a fair part of the tile
        assertTrue(layout.texelSize() < 0.1, "Texel " + layout.texelSize());
    }

    @Test
    void testCurvedStripSplitsWhereItTurns() {
        final float[] corners = halfCylinder(12);
        final BakeLayout layout = BakeLayout.of(corners, TILE);
        // Half a turn can't lie flat within 60 degrees of one facing
        assertTrue(layout.chartCount() >= 2 && layout.chartCount() <= 4, "" + layout.chartCount());
        assertSound(corners, layout);
    }

    @Test
    void testManyScatteredTriangles() {
        final List<float[]> triangles = new ArrayList<>();
        for (int i = 0; i < 128; ++i) {
            final float x = (i % 16) * 3;
            final float z = (i / 16) * 3;
            final float tilt = i % 7;
            triangles.add(
                    join(
                            new float[] {x, 0, z},
                            new float[] {x + 1, tilt, z},
                            new float[] {x, 1, z + 1}));
        }
        final float[] corners = flatten(triangles);
        final BakeLayout layout = BakeLayout.of(corners, TILE);
        assertEquals(128, layout.chartCount());
        assertSound(corners, layout);
    }
}
