package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** Tests for working out visibility, and the triangle tree it uses. */
class VisibilityTest {

    /** How far rays go in these tests. */
    private static final double REACH = 8;

    /**
     * The two triangles of an axis-aligned quad, wound to face along a normal.
     *
     * @param out Where the 18 floats go.
     * @param at Where they start.
     * @param a A corner.
     * @param b The next corner.
     * @param c The one after.
     * @param d The last.
     */
    private static void quad(float[] out, int at, float[] a, float[] b, float[] c, float[] d) {
        final float[][] corners = {a, b, c, a, c, d};
        for (int i = 0; i < corners.length; ++i) {
            System.arraycopy(corners[i], 0, out, at + i * 3, 3);
        }
    }

    /**
     * A box's six faces, facing out, or in if asked.
     *
     * @param min The minimum corner.
     * @param max The maximum corner.
     * @param inward Whether the faces face in.
     * @return 12 triangles.
     */
    private static float[] box(float[] min, float[] max, boolean inward) {
        final float x0 = min[0], y0 = min[1], z0 = min[2];
        final float x1 = max[0], y1 = max[1], z1 = max[2];
        final float[][][] faces = {
            {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, // +y
            {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, // -y
            {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, // +x
            {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, // -x
            {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, // +z
            {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}}, // -z
        };
        final float[] out = new float[faces.length * 18];
        for (int f = 0; f < faces.length; ++f) {
            final float[][] q = faces[f];
            if (inward) {
                quad(out, f * 18, q[0], q[3], q[2], q[1]);
            } else {
                quad(out, f * 18, q[0], q[1], q[2], q[3]);
            }
        }
        return out;
    }

    /** Faces with nothing in front of them are open. */
    @Test
    void testOpenFacesAreVisible() {
        final float[] visibility =
                Visibility.compute(box(new float[3], new float[] {1, 1, 1}, false), REACH, null);
        for (float v : visibility) {
            assertEquals(1, v, 1e-6);
        }
    }

    /** The faces inside a closed room see only the room. */
    @Test
    void testInsideOfARoomIsHidden() {
        final float[] visibility =
                Visibility.compute(box(new float[3], new float[] {2, 2, 2}, true), REACH, null);
        for (float v : visibility) {
            assertEquals(0, v, 1e-6);
        }
    }

    /**
     * A slab over a cave: its top is open, the cave's ceiling (the slab's underside, facing down
     * into a closed cave) is hidden.
     */
    @Test
    void testCaveCeilingIsHidden() {
        // Ground with a closed cave under it: the cave is a room facing in, the ground a quad on
        // top
        final float[] room = box(new float[] {0, 0, 0}, new float[] {4, 2, 4}, true);
        final float[] all = new float[room.length + 18];
        System.arraycopy(room, 0, all, 0, room.length);
        quad(
                all,
                room.length,
                new float[] {-2, 3, -2},
                new float[] {-2, 3, 6},
                new float[] {6, 3, 6},
                new float[] {6, 3, -2});
        final float[] visibility = Visibility.compute(all, REACH, null);
        // The room's first face is its ceiling, facing down
        assertTrue(visibility[0] < 0.1, "Ceiling " + visibility[0]);
        assertEquals(1, visibility[visibility.length - 1], 1e-6);
    }

    /** The same input always gives the same visibility, on one thread or several. */
    @Test
    void testDeterministic() {
        final float[] room = box(new float[] {0, 0, 0}, new float[] {3, 1, 2}, true);
        final float[] cube = box(new float[] {1, 0, 0.5f}, new float[] {2, 0.5f, 1.5f}, false);
        final float[] all = new float[room.length + cube.length];
        System.arraycopy(room, 0, all, 0, room.length);
        System.arraycopy(cube, 0, all, room.length, cube.length);
        final float[] once = Visibility.compute(all, REACH, null);
        final java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            assertArrayEquals(once, Visibility.compute(all, REACH, pool));
        } finally {
            pool.shutdownNow();
        }
    }

    /** The tree finds a hit exactly when a brute-force search over every triangle does. */
    @Test
    void testTreeMatchesBruteForce() {
        final Random random = new Random(5);
        final int count = 300;
        final float[] triangles = new float[count * 9];
        for (int i = 0; i < triangles.length; ++i) {
            triangles[i] = (float) (random.nextDouble() * 20);
        }
        // Make most triangles small, so rays both hit and miss
        for (int t = 0; t < count; ++t) {
            for (int c = 1; c < 3; ++c) {
                for (int axis = 0; axis < 3; ++axis) {
                    triangles[t * 9 + c * 3 + axis] =
                            triangles[t * 9 + axis] + (float) (random.nextDouble() * 2 - 1);
                }
            }
        }
        final TriangleBvh bvh = new TriangleBvh(triangles);
        final TriangleBvh single = new TriangleBvh(new float[9]);
        final int[] stack = new int[TriangleBvh.STACK_SIZE];
        for (int r = 0; r < 2000; ++r) {
            final double[] origin = {
                random.nextDouble() * 20, random.nextDouble() * 20, random.nextDouble() * 20
            };
            final double[] ray = {
                random.nextDouble() * 10 - 5,
                random.nextDouble() * 10 - 5,
                random.nextDouble() * 10 - 5
            };
            boolean expected = false;
            for (int t = 0; t < count && !expected; ++t) {
                final float[] one = new float[9];
                System.arraycopy(triangles, t * 9, one, 0, 9);
                expected = new TriangleBvh(one).anyHit(origin, ray, stack);
            }
            assertEquals(expected, bvh.anyHit(origin, ray, stack), "Ray " + r);
        }
        // A degenerate triangle is never hit
        assertEquals(false, single.anyHit(new double[] {0, 1, 0}, new double[] {0, -2, 0}, stack));
    }

    /** The tree finds the nearest triangle's distance, as a brute-force search does. */
    @Test
    void testNearestMatchesBruteForce() {
        final Random random = new Random(9);
        final int count = 300;
        final float[] triangles = new float[count * 9];
        for (int t = 0; t < count; ++t) {
            for (int axis = 0; axis < 3; ++axis) {
                triangles[t * 9 + axis] = (float) (random.nextDouble() * 20);
            }
            for (int c = 1; c < 3; ++c) {
                for (int axis = 0; axis < 3; ++axis) {
                    triangles[t * 9 + c * 3 + axis] =
                            triangles[t * 9 + axis] + (float) (random.nextDouble() * 2 - 1);
                }
            }
        }
        final TriangleBvh bvh = new TriangleBvh(triangles);
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        for (int r = 0; r < 500; ++r) {
            final double[] point = {
                random.nextDouble() * 30 - 5,
                random.nextDouble() * 30 - 5,
                random.nextDouble() * 30 - 5
            };
            double expected = Double.POSITIVE_INFINITY;
            for (int t = 0; t < count; ++t) {
                final float[] one = new float[9];
                System.arraycopy(triangles, t * 9, one, 0, 9);
                expected =
                        Math.min(
                                expected,
                                new TriangleBvh(one)
                                        .nearest(point, Double.POSITIVE_INFINITY, scratch));
            }
            assertEquals(
                    expected,
                    bvh.nearest(point, Double.POSITIVE_INFINITY, scratch),
                    1e-9,
                    "Point " + r);
        }
        // Nothing nearer than the limit gives the limit
        assertEquals(1, bvh.nearest(new double[] {1000, 0, 0}, 1, scratch));
        assertEquals(5, new TriangleBvh(new float[0]).nearest(new double[3], 5, scratch));
    }
}
