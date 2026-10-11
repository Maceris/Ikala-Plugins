package com.ikalagaming.graphics.lod;

import lombok.NonNull;

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

    /**
     * Set up a mapping.
     *
     * @param input The mesh that was simplified.
     * @param history Its collapse history.
     */
    public Correspondence(@NonNull LodMesh input, Simplifier.@NonNull History history) {
        this.input = input;
        this.history = history;
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
    public static void assignMaterials(
            @NonNull LodMesh input, Simplifier.@NonNull Result result) {
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
        for (int collapse = history.count - 1; collapse >= 0; --collapse) {
            if (!aroundNewVertex(collapse, current)) {
                continue;
            }
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
     * Whether a face was around a collapse's new vertex, right after that collapse.
     *
     * @param collapse The collapse.
     * @param face The face.
     * @return True if it was.
     */
    private boolean aroundNewVertex(int collapse, int face) {
        int low = history.afterStart.getInt(collapse);
        int high = history.afterStart.getInt(collapse + 1) - 1;
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            final int value = history.afterFaces.getInt(middle);
            if (value < face) {
                low = middle + 1;
            } else if (value > face) {
                high = middle - 1;
            } else {
                return true;
            }
        }
        return false;
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
