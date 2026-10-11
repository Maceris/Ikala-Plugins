package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.bake.BakedVertex;

import lombok.NonNull;

import java.nio.ByteBuffer;

/**
 * The vertex level of detail clusters are drawn from, {@value #SIZE} bytes, little endian:
 *
 * <pre>
 * int32 x, y, z       // position on a grid of 1 / {@link #STEPS_PER_UNIT} unit from the world's origin
 * snorm16 normal[2]   // octahedral
 * uint16 material     // index into the material table
 * uint16 flags        // none yet
 * </pre>
 *
 * <p>Every cluster of a world shares one grid, so a vertex two clusters share, such as one on a
 * locked group border, encodes the same in both and they meet exactly whatever their levels. The
 * grid is the baked sections' own, so leaves are exact; simplified vertices move at most half a
 * step. The shader subtracts the camera's grid position as integers, so positions are exact
 * relative to the camera however far from the origin.
 *
 * <p>{@code scene_lod.vert} and {@code shadow_lod.vert} decode this; keep them in step.
 */
public final class LodVertex {

    /** The size of one vertex in bytes. */
    public static final int SIZE = 20;

    /** Grid steps per unit, the baked sections' own precision. */
    public static final int STEPS_PER_UNIT = BakedVertex.STEPS_PER_UNIT;

    /** Where the position starts. */
    public static final int POSITION_OFFSET = 0;

    /** Where the octahedral normal starts. */
    public static final int NORMAL_OFFSET = 12;

    /** Where the material index is. */
    public static final int MATERIAL_OFFSET = 16;

    /** Where the flags are. */
    public static final int FLAGS_OFFSET = 18;

    /** The largest material index that fits. */
    public static final int MAX_MATERIAL = 0xFFFF;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * The grid step a coordinate falls on.
     *
     * @param units The coordinate, in units from the world's origin.
     * @return The nearest step.
     * @throws IllegalArgumentException If it is too far from the origin to fit.
     */
    public static int grid(double units) {
        final long step = Math.round(units * STEPS_PER_UNIT);
        if (step < Integer.MIN_VALUE || step > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A LOD position must be within "
                            + (double) Integer.MAX_VALUE / STEPS_PER_UNIT
                            + " units of the world's origin, it is "
                            + units);
        }
        return (int) step;
    }

    /**
     * Write a vertex.
     *
     * @param out Where it goes, a little endian buffer.
     * @param index Which vertex it is; it goes at {@code index * SIZE}.
     * @param grid Its position, in grid steps.
     * @param normal Its normal, unit length.
     * @param material Its material index.
     * @throws IllegalArgumentException If the material doesn't fit.
     */
    public static void put(
            @NonNull ByteBuffer out,
            int index,
            int @NonNull [] grid,
            float @NonNull [] normal,
            int material) {
        if (material < 0 || material > MAX_MATERIAL) {
            throw new IllegalArgumentException("Material " + material + " doesn't fit");
        }
        final int base = index * SIZE;
        for (int axis = 0; axis < AXES; ++axis) {
            out.putInt(base + POSITION_OFFSET + axis * Integer.BYTES, grid[axis]);
        }
        final float[] octahedral = new float[2];
        BakedVertex.octahedral(normal[0], normal[1], normal[2], octahedral);
        out.putShort(base + NORMAL_OFFSET, BakedVertex.snorm16(octahedral[0]));
        out.putShort(base + NORMAL_OFFSET + Short.BYTES, BakedVertex.snorm16(octahedral[1]));
        out.putShort(base + MATERIAL_OFFSET, (short) material);
        out.putShort(base + FLAGS_OFFSET, (short) 0);
    }

    /**
     * Read a vertex's position.
     *
     * @param in The vertices, little endian.
     * @param index Which vertex.
     * @param grid Receives the position, in grid steps.
     */
    public static void position(@NonNull ByteBuffer in, int index, int @NonNull [] grid) {
        final int base = index * SIZE;
        for (int axis = 0; axis < AXES; ++axis) {
            grid[axis] = in.getInt(base + POSITION_OFFSET + axis * Integer.BYTES);
        }
    }

    /**
     * Read a vertex's normal.
     *
     * @param in The vertices, little endian.
     * @param index Which vertex.
     * @param normal Receives the normal, unit length.
     */
    public static void normal(@NonNull ByteBuffer in, int index, float @NonNull [] normal) {
        final int base = index * SIZE;
        BakedVertex.fromOctahedral(
                BakedVertex.unsnorm16(in.getShort(base + NORMAL_OFFSET)),
                BakedVertex.unsnorm16(in.getShort(base + NORMAL_OFFSET + Short.BYTES)),
                normal);
    }

    /**
     * Read a vertex's material.
     *
     * @param in The vertices, little endian.
     * @param index Which vertex.
     * @return The material index.
     */
    public static int material(@NonNull ByteBuffer in, int index) {
        return Short.toUnsignedInt(in.getShort(index * SIZE + MATERIAL_OFFSET));
    }

    /** Static helpers only. */
    private LodVertex() {}
}
