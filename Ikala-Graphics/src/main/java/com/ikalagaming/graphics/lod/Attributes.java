package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.bake.BakedVertex;

import lombok.NonNull;

import java.nio.ByteBuffer;

/**
 * A textured triangle corner's normal, tangent and texture coordinates, packed in {@value #VALUES}
 * ints as {@link BakedVertex} stores them: the octahedral normal's two snorm16s, the octahedral
 * tangent's two snorm16s with the bitangent's flip in the lowest bit, and the two half floats of
 * the texture coordinates. Level of detail keeps these only for triangles whose material is
 * textured rather than projected; a mesh's, or a cluster's, are {@value #PER_TRIANGLE} ints a
 * triangle, zeros for projected triangles, or none at all if every triangle is projected.
 */
public final class Attributes {

    /** Ints per corner. */
    public static final int VALUES = 3;

    /** Ints per triangle. */
    public static final int PER_TRIANGLE = VALUES * LodMesh.CORNERS;

    /** No attributes: every triangle projected. */
    public static final int[] NONE = new int[0];

    /** Where the normal is in a corner's ints. */
    private static final int NORMAL = 0;

    /** Where the tangent is. */
    private static final int TANGENT = 1;

    /** Where the texture coordinates are. */
    private static final int UV = 2;

    /** The bit of the tangent's int that holds the bitangent's flip. */
    private static final int FLIP_BIT = 1;

    /** The low 16 bits of an int. */
    private static final int LOW_16 = 0xFFFF;

    /** Bits in a short. */
    private static final int SHORT_BITS = 16;

    /** Coordinates in a direction. */
    private static final int AXES = 3;

    /**
     * Read one baked vertex's attributes.
     *
     * @param vertices The baked vertices, little endian.
     * @param vertex Which vertex.
     * @param out Where they go.
     * @param at Where in it.
     */
    public static void fromBaked(
            @NonNull ByteBuffer vertices, int vertex, int @NonNull [] out, int at) {
        final int base = vertex * BakedVertex.SIZE;
        out[at + NORMAL] = vertices.getInt(base + BakedVertex.NORMAL_OFFSET);
        final boolean flipped =
                (vertices.getShort(base + BakedVertex.FLAGS_OFFSET)
                                & BakedVertex.FLAG_FLIPPED_BITANGENT)
                        != 0;
        out[at + TANGENT] =
                (vertices.getInt(base + BakedVertex.TANGENT_OFFSET) & ~FLIP_BIT)
                        | (flipped ? FLIP_BIT : 0);
        out[at + UV] = vertices.getInt(base + BakedVertex.UV_OFFSET);
    }

    /**
     * Whether a triangle is textured: its attributes aren't all zero. A textured corner never is,
     * since its tangent is at right angles to its normal and so never encodes the same.
     *
     * @param attributes Triangles' attributes, or {@link #NONE}.
     * @param triangle The triangle.
     * @return True if it is textured.
     */
    public static boolean textured(int @NonNull [] attributes, int triangle) {
        if (attributes.length == 0) {
            return false;
        }
        final int base = triangle * PER_TRIANGLE;
        for (int i = 0; i < PER_TRIANGLE; ++i) {
            if (attributes[base + i] != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tangent's bits as stored in a vertex, without the flip.
     *
     * @param tangent The packed tangent.
     * @return Its two snorm16s.
     */
    public static int tangentBits(int tangent) {
        return tangent & ~FLIP_BIT;
    }

    /**
     * Whether a packed tangent's bitangent is flipped.
     *
     * @param tangent The packed tangent.
     * @return True if it points the other way.
     */
    public static boolean flipped(int tangent) {
        return (tangent & FLIP_BIT) != 0;
    }

    /**
     * Whether two corners' attributes are the same, so they can share a vertex.
     *
     * @param a The first corners' ints.
     * @param at Where the first corner starts.
     * @param b The second corners' ints.
     * @param bt Where the second corner starts.
     * @return True if every value matches.
     */
    public static boolean same(int @NonNull [] a, int at, int @NonNull [] b, int bt) {
        return a[at] == b[bt] && a[at + 1] == b[bt + 1] && a[at + 2] == b[bt + 2];
    }

    /**
     * Blend three corners' attributes with barycentric weights, as a point inside their triangle
     * takes them: directions blended and normalized, texture coordinates blended, the flip of the
     * corner that weighs most.
     *
     * @param in The corners' ints.
     * @param first Where the first of the three corners starts; the others follow.
     * @param weights The three weights, summing to 1.
     * @param out Where the blended corner goes.
     * @param at Where in it.
     */
    public static void blend(
            int @NonNull [] in,
            int first,
            double @NonNull [] weights,
            int @NonNull [] out,
            int at) {
        final float[] normal = new float[AXES];
        final float[] tangent = new float[AXES];
        final float[] direction = new float[AXES];
        double u = 0;
        double v = 0;
        int heaviest = 0;
        for (int corner = 0; corner < LodMesh.CORNERS; ++corner) {
            final int base = first + corner * VALUES;
            final float weight = (float) weights[corner];
            decodeDirection(in[base + NORMAL], direction);
            for (int axis = 0; axis < AXES; ++axis) {
                normal[axis] += weight * direction[axis];
            }
            decodeDirection(tangentBits(in[base + TANGENT]), direction);
            for (int axis = 0; axis < AXES; ++axis) {
                tangent[axis] += weight * direction[axis];
            }
            u += weights[corner] * Float.float16ToFloat((short) (in[base + UV] & LOW_16));
            v += weights[corner] * Float.float16ToFloat((short) (in[base + UV] >>> SHORT_BITS));
            if (weights[corner] > weights[heaviest]) {
                heaviest = corner;
            }
        }
        out[at + NORMAL] = encodeDirection(normal);
        out[at + TANGENT] =
                (encodeDirection(tangent) & ~FLIP_BIT)
                        | (in[first + heaviest * VALUES + TANGENT] & FLIP_BIT);
        out[at + UV] =
                (Float.floatToFloat16((float) u) & LOW_16)
                        | (Float.floatToFloat16((float) v) << SHORT_BITS);
    }

    /**
     * Decode an octahedral direction's two snorm16s.
     *
     * @param packed The two snorm16s, the first in the low bits.
     * @param out Receives the unit direction.
     */
    public static void decodeDirection(int packed, float @NonNull [] out) {
        BakedVertex.fromOctahedral(
                BakedVertex.unsnorm16((short) (packed & LOW_16)),
                BakedVertex.unsnorm16((short) (packed >>> SHORT_BITS)),
                out);
    }

    /**
     * Encode a direction, normalized, as an octahedral direction's two snorm16s.
     *
     * @param direction The direction; it needn't be unit length.
     * @return The two snorm16s, the first in the low bits.
     */
    public static int encodeDirection(float @NonNull [] direction) {
        final float[] encoded = new float[2];
        BakedVertex.octahedral(direction[0], direction[1], direction[2], encoded);
        return (BakedVertex.snorm16(encoded[0]) & LOW_16)
                | (BakedVertex.snorm16(encoded[1]) << SHORT_BITS);
    }

    /** Static helpers only. */
    private Attributes() {}
}
