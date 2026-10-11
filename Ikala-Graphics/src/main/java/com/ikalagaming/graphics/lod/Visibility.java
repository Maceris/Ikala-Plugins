package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * How visible each triangle is from outside, for weighting simplification by visibility as Liu,
 * Zhang and Yuksel suggest (<i>Simplifying Textured Triangle Meshes in the Wild</i>, 2025, Fig. 20,
 * after Hoppe 1997). Rays leave each triangle's center over its outward hemisphere, and its
 * visibility is the share that travel a set reach without hitting anything: open ground reads near
 * 1, and a cave ceiling or the underside of an overhang near 0, so simplifying moves those freely
 * instead of pushing them through the visible surface.
 *
 * <p>It is local, like ambient occlusion: only geometry within the reach counts, so an edit only
 * changes the visibility of triangles near it. The directions are a fixed cosine-weighted set, so
 * the same input always gives the same result.
 */
final class Visibility {

    /** Rays per triangle. */
    static final int RAYS = 16;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** How far off its surface a ray starts, as a share of the reach. */
    private static final double LIFT = 1e-4;

    /** Triangles per task when spreading the work over threads. */
    private static final int BATCH = 1024;

    /** Bits in an int, for the radical inverse. */
    private static final double TWO_TO_32 = 0x1p32;

    /**
     * The ray directions in a triangle's own frame, z out of the surface: a Hammersley set, cosine
     * weighted, so rays near the surface count as little as they light.
     */
    private static final double[] DIRECTIONS = directions();

    /**
     * Work out the directions.
     *
     * @return x, y, z for each ray.
     */
    private static double[] directions() {
        final double[] out = new double[RAYS * AXES];
        for (int i = 0; i < RAYS; ++i) {
            final double u = (i + 0.5) / RAYS;
            final double v = Integer.toUnsignedLong(Integer.reverse(i)) / TWO_TO_32;
            final double r = Math.sqrt(u);
            final double phi = 2 * Math.PI * v;
            out[i * AXES] = r * Math.cos(phi);
            out[i * AXES + 1] = r * Math.sin(phi);
            out[i * AXES + 2] = Math.sqrt(1 - u);
        }
        return out;
    }

    /**
     * Work out every triangle's visibility.
     *
     * @param triangles The triangles' corners, 9 floats each, which also block the rays.
     * @param reach How far a ray must get to count as escaping.
     * @param pool Threads to share the work over, or null for this one.
     * @return Each triangle's visibility, from 0 (hidden) to 1 (open).
     */
    static float[] compute(float @NonNull [] triangles, double reach, ExecutorService pool) {
        final int n = triangles.length / TRIANGLE_VALUES;
        final float[] out = new float[n];
        final TriangleBvh bvh = new TriangleBvh(triangles);
        if (pool == null) {
            range(triangles, bvh, reach, out, 0, n);
            return out;
        }
        List<Future<?>> work = new ArrayList<>();
        for (int start = 0; start < n; start += BATCH) {
            final int from = start;
            final int to = Math.min(n, start + BATCH);
            work.add(pool.submit(() -> range(triangles, bvh, reach, out, from, to)));
        }
        try {
            for (Future<?> future : work) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted working out visibility", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not work out visibility", e.getCause());
        }
        return out;
    }

    /**
     * Work out some triangles' visibility.
     *
     * @param triangles The corners.
     * @param bvh The tree over them.
     * @param reach How far a ray must get.
     * @param out Where the results go.
     * @param from The first triangle.
     * @param to One past the last.
     */
    private static void range(
            float @NonNull [] triangles,
            @NonNull TriangleBvh bvh,
            double reach,
            float @NonNull [] out,
            int from,
            int to) {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] n = new double[AXES];
        final double[] tangent = new double[AXES];
        final double[] bitangent = new double[AXES];
        final double[] origin = new double[AXES];
        final double[] ray = new double[AXES];
        final int[] stack = new int[TriangleBvh.STACK_SIZE];
        for (int t = from; t < to; ++t) {
            final int base = t * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                a[axis] = triangles[base + axis];
                b[axis] = triangles[base + AXES + axis];
                c[axis] = triangles[base + 2 * AXES + axis];
            }
            Geometry.normal(a, b, c, n);
            final double length = Math.sqrt(Geometry.dot(n, n));
            if (length == 0) {
                out[t] = 1;
                continue;
            }
            for (int axis = 0; axis < AXES; ++axis) {
                n[axis] /= length;
            }
            frame(n, tangent, bitangent);
            for (int axis = 0; axis < AXES; ++axis) {
                origin[axis] = (a[axis] + b[axis] + c[axis]) / AXES + n[axis] * reach * LIFT;
            }
            int escaped = 0;
            for (int i = 0; i < RAYS; ++i) {
                final double x = DIRECTIONS[i * AXES];
                final double y = DIRECTIONS[i * AXES + 1];
                final double z = DIRECTIONS[i * AXES + 2];
                for (int axis = 0; axis < AXES; ++axis) {
                    ray[axis] = reach * (x * tangent[axis] + y * bitangent[axis] + z * n[axis]);
                }
                if (!bvh.anyHit(origin, ray, stack)) {
                    ++escaped;
                }
            }
            out[t] = (float) escaped / RAYS;
        }
    }

    /**
     * Two unit vectors at right angles to a unit normal and to each other (Duff et al. 2017).
     *
     * @param n The normal.
     * @param tangent The first, written.
     * @param bitangent The second, written.
     */
    private static void frame(
            double @NonNull [] n, double @NonNull [] tangent, double @NonNull [] bitangent) {
        final double sign = Math.copySign(1, n[2]);
        final double p = -1 / (sign + n[2]);
        final double q = n[0] * n[1] * p;
        tangent[0] = 1 + sign * n[0] * n[0] * p;
        tangent[1] = sign * q;
        tangent[2] = -sign * n[0];
        bitangent[0] = q;
        bitangent[1] = sign + n[1] * n[1] * p;
        bitangent[2] = -n[1];
    }

    /** Static helpers only. */
    private Visibility() {}
}
