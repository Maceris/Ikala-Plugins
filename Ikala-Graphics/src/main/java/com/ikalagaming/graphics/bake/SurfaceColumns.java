package com.ikalagaming.graphics.bake;

import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * A section's soft surface layer as the baker reads it, materials resolved to indices: one entry
 * per column of cells, the section's {@value #SECTION} by {@value #SECTION} and a ring of its
 * neighbors' around them, {@value #SPAN} by {@value #SPAN}, row by row along x, z after z. The
 * ring's tops only decide where side faces go, see {@link SectionBaker}. See {@code
 * com.ikalagaming.graphics.SurfaceLayer} for what the game hands in.
 *
 * @param stepsPerMeter How many height steps make a meter.
 * @param tops Each column's surface top, in steps above the section's origin, or {@link #NONE}.
 * @param depths How deep each column's surface is, in steps: its bottom is its top less this.
 * @param materials Each column's material index.
 * @param refillRates How fast each column fills back in, meters a second, or null, or an entry that
 *     isn't a number, for its material's rate; only the section's columns are read.
 * @param flags Each column's flags, see {@code SurfaceLayer.PRESSABLE}, or null for every column
 *     pressable.
 */
public record SurfaceColumns(
        float stepsPerMeter,
        short @NonNull [] tops,
        short @NonNull [] depths,
        int @NonNull [] materials,
        @Nullable float[] refillRates,
        @Nullable byte[] flags) {

    /**
     * A layer whose columns all take their materials' refill rates and may all be pressed.
     *
     * @param stepsPerMeter How many height steps make a meter.
     * @param tops Each column's surface top, in steps above the section's origin, or {@link #NONE}.
     * @param depths How deep each column's surface is, in steps.
     * @param materials Each column's material index.
     */
    public SurfaceColumns(
            float stepsPerMeter,
            short @NonNull [] tops,
            short @NonNull [] depths,
            int @NonNull [] materials) {
        this(stepsPerMeter, tops, depths, materials, null, null);
    }

    /** Columns along a section's side. */
    public static final int SECTION = 16;

    /** Columns along a side with the ring of neighbors. */
    public static final int SPAN = SECTION + 2;

    /** How many entries a layer has. */
    public static final int COLUMNS = SPAN * SPAN;

    /** A column with no surface. */
    public static final short NONE = Short.MIN_VALUE;

    /**
     * Check the arrays are all {@link #COLUMNS} long and the steps positive.
     *
     * @throws IllegalArgumentException If they aren't.
     */
    public SurfaceColumns {
        if (tops.length != COLUMNS || depths.length != COLUMNS || materials.length != COLUMNS) {
            throw new IllegalArgumentException(
                    "A surface layer has "
                            + COLUMNS
                            + " columns: the section and a ring around it");
        }
        if (stepsPerMeter <= 0) {
            throw new IllegalArgumentException("Height steps must be positive: " + stepsPerMeter);
        }
    }

    /**
     * Where a column's entry is.
     *
     * @param x The column along x, -1 to {@value #SECTION} with the ring.
     * @param z The column along z, -1 to {@value #SECTION} with the ring.
     * @return Its index.
     */
    public static int index(int x, int z) {
        return (z + 1) * SPAN + x + 1;
    }
}
