package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Distances between triangle meshes, by sampling one and finding each sample's nearest triangle in
 * the other. Samples are spread by size, so a large triangle bridging a gap is sampled over the gap
 * and not only at its corners.
 */
final class Hausdorff {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** Distances under this share of the sample spacing count as touching, so 0. */
    private static final double TOUCHING = 1e-3;

    /** 2^64 over the golden ratio, an odd multiplier that spreads hash bits. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /** Where samples go. */
    @FunctionalInterface
    interface Visitor {
        /**
         * Take a sample.
         *
         * @param point The point. Only valid during the call.
         * @param area The area it stands for: 0 for points that only count toward a maximum.
         */
        void visit(double @NonNull [] point, double area);
    }

    /**
     * Sample a triangle. It is cut into rows of equal smaller triangles, about the spacing apart:
     * each smaller triangle's center stands for its area, and the grid's corners, the triangle's
     * own corners and edges among them, are sampled with no area.
     *
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @param spacing About how far apart samples are.
     * @param withCorners Whether to sample the triangle's own corners, which a caller sampling each
     *     vertex once leaves out.
     * @param point Scratch for the sample point.
     * @param visitor Where the samples go.
     */
    static void sample(
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c,
            double spacing,
            boolean withCorners,
            double @NonNull [] point,
            @NonNull Visitor visitor) {
        final double longest =
                Math.sqrt(
                        Math.max(
                                Geometry.distanceSquared(a, b),
                                Math.max(
                                        Geometry.distanceSquared(b, c),
                                        Geometry.distanceSquared(c, a))));
        final int rows = Math.max(1, (int) Math.ceil(longest / spacing));
        final double share = Geometry.area(a, b, c) / ((double) rows * rows);
        for (int i = 0; i <= rows; ++i) {
            for (int j = 0; i + j <= rows; ++j) {
                final boolean corner = (i == 0 || i == rows) && (j == 0 || j == rows);
                if (withCorners || !corner) {
                    at(a, b, c, (double) i / rows, (double) j / rows, point);
                    visitor.visit(point, 0);
                }
                if (i + j < rows) {
                    // The upright smaller triangle with this corner
                    at(a, b, c, (i + 1.0 / AXES) / rows, (j + 1.0 / AXES) / rows, point);
                    visitor.visit(point, share);
                }
                if (i + j < rows - 1) {
                    // The upside down one beside it
                    at(a, b, c, (i + 2.0 / AXES) / rows, (j + 2.0 / AXES) / rows, point);
                    visitor.visit(point, share);
                }
            }
        }
    }

