package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The vertex format of skinned meshes: the full scene vertex (position, normal, tangent, bitangent,
 * texture coordinates), then four bone indices, their four weights, and a mask region.
 *
 * <ul>
 *   <li>Bone indices are 16-bit unsigned, into the instance's palette.
 *   <li>Weights are 8-bit normalized and add up to 255, so they sum to exactly 1 on the GPU.
 *   <li>The region, 0 to {@value #MAX_REGION}, lets an instance hide parts of a mesh, like skin
 *       under clothing. {@value #NO_REGION} is never hidden. Hiding works per vertex, so a triangle
 *       must have all three of its vertices in one region; split vertices along region borders.
 * </ul>
 *
 * @author Ches Burks
 */
public final class SkinnedVertex {
    /** Bones that can affect one vertex. */
    public static final int BONES_PER_VERTEX = 4;

    /** Floats per vertex in the scene vertex format the skinned format starts with. */
    public static final int SCENE_FLOATS = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Where the bone indices start, in bytes. */
    public static final int JOINTS_OFFSET = MeshData.VERTEX_SIZE_IN_BYTES;

    /** Where the weights start, in bytes. */
    public static final int WEIGHTS_OFFSET = JOINTS_OFFSET + BONES_PER_VERTEX * Short.BYTES;

    /** Where the region is, in bytes. */
    public static final int REGION_OFFSET = WEIGHTS_OFFSET + BONES_PER_VERTEX;

    /** The size of one vertex, in bytes. */
    public static final int SIZE = REGION_OFFSET + Integer.BYTES;

    /** Where the texture coordinates are, in bytes, after position, normal, tangent, bitangent. */
    public static final int UV_OFFSET = 12 * Float.BYTES;

    /** The highest region that can be hidden, one per bit of a 64-bit mask. */
    public static final int MAX_REGION = 63;

    /** The region of vertices that are never hidden. */
    public static final int NO_REGION = 255;

    /** The highest bone index a vertex can name. */
    public static final int MAX_JOINT = 0xFFFF;

    private SkinnedVertex() {}

    /**
     * Check skinned vertex data and pack it into this format.
     *
     * @param vertices The scene vertices, {@link #SCENE_FLOATS} floats each.
     * @param joints Four bone indices per vertex.
     * @param weights Four weights per vertex, which are scaled to add up to 1. A vertex whose
     *     weights are all zero follows its first bone.
     * @param regions One region per vertex, or null for no regions.
     * @param out Where to write, at its position, which is moved past the vertices. It must have
     *     room for {@link #SIZE} bytes per vertex.
     * @return How many bones the mesh needs: one more than its highest bone index.
     * @throws IllegalArgumentException If the arrays don't agree on the vertex count, or a value is
     *     out of range.
     */
    public static int pack(
            float @NonNull [] vertices,
            int @NonNull [] joints,
            float @NonNull [] weights,
            int[] regions,
            @NonNull ByteBuffer out) {
        if (vertices.length % SCENE_FLOATS != 0) {
            throw new IllegalArgumentException(
                    vertices.length + " floats isn't a whole number of vertices");
        }
        final int count = vertices.length / SCENE_FLOATS;
        if (joints.length != count * BONES_PER_VERTEX
                || weights.length != count * BONES_PER_VERTEX) {
            throw new IllegalArgumentException(
                    "Expected "
                            + BONES_PER_VERTEX
                            + " bones and weights for each of "
                            + count
                            + " vertices");
        }
        if (regions != null && regions.length != count) {
            throw new IllegalArgumentException(
                    "Expected a region for each of " + count + " vertices");
        }
        final ByteOrder order = out.order();
        out.order(ByteOrder.LITTLE_ENDIAN);
        int highest = -1;
        final int[] quantized = new int[BONES_PER_VERTEX];
        for (int v = 0; v < count; ++v) {
            for (int f = 0; f < SCENE_FLOATS; ++f) {
                out.putFloat(vertices[v * SCENE_FLOATS + f]);
            }
            for (int b = 0; b < BONES_PER_VERTEX; ++b) {
                final int joint = joints[v * BONES_PER_VERTEX + b];
                if (joint < 0 || joint > MAX_JOINT) {
                    throw new IllegalArgumentException(
                            "Vertex " + v + " names the bone " + joint + ", past " + MAX_JOINT);
                }
                out.putShort((short) joint);
            }
            quantize(weights, v * BONES_PER_VERTEX, quantized);
            for (int b = 0; b < BONES_PER_VERTEX; ++b) {
                out.put((byte) quantized[b]);
                // Only bones that move the vertex need to be in the palette
                if (quantized[b] > 0) {
                    highest = Math.max(highest, joints[v * BONES_PER_VERTEX + b]);
                }
            }
            final int region = regions == null ? NO_REGION : regions[v];
            if ((region < 0 || region > MAX_REGION) && region != NO_REGION) {
                throw new IllegalArgumentException(
                        "Vertex "
                                + v
                                + " is in the region "
                                + region
                                + ", not 0 to "
                                + MAX_REGION
                                + " or "
                                + NO_REGION);
            }
            out.putInt(region);
        }
        out.order(order);
        return highest + 1;
    }

    /**
     * Turn four weights into bytes that add up to exactly 255, giving the rounding's remainder to
     * the largest.
     *
     * @param weights The weights.
     * @param offset Where the vertex's four weights start.
     * @param out Receives the four bytes, 0 to 255.
     * @throws IllegalArgumentException If a weight is negative or not finite.
     */
    static void quantize(float[] weights, int offset, int[] out) {
        float total = 0;
        for (int b = 0; b < BONES_PER_VERTEX; ++b) {
            final float weight = weights[offset + b];
            if (weight < 0 || Float.isInfinite(weight)) {
                throw new IllegalArgumentException("The weight " + weight + " is not usable");
            }
            total += weight;
        }
        if (total <= 0) {
            out[0] = 255;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            return;
        }
        int sum = 0;
        int largest = 0;
        for (int b = 0; b < BONES_PER_VERTEX; ++b) {
            out[b] = Math.round(weights[offset + b] / total * 255);
            sum += out[b];
            if (weights[offset + b] > weights[offset + largest]) {
                largest = b;
            }
        }
        out[largest] += 255 - sum;
    }
}
