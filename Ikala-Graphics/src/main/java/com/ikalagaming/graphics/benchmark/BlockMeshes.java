package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * The benchmark's blocks, built in code against the unit box {@code [0, 1]^3}, one mesh and
 * material per {@link SyntheticTerrain} block type.
 */
public final class BlockMeshes {

    /** The unit box's minimum corner. */
    public static final Vector3f BOX_MIN = new Vector3f(0, 0, 0);

    /** The unit box's maximum corner. */
    public static final Vector3f BOX_MAX = new Vector3f(1, 1, 1);

    /** How many floats a vertex takes. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** How much clear glass hides of what is behind it. */
    private static final float CLEAR_GLASS_OPACITY = 0.3f;

    /** How much tinted glass hides of what is behind it. */
    private static final float TINTED_GLASS_OPACITY = 0.55f;

    /** How rough glass is: smooth, for a sharp highlight. */
    private static final float GLASS_ROUGHNESS = 0.1f;

    /** How strong glass's highlight is, about an index of refraction of 1.5. */
    private static final float GLASS_SPECULAR = 0.5f;

    /**
     * Make each block type's mesh, with a new material each.
     *
     * @return The meshes by block id, null for air.
     */
    public static MeshData[] create() {
        final MeshData[] meshes = new MeshData[SyntheticTerrain.BLOCK_TYPES];
        final float[] zero = {0, 0, 0};
        final float[] one = {1, 1, 1};
        meshes[SyntheticTerrain.STONE] =
                box(zero, one, material(0.5f, 0.5f, 0.48f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.GRASS] =
                box(zero, one, material(0.3f, 0.55f, 0.22f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.SLAB] =
                box(
                        zero,
                        new float[] {1, 0.5f, 1},
                        material(0.35f, 0.6f, 0.25f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.PLANT] =
                plant(material(0.4f, 0.75f, 0.3f, Material.Transparency.CUTOUT));
        meshes[SyntheticTerrain.LEAVES] =
                box(zero, one, material(0.15f, 0.4f, 0.12f, Material.Transparency.CUTOUT));
        meshes[SyntheticTerrain.GLASS] =
                box(zero, one, glass(0.7f, 0.85f, 0.95f, CLEAR_GLASS_OPACITY));
        meshes[SyntheticTerrain.TINTED_GLASS] =
                box(zero, one, glass(0.9f, 0.3f, 0.25f, TINTED_GLASS_OPACITY));
        return meshes;
    }

    /**
     * Work out each block type's bake source, without registering anything.
     *
     * @param meshes The meshes by block id.
     * @return The sources by block id, null for air.
     */
    public static BakeSource[] sources(@NonNull MeshData @NonNull [] meshes) {
        final BakeSource[] sources = new BakeSource[meshes.length];
        for (int block = 1; block < meshes.length; ++block) {
            sources[block] = BakeSource.derive(meshes[block], BOX_MIN, BOX_MAX);
        }
        return sources;
    }

    /**
     * A material of one color.
     *
     * @param red The red.
     * @param green The green.
     * @param blue The blue.
     * @param transparency How it lets light through.
     * @return The material.
     */
    private static Material material(
            float red, float green, float blue, @NonNull Material.Transparency transparency) {
        Material material = new Material();
        material.getBaseColor().set(red, green, blue, 1);
        material.setRoughness(0.85f);
        material.setTransparency(transparency);
        return material;
    }

    /**
     * A see-through, smooth material.
     *
     * @param red The red.
     * @param green The green.
     * @param blue The blue.
     * @param opacity How much of what is behind it it hides, from 0 to 1.
     * @return The material.
     */
    private static Material glass(float red, float green, float blue, float opacity) {
        Material material = material(red, green, blue, Material.Transparency.TRANSLUCENT);
        material.getBaseColor().w = opacity;
        material.setRoughness(GLASS_ROUGHNESS);
        material.setSpecular(GLASS_SPECULAR);
        return material;
    }

    /** A mesh being put together. */
    private static final class Builder {
        /** The vertex data so far. */
        private float[] vertices = new float[0];

        /** The indices so far. */
        private int[] indices = new int[0];

        /**
         * Add a quad, its corners counter-clockwise seen from the side it faces.
         *
         * @param corners Four corners, 3 floats each.
         */
        void quad(float[]... corners) {
            final float[] tangent = normalize(subtract(corners[1], corners[0]));
            final float[] bitangent = normalize(subtract(corners[3], corners[0]));
            final float[] normal = normalize(cross(tangent, bitangent));
            final float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            final int first = vertices.length / STRIDE;
            vertices = Arrays.copyOf(vertices, vertices.length + 4 * STRIDE);
            for (int i = 0; i < 4; ++i) {
                final int at = (first + i) * STRIDE;
                System.arraycopy(corners[i], 0, vertices, at, 3);
                System.arraycopy(normal, 0, vertices, at + 3, 3);
                System.arraycopy(tangent, 0, vertices, at + 6, 3);
                System.arraycopy(bitangent, 0, vertices, at + 9, 3);
                System.arraycopy(uvs[i], 0, vertices, at + 12, 2);
            }
            final int count = indices.length;
            indices = Arrays.copyOf(indices, count + 6);
            final int[] quad = {first, first + 1, first + 2, first, first + 2, first + 3};
            System.arraycopy(quad, 0, indices, count, 6);
        }

        /**
         * Finish the mesh.
         *
         * @param min The bounds' minimum corner.
         * @param max The bounds' maximum corner.
         * @param material The material.
         * @return The mesh.
         */
        MeshData build(float[] min, float[] max, Material material) {
            MeshData mesh =
                    new MeshData(
                            new Vector3f(min[0], min[1], min[2]),
                            new Vector3f(max[0], max[1], max[2]),
                            vertices.length / STRIDE,
                            vertices,
                            indices,
                            0,
                            new byte[0]);
            mesh.setMaterial(material);
            return mesh;
        }
    }

    /**
     * A box's six faces, facing out.
     *
     * @param min The minimum corner.
     * @param max The maximum corner.
     * @param material The material.
     * @return The mesh.
     */
    static MeshData box(float[] min, float[] max, Material material) {
        final Builder builder = new Builder();
        for (int axis = 0; axis < 3; ++axis) {
            for (int sign = -1; sign <= 1; sign += 2) {
                // e1 x e2 is the axis, so (0,0), (1,0), (1,1), (0,1) is counter-clockwise from
                // +axis
                final int e1 = (axis + 1) % 3;
                final int e2 = (axis + 2) % 3;
                final float plane = sign > 0 ? max[axis] : min[axis];
                final float[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                final float[][] corners = new float[4][];
                for (int i = 0; i < 4; ++i) {
                    final float[] corner = new float[3];
                    corner[axis] = plane;
                    corner[e1] = square[i][0] == 0 ? min[e1] : max[e1];
                    corner[e2] = square[i][1] == 0 ? min[e2] : max[e2];
                    // Reversed for faces pointing down the axis
                    corners[sign > 0 ? i : 3 - i] = corner;
                }
                builder.quad(corners);
            }
        }
        return builder.build(min, max, material);
    }

    /**
     * Two crossed quads through the cell's center, each with a back so it shows from both sides.
     *
     * @param material The material.
     * @return The mesh.
     */
    static MeshData plant(Material material) {
        final Builder builder = new Builder();
        final float[][][] planes = {
            {{0, 0, 0}, {1, 0, 1}, {1, 1, 1}, {0, 1, 0}},
            {{1, 0, 0}, {0, 0, 1}, {0, 1, 1}, {1, 1, 0}}
        };
        for (float[][] plane : planes) {
            builder.quad(plane[0], plane[1], plane[2], plane[3]);
            builder.quad(plane[1], plane[0], plane[3], plane[2]);
        }
        return builder.build(new float[] {0, 0, 0}, new float[] {1, 1, 1}, material);
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
        final float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[] {v[0] / length, v[1] / length, v[2] / length};
    }

    /** Static helpers only. */
    private BlockMeshes() {}
}
