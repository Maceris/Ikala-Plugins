package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for laying clusters out in bake tiles. */
class BakeLayoutTest {

    /** The largest a tile's side may be, in texels. */
    private static final int MAX_SIDE = 128;

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
     * Check what every layout must be: a tile whose sides are whole multiples of the alignment and
     * no more than the largest, everything inside it and clear of its edge by the gap, no two
     * triangles overlapping, and every triangle at the layout's texels per world unit, less only
     * what laying a tilted triangle flat on its chart's plane takes off.
     *
     * @param corners The triangles.
     * @param layout Their layout.
     */
    private static void assertSound(float[] corners, BakeLayout layout) {
        final float[] uv = layout.uvs();
        final int triangles = corners.length / 9;
        final int width = layout.width();
        final int height = layout.height();
        assertTrue(
                width % BakeLayout.ALIGN == 0
                        && height % BakeLayout.ALIGN == 0
                        && width <= MAX_SIDE
                        && height <= MAX_SIDE,
                width + " by " + height);
        for (int i = 0; i < uv.length; ++i) {
            final double margin = (BakeLayout.GUTTER - 1e-3) / (i % 2 == 0 ? width : height);
            assertTrue(uv[i] >= margin && uv[i] <= 1 - margin, "Out of the tile: " + uv[i]);
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
                            * width
                            * height;
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
        final BakeLayout layout = BakeLayout.at(corners, 0.1, MAX_SIDE);
        assertEquals(6, layout.chartCount());
        // Each face's two triangles share a chart
        for (int face = 0; face < 6; ++face) {
            assertEquals(layout.charts()[face * 2], layout.charts()[face * 2 + 1]);
        }
        assertSound(corners, layout);
        // At the texel size asked for, six squares of 10 texels and their gaps fill most of it
        assertEquals(0.1, layout.texelSize(), 1e-9);
        final int boxes = 6 * 12 * 12;
        assertTrue(
                layout.width() * layout.height() <= boxes * 1.3,
                layout.width() + " by " + layout.height());
    }

    @Test
    void testTooFineShrinksIntoTheLargestTile() {
        final float[] corners = cube();
        final BakeLayout layout = BakeLayout.at(corners, 0.001, MAX_SIDE);
        assertSound(corners, layout);
        assertTrue(layout.texelSize() > 0.001, "Texel " + layout.texelSize());
        // But not by much more than the gaps and the shrinking steps take
        assertTrue(layout.texelSize() < 0.05, "Texel " + layout.texelSize());
    }

    @Test
    void testLongStripGetsLongTile() {
        final List<float[]> triangles = new ArrayList<>();
        triangles.add(join(new float[] {0, 0, 0}, new float[] {0, 0, 1}, new float[] {10, 0, 0}));
        triangles.add(join(new float[] {10, 0, 0}, new float[] {0, 0, 1}, new float[] {10, 0, 1}));
        final float[] corners = flatten(triangles);
        final BakeLayout layout = BakeLayout.at(corners, 0.25, MAX_SIDE);
        assertSound(corners, layout);
        assertEquals(1, layout.chartCount());
        // 40 by 4 texels, gaps and all, in 44 by 8 at most either way round
        assertEquals(44 * 8, layout.width() * layout.height());
    }

    @Test
    void testCurvedStripSplitsWhereItTurns() {
        final float[] corners = halfCylinder(12);
        final BakeLayout layout = BakeLayout.at(corners, 0.05, MAX_SIDE);
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
        final BakeLayout layout = BakeLayout.at(corners, 0.25, MAX_SIDE);
        assertEquals(128, layout.chartCount());
        assertSound(corners, layout);
    }
}
