package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The benchmark's blocks, built in code against the unit box {@code [0, 1]^3}, one mesh and
 * material per {@link SyntheticTerrain} block type.
 *
 * <p>Texture coordinates come from where a vertex is in its cell, the way a texture projected along
 * the face's axis would land: on a side face u runs left to right seen from outside and v down from
 * the top, and on a top or bottom face u follows x and v follows z. So a slab's side shows the
 * bottom half of its texture, and a coarse level of detail textured by projection lines up with the
 * blocks it replaces.
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

    /** How rough water is: smooth, for a highlight. */
    private static final float WATER_ROUGHNESS = 0.1f;

    /** How strong water's highlight is, about an index of refraction of 1.33. */
    private static final float WATER_SPECULAR = 0.25f;

    /** How rough everything else is. */
    private static final float ROUGHNESS = 0.85f;

    /** Half a block, the height of a slab and of a stair's step. */
    private static final float HALF = 0.5f;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a quad. */
    private static final int QUAD = 4;

    /**
     * Blocks with textures, and what to release when done with them.
     *
     * @param meshes The meshes by block id, null for air.
     * @param materials Every material, face variants included, to add to and remove from the
     *     material cache.
     * @param textures The textures, to release.
     */
    public record Textured(
            MeshData @NonNull [] meshes,
            @NonNull List<Material> materials,
            @NonNull List<TextureHandle> textures) {}

    /**
     * Make each block type's mesh, with a new material each in a plain color.
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
                        new float[] {1, HALF, 1},
                        material(0.35f, 0.6f, 0.25f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.PLANT] =
                plant(material(0.4f, 0.75f, 0.3f, Material.Transparency.CUTOUT));
        meshes[SyntheticTerrain.LEAVES] =
                box(zero, one, material(0.15f, 0.4f, 0.12f, Material.Transparency.CUTOUT));
        meshes[SyntheticTerrain.GLASS] =
                box(zero, one, glass(0.7f, 0.85f, 0.95f, CLEAR_GLASS_OPACITY));
        meshes[SyntheticTerrain.TINTED_GLASS] =
                box(zero, one, glass(0.9f, 0.3f, 0.25f, TINTED_GLASS_OPACITY));
        meshes[SyntheticTerrain.DIRT] =
                box(zero, one, material(0.45f, 0.31f, 0.19f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.PLANKS] =
                box(zero, one, material(0.62f, 0.45f, 0.26f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.STAIRS] =
                stairs(material(0.62f, 0.45f, 0.26f, Material.Transparency.OPAQUE));
        meshes[SyntheticTerrain.WATER] = box(zero, one, water());
        return meshes;
    }

    /**
     * Make each block type's mesh with textures drawn by {@link BlockTextures}: grass with its own
     * top and a dirt bottom, planks for stairs, leaves for plants. Glass stays a plain color.
     *
     * @param textures Where to load the textures.
     * @return The blocks, their materials and textures.
     */
    public static Textured textured(@NonNull Textures textures) {
        final MeshData[] meshes = create();
        final Map<BlockTextures.Tile, TextureHandle> loaded =
                new EnumMap<>(BlockTextures.Tile.class);
        for (BlockTextures.Tile tile : BlockTextures.Tile.values()) {
            final byte[] pixels = BlockTextures.pixels(tile);
            final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
            try {
                buffer.put(pixels).flip();
                loaded.put(
                        tile,
                        textures.load(
                                buffer,
                                Format.R8G8B8A8_UNORM,
                                BlockTextures.SIZE,
                                BlockTextures.SIZE));
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }
        texture(meshes[SyntheticTerrain.STONE], loaded.get(BlockTextures.Tile.STONE));
        texture(meshes[SyntheticTerrain.DIRT], loaded.get(BlockTextures.Tile.DIRT));
        texture(meshes[SyntheticTerrain.PLANKS], loaded.get(BlockTextures.Tile.PLANKS));
        texture(meshes[SyntheticTerrain.STAIRS], loaded.get(BlockTextures.Tile.PLANKS));
        texture(meshes[SyntheticTerrain.LEAVES], loaded.get(BlockTextures.Tile.LEAVES));
        texture(meshes[SyntheticTerrain.PLANT], loaded.get(BlockTextures.Tile.LEAVES));
        texture(meshes[SyntheticTerrain.WATER], loaded.get(BlockTextures.Tile.WATER));
        for (int block : new int[] {SyntheticTerrain.GRASS, SyntheticTerrain.SLAB}) {
            final Material side = meshes[block].getMaterial();
            side.setTexture(loaded.get(BlockTextures.Tile.GRASS_SIDE));
            side.getBaseColor().set(Material.DEFAULT_COLOR);
            final Material top = material(1, 1, 1, Material.Transparency.OPAQUE);
            top.setTexture(loaded.get(BlockTextures.Tile.GRASS_TOP));
            side.setTop(top);
            final Material bottom = material(1, 1, 1, Material.Transparency.OPAQUE);
            bottom.setTexture(loaded.get(BlockTextures.Tile.DIRT));
            side.setBottom(bottom);
        }
        return new Textured(meshes, materials(meshes), List.copyOf(loaded.values()));
    }

    /**
     * Give a block's material a texture, which then gives its color.
     *
     * @param mesh The block's mesh.
     * @param texture The texture.
     */
    private static void texture(@NonNull MeshData mesh, @NonNull TextureHandle texture) {
        mesh.getMaterial().setTexture(texture);
        mesh.getMaterial().getBaseColor().set(Material.DEFAULT_COLOR);
    }

    /**
     * Every material the blocks use, each once: their own and their face variants.
     *
     * @param meshes The meshes by block id.
     * @return The materials.
     */
    public static List<Material> materials(MeshData @NonNull [] meshes) {
        final Set<Material> out = new LinkedHashSet<>();
        for (MeshData mesh : meshes) {
            if (mesh == null || mesh.getMaterial() == null) {
                continue;
            }
            out.add(mesh.getMaterial());
            out.add(mesh.getMaterial().forTop());
            out.add(mesh.getMaterial().forBottom());
        }
        return new ArrayList<>(out);
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
        material.setRoughness(ROUGHNESS);
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

    /**
     * Water: see-through and smooth.
     *
     * @return The material.
     */
    private static Material water() {
        Material material = glass(0.18f, 0.34f, 0.68f, 0.62f);
        material.setRoughness(WATER_ROUGHNESS);
        material.setSpecular(WATER_SPECULAR);
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
         * @param uvs Their texture coordinates.
         */
        void quad(float[][] corners, float[][] uvs) {
            final float[] e1 = subtract(corners[1], corners[0]);
            final float[] e2 = subtract(corners[QUAD - 1], corners[0]);
            final float[] normal = normalize(cross(e1, e2));
            // The directions u and v grow in, from two edges and their texture coordinates
            final float du1 = uvs[1][0] - uvs[0][0];
            final float dv1 = uvs[1][1] - uvs[0][1];
            final float du2 = uvs[QUAD - 1][0] - uvs[0][0];
            final float dv2 = uvs[QUAD - 1][1] - uvs[0][1];
            final float det = du1 * dv2 - du2 * dv1;
            final float[] tangent = new float[AXES];
            final float[] bitangent = new float[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                tangent[axis] = (e1[axis] * dv2 - e2[axis] * dv1) / det;
                bitangent[axis] = (e2[axis] * du1 - e1[axis] * du2) / det;
            }
            final float[] t = normalize(tangent);
            final float[] b = normalize(bitangent);
            final int first = vertices.length / STRIDE;
            vertices = Arrays.copyOf(vertices, vertices.length + QUAD * STRIDE);
            for (int i = 0; i < QUAD; ++i) {
                final int at = (first + i) * STRIDE;
                System.arraycopy(corners[i], 0, vertices, at, AXES);
                System.arraycopy(normal, 0, vertices, at + AXES, AXES);
                System.arraycopy(t, 0, vertices, at + 2 * AXES, AXES);
                System.arraycopy(b, 0, vertices, at + 3 * AXES, AXES);
                System.arraycopy(uvs[i], 0, vertices, at + 4 * AXES, 2);
            }
            final int count = indices.length;
            indices = Arrays.copyOf(indices, count + 6);
            final int[] quad = {first, first + 1, first + 2, first, first + 2, first + 3};
            System.arraycopy(quad, 0, indices, count, 6);
        }

        /**
         * Add an axis-aligned rectangle facing along an axis, textured by where it is in its cell.
         *
         * @param axis The axis it faces along.
         * @param sign 1 to face up the axis, -1 down it.
         * @param min Its minimum corner; its own axis's value is ignored.
         * @param max Its maximum corner; its own axis's value is ignored.
         */
        void rect(int axis, int sign, float[] min, float[] max) {
            // e1 x e2 is the axis, so (0,0), (1,0), (1,1), (0,1) is counter-clockwise from +axis
            final int e1 = (axis + 1) % AXES;
            final int e2 = (axis + 2) % AXES;
            final float plane = sign > 0 ? max[axis] : min[axis];
            final float[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            final float[][] corners = new float[QUAD][];
            final float[][] uvs = new float[QUAD][];
            for (int i = 0; i < QUAD; ++i) {
                final float[] corner = new float[AXES];
                corner[axis] = plane;
                corner[e1] = square[i][0] == 0 ? min[e1] : max[e1];
                corner[e2] = square[i][1] == 0 ? min[e2] : max[e2];
                // Reversed for faces pointing down the axis
                final int at = sign > 0 ? i : QUAD - 1 - i;
                corners[at] = corner;
                uvs[at] = uv(axis, sign, corner);
            }
            quad(corners, uvs);
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
     * Where a point on a face falls in its texture, see the class description.
     *
     * @param axis The axis the face points along.
     * @param sign Which way along it.
     * @param point The point, in its cell.
     * @return The texture coordinates.
     */
    public static float[] uv(int axis, int sign, float[] point) {
        final float x = point[0];
        final float y = point[1];
        final float z = point[2];
        return switch (axis) {
                // Seen from +x, right is -z; from -x, +z
            case 0 -> new float[] {sign > 0 ? 1 - z : z, 1 - y};
            case 1 -> new float[] {x, z};
                // Seen from +z, right is +x; from -z, -x
            default -> new float[] {sign > 0 ? x : 1 - x, 1 - y};
        };
    }

    /**
     * A box's six faces, facing out. Also used by plugins that need a placeholder block.
     *
     * @param min The minimum corner.
     * @param max The maximum corner.
     * @param material The material.
     * @return The mesh.
     */
    public static MeshData box(float[] min, float[] max, Material material) {
        final Builder builder = new Builder();
        for (int axis = 0; axis < AXES; ++axis) {
            for (int sign = -1; sign <= 1; sign += 2) {
                builder.rect(axis, sign, min, max);
            }
        }
        return builder.build(min, max, material);
    }

    /**
     * Stairs: a slab along the bottom, and a step on its back half, climbing toward +z. Only
     * outside faces, so the bottom and back cover their whole face of the cell.
     *
     * @param material The material.
     * @return The mesh.
     */
    static MeshData stairs(Material material) {
        final Builder builder = new Builder();
        final float[] zero = {0, 0, 0};
        final float[] one = {1, 1, 1};
        // The bottom and back, whole
        builder.rect(1, -1, zero, one);
        builder.rect(2, 1, zero, one);
        // The front: the slab's, then the step's
        builder.rect(2, -1, zero, new float[] {1, HALF, 1});
        builder.rect(2, -1, new float[] {0, HALF, HALF}, one);
        // The tops: the slab's front half, then the step
        builder.rect(1, 1, zero, new float[] {1, HALF, HALF});
        builder.rect(1, 1, new float[] {0, 0, HALF}, one);
        // Each side in two parts, the slab's and the step's
        for (int sign = -1; sign <= 1; sign += 2) {
            builder.rect(0, sign, zero, new float[] {1, HALF, 1});
            builder.rect(0, sign, new float[] {0, HALF, HALF}, one);
        }
        return builder.build(zero, one, material);
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
        final float[][] front = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        final float[][] back = {{1, 1}, {0, 1}, {0, 0}, {1, 0}};
        for (float[][] plane : planes) {
            builder.quad(new float[][] {plane[0], plane[1], plane[2], plane[3]}, front);
            builder.quad(new float[][] {plane[1], plane[0], plane[3], plane[2]}, back);
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
