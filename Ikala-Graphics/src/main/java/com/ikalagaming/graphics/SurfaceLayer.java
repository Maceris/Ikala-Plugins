package com.ikalagaming.graphics;

import com.ikalagaming.graphics.bake.SurfaceColumns;
import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import javax.annotation.Nullable;

/**
 * A section's soft surface layer: a height field over its columns that stamps can press and that
 * fills back in, like snow, sand or ash. Graphics knows nothing of what it is: the game decides
 * which columns hold one, how deep, and what it looks like. Undisturbed, a surface is drawn as a
 * flat top at its height, with short side faces where it stands above a neighbor without one or
 * with a lower one, baked into the section like its blocks, so far levels of detail show it too.
 *
 * <p>Every array has one entry per column, {@link #SPAN} by {@link #SPAN}: the section's 16 by 16
 * columns and a ring of the neighboring sections' columns around them, row by row along x, z after
 * z, see {@link #index}. The ring's entries only say how high the neighbors' surfaces stand, for
 * the side faces along the section's border; the game rebakes a neighbor when a border column
 * changes, as it does for block edits. A column's surface belongs to the section of the block that
 * supports it, so a surface on a section's top layer of blocks needs no section above it: its top
 * may stand past the section's top.
 *
 * <pre>{@code
 * SurfaceLayer snow = new SurfaceLayer(16, new Material[] {snowMaterial}, tops, depths, ids,
 *         null, null);
 * context.sections().compose(origin).placements(...).surfaces(snow).submit();
 * }</pre>
 *
 * @param stepsPerMeter How many height steps make a meter: 16 for steps of 1/16 m.
 * @param materials The materials the columns choose from.
 * @param tops Each column's surface top, in steps above the section's origin, or {@link #NONE}
 *     where the column has none.
 * @param depths How deep each column's surface is, in steps: its bottom is its top less this, where
 *     what supports it begins.
 * @param materialIds Each column's material, an index into {@code materials}.
 * @param refillRates How fast each column fills back in once pressed, in meters a second, or null
 *     to use each material's rate; an entry that isn't a number uses its material's too.
 * @param flags Each column's flags, like {@link #PRESSABLE}, or null for every column pressable.
 */
public record SurfaceLayer(
        float stepsPerMeter,
        @NonNull Material @NonNull [] materials,
        short @NonNull [] tops,
        short @NonNull [] depths,
        byte @NonNull [] materialIds,
        @Nullable float[] refillRates,
        @Nullable byte[] flags) {

    /** Columns along a side, with the ring of neighbors. */
    public static final int SPAN = SurfaceColumns.SPAN;

    /** How many entries each array has. */
    public static final int COLUMNS = SurfaceColumns.COLUMNS;

    /** A column with no surface. */
    public static final short NONE = SurfaceColumns.NONE;

    /** A column's flag: stamps may press it. */
    public static final byte PRESSABLE = 1;

    /**
     * Check every array has {@link #COLUMNS} entries, the steps are positive, depths aren't
     * negative and every material id names a material.
     *
     * @throws IllegalArgumentException If one isn't so.
     */
    public SurfaceLayer {
        if (tops.length != COLUMNS
                || depths.length != COLUMNS
                || materialIds.length != COLUMNS
                || (refillRates != null && refillRates.length != COLUMNS)
                || (flags != null && flags.length != COLUMNS)) {
            throw new IllegalArgumentException(
                    "A surface layer has "
                            + COLUMNS
                            + " entries in each array: the section's columns and a ring around"
                            + " them");
        }
        if (stepsPerMeter <= 0) {
            throw new IllegalArgumentException("Height steps must be positive: " + stepsPerMeter);
        }
        for (int i = 0; i < COLUMNS; ++i) {
            if (tops[i] == NONE) {
                continue;
            }
            if (depths[i] < 0) {
                throw new IllegalArgumentException("Column " + i + " has a negative depth");
            }
            if (materialIds[i] < 0 || materialIds[i] >= materials.length) {
                throw new IllegalArgumentException(
                        "Column " + i + "'s material " + materialIds[i] + " isn't in the layer");
            }
        }
    }

    /**
     * Where a column's entry is in the arrays.
     *
     * @param x The column along x, -1 to 16 with the ring.
     * @param z The column along z, -1 to 16 with the ring.
     * @return Its index.
     */
    public static int index(int x, int z) {
        return SurfaceColumns.index(x, z);
    }

    /**
     * An empty layer to fill in: every column without a surface.
     *
     * @param stepsPerMeter How many height steps make a meter.
     * @param materials The materials the columns choose from.
     * @return The layer.
     */
    public static SurfaceLayer empty(
            float stepsPerMeter, @NonNull Material @NonNull ... materials) {
        final short[] tops = new short[COLUMNS];
        java.util.Arrays.fill(tops, NONE);
        return new SurfaceLayer(
                stepsPerMeter, materials, tops, new short[COLUMNS], new byte[COLUMNS], null, null);
    }
}
