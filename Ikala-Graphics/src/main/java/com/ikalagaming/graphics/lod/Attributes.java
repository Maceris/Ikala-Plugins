package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.vulkan.ToneMapMath;

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

    /**
     * The low half of an averaged corner's texture coordinates: a half float NaN, which no real
     * texture coordinate is. An averaged corner has no texture coordinates; it carries its own
     * color, roughness, metallic and specular, blended from what its triangle stands for, in the
     * tangent's int and the high half of this one (see {@link #averaged}).
     */
    public static final int NO_UV = 0x7E00;

    /**
     * What a coverage volume shell's corner holds in place of texture coordinates, another NaN, so
     * it is averaged too, and in the specular's place how much of the light it stops, see {@link
     * #shell}.
     */
    public static final int SHELL_UV = 0x7E01;

    /** The most a byte holds, for values from 0 to 1. */
    private static final int BYTE_MAX = 0xFF;

    /** Bits in a byte. */
    private static final int BYTE_BITS = 8;

    /** A half float's exponent bits, all set for a NaN or infinity. */
    private static final int HALF_EXPONENT = 0x7C00;

    /** A half float's mantissa bits, some set for a NaN. */
    private static final int HALF_MANTISSA = 0x03FF;

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

    /** The low 16 bits. */
    private static final int SHORT_MASK = 0xFFFF;

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
     * Whether packed texture coordinates say there are none, as an averaged corner's do: their low
     * half is a NaN, as {@link #NO_UV} is.
     *
     * @param uv The two packed half floats.
     * @return True if the first is NaN.
     */
    public static boolean noTextureCoordinates(int uv) {
        return (uv & HALF_EXPONENT) == HALF_EXPONENT && (uv & HALF_MANTISSA) != 0;
    }

    /**
     * Set an averaged corner's attributes: its normal, then in place of a tangent its color,
     * encoded as sRGB as textures are (the scene's g-buffer holds base colors that way, and it
     * keeps precision in the darks), and its roughness, a byte each; in place of texture
     * coordinates {@link #NO_UV}, then its metallic and specular, a byte each.
     *
     * @param normal The normal; it needn't be unit length, but mustn't be zero.
     * @param average Its {@link MaterialAverages#VALUES} values, its color linear.
     * @param out Where the corner goes.
     * @param at Where in it.
     */
    public static void averaged(
            float @NonNull [] normal, float @NonNull [] average, int @NonNull [] out, int at) {
        out[at + NORMAL] = encodeDirection(normal);
        int color = 0;
        for (int c = 0; c < AXES; ++c) {
            color |=
                    toByte(ToneMapMath.encode(average[MaterialAverages.RED + c]))
                            << (c * BYTE_BITS);
        }
        out[at + TANGENT] =
                color | toByte(average[MaterialAverages.ROUGHNESS]) << (AXES * BYTE_BITS);
        out[at + UV] =
                NO_UV
                        | toByte(average[MaterialAverages.METALLIC]) << SHORT_BITS
                        | toByte(average[MaterialAverages.SPECULAR]) << (SHORT_BITS + BYTE_BITS);
    }

    /**
     * Set a coverage volume shell's corner, as {@link #averaged} sets a corner, but with how much
     * of the light it stops in place of its specular, for a hashed alpha test.
     *
     * @param normal The normal; it needn't be unit length, but mustn't be zero.
     * @param average Its {@link MaterialAverages#VALUES} values, its color linear; the specular is
     *     ignored.
     * @param coverage How much of the light it stops, 0 to 1.
     * @param out Where the corner goes.
     * @param at Where in it.
     */
    public static void shell(
            float @NonNull [] normal,
            float @NonNull [] average,
            float coverage,
            int @NonNull [] out,
            int at) {
        averaged(normal, average, out, at);
        out[at + UV] =
                SHELL_UV
                        | toByte(average[MaterialAverages.METALLIC]) << SHORT_BITS
                        | toByte(coverage) << (SHORT_BITS + BYTE_BITS);
    }

    /**
     * Whether a corner is a coverage volume shell's.
     *
     * @param attributes Corners' ints.
     * @param at Where the corner starts.
     * @return True if it is.
     */
    public static boolean isShell(int @NonNull [] attributes, int at) {
        return (attributes[at + UV] & SHORT_MASK) == SHELL_UV;
    }

    /**
     * A textured corner's texture coordinates.
     *
     * @param attributes Corners' ints.
     * @param at Where the corner starts.
     * @return Its u and v.
     */
    public static float[] textureCoordinates(int @NonNull [] attributes, int at) {
        final int uv = attributes[at + UV];
        return new float[] {
            Float.float16ToFloat((short) (uv & LOW_16)),
            Float.float16ToFloat((short) (uv >>> SHORT_BITS))
        };
    }

    /**
     * Whether a corner is averaged.
     *
     * @param attributes Corners' ints.
     * @param at Where the corner starts.
     * @return True if it carries its own color rather than texture coordinates.
     */
    public static boolean isAveraged(int @NonNull [] attributes, int at) {
        return noTextureCoordinates(attributes[at + UV]);
    }

    /**
     * Read an averaged corner's values back.
     *
     * @param attributes Corners' ints.
     * @param at Where the corner starts.
     * @param out Receives its {@link MaterialAverages#VALUES} values, its color linear.
     */
    public static void averageOf(int @NonNull [] attributes, int at, float @NonNull [] out) {
        final int color = attributes[at + TANGENT];
        for (int c = 0; c < AXES; ++c) {
            out[MaterialAverages.RED + c] = ToneMapMath.decode(fromByte(color >>> (c * BYTE_BITS)));
        }
        out[MaterialAverages.ROUGHNESS] = fromByte(color >>> (AXES * BYTE_BITS));
        out[MaterialAverages.METALLIC] = fromByte(attributes[at + UV] >>> SHORT_BITS);
        out[MaterialAverages.SPECULAR] = fromByte(attributes[at + UV] >>> (SHORT_BITS + BYTE_BITS));
    }

    /**
     * A value from 0 to 1 as a byte.
     *
     * @param value The value, clamped.
     * @return The byte, in the low bits.
     */
    private static int toByte(double value) {
        return (int) Math.round(Math.clamp(value, 0, 1) * BYTE_MAX);
    }

    /**
     * A byte back as a value from 0 to 1.
     *
     * @param bits The byte, in the low bits; higher bits are ignored.
     * @return The value.
     */
    private static float fromByte(int bits) {
        return (bits & BYTE_MAX) / (float) BYTE_MAX;
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