    /**
     * A point on a triangle.
     *
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @param u The weight of the second corner.
     * @param v The weight of the third.
     * @param out The point, written.
     */
    private static void at(
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c,
            double u,
            double v,
            double @NonNull [] out) {
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] = a[axis] + u * (b[axis] - a[axis]) + v * (c[axis] - a[axis]);
        }
    }

    /**
     * The two-sided distance between a simplified mesh and the mesh it replaces: the farther of
     * each one's samples from the other. The input side only counts visible triangles, so input
     * nothing can see may be removed. Triangles both meshes have unchanged are 0 apart and skipped,
     * and each vertex is sampled once.
     *
     * <p>A connected part of the input counts no more than its width, {@link Geometry#partWidths}:
     * taking a part away changes the picture by its own size, wherever the nearest surface left is.
     * Measured by distance, a small free part gone, like a sliver of trim a meter from any wall,
     * would cost as much as moving a wall that meter.
     *
     * @param simplified The simplified mesh.
     * @param input The mesh it replaces.
     * @param spacing About how far apart samples are.
     * @return The distance.
     */
    static double between(@NonNull LodMesh simplified, @NonNull LodMesh input, double spacing) {
        final LongIntMap simplifiedKeys = keys(simplified);
        final LongIntMap inputKeys = keys(input);
        final double out =
                oneWay(
                        simplified,
                        false,
                        new TriangleBvh(corners(input)),
                        inputKeys,
                        spacing,
                        0,
                        null,
                        null);
        // Only the larger side counts, so the second needs only samples farther than the first
        final int[] part = VirtualEdges.components(input);
        return oneWay(
                input,
                true,
                new TriangleBvh(corners(simplified)),
                simplifiedKeys,
                spacing,
                out,
                part,
                Geometry.partWidths(input, part));
    }

    /**
     * The farthest any sample on a mesh is from the nearest triangle of a tree.
     *
     * @param from The mesh sampled.
     * @param visibleOnly Whether to skip triangles under {@link Fidelity#VISIBLE}.
     * @param to The tree measured to.
     * @param same The triangles the tree's mesh has, by {@link #key}, which are skipped.
     * @param spacing About how far apart samples are.
     * @param atLeast A distance to start from, which samples must beat to count.
     * @param part Each vertex's connected part, by root vertex, or null to count every sample.
     * @param width Each part's width, by root vertex, that its samples count to at most; or null.
     * @return The distance, at least the starting one.
     */
    private static double oneWay(
            @NonNull LodMesh from,
            boolean visibleOnly,
            @NonNull TriangleBvh to,
            @NonNull LongIntMap same,
            double spacing,
            double atLeast,
            int[] part,
            double[] width) {
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        // The farthest so far, squared, and never under what counts as touching
        final double touching = spacing * TOUCHING;
        final double[] max = {Math.max(touching * touching, atLeast * atLeast)};
        final int[] last = {-1};
        // What the current part counts to at most, squared
        final double[] cap = {Double.POSITIVE_INFINITY};
        // Only a sample farther than the farthest so far matters. Neighboring samples usually share
        // a nearest triangle, so the last one is tried first, and a search stops at any triangle
        // that close.
        final Visitor visitor =
                (point, area) -> {
                    if (max[0] >= cap[0]) {
                        return;
                    }
                    if (last[0] >= 0 && to.distanceSquared(point, last[0], scratch) <= max[0]) {
                        return;
                    }
                    max[0] =
                            Math.max(
                                    max[0],
                                    to.nearest(point, Double.POSITIVE_INFINITY, max[0], scratch));
                    last[0] = scratch.triangle;
                };
        // The triangles to sample, part by part
        final int vertexCount = from.getVertexCount();
        final int triangleCount = from.getTriangleCount();
        final int[] start = new int[vertexCount + 1];
        final boolean[] sampled = new boolean[triangleCount];
        for (int t = 0; t < triangleCount; ++t) {
            sampled[t] =
                    !(visibleOnly && from.visibility(t) < Fidelity.VISIBLE)
                            && same.get(key(from, t)) == LongIntMap.MISSING;
            if (sampled[t]) {
                ++start[partOf(from, t, part) + 1];
            }
        }
        for (int v = 0; v < vertexCount; ++v) {
            start[v + 1] += start[v];
        }
        final int[] order = new int[start[vertexCount]];
        final int[] fill = Arrays.copyOf(start, vertexCount);
        for (int t = 0; t < triangleCount; ++t) {
            if (sampled[t]) {
                order[fill[partOf(from, t, part)]++] = t;
            }
        }
        final boolean[] vertices = new boolean[vertexCount];
        final IntArrayList partVertices = new IntArrayList();
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] point = new double[AXES];
        for (int root = 0; root < vertexCount; ++root) {
            if (start[root] == start[root + 1]) {
                continue;
            }
            cap[0] = width == null ? Double.POSITIVE_INFINITY : width[root] * width[root];
            if (cap[0] <= max[0]) {
                // Nothing in this part can count for more than what is already measured
                continue;
            }
            partVertices.clear();
            for (int i = start[root]; i < start[root + 1]; ++i) {
                final int t = order[i];
                Geometry.corners(from, t, a, b, c);
                sample(a, b, c, spacing, false, point, visitor);
                for (int corner = 0; corner < AXES; ++corner) {
                    final int v = from.corner(t, corner);
                    if (!vertices[v]) {
                        vertices[v] = true;
                        partVertices.addInt(v);
                    }
                }
            }
            for (int i = 0; i < partVertices.size(); ++i) {
                final int v = partVertices.getInt(i);
                for (int axis = 0; axis < AXES; ++axis) {
                    point[axis] = from.position(v, axis);
                }
                visitor.visit(point, 0);
            }
            max[0] = Math.min(max[0], cap[0]);
        }
        return max[0] <= touching * touching ? atLeast : Math.sqrt(max[0]);
    }

    /**
     * A triangle's connected part.
     *
     * @param mesh The mesh.
     * @param triangle The triangle.
     * @param part Each vertex's part, or null if all are one.
     * @return Its part's root vertex, or 0 if all are one.
     */
    private static int partOf(@NonNull LodMesh mesh, int triangle, int[] part) {
        return part == null ? 0 : part[mesh.corner(triangle, 0)];
    }

    /**
     * Every triangle of a mesh, by {@link #key}.
     *
     * @param mesh The mesh.
     * @return The keys.
     */
    private static LongIntMap keys(@NonNull LodMesh mesh) {
        final LongIntMap keys = new LongIntMap(mesh.getTriangleCount());
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            keys.putIfAbsent(key(mesh, t), t);
        }
        return keys;
    }

    /**
     * A hash of a triangle's corner positions, the same whichever corner it starts from, so the
     * same triangle in two meshes has the same key. Two different triangles share one only by a
     * 64-bit hash collision.
     *
     * @param mesh The mesh.
     * @param triangle The triangle.
     * @return The key.
     */
    private static long key(@NonNull LodMesh mesh, int triangle) {
        // Start from the corner with the smallest position, keeping the winding
        int first = 0;
        for (int corner = 1; corner < AXES; ++corner) {
            if (compare(mesh, mesh.corner(triangle, corner), mesh.corner(triangle, first)) < 0) {
                first = corner;
            }
        }
        long hash = 0;
        for (int k = 0; k < AXES; ++k) {
            final int v = mesh.corner(triangle, (first + k) % AXES);
            for (int axis = 0; axis < AXES; ++axis) {
                hash = (hash ^ Float.floatToIntBits(mesh.position(v, axis))) * GOLDEN;
                hash ^= hash >>> Integer.SIZE;
            }
        }
        return hash;
    }

    /**
     * Order two vertices by position.
     *
     * @param mesh The mesh.
     * @param v One vertex.
     * @param w The other.
     * @return Negative, 0 or positive as v's position comes before, equals or follows w's.
     */
    private static int compare(@NonNull LodMesh mesh, int v, int w) {
        for (int axis = 0; axis < AXES; ++axis) {
            final int order = Float.compare(mesh.position(v, axis), mesh.position(w, axis));
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /**
     * Every triangle's corners, in one array.
     *
     * @param mesh The mesh.
     * @return The corners, 9 floats per triangle.
     */
    private static float[] corners(@NonNull LodMesh mesh) {
        final float[] out = new float[mesh.getTriangleCount() * TRIANGLE_VALUES];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                for (int axis = 0; axis < AXES; ++axis) {
                    out[t * TRIANGLE_VALUES + corner * AXES + axis] = mesh.position(v, axis);
                }
            }
        }
        return out;
    }

    /**
     * Sample some triangles.
     *
     * @param triangles The triangles, 9 floats each.
     * @param visibility Each one's visibility, to skip those under {@link Fidelity#VISIBLE}, or
     *     null to sample all of them.
     * @param spacing About how far apart samples are.
     * @param visitor Where the samples go.
     */
    static void each(
            float @NonNull [] triangles,
            float[] visibility,
            double spacing,
            @NonNull Visitor visitor) {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] point = new double[AXES];
        for (int t = 0; t < triangles.length / TRIANGLE_VALUES; ++t) {
            if (visibility != null && visibility[t] < Fidelity.VISIBLE) {
                continue;
            }
            final int base = t * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                a[axis] = triangles[base + axis];
                b[axis] = triangles[base + AXES + axis];
                c[axis] = triangles[base + 2 * AXES + axis];
            }
            sample(a, b, c, spacing, true, point, visitor);
        }
    }

    /** Static helpers only. */
    private Hausdorff() {}
}
