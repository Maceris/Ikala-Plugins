package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Small meshes built in code for the baking tests: boxes, slabs, plants and decals, in {@link
 * MeshData}'s vertex layout, against the unit box {@code [0, 1]^3}.
 */
final class BakeFixtures {

    /** The unit box's minimum corner. */
    static final Vector3f BOX_MIN = new Vector3f(0, 0, 0);

    /** The unit box's maximum corner. */
    static final Vector3f BOX_MAX = new Vector3f(1, 1, 1);

    /** A mesh being put together. */
    static final class Mesh {
        /** The vertex data so far. */
        float[] vertices = new float[0];

        /** The indices so far. */
        int[] indices = new int[0];

        /**
         * Add a quad, its corners counter-clockwise seen from the side it faces.
         *
         * @param corners Four corners, 3 floats each.
         * @return This mesh.
         */
        Mesh quad(float[]... corners) {
            final float[] a = corners[0];
            final float[] b = corners[1];
            final float[] d = corners[3];
            final float[] tangent = normalize(subtract(b, a));
            final float[] bitangent = normalize(subtract(d, a));
            final float[] normal = normalize(cross(tangent, bitangent));
            final float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            final int first = vertices.length / MeshData.VERTEX_SIZE_IN_FLOATS;
            final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
            vertices = Arrays.copyOf(vertices, vertices.length + 4 * stride);
            for (int i = 0; i < 4; ++i) {
                final int at = (first + i) * stride;
                System.arraycopy(corners[i], 0, vertices, at, 3);
                System.arraycopy(normal, 0, vertices, at + 3, 3);
                System.arraycopy(tangent, 0, vertices, at + 6, 3);
                System.arraycopy(bitangent, 0, vertices, at + 9, 3);
                System.arraycopy(uvs[i], 0, vertices, at + 12, 2);
            }
            final int count = indices.length;
            indices = Arrays.copyOf(indices, count + 6);
            int[] quad = {first, first + 1, first + 2, first, first + 2, first + 3};
            System.arraycopy(quad, 0, indices, count, 6);
            return this;
        }

        /**
         * Add a box's six faces, facing out.
         *
         * @param min The minimum corner.
         * @param max The maximum corner.
         * @return This mesh.
         */
        Mesh box(float[] min, float[] max) {
            for (int axis = 0; axis < 3; ++axis) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    face(min, max, axis, sign);
                }
            }
            return this;
        }

        /**
         * Add one face of a box, facing out.
         *
         * @param min The minimum corner.
         * @param max The maximum corner.
         * @param axis The axis it is perpendicular to.
         * @param sign Which way it faces.
         * @return This mesh.
         */
        Mesh face(float[] min, float[] max, int axis, int sign) {
            // e1 × e2 = the axis, so (0,0), (1,0), (1,1), (0,1) is counter-clockwise from +axis
            final int e1 = (axis + 1) % 3;
            final int e2 = (axis + 2) % 3;
            final float plane = sign > 0 ? max[axis] : min[axis];
            final float[][] corners = new float[4][3];
            final float[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (int i = 0; i < 4; ++i) {
                final float[] corner = new float[3];
                corner[axis] = plane;
                corner[e1] = square[i][0] == 0 ? min[e1] : max[e1];
                corner[e2] = square[i][1] == 0 ? min[e2] : max[e2];
                // Reversed for faces pointing down the axis
                corners[sign > 0 ? i : 3 - i] = corner;
            }
            return quad(corners);
        }

        /**
         * Make a bake source against the unit box.
         *
         * @param material The material, or null.
         * @return The source.
         */
        BakeSource source(Material material) {
            return BakeSource.derive(vertices, indices, BOX_MIN, BOX_MAX, material);
        }

        /**
         * Make a bake source against the unit box, with the default material.
         *
         * @return The source.
         */
        BakeSource source() {
            return source(null);
        }
    }

    /**
     * A whole cube filling the unit box.
     *
     * @return The mesh.
     */
    static Mesh cube() {
        return new Mesh().box(new float[] {0, 0, 0}, new float[] {1, 1, 1});
    }

    /**
     * A slab filling the bottom half of the unit box.
     *
     * @return The mesh.
     */
    static Mesh slab() {
        return new Mesh().box(new float[] {0, 0, 0}, new float[] {1, 0.5f, 1});
    }

    /**
     * A stair: a slab with a quarter block on top in one corner. No two of its faces cover the
     * same, so it shows where every rotation sends each face.
     *
     * @return The mesh.
     */
    static Mesh stair() {
        return new Mesh()
                .box(new float[] {0, 0, 0}, new float[] {1, 0.5f, 1})
                .box(new float[] {0, 0.5f, 0}, new float[] {0.5f, 1, 0.5f});
    }

    /**
     * Two crossed quads through the box's center, like a plant.
     *
     * @return The mesh.
     */
    static Mesh plant() {
        return new Mesh()
                .quad(
                        new float[] {0, 0, 0},
                        new float[] {1, 0, 1},
                        new float[] {1, 1, 1},
                        new float[] {0, 1, 0})
                .quad(
                        new float[] {1, 0, 0},
                        new float[] {0, 0, 1},
                        new float[] {0, 1, 1},
                        new float[] {1, 1, 0});
    }

    /**
     * A material with a transparency.
     *
     * @param transparency The transparency.
     * @return A new material.
     */
    static Material material(Material.Transparency transparency) {
        Material material = new Material();
        material.setTransparency(transparency);
        return material;
    }

    private static float[] subtract(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float[] normalize(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[] {v[0] / length, v[1] / length, v[2] / length};
    }

    private BakeFixtures() {}
}
