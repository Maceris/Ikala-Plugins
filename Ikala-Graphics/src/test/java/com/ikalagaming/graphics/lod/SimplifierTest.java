package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Tests for the simplifier, its quadrics, virtual edges and the mapping back to the input. */
class SimplifierTest {

    /** Close enough to zero. */
    private static final double TOLERANCE = 1e-6;

    /**
     * A grid of squares in the xz plane, each split into two triangles, facing up.
     *
     * @param size Squares along each side.
     * @param height The height at each grid point, or null for flat.
     * @param lockBorder Whether to lock the outer border.
     * @return The mesh.
     */
    private static LodMesh grid(int size, double[][] height, boolean lockBorder) {
        LodMesh.Builder builder = LodMesh.builder(new double[3]);
        int[][] index = new int[size + 1][size + 1];
        for (int x = 0; x <= size; ++x) {
            for (int z = 0; z <= size; ++z) {
                final float y = height == null ? 0 : (float) height[x][z];
                index[x][z] = builder.vertex(x, y, z);
                if (lockBorder && (x == 0 || z == 0 || x == size || z == size)) {
                    builder.lock(index[x][z]);
                }
            }
        }
        for (int x = 0; x < size; ++x) {
            for (int z = 0; z < size; ++z) {
                // Counter-clockwise seen from +y
                builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], 0);
                builder.triangle(index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], 0);
            }
        }
        return builder.build();
    }

    /**
     * A closed axis-aligned box, facing out.
     *
     * @param builder Where to add it.
     * @param min The minimum corner.
     * @param max The maximum corner.
     * @param material Its material.
     */
    private static void box(LodMesh.Builder builder, float[] min, float[] max, int material) {
        int[] v = new int[8];
        for (int i = 0; i < 8; ++i) {
            v[i] =
                    builder.vertex(
                            (i & 1) == 0 ? min[0] : max[0],
                            (i & 2) == 0 ? min[1] : max[1],
                            (i & 4) == 0 ? min[2] : max[2]);
        }
        int[][] quads = {
            {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
        };
        for (int[] q : quads) {
            builder.triangle(v[q[0]], v[q[1]], v[q[2]], material);
            builder.triangle(v[q[0]], v[q[2]], v[q[3]], material);
        }
    }

    /** A flat grid simplifies to a handful of triangles with no error, keeping its area. */
    @Test
    void testFlatGridHasNoError() {
        final LodMesh flat = grid(8, null, false);
        final Simplifier.Result result =
                Simplifier.simplify(flat, Simplifier.Options.toCount(2, 0));
        assertTrue(
                result.mesh().getTriangleCount() <= 4,
                "Got " + result.mesh().getTriangleCount() + " triangles");
        assertEquals(0, result.error(), TOLERANCE);
        assertEquals(flat.area(), result.mesh().area(), TOLERANCE);
    }

    /** Locked vertices stay, exactly where they were. */
    @Test
    void testLockedVerticesStay() {
        final LodMesh mesh = grid(8, bumps(8, 1), true);
        Set<String> before = new HashSet<>();
        for (int v = 0; v < mesh.getVertexCount(); ++v) {
            if (mesh.isLocked(v)) {
                before.add(key(mesh, v));
            }
        }
        final LodMesh simplified =
                Simplifier.simplify(mesh, Simplifier.Options.toCount(1, 0)).mesh();
        Set<String> after = new HashSet<>();
        for (int v = 0; v < simplified.getVertexCount(); ++v) {
            if (simplified.isLocked(v)) {
                after.add(key(simplified, v));
            }
        }
        assertEquals(before, after);
    }

    /**
     * A two-sided sheet standing on an edge between two locked vertices where another group meets
     * it keeps a face on that edge, though its two sides' faces there go together in one collapse.
     */
    @Test
    void testSheetKeepsLockedEdge() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int a = builder.vertex(0, 0, 0);
        final int b = builder.vertex(1, 0, 0);
        final int c = builder.vertex(0, 0.5f, 0);
        final int d = builder.vertex(1, 0.5f, 0);
        builder.lock(a);
        builder.lock(b);
        // Front, then back
        builder.triangle(a, b, d, 0);
        builder.triangle(a, d, c, 0);
        builder.triangle(a, d, b, 0);
        builder.triangle(a, c, d, 0);
        final LodMesh simplified =
                Simplifier.simplify(
                                builder.build(),
                                Simplifier.Options.toCount(0, 0)
                                        .meetingAt(new long[] {LongIntMap.pair(a, b)}))
                        .mesh();
        boolean kept = false;
        for (int t = 0; t < simplified.getTriangleCount(); ++t) {
            boolean usesA = false;
            boolean usesB = false;
            for (int corner = 0; corner < 3; ++corner) {
                final int v = simplified.corner(t, corner);
                usesA |= simplified.position(v, 0) == 0 && simplified.position(v, 1) == 0;
                usesB |= simplified.position(v, 0) == 1 && simplified.position(v, 1) == 0;
            }
            kept |= usesA && usesB;
        }
        assertTrue(kept, "No face left on the locked edge");
    }

    /**
     * Two locked vertices another group shares get no new edge between them, which that group could
     * make too; without the group they may.
     */
    @Test
    void testNoNewEdgeBetweenSharedCorners() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int a = builder.vertex(0, 0, 0);
        final int m = builder.vertex(1, 0, 0);
        final int b = builder.vertex(2, 0, 0);
        final int c = builder.vertex(1, 0, 1);
        builder.lock(a);
        builder.lock(b);
        builder.triangle(a, c, m, 0);
        builder.triangle(m, c, b, 0);
        final LodMesh mesh = builder.build();
        final int[][] sharers = new int[mesh.getVertexCount()][];
        for (int v = 0; v < sharers.length; ++v) {
            sharers[v] = mesh.isLocked(v) ? new int[] {1} : new int[0];
        }
        assertTrue(joins(Simplifier.simplify(mesh, Simplifier.Options.toCount(0, 0)).mesh(), 0, 2));
        assertTrue(
                !joins(
                        Simplifier.simplify(
                                        mesh, Simplifier.Options.toCount(0, 0).sharedWith(sharers))
                                .mesh(),
                        0,
                        2));
    }

    /**
     * Whether a triangle has corners at two points on the x axis.
     *
     * @param mesh The mesh.
     * @param x0 One point's x.
     * @param x1 The other's.
     * @return True if one does.
     */
    private static boolean joins(LodMesh mesh, double x0, double x1) {
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            boolean first = false;
            boolean second = false;
            for (int corner = 0; corner < 3; ++corner) {
                final int v = mesh.corner(t, corner);
                final boolean onAxis = mesh.position(v, 1) == 0 && mesh.position(v, 2) == 0;
                first |= onAxis && mesh.position(v, 0) == x0;
                second |= onAxis && mesh.position(v, 0) == x1;
            }
            if (first && second) {
                return true;
            }
        }
        return false;
    }

    /** Simplifying to half gets to half, and no triangle turns over. */
    @Test
    void testHalvesWithoutFlips() {
        final LodMesh mesh = grid(16, bumps(16, 2), false);
        final int half = mesh.getTriangleCount() / 2;
        final Simplifier.Result result =
                Simplifier.simplify(mesh, Simplifier.Options.toCount(half, 0));
        assertTrue(result.mesh().getTriangleCount() <= half);
        assertTrue(result.error() > 0);
        final double[] a = new double[3];
        final double[] b = new double[3];
        final double[] c = new double[3];
        final double[] n = new double[3];
        for (int t = 0; t < result.mesh().getTriangleCount(); ++t) {
            Geometry.corners(result.mesh(), t, a, b, c);
            Geometry.normal(a, b, c, n);
            assertTrue(n[1] > 0, "Triangle " + t + " faces down");
        }
    }

    /**
     * Two boxes a little apart get virtual edges at a big enough radius, and none at a small one.
     */
    @Test
    void testVirtualEdgesJoinNearbyParts() {
        LodMesh.Builder builder = LodMesh.builder(new double[3]);
        box(builder, new float[] {0, 0, 0}, new float[] {1, 1, 1}, 0);
        box(builder, new float[] {1.1f, 0, 0}, new float[] {2.1f, 1, 1}, 0);
        final LodMesh boxes = builder.build();
        assertTrue(VirtualEdges.find(boxes, 0.1).length > 0);
        assertEquals(0, VirtualEdges.find(boxes, 0.04).length);

        // With them, the two boxes can merge into one part
        final LodMesh merged =
                Simplifier.simplify(boxes, Simplifier.Options.toCount(12, 0.1)).mesh();
        final int[] component = VirtualEdges.components(merged);
        for (int v = 1; v < merged.getVertexCount(); ++v) {
            assertEquals(component[0], component[v]);
        }
    }

    /** The exact triangle distance matches dense sampling. */
    @Test
    void testTriangleDistanceMatchesSampling() {
        Random random = new Random(7);
        for (int trial = 0; trial < 50; ++trial) {
            double[] a = new double[9];
            double[] b = new double[9];
            for (int i = 0; i < 9; ++i) {
                a[i] = random.nextDouble();
                b[i] = random.nextDouble() + (i % 3 == 0 ? 1.5 : 0);
            }
            final double exact = Math.sqrt(Geometry.triangleDistanceSquared(a, b));
            final double sampled = Math.sqrt(sampledDistanceSquared(a, b));
            assertTrue(exact <= sampled + TOLERANCE, "Exact distance is above a sampled one");
            assertTrue(sampled - exact < 0.05, "Exact " + exact + " vs sampled " + sampled);
        }
    }

    /**
     * A plane quadric's error grows with the square of the distance; a boundary quadric's with
     * area.
     */
    @Test
    void testQuadricErrors() {
        double[] q = new double[Quadric.STRIDE];
        // A right triangle of area 2 in the plane y = 0
        Quadric.addFace(
                q, 0, new double[] {0, 0, 0}, new double[] {0, 0, 2}, new double[] {2, 0, 0});
        final double weight = q[Quadric.WEIGHT];
        assertEquals(2.0 / 3, weight, TOLERANCE);
        assertEquals(weight * 9, Quadric.error(q, 0, new double[] {5, 3, 5}), TOLERANCE);
        assertEquals(0, Quadric.error(q, 0, new double[] {7, 0, -4}), TOLERANCE);

        double[] edge = new double[Quadric.STRIDE];
        Quadric.addBoundaryEdge(edge, 0, new double[] {0, 0, 0}, new double[] {4, 0, 0});
        // x is 3 from the edge: (2 * area)^2 / 2 = (4 * 3)^2 / 2
        assertEquals(72, Quadric.error(edge, 0, new double[] {1, 3, 0}), TOLERANCE);
        assertEquals(0, Quadric.error(edge, 0, new double[] {9, 0, 0}), TOLERANCE);
    }

    /** Each half of a two-material plane keeps its material away from the seam. */
    @Test
    void testCorrespondenceKeepsMaterials() {
        LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int size = 16;
        int[][] index = new int[size + 1][size + 1];
        for (int x = 0; x <= size; ++x) {
            for (int z = 0; z <= size; ++z) {
                index[x][z] = builder.vertex(x, 0, z);
            }
        }
        for (int x = 0; x < size; ++x) {
            for (int z = 0; z < size; ++z) {
                final int material = x < size / 2 ? 1 : 2;
                builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], material);
                builder.triangle(index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], material);
            }
        }
        final LodMesh plane = builder.build();
        final Simplifier.Result result =
                Simplifier.simplify(
                        plane, Simplifier.Options.toCount(plane.getTriangleCount() / 4, 0));
        Correspondence.assignMaterials(plane, result);
        final LodMesh mesh = result.mesh();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            double cx = 0;
            for (int corner = 0; corner < 3; ++corner) {
                cx += mesh.position(mesh.corner(t, corner), 0) / 3;
            }
            if (cx < size / 2.0 - 2) {
                assertEquals(1, mesh.material(t), "Triangle centered at x " + cx);
            } else if (cx > size / 2.0 + 2) {
                assertEquals(2, mesh.material(t), "Triangle centered at x " + cx);
            }
        }
    }

    /**
     * How long simplifying takes, logged rather than asserted, so it can't be flaky: a bumpy height
     * field, and a scatter of separate boxes that only merge through virtual edges.
     */
    @Test
    void testTimingLogged() {
        final LodMesh field = grid(128, bumps(128, 3), false);
        // Warm up, then time
        Simplifier.simplify(field, Simplifier.Options.toCount(field.getTriangleCount() / 2, 0));
        long start = System.nanoTime();
        Simplifier.Result result =
                Simplifier.simplify(
                        field, Simplifier.Options.toCount(field.getTriangleCount() / 2, 0));
        System.out.printf(
                "Height field: %d to %d triangles in %.1f ms, error %.4f%n",
                field.getTriangleCount(),
                result.mesh().getTriangleCount(),
                (System.nanoTime() - start) / 1e6,
                result.error());

        LodMesh.Builder builder = LodMesh.builder(new double[3]);
        Random random = new Random(3);
        for (int i = 0; i < 1000; ++i) {
            final float x = random.nextInt(40);
            final float y = random.nextInt(4);
            final float z = random.nextInt(40);
            box(builder, new float[] {x, y, z}, new float[] {x + 0.6f, y + 0.6f, z + 0.6f}, i % 3);
        }
        final LodMesh scatter = builder.build();
        start = System.nanoTime();
        result =
                Simplifier.simplify(
                        scatter, Simplifier.Options.toCount(scatter.getTriangleCount() / 2, 0.25));
        System.out.printf(
                "Scatter: %d to %d triangles, %d virtual edges, in %.1f ms, error %.4f%n",
                scatter.getTriangleCount(),
                result.mesh().getTriangleCount(),
                result.virtualEdges(),
                (System.nanoTime() - start) / 1e6,
                result.error());
        start = System.nanoTime();
        Correspondence.assignMaterials(scatter, result);
        System.out.printf(
                "Scatter materials mapped in %.1f ms%n", (System.nanoTime() - start) / 1e6);
    }

    /**
     * Smooth bumps for a height field.
     *
     * @param size Squares along each side.
     * @param amplitude How high they go.
     * @return The heights.
     */
    private static double[][] bumps(int size, double amplitude) {
        double[][] height = new double[size + 1][size + 1];
        for (int x = 0; x <= size; ++x) {
            for (int z = 0; z <= size; ++z) {
                height[x][z] = amplitude * Math.sin(x * 0.7) * Math.cos(z * 0.5);
            }
        }
        return height;
    }

    /**
     * A vertex's position as text, for comparing sets.
     *
     * @param mesh The mesh.
     * @param v The vertex.
     * @return The key.
     */
    private static String key(LodMesh mesh, int v) {
        return mesh.position(v, 0) + "," + mesh.position(v, 1) + "," + mesh.position(v, 2);
    }

    /**
     * The least squared distance between dense samples of two triangles.
     *
     * @param a The first triangle.
     * @param b The second triangle.
     * @return The squared distance.
     */
    private static double sampledDistanceSquared(double[] a, double[] b) {
        final int steps = 40;
        double best = Double.MAX_VALUE;
        double[][] sa = samples(a, steps);
        double[][] sb = samples(b, steps);
        for (double[] p : sa) {
            for (double[] q : sb) {
                best = Math.min(best, Geometry.distanceSquared(p, q));
            }
        }
        return best;
    }

    /**
     * Points spread over a triangle.
     *
     * @param t The triangle's corners.
     * @param steps Steps along each side.
     * @return The points.
     */
    private static double[][] samples(double[] t, int steps) {
        double[][] points = new double[(steps + 1) * (steps + 2) / 2][];
        int n = 0;
        for (int i = 0; i <= steps; ++i) {
            for (int j = 0; i + j <= steps; ++j) {
                final double u = (double) i / steps;
                final double v = (double) j / steps;
                final double w = 1 - u - v;
                points[n++] =
                        new double[] {
                            w * t[0] + u * t[3] + v * t[6],
                            w * t[1] + u * t[4] + v * t[7],
                            w * t[2] + u * t[5] + v * t[8]
                        };
            }
        }
        return points;
    }
}
