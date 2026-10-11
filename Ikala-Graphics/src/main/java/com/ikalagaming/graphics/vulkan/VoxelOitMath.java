package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

/**
 * Voxel-based order independent transparency, the basic form of Drobot's Adaptive Voxel-Based Order
 * Independent Transparency (SIGGRAPH 2025), as {@code oit_splat.frag}, {@code oit_integrate.comp}
 * and {@code translucent.frag} do it; keep them in step with this.
 *
 * <p>Translucent surfaces first add their extinction, {@code -log(1 - opacity)}, into a low
 * resolution volume over the view: {@link #TILE_SIZE} pixel square tiles across the screen, each
 * split into {@link #SLICES} slices by depth, spaced exponentially. Each surface splits its
 * extinction between the two slices nearest its depth. Then each tile's slices are summed front to
 * back into how much light gets through up to the far end of each slice. When the surfaces are
 * drawn, each is weighted by how much light gets through to just in front of it, read {@link
 * #SLICE_BIAS} slices nearer than its depth so it doesn't dim itself.
 *
 * <p>With those weights, layers a few slices apart composite exactly like sorted blending; layers
 * closer than that see the same weight, and mix as a weighted average.
 *
 * <p>Depths are distances in front of the camera, so the negated view space z. Slice coordinates
 * are continuous: slice {@code i} covers {@code [i, i + 1)}, and its texel's center is {@code i +
 * 0.5}.
 */
public final class VoxelOitMath {

    /** How many pixels across and down each tile of the volume covers. */
    public static final int TILE_SIZE = 8;

    /** How many slices by depth. */
    public static final int SLICES = 128;

    /**
     * Where the slices start, in meters. Everything nearer is in the first slice; starting at the
     * near plane would spend about two fifths of them on the first half meter, at a 200 meter view
     * distance.
     */
    public static final float FIRST_DEPTH = 0.5f;

    /** How many units of the volume's integers make one unit of extinction. */
    public static final float FIXED_POINT_SCALE = 1 << 16;

    /**
     * How many slices nearer than a surface its weight is read. A surface's extinction spreads over
     * the slices either side of its texel centers, and the read blends two texels, so the nearest
     * slice it can read without seeing itself is two away (the paper's value for linear splats).
     */
    public static final float SLICE_BIAS = 2;

    /**
     * The scale from the log of a depth to a slice coordinate, for a view distance: the slices
     * evenly divide the log of the depth range from {@link #FIRST_DEPTH} to the view distance.
     *
     * @param viewDistance How far the view reaches, in meters.
     * @return The scale, {@code SLICES / ln(viewDistance / FIRST_DEPTH)}.
     */
    public static float logScale(double viewDistance) {
        // Leave the slices at least a doubling of depth to divide up
        final double far = Math.max(viewDistance, 2 * FIRST_DEPTH);
        return (float) (SLICES / Math.log(far / FIRST_DEPTH));
    }

    /**
     * How many tiles cover some pixels.
     *
     * @param pixels The width or height in pixels.
     * @return The tiles across or down.
     */
    public static int tiles(int pixels) {
        return (pixels + TILE_SIZE - 1) / TILE_SIZE;
    }

    /**
     * Where a depth falls among the slices.
     *
     * @param depth The distance in front of the camera, in meters.
     * @param logScale From {@link #logScale(double)}.
     * @return The slice coordinate, from 0 to {@link #SLICES}.
     */
    public static float sliceCoordinate(float depth, float logScale) {
        final double coordinate = Math.log(Math.max(depth, FIRST_DEPTH) / FIRST_DEPTH) * logScale;
        return (float) Math.min(coordinate, SLICES);
    }

    /**
     * Add a surface's extinction to one tile's column of slices, split between the two slice
     * centers either side of its depth.
     *
     * @param column The tile's slices, in fixed point, changed in place. Treated as unsigned.
     * @param depth The surface's distance in front of the camera, in meters.
     * @param logScale From {@link #logScale(double)}.
     * @param extinction The surface's extinction, {@code -log(1 - opacity)}.
     */
    public static void splat(
            int @NonNull [] column, float depth, float logScale, float extinction) {
        final float center = sliceCoordinate(depth, logScale) - 0.5f;
        final int lower = (int) Math.floor(center);
        final float fraction = center - lower;
        final int total = (int) (extinction * FIXED_POINT_SCALE + 0.5f);
        // Rounded once, so the two parts always add up to the whole
        final int upperPart = (int) (total * fraction + 0.5f);
        column[Math.clamp(lower, 0, SLICES - 1)] += total - upperPart;
        column[Math.clamp(lower + 1, 0, SLICES - 1)] += upperPart;
    }

    /**
     * Sum one tile's column front to back into how much light gets through up to the far end of
     * each slice, and clear it for the next frame.
     *
     * @param column The tile's slices, in fixed point, set to zero.
     * @return How much light gets through each slice, from 1 for all of it.
     */
    public static float[] integrate(int @NonNull [] column) {
        float[] transmittance = new float[SLICES];
        float sum = 0;
        for (int slice = 0; slice < SLICES; ++slice) {
            sum += Integer.toUnsignedLong(column[slice]) / FIXED_POINT_SCALE;
            column[slice] = 0;
            transmittance[slice] = (float) Math.exp(-sum);
        }
        return transmittance;
    }

    /**
     * How much light gets through to just in front of a surface, read {@link #SLICE_BIAS} slices
     * nearer and blended between the two nearest texel centers. Nearer than the first texel center
     * it blends towards nothing in the way.
     *
     * @param transmittance The tile's integrated column.
     * @param depth The surface's distance in front of the camera, in meters.
     * @param logScale From {@link #logScale(double)}.
     * @return How much light gets through, from 0 to 1.
     */
    public static float inFront(float @NonNull [] transmittance, float depth, float logScale) {
        final float read = sliceCoordinate(depth, logScale) - SLICE_BIAS - 0.5f;
        final int lower = (int) Math.floor(read);
        final float fraction = read - lower;
        return texel(transmittance, lower) * (1 - fraction)
                + texel(transmittance, lower + 1) * fraction;
    }

    /**
     * One texel of a column, with everything before the first being clear.
     *
     * @param transmittance The tile's integrated column.
     * @param slice The slice.
     * @return Its transmittance, or 1 before the first slice.
     */
    private static float texel(float @NonNull [] transmittance, int slice) {
        return slice < 0 ? 1 : transmittance[Math.min(slice, SLICES - 1)];
    }

    private VoxelOitMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
