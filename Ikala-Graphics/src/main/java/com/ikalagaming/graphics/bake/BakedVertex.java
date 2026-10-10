package com.ikalagaming.graphics.bake;

import lombok.NonNull;

import java.nio.ByteBuffer;

/**
 * The compact vertex baked sections are drawn from, {@value #SIZE} bytes, little endian:
 *
 * <pre>
 * int16 x, y, z  // position from the section's origin, in steps of 1 / {@link #STEPS_PER_UNIT} unit
 * int16 user     // the placement's user data, which graphics doesn't interpret
 * snorm16 normal[2]   // octahedral
 * snorm16 tangent[2]  // octahedral
 * half u, v
 * uint16 material     // index into the material table
 * uint16 flags        // FLAG_FLIPPED_BITANGENT
 * </pre>
 *
 * <p>Positions are whole steps, so a point on the grid is exact in every section and neighbors line
 * up without cracks. The section's instance is scaled by {@code 1 / STEPS_PER_UNIT}, so the mesh
 * itself is in steps. The first 8 bytes also read as {@code R16G16B16A16_SNORM}, a format ray
 * tracing builders accept, with the scale moved into the instance transform.
 *
 * <p>{@code scene_baked.vert} decodes this; keep the two in step.
 */
public final class BakedVertex {

    /** The size of one vertex in bytes. */
    public static final int SIZE = 24;

    /** How many position steps make up one unit of the source meshes. */
    public static final int STEPS_PER_UNIT = 1024;

    /** Where the position starts. */
    public static final int POSITION_OFFSET = 0;

    /** Where the user data is, the fourth position component. */
    public static final int USER_OFFSET = 6;

    /** Where the octahedral normal starts. */
    public static final int NORMAL_OFFSET = 8;

    /** Where the octahedral tangent starts. */
    public static final int TANGENT_OFFSET = 12;

    /** Where the texture coordinates start. */
    public static final int UV_OFFSET = 16;

    /** Where the material index is. */
    public static final int MATERIAL_OFFSET = 20;

    /** Where the flags are. */
    public static final int FLAGS_OFFSET = 22;

    /** Set in the flags when the bitangent is {@code -(normal × tangent)}. */
    public static final int FLAG_FLIPPED_BITANGENT = 1;

    /** The largest material index that fits. */
    public static final int MAX_MATERIAL = 0xFFFF;

    /** The furthest from the origin a position can be, in units, either way. */
    public static final float MAX_DISTANCE = (float) Short.MAX_VALUE / STEPS_PER_UNIT;

