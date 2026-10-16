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

    /** The grid the jumble recipe's shapes are drawn on, sixteenths of a block like Minecraft's. */
    private static final int GRID = 16;

    /** Half the grid, in sixteenths. */
    private static final int MID = GRID / 2;

    /** How thick a glass pane is, in sixteenths. */
    private static final int PANE = 2;

    /** How much a chest is inset from its cell on every side but the bottom, in sixteenths. */
    private static final int CHEST_INSET = 1;

    /** How far a lectern's desk tilts toward its front, in degrees. */
    private static final double DESK_TILT = 22.5;

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
        jumble(meshes);
        return meshes;
    }

    /**
     * Make the jumble recipe's blocks. Each kind shares one material, so level of detail sees the
     * many shapes as one surface to merge.
     *
     * @param meshes The meshes by block id, to fill in.
     */
    private static void jumble(MeshData @NonNull [] meshes) {
        final Material stone = material(0.5f, 0.5f, 0.48f, Material.Transparency.OPAQUE);
        meshes[SyntheticTerrain.STONE_SLAB] = shape(stone, 0, new int[] {0, 0, 0, GRID, MID, GRID});
        meshes[SyntheticTerrain.STONE_SLAB_TOP] =
                shape(stone, 0, new int[] {0, MID, 0, GRID, GRID, GRID});
        final Material wood = material(0.62f, 0.45f, 0.26f, Material.Transparency.OPAQUE);
        for (int shape = 0; shape < SyntheticTerrain.STAIR_SHAPES; ++shape) {
            for (int facing = 0; facing < SyntheticTerrain.FACINGS; ++facing) {
                for (boolean upsideDown : new boolean[] {false, true}) {
                    meshes[SyntheticTerrain.stairs(shape, upsideDown, facing)] =
                            shape(wood, facing, stairBoxes(shape, upsideDown));
                }
            }
        }
        final Material pane = glass(0.7f, 0.85f, 0.95f, CLEAR_GLASS_OPACITY);
        final int[] alongX = {0, 0, MID - PANE / 2, GRID, GRID, MID + PANE / 2};
        final int[] alongZ = {MID - PANE / 2, 0, 0, MID + PANE / 2, GRID, GRID};
        meshes[SyntheticTerrain.PANE_X] = shape(pane, 0, alongX);
        meshes[SyntheticTerrain.PANE_Z] = shape(pane, 0, alongZ);
        meshes[SyntheticTerrain.PANE_CROSS] = shape(pane, 0, alongX, alongZ);
        for (int facing = 0; facing < SyntheticTerrain.FACINGS; ++facing) {
            meshes[SyntheticTerrain.LECTERN_FIRST + facing] = lectern(wood, facing);
            meshes[SyntheticTerrain.CHEST_FIRST + facing] =
                    shape(
                            wood,
                            facing,
                            new int[] {
                                CHEST_INSET,
                                0,
                                CHEST_INSET,
                                GRID - CHEST_INSET,
                                GRID - CHEST_INSET,
                                GRID - CHEST_INSET
                            },
                            // The latch, on the front, out to the cell's side
                            new int[] {MID - 1, MID, 0, MID + 1, MID + 4, CHEST_INSET});
        }
    }

    /**
     * Stairs' boxes, climbing toward +z: a slab, and a step on half, three quarters or a quarter of
     * it.
     *
     * @param shape {@link SyntheticTerrain#STAIRS_STRAIGHT}, {@link SyntheticTerrain#STAIRS_INNER}
     *     or {@link SyntheticTerrain#STAIRS_OUTER}.
     * @param upsideDown Whether the slab is on top and the step below.
     * @return The boxes, in sixteenths.
     */
    private static int[][] stairBoxes(int shape, boolean upsideDown) {
        final int slabLow = upsideDown ? MID : 0;
        final int stepLow = upsideDown ? 0 : MID;
        final int[] slab = {0, slabLow, 0, GRID, slabLow + MID, GRID};
        final int[] back = {0, stepLow, MID, GRID, stepLow + MID, GRID};
        final int[] frontRight = {MID, stepLow, 0, GRID, stepLow + MID, MID};
        final int[] backRight = {MID, stepLow, MID, GRID, stepLow + MID, GRID};
        return switch (shape) {
            case SyntheticTerrain.STAIRS_STRAIGHT -> new int[][] {slab, back};
            case SyntheticTerrain.STAIRS_INNER -> new int[][] {slab, back, frontRight};
            default -> new int[][] {slab, backRight};
        };
    }

    /**
     * A lectern, its front toward -z before turning: a base plate, a post, and a desk on top tilted
     * down toward the front, only its bottom flat against its cell.
     *
     * @param material The material.
     * @param facing How many quarter turns about y.
     * @return The mesh.
     */
    private static MeshData lectern(Material material, int facing) {
        final Builder builder = new Builder();
        builder.boxes(
                facing,
                new int[] {0, 0, 0, GRID, 2, GRID},
                // Its top inside the desk all the way across
                new int[] {MID - 4, 2, MID - 4, MID + 4, 12, MID + 4});
        // The desk: 16 wide, 14 deep and 4 thick, its middle at y 11.4 sixteenths so its back
        // corner stays just under the top of the cell once tilted
        final double tilt = Math.toRadians(DESK_TILT);
        final float cos = (float) Math.cos(tilt);
        final float sin = (float) Math.sin(tilt);
        final float middleY = 11.4f / GRID;
        final float[] min = {0, -2f / GRID, -7f / GRID};
        final float[] max = {1, 2f / GRID, 7f / GRID};
        for (int axis = 0; axis < AXES; ++axis) {
            for (int sign = -1; sign <= 1; sign += 2) {
                final float[][] corners = Builder.rectCorners(axis, sign, min, max);
                for (float[] corner : corners) {
                    // Tilt about x, raising the back, then move up and turn about y
                    final float y = corner[1] * cos + corner[2] * sin;
                    final float z = -corner[1] * sin + corner[2] * cos;
                    corner[1] = y + middleY;
                    corner[2] = z + HALF;
                    turn(corner, facing);
                }
                builder.quadProjected(corners);
            }
        }
        return builder.build(new float[] {0, 0, 0}, new float[] {1, 1, 1}, material);
    }

    /**
     * Turn a point in its cell a number of quarter turns about y, around the cell's middle.
     *
     * @param point The point, changed in place.
     * @param facing How many quarter turns.
     */
    private static void turn(float[] point, int facing) {
        for (int i = 0; i < facing; ++i) {
            final float x = point[0];
            point[0] = 1 - point[2];
            point[2] = x;
        }
    }

    /**
     * A block made of boxes on the sixteenths grid, turned about y, with only the faces between
     * filled and empty sixteenths, merged into as few rectangles as each slice allows.
     *
     * @param material The material.
     * @param facing How many quarter turns about y.
     * @param boxes The boxes, each {minX, minY, minZ, maxX, maxY, maxZ} in sixteenths.
     * @return The mesh.
     */
    private static MeshData shape(Material material, int facing, int[]... boxes) {
        final Builder builder = new Builder();
        builder.boxes(facing, boxes);
        return builder.build(new float[] {0, 0, 0}, new float[] {1, 1, 1}, material);
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
        // The jumble's kinds share a material each, so texturing one textures them all
        texture(meshes[SyntheticTerrain.STONE_SLAB], loaded.get(BlockTextures.Tile.STONE));
        texture(meshes[SyntheticTerrain.STAIRS_FIRST], loaded.get(BlockTextures.Tile.PLANKS));
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
        // Each block face's texture fills one unit, so level of detail projects it
        material.setTriplanar(true);
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
            final float[][] corners = rectCorners(axis, sign, min, max);
            final float[][] uvs = new float[QUAD][];
            for (int i = 0; i < QUAD; ++i) {
                uvs[i] = uv(axis, sign, corners[i]);
            }
            quad(corners, uvs);
        }

        /**
         * The corners of an axis-aligned rectangle facing along an axis, counter-clockwise seen
         * from the side it faces.
         *
         * @param axis The axis it faces along.
         * @param sign 1 to face up the axis, -1 down it.
         * @param min Its minimum corner; its own axis's value is ignored.
         * @param max Its maximum corner; its own axis's value is ignored.
         * @return The four corners.
         */
        static float[][] rectCorners(int axis, int sign, float[] min, float[] max) {
            // e1 x e2 is the axis, so (0,0), (1,0), (1,1), (0,1) is counter-clockwise from +axis
            final int e1 = (axis + 1) % AXES;
            final int e2 = (axis + 2) % AXES;
            final float plane = sign > 0 ? max[axis] : min[axis];
            final float[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            final float[][] corners = new float[QUAD][];
            for (int i = 0; i < QUAD; ++i) {
                final float[] corner = new float[AXES];
                corner[axis] = plane;
                corner[e1] = square[i][0] == 0 ? min[e1] : max[e1];
                corner[e2] = square[i][1] == 0 ? min[e2] : max[e2];
                // Reversed for faces pointing down the axis
                corners[sign > 0 ? i : QUAD - 1 - i] = corner;
            }
            return corners;
        }

        /**
         * Add a quad facing any way, textured as if projected along the axis it faces most.
         *
         * @param corners Four corners, counter-clockwise seen from the side it faces.
         */
        void quadProjected(float[][] corners) {
            final float[] normal =
                    cross(
                            subtract(corners[1], corners[0]),
                            subtract(corners[QUAD - 1], corners[0]));
            int axis = 0;
            for (int i = 1; i < AXES; ++i) {
                if (Math.abs(normal[i]) > Math.abs(normal[axis])) {
                    axis = i;
                }
            }
            final int sign = normal[axis] > 0 ? 1 : -1;
            final float[][] uvs = new float[QUAD][];
            for (int i = 0; i < QUAD; ++i) {
                uvs[i] = uv(axis, sign, corners[i]);
            }
            quad(corners, uvs);
        }

        /**
         * Add the outside of a union of boxes on the sixteenths grid: every face between a filled
         * sixteenth and an empty one or the cell's outside, merged greedily within each layer.
         *
         * @param facing How many quarter turns about y to turn the boxes.
         * @param boxes The boxes, each {minX, minY, minZ, maxX, maxY, maxZ} in sixteenths.
         */
        void boxes(int facing, int[]... boxes) {
            final boolean[][][] filled = new boolean[GRID][GRID][GRID];
            for (int[] box : boxes) {
                for (int x = box[0]; x < box[3]; ++x) {
                    for (int y = box[1]; y < box[4]; ++y) {
                        for (int z = box[2]; z < box[5]; ++z) {
                            // Turn the sixteenth's middle, then fill the one it lands in
                            final float[] middle = {(x + HALF) / GRID, 0, (z + HALF) / GRID};
                            turn(middle, facing);
                            filled[(int) (middle[0] * GRID)][y][(int) (middle[2] * GRID)] = true;
                        }
                    }
                }
            }
            final int[] at = new int[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                final int e1 = (axis + 1) % AXES;
                final int e2 = (axis + 2) % AXES;
                for (int sign = -1; sign <= 1; sign += 2) {
                    for (int layer = 0; layer < GRID; ++layer) {
                        // Which sixteenths of this layer show a face toward the sign
                        final boolean[][] open = new boolean[GRID][GRID];
                        for (int a = 0; a < GRID; ++a) {
                            for (int b = 0; b < GRID; ++b) {
                                at[axis] = layer;
                                at[e1] = a;
                                at[e2] = b;
                                if (!filled[at[0]][at[1]][at[2]]) {
                                    continue;
                                }
                                final int next = layer + sign;
                                at[axis] = next;
                                open[a][b] =
                                        next < 0 || next >= GRID || !filled[at[0]][at[1]][at[2]];
                            }
                        }
                        mergeLayer(open, axis, sign, layer);
                    }
                }
            }
        }

        /**
         * Cover a layer's open sixteenths with as few rectangles as a greedy sweep finds.
         *
         * @param open Which sixteenths show a face, by e1 then e2, cleared as they are covered.
         * @param axis The axis the faces point along.
         * @param sign Which way along it.
         * @param layer The layer of sixteenths, along the axis.
         */
        private void mergeLayer(boolean[][] open, int axis, int sign, int layer) {
            final int e1 = (axis + 1) % AXES;
            final int e2 = (axis + 2) % AXES;
            for (int a = 0; a < GRID; ++a) {
                for (int b = 0; b < GRID; ++b) {
                    if (!open[a][b]) {
                        continue;
                    }
                    int bEnd = b + 1;
                    while (bEnd < GRID && open[a][bEnd]) {
                        ++bEnd;
                    }
                    int aEnd = a + 1;
                    while (aEnd < GRID && rowOpen(open[aEnd], b, bEnd)) {
                        ++aEnd;
                    }
                    for (int i = a; i < aEnd; ++i) {
                        Arrays.fill(open[i], b, bEnd, false);
                    }
                    final float[] min = new float[AXES];
                    final float[] max = new float[AXES];
                    min[axis] = (float) layer / GRID;
                    max[axis] = (float) (layer + 1) / GRID;
                    min[e1] = (float) a / GRID;
                    max[e1] = (float) aEnd / GRID;
                    min[e2] = (float) b / GRID;
                    max[e2] = (float) bEnd / GRID;
                    rect(axis, sign, min, max);
                }
            }
        }

        /**
         * Whether a run of a row is all open.
         *
         * @param row The row.
         * @param from The first in the run.
         * @param to Past the last in the run.
         * @return True if every one is open.
         */
        private static boolean rowOpen(boolean[] row, int from, int to) {
            for (int i = from; i < to; ++i) {
                if (!row[i]) {
                    return false;
                }
            }
            return true;
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
