package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;

/**
 * A bounding volume hierarchy over triangles, for asking whether a ray hits anything within a
 * distance. Nodes split at the middle of their triangles' centers along the longest axis, down to a
 * few triangles each. Everything is in flat arrays, and queries allocate nothing, so many threads
 * can query one tree at once.
 */
final class TriangleBvh {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** Triangles in a leaf at most. */
    private static final int LEAF_SIZE = 4;

    /**
     * Room a query's stack needs. Splits at the middle of the centers make an unbalanced tree at
     * worst as deep as there are nodes, but the stack only holds one pending sibling per level.
     */
    static final int STACK_SIZE = 256;

    /** Hits closer than this along the ray, as a share of its length, are ignored. */
    private static final double MIN_T = 1e-9;

    /** Determinants smaller than this are rays parallel to the triangle. */
    private static final double PARALLEL = 1e-12;

    /** The triangles' corners, reordered so each leaf's triangles are together. */
    private final float[] corners;

    /** Each node's bounds: min x, y, z, then max x, y, z. */
    private float[] bounds = new float[2 * AXES * LEAF_SIZE];

    /** Each node's first child, or for a leaf, its first triangle. */
    private int[] first = new int[LEAF_SIZE];

    /** How many triangles a leaf has, or 0 for an inner node, whose children are adjacent. */
    private int[] count = new int[LEAF_SIZE];

    /** How many nodes there are. */
    private int nodeCount;

    /**
     * Build a tree.
     *
     * @param triangles The triangles' corners, 9 floats each.
     */
    TriangleBvh(float @NonNull [] triangles) {
        final int n = triangles.length / TRIANGLE_VALUES;
        final int[] order = new int[n];
        final float[] centers = new float[n * AXES];
        for (int t = 0; t < n; ++t) {
            order[t] = t;
            for (int axis = 0; axis < AXES; ++axis) {
                centers[t * AXES + axis] =
                        (triangles[t * TRIANGLE_VALUES + axis]
                                        + triangles[t * TRIANGLE_VALUES + AXES + axis]
                                        + triangles[t * TRIANGLE_VALUES + 2 * AXES + axis])
                                / AXES;
            }
        }
        newNode();
        // Split nodes breadth first, without recursion: each entry is a node and its range
        final IntArrayList work = new IntArrayList();
        work.addInt(0);
        work.addInt(0);
        work.addInt(n);
        for (int w = 0; w < work.size(); w += AXES) {
            final int node = work.getInt(w);
            final int start = work.getInt(w + 1);
            final int end = work.getInt(w + 2);
            fit(node, triangles, order, start, end);
            if (end - start <= LEAF_SIZE) {
                first[node] = start;
                count[node] = end - start;
                continue;
            }
            final int middle = split(centers, order, start, end);
            final int left = newNode();
            newNode();
            first[node] = left;
            count[node] = 0;
            work.addInt(left);
            work.addInt(start);
            work.addInt(middle);
            work.addInt(left + 1);
            work.addInt(middle);
            work.addInt(end);
        }
        corners = new float[n * TRIANGLE_VALUES];
        for (int i = 0; i < n; ++i) {
            System.arraycopy(
                    triangles,
                    order[i] * TRIANGLE_VALUES,
                    corners,
                    i * TRIANGLE_VALUES,
                    TRIANGLE_VALUES);
        }
    }

    /**
     * Add a node.
     *
     * @return Its index.
     */
    private int newNode() {
        if (nodeCount == first.length) {
            first = Arrays.copyOf(first, nodeCount * 2);
            count = Arrays.copyOf(count, nodeCount * 2);
            bounds = Arrays.copyOf(bounds, nodeCount * 2 * 2 * AXES);
        }
        return nodeCount++;
    }

    /**
     * Set a node's bounds around its triangles.
     *
     * @param node The node.
     * @param triangles The corners.
     * @param order The triangle order.
     * @param start The node's first entry in the order.
     * @param end One past its last.
     */
    private void fit(
            int node, float @NonNull [] triangles, int @NonNull [] order, int start, int end) {
        final int at = node * 2 * AXES;
        Arrays.fill(bounds, at, at + AXES, Float.MAX_VALUE);
        Arrays.fill(bounds, at + AXES, at + 2 * AXES, -Float.MAX_VALUE);
        for (int i = start; i < end; ++i) {
            final int base = order[i] * TRIANGLE_VALUES;
            for (int corner = 0; corner < AXES; ++corner) {
                for (int axis = 0; axis < AXES; ++axis) {
                    final float value = triangles[base + corner * AXES + axis];
                    bounds[at + axis] = Math.min(bounds[at + axis], value);
                    bounds[at + AXES + axis] = Math.max(bounds[at + AXES + axis], value);
                }
            }
        }
    }

