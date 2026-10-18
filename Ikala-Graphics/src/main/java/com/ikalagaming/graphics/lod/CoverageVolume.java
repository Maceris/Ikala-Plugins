package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * A sparse volume standing for geometry finer than a level of detail's resolution, like foliage,
 * twigs and wires, see {@link CoverageVoxelizer}: a microflake medium after Heitz et al., <i>The
 * SGGX Microflake Distribution</i> (2015), each voxel a density and an SGGX matrix, which together
 * give its extinction in any direction, and the look of the surfaces in it.
 *
 * <p>Voxels are cubes {@link #voxelSize()} wide on a grid from {@link #origin()}, kept in order of
 * their packed grid coordinates, see {@link #key}.
 */
public final class CoverageVolume {

    /** Coordinates in a position. */
    public static final int AXES = 3;

    /** Values in an SGGX matrix: xx, yy, zz, xy, xz, yz. */
    public static final int SGGX_VALUES = 6;

    /** Values of a voxel's color: linear red, green, blue. */
    public static final int COLOR_VALUES = 3;

    /** Bits of each grid coordinate in a key. */
    public static final int KEY_BITS = 21;

    /** The low {@link #KEY_BITS} bits. */
    private static final long KEY_MASK = (1L << KEY_BITS) - 1;

    /** Where the grid starts, in the DAG's frame. */
    private final double[] origin;

    /** How wide a voxel is. */
    private final double voxelSize;

    /** Each voxel's packed grid coordinates, in increasing order. */
    private final long[] keys;

    /** Each voxel's density: microflake area per unit volume, by projected area 1. */
    private final float[] density;

    /**
     * Each voxel's SGGX matrix, {@link #SGGX_VALUES} values, scaled so it projects an area of 1
     * along a surface's normal: its projected area in a direction w is sqrt(w^T S w).
     */
    private final float[] sggx;

    /** Each voxel's linear color. */
    private final float[] color;

    /** Each voxel's roughness. */
    private final float[] roughness;

    /** Each voxel's metalness. */
    private final float[] metallic;

    /**
     * Keep a volume.
     *
     * @param origin Where the grid starts.
     * @param voxelSize How wide a voxel is.
     * @param keys Each voxel's packed coordinates, in increasing order.
     * @param density Each voxel's density.
     * @param sggx Each voxel's SGGX matrix.
     * @param color Each voxel's linear color.
     * @param roughness Each voxel's roughness.
     * @param metallic Each voxel's metalness.
     */
    CoverageVolume(
            double @NonNull [] origin,
            double voxelSize,
            long @NonNull [] keys,
            float @NonNull [] density,
            float @NonNull [] sggx,
            float @NonNull [] color,
            float @NonNull [] roughness,
            float @NonNull [] metallic) {
        this.origin = origin.clone();
        this.voxelSize = voxelSize;
        this.keys = keys;
        this.density = density;
        this.sggx = sggx;
        this.color = color;
        this.roughness = roughness;
        this.metallic = metallic;
    }

    /**
     * Pack a voxel's grid coordinates.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The key.
     */
    public static long key(long x, long y, long z) {
        return (x & KEY_MASK) << (2 * KEY_BITS) | (y & KEY_MASK) << KEY_BITS | (z & KEY_MASK);
    }

    /**
     * A key's grid coordinate along an axis.
     *
     * @param key The key.
     * @param axis The axis, 0 to 2.
     * @return The coordinate.
     */
    public static int coordinate(long key, int axis) {
        return (int) ((key >>> ((AXES - 1 - axis) * KEY_BITS)) & KEY_MASK);
    }

    /**
     * Where the grid starts.
     *
     * @return The corner, a copy.
     */
    public double[] origin() {
        return origin.clone();
    }

    /**
     * How wide a voxel is.
     *
     * @return The width.
     */
    public double voxelSize() {
        return voxelSize;
    }

    /**
     * How many voxels there are.
     *
     * @return The count.
     */
    public int size() {
        return keys.length;
    }

    /**
     * A voxel's packed grid coordinates.
     *
     * @param voxel The voxel.
     * @return The key.
     */
    public long keyOf(int voxel) {
        return keys[voxel];
    }

    /**
     * A voxel's density.
     *
     * @param voxel The voxel.
     * @return Microflake area per unit volume.
     */
    public float density(int voxel) {
        return density[voxel];
    }

    /**
     * One of a voxel's SGGX values.
     *
     * @param voxel The voxel.
     * @param value Which: xx, yy, zz, xy, xz, yz.
     * @return The value.
     */
    public float sggx(int voxel, int value) {
        return sggx[voxel * SGGX_VALUES + value];
    }

    /**
     * One of a voxel's color channels.
     *
     * @param voxel The voxel.
     * @param channel Red, green or blue.
     * @return The linear value.
     */
    public float color(int voxel, int channel) {
        return color[voxel * COLOR_VALUES + channel];
    }

    /**
     * A voxel's roughness.
     *
     * @param voxel The voxel.
     * @return The roughness.
     */
    public float roughness(int voxel) {
        return roughness[voxel];
    }

    /**
     * A voxel's metalness.
     *
     * @param voxel The voxel.
     * @return The metalness.
     */
    public float metallic(int voxel) {
        return metallic[voxel];
    }

    /**
     * The voxel a point is in.
     *
     * @param point The point, in the volume's frame.
     * @return The voxel, or -1 if no voxel there stops light.
     */
    public int indexAt(double @NonNull [] point) {
        final long x = (long) Math.floor((point[0] - origin[0]) / voxelSize);
        final long y = (long) Math.floor((point[1] - origin[1]) / voxelSize);
        final long z = (long) Math.floor((point[2] - origin[2]) / voxelSize);
        if (x < 0 || y < 0 || z < 0 || x > KEY_MASK || y > KEY_MASK || z > KEY_MASK) {
            return -1;
        }
        final int at = Arrays.binarySearch(keys, key(x, y, z));
        return at < 0 ? -1 : at;
    }

    /**
     * The area a voxel's microflakes project along a direction, per unit density: sqrt(w^T S w).
     *
     * @param voxel The voxel.
     * @param direction The direction, unit length.
     * @return The projected area.
     */
    public double projectedArea(int voxel, double @NonNull [] direction) {
        return projectedArea(sggx, voxel * SGGX_VALUES, direction);
    }

    /**
     * The area an SGGX matrix projects along a direction.
     *
     * @param matrix SGGX values: xx, yy, zz, xy, xz, yz.
     * @param at Where they start.
     * @param w The direction, unit length.
     * @return sqrt(w^T S w).
     */
    static double projectedArea(float @NonNull [] matrix, int at, double @NonNull [] w) {
        final double q =
                matrix[at] * w[0] * w[0]
                        + matrix[at + 1] * w[1] * w[1]
                        + matrix[at + 2] * w[2] * w[2]
                        + 2
                                * (matrix[at + 3] * w[0] * w[1]
                                        + matrix[at + 4] * w[0] * w[2]
                                        + matrix[at + 5] * w[1] * w[2]);
        return Math.sqrt(Math.max(0, q));
    }
}
