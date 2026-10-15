package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns clusters into meshes in the {@link LodVertex} format. Projected triangles are flat, their
 * normals worked out when they are drawn, so a vertex is shared wherever position and material
 * match, and every material gets its own copies; a textured triangle's vertex also needs the same
 * attributes. Triangles that collapse to no area on the grid are dropped.
 */
public final class ClusterMeshes {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * How a vertex is told apart from the rest: position, material and, for a textured triangle,
     * its attributes, which are all zero for a projected one.
     */
    private record Key(int x, int y, int z, int material, int normal, int tangent, int uv) {}

    /**
     * One cluster as a mesh.
     *
     * @param vertices The vertices, {@link LodVertex#SIZE} bytes each, little endian, from 0.
     * @param vertexCount How many vertices there are.
     * @param indices Three per triangle.
     * @param min The minimum corner of its bounds, relative to the world's origin.
     * @param max The maximum corner of its bounds.
     */
    public record Mesh(
            @NonNull ByteBuffer vertices,
            int vertexCount,
            int @NonNull [] indices,
            float @NonNull [] min,
            float @NonNull [] max) {

        /**
         * How many triangles it has.
         *
         * @return The count.
         */
        public int triangleCount() {
            return indices.length / CORNERS;
        }
    }

    /**
     * Turn a cluster into a mesh.
     *
     * @param cluster The cluster, its corners relative to the world's origin.
     * @return The mesh, which may have no triangles if every one collapsed on the grid.
     */
    public static Mesh of(LodDag.@NonNull Cluster cluster) {
        final float[] corners = cluster.corners();
        final int triangles = cluster.triangleCount();
        ByteBuffer vertices =
                ByteBuffer.allocate(triangles * CORNERS * LodVertex.SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
        final int[] indices = new int[triangles * CORNERS];
        final Map<Key, Integer> seen = new HashMap<>();
        final int[][] grid = new int[CORNERS][AXES];
        final float[] normal = new float[AXES];
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        int vertexCount = 0;
        int indexCount = 0;
        for (int t = 0; t < triangles; ++t) {
            final int base = t * TRIANGLE_VALUES;
            for (int corner = 0; corner < CORNERS; ++corner) {
                for (int axis = 0; axis < AXES; ++axis) {
                    grid[corner][axis] = LodVertex.grid(corners[base + corner * AXES + axis]);
                }
            }
            if (!normalOf(grid, normal)) {
                continue;
            }
            final int material = cluster.materials()[t];
            final int[] attributes = cluster.attributes();
            final boolean textured = Attributes.textured(attributes, t);
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int[] at = grid[corner];
                final int first = t * Attributes.PER_TRIANGLE + corner * Attributes.VALUES;
                final Key key =
                        textured
                                ? new Key(
                                        at[0],
                                        at[1],
                                        at[2],
                                        material,
                                        attributes[first],
                                        attributes[first + 1],
                                        attributes[first + 2])
                                : new Key(at[0], at[1], at[2], material, 0, 0, 0);
                Integer index = seen.get(key);
                if (index == null) {
                    index = vertexCount++;
                    seen.put(key, index);
                    LodVertex.put(
                            vertices, index, at, material, textured ? attributes : null, first);
                    for (int axis = 0; axis < AXES; ++axis) {
                        final float units = (float) at[axis] / LodVertex.STEPS_PER_UNIT;
                        min[axis] = Math.min(min[axis], units);
                        max[axis] = Math.max(max[axis], units);
                    }
                }
                indices[indexCount++] = index;
            }
        }
        if (vertexCount == 0) {
            Arrays.fill(min, 0);
            Arrays.fill(max, 0);
        }
        vertices.limit(vertexCount * LodVertex.SIZE);
        return new Mesh(vertices, vertexCount, Arrays.copyOf(indices, indexCount), min, max);
    }

    /**
     * A triangle's unit normal, from its grid corners.
     *
     * @param grid The corners, in grid steps.
     * @param out Receives the normal.
     * @return False if the triangle has no area on the grid.
     */
    private static boolean normalOf(int @NonNull [] @NonNull [] grid, float @NonNull [] out) {
        final long[] e1 = new long[AXES];
        final long[] e2 = new long[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            e1[axis] = (long) grid[1][axis] - grid[0][axis];
            e2[axis] = (long) grid[2][axis] - grid[0][axis];
        }
        // In doubles: products of grid differences pass a long's range for triangles kilometers
        // wide
        final double x = (double) e1[1] * e2[2] - (double) e1[2] * e2[1];
        final double y = (double) e1[2] * e2[0] - (double) e1[0] * e2[2];
        final double z = (double) e1[0] * e2[1] - (double) e1[1] * e2[0];
        final double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0) {
            return false;
        }
        out[0] = (float) (x / length);
        out[1] = (float) (y / length);
        out[2] = (float) (z / length);
        return true;
    }

    /** Static helpers only. */
    private ClusterMeshes() {}
}
