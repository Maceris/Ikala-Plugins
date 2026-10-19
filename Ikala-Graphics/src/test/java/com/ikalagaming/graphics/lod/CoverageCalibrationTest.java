package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * How much light coverage volumes stop against the triangles they stand for, along random lines
 * through them: scattered leaves, thin rods, a wall at an angle to the grid and a closed box.
 */
class CoverageCalibrationTest {

    /** How wide the test voxels are. */
    private static final double VOXEL = 0.5;

    /** Lines measured per shape. */
    private static final int LINES = 6000;

    /** Steps along a line per voxel, for its optical depth. */
    private static final int STEPS_PER_VOXEL = 8;

    /** The wall's normal. */
    private static final double[] WALL_NORMAL = unit(new double[] {0.31, 0.17, 1});

    /**
     * How a volume stops light against its triangles.
     *
     * @param truth The share of lines that hit a triangle.
     * @param predicted The volume's mean chance of stopping a line.
     * @param faceOnTruth The share of lines within about 37 degrees of the wall's normal that hit.
     * @param faceOnPredicted The volume's mean chance for them.
     */
    private record Measured(
            double truth, double predicted, double faceOnTruth, double faceOnPredicted) {

        /**
         * How much the volume stops against the triangles.
         *
         * @return Predicted over truth.
         */
        double ratio() {
            return predicted / truth;
        }
    }

    /**
     * Two triangles of a parallelogram.
     *
     * @param out Receives the triangles.
     * @param c Its center.
     * @param u Half one side.
     * @param v Half the other.
     */
    private static void quad(List<float[]> out, double[] c, double[] u, double[] v) {
        final double[][] p = new double[4][3];
        for (int axis = 0; axis < 3; ++axis) {
            p[0][axis] = c[axis] - u[axis] - v[axis];
            p[1][axis] = c[axis] + u[axis] - v[axis];
            p[2][axis] = c[axis] + u[axis] + v[axis];
            p[3][axis] = c[axis] - u[axis] + v[axis];
        }
        out.add(triangle(p[0], p[1], p[2]));
        out.add(triangle(p[0], p[2], p[3]));
    }

    /**
     * A triangle's corners.
     *
     * @param a The first.
     * @param b The second.
     * @param c The third.
     * @return Its 9 floats.
     */
    private static float[] triangle(double[] a, double[] b, double[] c) {
        return new float[] {
            (float) a[0],
            (float) a[1],
            (float) a[2],
            (float) b[0],
            (float) b[1],
            (float) b[2],
            (float) c[0],
            (float) c[1],
            (float) c[2]
        };
    }

    /**
     * A vector at unit length.
     *
     * @param v The vector.
     * @return It, unit length.
     */
    private static double[] unit(double[] v) {
        final double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    /**
     * A cross product.
     *
     * @param a One vector.
     * @param b The other.
     * @return a x b.
     */
    private static double[] cross(double[] a, double[] b) {
        return new double[] {
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        };
    }

    /**
     * A vector scaled.
     *
     * @param v The vector.
     * @param s The scale.
     * @return It scaled.
     */
    private static double[] scaled(double[] v, double s) {
        return new double[] {v[0] * s, v[1] * s, v[2] * s};
    }

    /**
     * A random unit direction.
     *
     * @param random The random numbers.
     * @return The direction.
     */
    private static double[] randomUnit(SplittableRandom random) {
        while (true) {
            final double[] v = {
                random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1)
            };
            final double length = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
            if (length > 1e-4 && length <= 1) {
                return unit(v);
            }
        }
    }

    /**
     * A direction at right angles to another.
     *
     * @param n The other, unit length.
     * @return The direction, unit length.
     */
    private static double[] across(double[] n) {
        return unit(
                cross(n, Math.abs(n[0]) < 0.9 ? new double[] {1, 0, 0} : new double[] {0, 1, 0}));
    }

    /**
     * Triangles end to end.
     *
     * @param triangles The triangles.
     * @return Their corners.
     */
    private static float[] flat(List<float[]> triangles) {
        final float[] out = new float[triangles.size() * 9];
        for (int i = 0; i < triangles.size(); ++i) {
            System.arraycopy(triangles.get(i), 0, out, i * 9, 9);
        }
        return out;
    }

    /**
     * An 8 unit square wall, at an angle to the grid.
     *
     * @return The triangles.
     */
    private static float[] wall() {
        final List<float[]> out = new ArrayList<>();
        final double[] u = across(WALL_NORMAL);
        final double[] v = cross(WALL_NORMAL, u);
        quad(out, new double[] {4.13, 4.07, 4.21}, scaled(u, 4), scaled(v, 4));
        return flat(out);
    }

    /**
     * A closed 4 unit box.
     *
     * @return The triangles.
     */
    private static float[] box() {
        final List<float[]> out = new ArrayList<>();
        final double half = 2;
        final double[] center = {2.37, 2.21, 2.13};
        for (int axis = 0; axis < 3; ++axis) {
            for (int side = -1; side <= 1; side += 2) {
                final double[] c = center.clone();
                c[axis] += side * half;
                final double[] u = new double[3];
                final double[] v = new double[3];
                u[(axis + 1) % 3] = half;
                v[(axis + 2) % 3] = half;
                quad(out, c, u, v);
            }
        }
        return flat(out);
    }

