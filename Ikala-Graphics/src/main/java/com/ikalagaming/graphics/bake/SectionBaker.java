package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Merges a section's placements into compact meshes, one per {@link Material.Transparency}, with
 * the faces each placement's neighbors hide left out. Pure Java over flat arrays, so it runs on a
 * worker thread and is tested without a GPU.
 *
 * <p>Each placement puts one source mesh in one cell: turned by one of the {@link CubeRotations}
 * about its box's center, then moved by a whole number of boxes from the section's origin.
 * Positions come out in {@link BakedVertex} steps from the origin.
 */
public final class SectionBaker {

    /** How many bits each axis of a packed position takes. */
    public static final int POSITION_BITS = 10;

    /**
     * Materials each placement gives, by the way a face points in the world: sideways, up, then
     * down. See {@link #bakeByFace}.
     */
    public static final int FACE_KINDS = 3;

    /** The material for faces pointing sideways. */
    public static final int SIDE = 0;

    /** The material for faces pointing up. */
    public static final int TOP = 1;

    /** The material for faces pointing down. */
    public static final int BOTTOM = 2;

    /** A normal's y over this points mostly up: more than at 45 degrees. */
    private static final float MOSTLY = (float) Math.sqrt(0.5);

    /** The smallest cell offset along an axis. */
    public static final int MIN_CELL = -(1 << (POSITION_BITS - 1));

    /** The largest cell offset along an axis. */
    public static final int MAX_CELL = (1 << (POSITION_BITS - 1)) - 1;

    /** The mask of one axis in a packed position. */
    private static final int AXIS_MASK = (1 << POSITION_BITS) - 1;

    /**
     * One baked mesh.
     *
     * @param vertices The vertices, {@link BakedVertex#SIZE} bytes each, little endian, from 0 to
     *     the limit.
     * @param vertexCount How many vertices there are.
     * @param indices The triangles, three indices each.
     * @param min The minimum corner of its bounds, in steps.
     * @param max The maximum corner of its bounds, in steps.
     */
    public record Bucket(
            @NonNull ByteBuffer vertices,
            int vertexCount,
            int @NonNull [] indices,
            float @NonNull [] min,
            float @NonNull [] max) {

        /**
         * How many triangles there are.
         *
         * @return The triangle count.
         */
        public int triangleCount() {
            return indices.length / 3;
        }
    }

    /**
     * The meshes a section baked into, one per transparency, null where nothing went.
     *
     * @param buckets By {@link Material.Transparency} ordinal.
     */
    public record Result(@Nullable Bucket @NonNull [] buckets) {

        /**
         * The mesh for one transparency.
         *
         * @param transparency The transparency.
         * @return The mesh, or null if the section has nothing of it.
         */
        @Nullable public Bucket get(@NonNull Material.Transparency transparency) {
            return buckets[transparency.ordinal()];
        }

        /**
         * How many triangles there are across every bucket.
         *
         * @return The triangle count.
         */
        public int triangleCount() {
            int count = 0;
            for (Bucket bucket : buckets) {
                count += bucket == null ? 0 : bucket.triangleCount();
            }
            return count;
        }
    }

    /**
     * Pack a cell offset into one int.
     *
     * @param x The offset along x, in boxes.
     * @param y The offset along y, in boxes.
     * @param z The offset along z, in boxes.
     * @return The packed position.
     * @throws IllegalArgumentException If an offset doesn't fit in {@link #POSITION_BITS} bits.
     */
    public static int pack(int x, int y, int z) {
        if (x < MIN_CELL
                || x > MAX_CELL
                || y < MIN_CELL
                || y > MAX_CELL
                || z < MIN_CELL
                || z > MAX_CELL) {
            throw new IllegalArgumentException(
                    "Cell offsets must be from " + MIN_CELL + " to " + MAX_CELL);
        }
        return (x & AXIS_MASK)
                | (y & AXIS_MASK) << POSITION_BITS
                | (z & AXIS_MASK) << (2 * POSITION_BITS);
    }

