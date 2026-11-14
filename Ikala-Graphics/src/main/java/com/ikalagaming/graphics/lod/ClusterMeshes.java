package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Turns clusters into meshes in the {@link LodVertex} format. Projected triangles are flat, their
 * normals worked out when they are drawn, so a vertex is shared wherever position and material
 * match, and every material gets its own copies; a textured triangle's vertex also needs the same
 * attributes. An averaged triangle's vertex is shared whatever the material, since it carries its
 * own color. Triangles that collapse to no area on the grid are dropped.
 */
public final class ClusterMeshes {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * Values in a vertex's key, how it is told apart from the rest: position, material and, for a
     * textured triangle, its attributes (normal, tangent, texture coordinates), which are all zero
     * for a projected one.
     */
    private static final int KEY = 7;

    /**
     * The most triangles a thread keeps scratch room for between meshes: clusters have at most
     * {@link Clusters#MAX_TRIANGLES}, so a larger body is rare and gets room of its own.
     */
    private static final int KEPT_TRIANGLES = 4 * Clusters.MAX_TRIANGLES;

    /**
     * Room a thread reuses for each mesh it makes. Clusters are turned into meshes by the hundred
     * thousand while a world streams, and a map of key records for each was a large share of
     * everything allocated.
     */
    private static final class Scratch {
        /** The vertices, before they are copied out at their size. */
        private ByteBuffer vertices;

        /** The indices, before they are copied out. */
        private int[] indices;

        /** Each vertex's key, {@link #KEY} values each. */
        private int[] keys;

        /** Open addressing table of vertex indices plus one, 0 where empty: a power of two. */
        private int[] table;

        /** The current mesh's grid corners. */
        private final int[][] grid = new int[CORNERS][AXES];

        /** The current triangle's normal. */
        private final float[] normal = new float[AXES];

        /** The key being looked up. */
        private final int[] key = new int[KEY];

        /**
         * Room for a mesh of some triangles, its table emptied.
         *
         * @param triangles How many triangles.
         */
        private void ready(int triangles) {
            final int corners = triangles * CORNERS;
            if (indices == null || indices.length < corners) {
                vertices =
                        ByteBuffer.allocate(corners * LodVertex.SIZE)
                                .order(ByteOrder.LITTLE_ENDIAN);
                indices = new int[corners];
                keys = new int[corners * KEY];
                // At most half full
                table = new int[Integer.highestOneBit(Math.max(4, corners * 2 - 1)) << 1];
            }
            vertices.clear();
            Arrays.fill(table, 0);
        }
    }

    /** Each thread's scratch. */
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** The material an averaged vertex's key has, whatever its triangle's. */
    private static final int AVERAGED_MATERIAL = -1;

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
        final Scratch scratch = triangles <= KEPT_TRIANGLES ? SCRATCH.get() : new Scratch();
        scratch.ready(triangles);
        final ByteBuffer vertices = scratch.vertices;
        final int[] indices = scratch.indices;
        final int[] keys = scratch.keys;
        final int[] table = scratch.table;
        final int mask = table.length - 1;
        final int[] key = scratch.key;
        final int[][] grid = scratch.grid;
        final float[] normal = scratch.normal;
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
                // An averaged vertex carries its own color, so its material matters little and
                // isn't told apart by: one material's copy serves the triangles of every other
                key[0] = at[0];
                key[1] = at[1];
                key[2] = at[2];
                if (textured) {
                    key[3] =
                            Attributes.isAveraged(attributes, first) ? AVERAGED_MATERIAL : material;
                    key[4] = attributes[first];
                    key[5] = attributes[first + 1];
                    key[6] = attributes[first + 2];
                } else {
                    key[3] = material;
                    key[4] = 0;
                    key[5] = 0;
                    key[6] = 0;
                }
                int slot = hash(key) & mask;
                int index = -1;
                while (table[slot] != 0) {
                    final int found = table[slot] - 1;
                    if (Arrays.equals(keys, found * KEY, found * KEY + KEY, key, 0, KEY)) {
                        index = found;
                        break;
                    }
                    slot = (slot + 1) & mask;
                }
                if (index < 0) {
                    index = vertexCount++;
                    table[slot] = index + 1;
                    System.arraycopy(key, 0, keys, index * KEY, KEY);
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
        // Copied out at their size: the scratch is kept, and room for three per triangle would be
        // kept with the mesh until it is drawn
        final ByteBuffer kept =
                ByteBuffer.allocate(vertexCount * LodVertex.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        kept.put(0, vertices, 0, vertexCount * LodVertex.SIZE);
        return new Mesh(kept, vertexCount, Arrays.copyOf(indices, indexCount), min, max);
    }

    /**
     * Mix a vertex key into a hash.
     *
     * @param key The key, {@link #KEY} values.
     * @return The hash, its low bits spread.
     */
    private static int hash(int @NonNull [] key) {
        int h = 0;
        for (int i = 0; i < KEY; ++i) {
            h = (h ^ key[i]) * 0x9E3779B1;
        }
        return h ^ (h >>> 15);
    }

    /**
     * A triangle's unit normal, from its grid corners.
     *
     * @param grid The corners, in grid steps.
     * @param out Receives the normal.
     * @return False if the triangle has no area on the grid.
     */
    private static boolean normalOf(int @NonNull [] @NonNull [] grid, float @NonNull [] out) {
        // In doubles: products of grid differences pass a long's range for triangles kilometers
        // wide
        final double e1x = (double) grid[1][0] - grid[0][0];
        final double e1y = (double) grid[1][1] - grid[0][1];
        final double e1z = (double) grid[1][2] - grid[0][2];
        final double e2x = (double) grid[2][0] - grid[0][0];
        final double e2y = (double) grid[2][1] - grid[0][1];
        final double e2z = (double) grid[2][2] - grid[0][2];
        final double x = e1y * e2z - e1z * e2y;
        final double y = e1z * e2x - e1x * e2z;
        final double z = e1x * e2y - e1y * e2x;
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