    /**
     * The step a position falls on.
     *
     * @param units The position in units.
     * @return The step.
     * @throws IllegalArgumentException If it is too far from the origin to fit.
     */
    public static short position(float units) {
        final long step = Math.round((double) units * STEPS_PER_UNIT);
        if (step < Short.MIN_VALUE || step > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A baked position must be within "
                            + MAX_DISTANCE
                            + " units of the section's origin, it is "
                            + units);
        }
        return (short) step;
    }

    /**
     * A float in [-1, 1] as a signed normalized 16-bit value.
     *
     * @param value The value.
     * @return The snorm16.
     */
    public static short snorm16(float value) {
        return (short) Math.round(Math.max(-1, Math.min(1, value)) * Short.MAX_VALUE);
    }

    /**
     * A signed normalized 16-bit value as a float.
     *
     * @param value The snorm16.
     * @return The value in [-1, 1].
     */
    public static float unsnorm16(short value) {
        return Math.max(-1, (float) value / Short.MAX_VALUE);
    }

    /**
     * Encode a unit direction as two octahedral coordinates in [-1, 1].
     *
     * @param x The direction's x.
     * @param y The direction's y.
     * @param z The direction's z.
     * @param out Receives the two coordinates.
     */
    public static void octahedral(float x, float y, float z, float @NonNull [] out) {
        final float length = Math.abs(x) + Math.abs(y) + Math.abs(z);
        if (length == 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        float u = x / length;
        float v = y / length;
        if (z < 0) {
            // Fold the lower half over the diagonals
            final float foldedU = (1 - Math.abs(v)) * (u >= 0 ? 1 : -1);
            final float foldedV = (1 - Math.abs(u)) * (v >= 0 ? 1 : -1);
            u = foldedU;
            v = foldedV;
        }
        out[0] = u;
        out[1] = v;
    }

    /**
     * Decode two octahedral coordinates into a unit direction.
     *
     * @param u The first coordinate.
     * @param v The second coordinate.
     * @param out Receives the direction, normalized.
     */
    public static void fromOctahedral(float u, float v, float @NonNull [] out) {
        float x = u;
        float y = v;
        final float z = 1 - Math.abs(u) - Math.abs(v);
        if (z < 0) {
            x = (1 - Math.abs(v)) * (u >= 0 ? 1 : -1);
            y = (1 - Math.abs(u)) * (v >= 0 ? 1 : -1);
        }
        final float length = (float) Math.sqrt(x * x + y * y + z * z);
        out[0] = x / length;
        out[1] = y / length;
        out[2] = z / length;
    }

    /**
     * Write one vertex.
     *
     * @param out Where to write, absolutely, in little endian order.
     * @param at The byte offset of the vertex.
     * @param position The position in units from the section's origin, 3 floats from {@code
     *     positionOffset}.
     * @param positionOffset Where the position starts.
     * @param user The placement's user data.
     * @param normal The unit normal, 3 floats from {@code directionOffset}.
     * @param tangent The unit tangent, 3 floats from {@code directionOffset}.
     * @param directionOffset Where the normal and tangent start.
     * @param flippedBitangent Whether the bitangent is {@code -(normal × tangent)}.
     * @param uv The texture coordinates, 2 floats from {@code uvOffset}.
     * @param uvOffset Where the texture coordinates start.
     * @param material The material index.
     * @throws IllegalArgumentException If the position is out of reach or the material index too
     *     big.
     */
    public static void put(
            @NonNull ByteBuffer out,
            int at,
            float @NonNull [] position,
            int positionOffset,
            short user,
            float @NonNull [] normal,
            float @NonNull [] tangent,
            int directionOffset,
            boolean flippedBitangent,
            float @NonNull [] uv,
            int uvOffset,
            int material) {
        if (material < 0 || material > MAX_MATERIAL) {
            throw new IllegalArgumentException("Material index " + material + " doesn't fit");
        }
        out.putShort(at + POSITION_OFFSET, position(position[positionOffset]));
        out.putShort(at + POSITION_OFFSET + 2, position(position[positionOffset + 1]));
        out.putShort(at + POSITION_OFFSET + 4, position(position[positionOffset + 2]));
        out.putShort(at + USER_OFFSET, user);
        final float[] encoded = new float[2];
        octahedral(
                normal[directionOffset],
                normal[directionOffset + 1],
                normal[directionOffset + 2],
                encoded);
        out.putShort(at + NORMAL_OFFSET, snorm16(encoded[0]));
        out.putShort(at + NORMAL_OFFSET + 2, snorm16(encoded[1]));
        octahedral(
                tangent[directionOffset],
                tangent[directionOffset + 1],
                tangent[directionOffset + 2],
                encoded);
        out.putShort(at + TANGENT_OFFSET, snorm16(encoded[0]));
        out.putShort(at + TANGENT_OFFSET + 2, snorm16(encoded[1]));
        out.putShort(at + UV_OFFSET, Float.floatToFloat16(uv[uvOffset]));
        out.putShort(at + UV_OFFSET + 2, Float.floatToFloat16(uv[uvOffset + 1]));
        out.putShort(at + MATERIAL_OFFSET, (short) material);
        out.putShort(at + FLAGS_OFFSET, (short) (flippedBitangent ? FLAG_FLIPPED_BITANGENT : 0));
    }

    /** Static helpers only. */
    private BakedVertex() {}
}
