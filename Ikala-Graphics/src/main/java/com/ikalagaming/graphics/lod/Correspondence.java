package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Maps points on a simplified mesh back to the input it came from, by successive closest point
 * projection through the collapse history, as Liu, Zhang and Yuksel do in <i>Simplifying Textured
 * Triangle Meshes in the Wild</i> (2025), §4.3.1: walking back from the last collapse to the first,
 * whenever the point is on a face around that collapse's new vertex, it is projected onto the
 * closest face around the edge as it was before the collapse. Each step only looks at one small
 * neighborhood, so the point can't jump to a far surface the way a single closest point search on
 * the input could.
 *
 * <p>Simplification ignores appearance; this is how it comes back. For now it gives each simplified
 * triangle the material of the input triangle its center maps to. Texture rebaking can use the same
 * mapping per texel later.
 */
public final class Correspondence {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** The input. */
    private final LodMesh input;

    /** The collapse history. */
    private final Simplifier.History history;

    /** Where each face's collapses start in {@link #collapses}, plus an end. */
    private final int[] collapseStart;

    /**
     * The collapses each face was around right after, in increasing order, so walking back finds
     * the next one that matters without looking at the rest.
     */
    private final int[] collapses;

    /**
     * Set up a mapping.
     *
     * @param input The mesh that was simplified.
     * @param history Its collapse history.
     */
    public Correspondence(@NonNull LodMesh input, Simplifier.@NonNull History history) {
        this.input = input;
        this.history = history;
        // Face ids are input triangle indices
        collapseStart = new int[input.getTriangleCount() + 1];
        final int entries = history.afterStart.getInt(history.count);
        for (int k = 0; k < entries; ++k) {
            ++collapseStart[history.afterFaces.getInt(k) + 1];
        }
        for (int f = 0; f < input.getTriangleCount(); ++f) {
            collapseStart[f + 1] += collapseStart[f];
        }
        collapses = new int[entries];
        final int[] fill = Arrays.copyOf(collapseStart, input.getTriangleCount());
        for (int collapse = 0; collapse < history.count; ++collapse) {
            final int end = history.afterStart.getInt(collapse + 1);
            for (int k = history.afterStart.getInt(collapse); k < end; ++k) {
                collapses[fill[history.afterFaces.getInt(k)]++] = collapse;
            }
        }
    }

    /**
     * Give every triangle of a simplified mesh the material of the input triangle its center maps
     * to.
     *
     * @param input The mesh that was simplified.
     * @param result The simplification, with its history recorded. Its mesh's materials are
     *     replaced.
     * @throws IllegalArgumentException If the history wasn't recorded.
     */
    public static void assignMaterials(@NonNull LodMesh input, Simplifier.@NonNull Result result) {
        if (result.history() == null) {
            throw new IllegalArgumentException("Mapping back needs the collapse history");
        }
        final Correspondence map = new Correspondence(input, result.history());
        final LodMesh mesh = result.mesh();
        final double[] center = new double[AXES];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int axis = 0; axis < AXES; ++axis) {
                center[axis] =
                        (mesh.position(mesh.corner(t, 0), axis)
                                        + mesh.position(mesh.corner(t, 1), axis)
                                        + mesh.position(mesh.corner(t, 2), axis))
                                / AXES;
            }
            mesh.materials[t] = input.material(map.inputFace(center, result.faceIds()[t]));
        }
    }

    /**
     * Find the input face a point on the simplified mesh maps to.
     *
     * @param point The point, on the simplified mesh. It is moved as it is projected.
     * @param face The face it is on, by its id (which is its input index).
     * @return The input face it maps to.
     */
    public int inputFace(double @NonNull [] point, int face) {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] closest = new double[AXES];
        int current = face;
        for (int collapse = latestAround(current, history.count - 1);
                collapse >= 0;
                collapse = latestAround(current, collapse - 1)) {
            // Project onto the closest face around the edge before it collapsed
            double best = Double.MAX_VALUE;
            int bestFace = current;
            final double[] bestPoint = new double[AXES];
            final int end = history.beforeStart.getInt(collapse + 1);
            for (int k = history.beforeStart.getInt(collapse); k < end; ++k) {
                final int base = k * TRIANGLE_VALUES;
                for (int axis = 0; axis < AXES; ++axis) {
                    a[axis] = history.beforeCorners[base + axis];
                    b[axis] = history.beforeCorners[base + AXES + axis];
                    c[axis] = history.beforeCorners[base + 2 * AXES + axis];
                }
                Geometry.closestOnTriangle(point, a, b, c, closest);
                final double d = Geometry.distanceSquared(point, closest);
                if (d < best) {
                    best = d;
                    bestFace = history.beforeFaces.getInt(k);
                    System.arraycopy(closest, 0, bestPoint, 0, AXES);
                }
            }
            current = bestFace;
            System.arraycopy(bestPoint, 0, point, 0, AXES);
        }
        return current;
    }

    /**
     * The latest collapse, up to a limit, that a face was around the new vertex of right after.
     *
     * @param face The face.
     * @param limit The latest collapse to consider.
     * @return The collapse, or -1 if there is none.
     */
    private int latestAround(int face, int limit) {
        if (limit < 0) {
            return -1;
        }
        // The last of the face's collapses that is at most the limit
        int low = collapseStart[face];
        int high = collapseStart[face + 1] - 1;
        int found = -1;
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            if (collapses[middle] <= limit) {
                found = collapses[middle];
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    /**
     * The input this maps to.
     *
     * @return The input mesh.
     */
    public LodMesh getInput() {
        return input;
    }
}