    /**
     * One axis of a packed position.
     *
     * @param packed The packed position.
     * @param axis 0 for x, 1 for y, 2 for z.
     * @return The cell offset along that axis.
     */
    public static int unpack(int packed, int axis) {
        final int bits = packed >>> (axis * POSITION_BITS) & AXIS_MASK;
        // Sign extend
        return bits << (Integer.SIZE - POSITION_BITS) >> (Integer.SIZE - POSITION_BITS);
    }

    /**
     * Each thread's buckets, kept between bakes so they grow once rather than from small for every
     * bake: sections are baked again whenever their meshes come back near the observer, and the
     * growing was a large share of what a streamed world allocated.
     */
    private static final ThreadLocal<Builder[]> BUILDERS =
            ThreadLocal.withInitial(() -> new Builder[Material.Transparency.values().length]);

    /** A growing bucket while baking, reused by its thread, see {@link #BUILDERS}. */
    private static final class Builder {
        /** The vertices so far. */
        private ByteBuffer vertices =
                ByteBuffer.allocate(BakedVertex.SIZE * 256).order(ByteOrder.LITTLE_ENDIAN);

        /** How many vertices there are. */
        private int vertexCount;

        /** The indices so far. */
        private int[] indices = new int[3 * 256];

        /** How many indices there are. */
        private int indexCount;

        /** The bounds so far, in steps. */
        private final float[] min = new float[3];

        /** The bounds so far, in steps. */
        private final float[] max = new float[3];

        /** Start empty. */
        Builder() {
            reset();
        }

        /**
         * Empty it for another bake, keeping its room.
         *
         * @return It.
         */
        Builder reset() {
            vertexCount = 0;
            indexCount = 0;
            vertices.clear();
            Arrays.fill(min, Float.MAX_VALUE);
            Arrays.fill(max, -Float.MAX_VALUE);
            return this;
        }

        /**
         * Make room for and write a vertex, and take it into the bounds.
         *
         * @return Where the next vertex goes, in bytes.
         */
        int nextVertex() {
            if ((vertexCount + 1) * BakedVertex.SIZE > vertices.capacity()) {
                ByteBuffer bigger =
                        ByteBuffer.allocate(vertices.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
                bigger.put(0, vertices, 0, vertexCount * BakedVertex.SIZE);
                vertices = bigger;
            }
            return vertexCount++ * BakedVertex.SIZE;
        }

        /**
         * Grow the bounds to take in the vertex just written.
         *
         * @param at Where the vertex is.
         */
        void bound(int at) {
            for (int axis = 0; axis < 3; ++axis) {
                final float step = vertices.getShort(at + BakedVertex.POSITION_OFFSET + axis * 2);
                min[axis] = Math.min(min[axis], step);
                max[axis] = Math.max(max[axis], step);
            }
        }

        /**
         * Add an index.
         *
         * @param index The vertex.
         */
        void index(int index) {
            if (indexCount == indices.length) {
                indices = Arrays.copyOf(indices, indexCount * 2);
            }
            indices[indexCount++] = index;
        }

        /**
         * Finish the bucket.
         *
         * @return The bucket, or null if nothing went in.
         */
        @Nullable Bucket build() {
            if (indexCount == 0) {
                return null;
            }
            // Copied out exactly, since the builder is kept for the next bake
            final int bytes = vertexCount * BakedVertex.SIZE;
            final ByteBuffer out = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
            out.put(0, vertices, 0, bytes);
            return new Bucket(
                    out, vertexCount, Arrays.copyOf(indices, indexCount), min.clone(), max.clone());
        }
    }

    /**
     * Bake a section. Every array has one entry per placement.
     *
     * @param sources The mesh each placement puts down.
     * @param materials The material index each placement's vertices get.
     * @param packedPositions The cell each placement fills, from {@link #pack(int, int, int)}.
     * @param rotations Each placement's rotation, one of {@link CubeRotations}.
     * @param faceMasks The faces of each placement its neighbors hide, after rotation, one bit per
     *     {@link Face#bit()}.
     * @param userData What each placement writes into its vertices for shaders, not interpreted
     *     here.
     * @return The baked meshes.
     * @throws IllegalArgumentException If the arrays differ in length, a rotation is out of range,
     *     or a vertex lands too far from the origin.
     */
    public static Result bake(
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] materials,
            int @NonNull [] packedPositions,
            byte @NonNull [] rotations,
            byte @NonNull [] faceMasks,
            short @NonNull [] userData) {
        if (materials.length != sources.length) {
            throw new IllegalArgumentException("Every placement array must be the same length");
        }
        final int[] byFace = new int[materials.length * FACE_KINDS];
        for (int i = 0; i < materials.length; ++i) {
            Arrays.fill(byFace, i * FACE_KINDS, (i + 1) * FACE_KINDS, materials[i]);
        }
        return bakeByFace(sources, byFace, packedPositions, rotations, faceMasks, userData);
    }