    /**
     * Split a range at the middle of its centers along their longest axis, or in half if they all
     * fall on one side.
     *
     * @param centers The triangle centers.
     * @param order The triangle order, partitioned in place.
     * @param start The range's start.
     * @param end One past its end.
     * @return Where the second half starts.
     */
    private static int split(float @NonNull [] centers, int @NonNull [] order, int start, int end) {
        final float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = start; i < end; ++i) {
            for (int axis = 0; axis < AXES; ++axis) {
                final float c = centers[order[i] * AXES + axis];
                lo[axis] = Math.min(lo[axis], c);
                hi[axis] = Math.max(hi[axis], c);
            }
        }
        int axis = 0;
        for (int a = 1; a < AXES; ++a) {
            if (hi[a] - lo[a] > hi[axis] - lo[axis]) {
                axis = a;
            }
        }
        final float middle = (lo[axis] + hi[axis]) / 2;
        int i = start;
        int j = end - 1;
        while (i <= j) {
            if (centers[order[i] * AXES + axis] < middle) {
                ++i;
            } else {
                final int swap = order[i];
                order[i] = order[j];
                order[j] = swap;
                --j;
            }
        }
        if (i == start || i == end) {
            return (start + end) >>> 1;
        }
        return i;
    }

    /**
     * Whether a segment hits any triangle.
     *
     * @param origin Where the segment starts.
     * @param direction Where it goes, its length the segment's length.
     * @param stack Scratch for the query, at least {@link #STACK_SIZE} long.
     * @return True if it hits a triangle between its ends.
     */
    boolean anyHit(double @NonNull [] origin, double @NonNull [] direction, int @NonNull [] stack) {
        final double inverseX = 1 / direction[0];
        final double inverseY = 1 / direction[1];
        final double inverseZ = 1 / direction[2];
        int top = 0;
        stack[top++] = 0;
        while (top > 0) {
            final int node = stack[--top];
            if (!crossesBox(node, origin, inverseX, inverseY, inverseZ)) {
                continue;
            }
            if (count[node] == 0) {
                if (top + 2 > stack.length) {
                    throw new IllegalStateException("The tree is too deep to search");
                }
                stack[top++] = first[node];
                stack[top++] = first[node] + 1;
                continue;
            }
            for (int t = first[node]; t < first[node] + count[node]; ++t) {
                if (crossesTriangle(t * TRIANGLE_VALUES, origin, direction)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the segment from the origin along the direction (t in 0..1) meets a node's box.
     *
     * @param node The node.
     * @param origin The origin.
     * @param inverseX One over the direction's x.
     * @param inverseY One over its y.
     * @param inverseZ One over its z.
     * @return True if it does.
     */
    private boolean crossesBox(
            int node,
            double @NonNull [] origin,
            double inverseX,
            double inverseY,
            double inverseZ) {
        final int at = node * 2 * AXES;
        double near = 0;
        double far = 1;
        for (int axis = 0; axis < AXES; ++axis) {
            final double inverse = axis == 0 ? inverseX : (axis == 1 ? inverseY : inverseZ);
            double t0 = (bounds[at + axis] - origin[axis]) * inverse;
            double t1 = (bounds[at + AXES + axis] - origin[axis]) * inverse;
            if (t0 > t1) {
                final double swap = t0;
                t0 = t1;
                t1 = swap;
            }
            // NaN from 0 times infinity means the ray runs along the slab's face: keep it
            if (!Double.isNaN(t0)) {
                near = Math.max(near, t0);
            }
            if (!Double.isNaN(t1)) {
                far = Math.min(far, t1);
            }
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the segment meets a triangle, by the Möller–Trumbore test.
     *
     * @param base Where the triangle's corners start.
     * @param origin The segment's start.
     * @param direction The segment, start to end.
     * @return True if they meet at t in (0, 1].
     */
    private boolean crossesTriangle(
            int base, double @NonNull [] origin, double @NonNull [] direction) {
        final double ax = corners[base];
        final double ay = corners[base + 1];
        final double az = corners[base + 2];
        final double e1x = corners[base + 3] - ax;
        final double e1y = corners[base + 4] - ay;
        final double e1z = corners[base + 5] - az;
        final double e2x = corners[base + 6] - ax;
        final double e2y = corners[base + 7] - ay;
        final double e2z = corners[base + 8] - az;
        final double px = direction[1] * e2z - direction[2] * e2y;
        final double py = direction[2] * e2x - direction[0] * e2z;
        final double pz = direction[0] * e2y - direction[1] * e2x;
        final double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < PARALLEL) {
            return false;
        }
        final double inverse = 1 / det;
        final double sx = origin[0] - ax;
        final double sy = origin[1] - ay;
        final double sz = origin[2] - az;
        final double u = (sx * px + sy * py + sz * pz) * inverse;
        if (u < 0 || u > 1) {
            return false;
        }
        final double qx = sy * e1z - sz * e1y;
        final double qy = sz * e1x - sx * e1z;
        final double qz = sx * e1y - sy * e1x;
        final double v = (direction[0] * qx + direction[1] * qy + direction[2] * qz) * inverse;
        if (v < 0 || u + v > 1) {
            return false;
        }
        final double t = (e2x * qx + e2y * qy + e2z * qz) * inverse;
        return t > MIN_T && t <= 1;
    }
}
