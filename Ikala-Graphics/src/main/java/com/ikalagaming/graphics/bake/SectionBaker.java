package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
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

    /** A growing bucket while baking. */
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
        private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};

        /** The bounds so far, in steps. */
        private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

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
            vertices.limit(vertexCount * BakedVertex.SIZE);
            return new Bucket(vertices, vertexCount, Arrays.copyOf(indices, indexCount), min, max);
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
        final int count = sources.length;
        if (materials.length != count
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
            final int hidden = faceMasks[placement];
            final int bucket = source.getTransparency().ordinal();
            if (builders[bucket] == null) {
                builders[bucket] = new Builder();
            }
            final Builder builder = builders[bucket];
            if (remap.length < source.getVertexCount()) {
                remap = new int[source.getVertexCount()];
            }
            Arrays.fill(remap, 0, source.getVertexCount(), -1);

            final float size = source.getBoxSize();
            final float[] boxMin = source.getBoxMin();
            final int[] indices = source.getIndices();
            final byte[] triangleFaces = source.getTriangleFaces();
            for (int triangle = 0; triangle < triangleFaces.length; ++triangle) {
                final byte face = triangleFaces[triangle];
                if (face != Face.NONE
                        && (hidden & CubeRotations.turn(rotation, Face.ALL[face]).bit()) != 0) {
                    continue;
                }
                for (int corner = 0; corner < 3; ++corner) {
                    final int vertex = indices[triangle * 3 + corner];
                    if (remap[vertex] < 0) {
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
                                materials[placement]);
                        builder.bound(at);
                        remap[vertex] = builder.vertexCount - 1;
                    }
                    builder.index(remap[vertex]);
                }
            }
        }

        final Bucket[] buckets = new Bucket[builders.length];
        for (int i = 0; i < builders.length; ++i) {
            buckets[i] = builders[i] == null ? null : builders[i].build();
        }
        return new Result(buckets);
    }

    /** Static helpers only. */
    private SectionBaker() {}
}