    /**
     * Bake a section, with a material for each way a face can point in the world, so a block can
     * have a different top and bottom, like grass. Every array has one entry per placement, except
     * the materials.
     *
     * @param sources The mesh each placement puts down.
     * @param faceMaterials {@link #FACE_KINDS} material indices per placement: for faces pointing
     *     sideways, up, then down, after the placement's rotation; a triangle on no face of its box
     *     goes by its normal.
     * @param packedPositions The cell each placement fills, from {@link #pack(int, int, int)}.
     * @param rotations Each placement's rotation, one of {@link CubeRotations}.
     * @param faceMasks The faces of each placement its neighbors hide, after rotation, one bit per
     *     {@link Face#bit()}.
     * @param userData What each placement writes into its vertices for shaders, not interpreted
     *     here.
     * @return The baked meshes.
     * @throws IllegalArgumentException If the arrays differ in length, a rotation is out of range,
     *     or a vertex lands too far from the origin.
     */
    public static Result bakeByFace(
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] faceMaterials,
            int @NonNull [] packedPositions,
            byte @NonNull [] rotations,
            byte @NonNull [] faceMasks,
            short @NonNull [] userData) {
        return bakeByFace(
                sources, faceMaterials, packedPositions, rotations, faceMasks, userData, ALL_KINDS);
    }

    /** Every kind of bucket. */
    private static final Set<Material.Transparency> ALL_KINDS =
            Set.copyOf(EnumSet.allOf(Material.Transparency.class));

    /**
     * Bake only some of a section's buckets, see {@link #bakeByFace(BakeSource[], int[], int[],
     * byte[], byte[], short[])}: the placements of other kinds are skipped, as when only the
     * buckets kept off the GPU are baked again to put them back.
     *
     * @param sources The mesh each placement puts down.
     * @param faceMaterials {@link #FACE_KINDS} material indices per placement.
     * @param packedPositions The cell each placement fills, from {@link #pack(int, int, int)}.
     * @param rotations Each placement's rotation, one of {@link CubeRotations}.
     * @param faceMasks The faces of each placement its neighbors hide, after rotation.
     * @param userData What each placement writes into its vertices for shaders.
     * @param kinds The buckets to bake.
     * @return The baked meshes, only of those kinds.
     * @throws IllegalArgumentException If the arrays differ in length, a rotation is out of range,
     *     or a vertex lands too far from the origin.
     */
    public static Result bakeByFace(
            @NonNull BakeSource @NonNull [] sources,
            int @NonNull [] faceMaterials,
            int @NonNull [] packedPositions,
            byte @NonNull [] rotations,
            byte @NonNull [] faceMasks,
            short @NonNull [] userData,
            @NonNull Set<Material.Transparency> kinds) {
        final int count = sources.length;
        if (faceMaterials.length != count * FACE_KINDS
                || packedPositions.length != count
                || rotations.length != count
                || faceMasks.length != count
                || userData.length != count) {
            throw new IllegalArgumentException("Every placement array must be the same length");
        }
        final Builder[] builders = new Builder[Material.Transparency.values().length];
        int[] remap = new int[0];
        final float[] centered = new float[3];
        final float[] position = new float[3];
        final float[] normal = new float[3];
        final float[] tangent = new float[3];
        for (int placement = 0; placement < count; ++placement) {
            final BakeSource source = sources[placement];
            final int rotation = rotations[placement];
            if (rotation < 0 || rotation >= CubeRotations.COUNT) {
                throw new IllegalArgumentException(
                        "Rotation " + rotation + " isn't a cube rotation");
            }
            if (!kinds.contains(source.getTransparency())) {
                continue;
            }
            final int hidden = faceMasks[placement];
            final int bucket = source.getTransparency().ordinal();
            if (builders[bucket] == null) {
                final Builder[] kept = BUILDERS.get();
                if (kept[bucket] == null) {
                    kept[bucket] = new Builder();
                }
                builders[bucket] = kept[bucket].reset();
            }
            final Builder builder = builders[bucket];
            // A vertex shared by faces pointing different ways gets one copy per material
            if (remap.length < source.getVertexCount() * FACE_KINDS) {
                remap = new int[source.getVertexCount() * FACE_KINDS];
            }
            Arrays.fill(remap, 0, source.getVertexCount() * FACE_KINDS, -1);

            final float size = source.getBoxSize();
            final float[] boxMin = source.getBoxMin();
            final int[] indices = source.getIndices();
            final byte[] triangleFaces = source.getTriangleFaces();
            for (int triangle = 0; triangle < triangleFaces.length; ++triangle) {
                final byte face = triangleFaces[triangle];
                final Face turned =
                        face == Face.NONE ? null : CubeRotations.turn(rotation, Face.ALL[face]);
                if (turned != null && (hidden & turned.bit()) != 0) {
                    continue;
                }
                final int kind;
                if (turned != null) {
                    kind = faceKind(turned);
                } else {
                    // On no face of the box, like a slab's top: by the way the triangle points
                    CubeRotations.apply(
                            rotation, source.getNormals(), indices[triangle * 3] * 3, normal, 0);
                    kind = kindOf(normal[1]);
                }
                final int material = faceMaterials[placement * FACE_KINDS + kind];
                for (int corner = 0; corner < 3; ++corner) {
                    final int vertex = indices[triangle * 3 + corner];
                    final int slot = vertex * FACE_KINDS + kind;
                    if (remap[slot] < 0) {
                        // Turn about the box's center, then move to the cell
                        for (int axis = 0; axis < 3; ++axis) {
                            centered[axis] =
                                    source.getPositions()[vertex * 3 + axis]
                                            - boxMin[axis]
                                            - size / 2;
                        }
                        CubeRotations.apply(rotation, centered, position);
                        for (int axis = 0; axis < 3; ++axis) {
                            position[axis] +=
                                    size / 2 + unpack(packedPositions[placement], axis) * size;
                        }
                        CubeRotations.apply(rotation, source.getNormals(), vertex * 3, normal, 0);
                        CubeRotations.apply(rotation, source.getTangents(), vertex * 3, tangent, 0);
                        final int at = builder.nextVertex();
                        BakedVertex.put(
                                builder.vertices,
                                at,
                                position,
                                0,
                                userData[placement],
                                normal,
                                tangent,
                                0,
                                source.getFlippedBitangents()[vertex],
                                source.getUvs(),
                                vertex * 2,
                                material);
                        builder.bound(at);
                        remap[slot] = builder.vertexCount - 1;
                    }
                    builder.index(remap[slot]);
                }
            }
        }

        final Bucket[] buckets = new Bucket[builders.length];
        for (int i = 0; i < builders.length; ++i) {
            buckets[i] = builders[i] == null ? null : builders[i].build();
        }
        return new Result(buckets);
    }

    /**
     * Which material a face takes, by the way it points in the world.
     *
     * @param face The face after rotation.
     * @return {@link #TOP}, {@link #BOTTOM} or {@link #SIDE}.
     */
    static int faceKind(@NonNull Face face) {
        if (face == Face.POS_Y) {
            return TOP;
        }
        if (face == Face.NEG_Y) {
            return BOTTOM;
        }
        return SIDE;
    }

    /**
     * Which material a triangle on no face of its box takes, by the way its normal points: up or
     * down if more than sideways.
     *
     * @param up The normal's y, after rotation, from a unit normal.
     * @return {@link #TOP}, {@link #BOTTOM} or {@link #SIDE}.
     */
    static int kindOf(float up) {
        if (up > MOSTLY) {
            return TOP;
        }
        if (up < -MOSTLY) {
            return BOTTOM;
        }
        return SIDE;
    }

    /** Static helpers only. */
    private SectionBaker() {}
}