    /**
     * Leaf sized cards facing every way, scattered through a 4 unit box.
     *
     * @param count How many.
     * @return The triangles.
     */
    private static float[] cards(int count) {
        final SplittableRandom random = new SplittableRandom(3);
        final List<float[]> out = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            final double[] c = {
                random.nextDouble(0, 4), random.nextDouble(0, 4), random.nextDouble(0, 4)
            };
            final double[] n = randomUnit(random);
            final double[] u = across(n);
            quad(out, c, scaled(u, 0.15), scaled(cross(n, u), 0.15));
        }
        return flat(out);
    }

    /**
     * Thin rods, each two crossed strips, scattered through a 4 unit box.
     *
     * @param count How many.
     * @return The triangles.
     */
    private static float[] rods(int count) {
        final SplittableRandom random = new SplittableRandom(5);
        final List<float[]> out = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            final double[] c = {
                random.nextDouble(0, 4), random.nextDouble(0, 4), random.nextDouble(0, 4)
            };
            final double[] a = randomUnit(random);
            final double[] b = across(a);
            final double[] d = cross(a, b);
            quad(out, c, a, scaled(b, 0.02));
            quad(out, c, a, scaled(d, 0.02));
        }
        return flat(out);
    }

    /**
     * Voxelize triangles and measure the volume along random lines through them.
     *
     * @param triangles The triangles.
     * @return How the volume stops light against them.
     */
    private static Measured measure(float[] triangles) {
        final CoverageVolume volume =
                CoverageVoxelizer.voxelize(
                        triangles, new int[triangles.length / 9], null, VOXEL, 7);
        final TriangleBvh bvh = new TriangleBvh(triangles);
        final double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < triangles.length; ++i) {
            low[i % 3] = Math.min(low[i % 3], triangles[i]);
            high[i % 3] = Math.max(high[i % 3], triangles[i]);
        }
        double reach = 2;
        for (int axis = 0; axis < 3; ++axis) {
            reach += high[axis] - low[axis];
        }
        final SplittableRandom random = new SplittableRandom(11);
        final int[] stack = new int[TriangleBvh.STACK_SIZE];
        final double[] along = new double[1];
        final double step = VOXEL / STEPS_PER_VOXEL;
        final double[] point = new double[3];
        double truth = 0;
        double predicted = 0;
        double faceOnTruth = 0;
        double faceOnPredicted = 0;
        int counted = 0;
        int faceOn = 0;
        for (int line = 0; line < LINES; ++line) {
            final double[] direction = randomUnit(random);
            final double[] through = {
                random.nextDouble(low[0], high[0]),
                random.nextDouble(low[1], high[1]),
                random.nextDouble(low[2], high[2])
            };
            final double[] start = new double[3];
            for (int axis = 0; axis < 3; ++axis) {
                start[axis] = through[axis] - direction[axis] * reach;
            }
            final boolean hit =
                    bvh.firstHit(start, scaled(direction, 2 * reach), stack, along) >= 0;
            double depth = 0;
            for (double d = step / 2; d < 2 * reach; d += step) {
                for (int axis = 0; axis < 3; ++axis) {
                    point[axis] = start[axis] + direction[axis] * d;
                }
                final int v = volume.indexAt(point);
                if (v >= 0) {
                    depth += volume.density(v) * volume.projectedArea(v, direction) * step;
                }
            }
            if (depth == 0 && !hit) {
                continue;
            }
            final double chance = 1 - Math.exp(-depth);
            truth += hit ? 1 : 0;
            predicted += chance;
            ++counted;
            final double facing =
                    Math.abs(
                            direction[0] * WALL_NORMAL[0]
                                    + direction[1] * WALL_NORMAL[1]
                                    + direction[2] * WALL_NORMAL[2]);
            if (facing >= 0.8) {
                faceOnTruth += hit ? 1 : 0;
                faceOnPredicted += chance;
                ++faceOn;
            }
        }
        return new Measured(
                truth / counted,
                predicted / counted,
                faceOnTruth / faceOn,
                faceOnPredicted / faceOn);
    }

    /**
     * Scattered parts, whose voxels are statistical media, stop about as much as their triangles.
     */
    @Test
    void testScatteredPartsStopWhatTheirTrianglesDo() {
        final Measured leaves = measure(cards(400));
        assertEquals(1, leaves.ratio(), 0.15, "Leaves stop " + leaves);
        final Measured rods = measure(rods(200));
        assertEquals(1, rods.ratio(), 0.15, "Rods stop " + rods);
    }

    /**
     * A wall's voxels, mostly one surface, stop what crosses it face on and about as much as it
     * overall; a closed box stops nearly everything that crosses it.
     */
    @Test
    void testSurfacesStopWhatCrossesThem() {
        final Measured wall = measure(wall());
        assertTrue(wall.ratio() > 0.85, "A wall stops " + wall);
        assertTrue(wall.faceOnPredicted() > 0.75, "A wall face on stops " + wall);
        final Measured box = measure(box());
        assertTrue(box.ratio() > 0.9, "A box stops " + box);
    }

    /** A plane's normals have one main direction; normals facing every way have none. */
    @Test
    void testMainDirection() {
        final double[] plane = {0, 0, 0, 0, 0, 0};
        final double[] n = unit(new double[] {0.2, -0.3, 0.9});
        plane[0] = n[0] * n[0];
        plane[1] = n[1] * n[1];
        plane[2] = n[2] * n[2];
        plane[3] = n[0] * n[1];
        plane[4] = n[0] * n[2];
        plane[5] = n[1] * n[2];
        final double[] main = CoverageVoxelizer.mainDirection(plane);
        assertNotNull(main);
        assertEquals(1, Math.abs(main[0] * n[0] + main[1] * n[1] + main[2] * n[2]), 1e-6);
        assertNull(CoverageVoxelizer.mainDirection(new double[] {1, 1, 1, 0, 0, 0}));
        // Two crossed strips' normals, half each
        assertNull(CoverageVoxelizer.mainDirection(new double[] {1, 1, 0, 0, 0, 0}));
    }
}
