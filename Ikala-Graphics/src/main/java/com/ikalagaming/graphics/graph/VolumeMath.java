package com.ikalagaming.graphics.graph;

/**
 * The math of volume bricks, which stand in for a section's cut out and translucent surfaces at a
 * distance: each voxel holds how much light its surfaces block per meter and their average color.
 * {@link VolumeVoxelizer} builds bricks with it, and {@code volume.glsl} reads them on the GPU;
 * keep them in step.
 *
 * <p>Surfaces become extinction by how often a ray crosses them. For rays in every direction
 * through a voxel, a surface of area A inside a voxel of volume V is crossed A / (2V) times per
 * meter on average (Cauchy and Crofton: a random chord crosses it 2A / S times, where S is the
 * voxel's surface, and the mean chord is 4V / S). Each crossing lets 1 - opacity through, so the
 * extinction is A / (2V) times -ln(1 - opacity), and a ray through the volume is blocked, on
 * average, as much as through the surfaces. Pure Java, so it is tested without a GPU.
 */
public final class VolumeMath {

    /** Voxels along each side of a brick, which covers one section. */
    public static final int BRICK = 16;

    /** How wide a voxel is, in meters. */
    public static final double VOXEL_SIZE = 1;

    /**
     * The most extinction a see-through voxel can hold, per meter: stored as the square root of its
     * share of this in the lower half of eight bits, so low extinction, which most voxels have,
     * keeps the most precision. The upper half marks solid voxels, inside cut out blocks, with how
     * much of their faces the texture covers.
     */
    public static final double MAX_EXTINCTION = 16;

    /** The largest stored value of a see-through voxel; solid voxels are stored above it. */
    public static final int MAX_SEE_THROUGH = 127;

    /** The smallest stored value of a solid voxel, for no coverage at all. */
    public static final int FIRST_SOLID = MAX_SEE_THROUGH + 1;

    /** The largest stored value. */
    public static final int MAX_STORED = 255;

    /** Opacity is kept below 1, so the extinction stays finite. */
    public static final double MAX_OPACITY = 0.98;

    /**
     * How many units of the voxelizer's integer accumulators make one unit: of area times optical
     * depth, and of weighted color.
     */
    public static final double FIXED_POINT_SCALE = 4096;

    /** Brick slots along the atlas's x. */
    public static final int SLOTS_X = 32;

    /** Brick slots along the atlas's y. */
    public static final int SLOTS_Y = 16;

    /** Brick slots along the atlas's z. */
    public static final int SLOTS_Z = 24;

    /** How many bricks the atlas holds. */
    public static final int SLOTS = SLOTS_X * SLOTS_Y * SLOTS_Z;

    /**
     * How long the hand over from sections' own blocks to their bricks is, in meters, centered on
     * the volume distance, see {@code volume_fade.glsl}: half of it before, where the bricks start
     * drawing a share of the places, half after, where the blocks stop.
     */
    public static final double FADE_LENGTH = 32;

    /** Bytes per voxel in the atlas: color and extinction, a byte each. */
    public static final int BYTES_PER_VOXEL = 4;

    /** The atlas's size in bytes. */
    public static final long ATLAS_BYTES = (long) SLOTS * BRICK * BRICK * BRICK * BYTES_PER_VOXEL;

    /**
     * The optical depth of crossing a surface once: how much it blocks, as -ln of what it lets
     * through.
     *
     * @param opacity The surface's opacity, 0 to 1.
     * @return The optical depth.
     */
    public static double opticalDepth(double opacity) {
        return -Math.log(1 - Math.clamp(opacity, 0, MAX_OPACITY));
    }

    /**
     * The extinction some surface inside a voxel adds to it.
     *
     * @param area The surface's area inside the voxel, in square meters.
     * @param opacity Its opacity.
     * @param voxelVolume The voxel's volume, in cubic meters.
     * @return The extinction, per meter.
     */
    public static double extinction(double area, double opacity, double voxelVolume) {
        return area / (2 * voxelVolume) * opticalDepth(opacity);
    }

    /**
     * Store a see-through voxel's extinction in eight bits, as {@code volume.glsl} reads it.
     *
     * @param extinction The extinction, per meter.
     * @return The stored value, 0 to {@link #MAX_SEE_THROUGH}.
     */
    public static int encode(double extinction) {
        final double share = Math.clamp(extinction / MAX_EXTINCTION, 0, 1);
        return (int) Math.round(Math.sqrt(share) * MAX_SEE_THROUGH);
    }

    /**
     * Store a solid voxel in eight bits, as {@code volume.glsl} reads it.
     *
     * @param coverage How much of its faces the texture covers, 0 to 1.
     * @return The stored value, {@link #FIRST_SOLID} to {@link #MAX_STORED}.
     */
    public static int encodeSolid(double coverage) {
        return FIRST_SOLID
                + (int) Math.round(Math.clamp(coverage, 0, 1) * (MAX_STORED - FIRST_SOLID));
    }

    /**
     * Whether a stored value is a solid voxel.
     *
     * @param stored The stored value, 0 to 255.
     * @return True for a solid voxel.
     */
    public static boolean isSolid(int stored) {
        return stored >= FIRST_SOLID;
    }

    /**
     * Read back a see-through voxel's extinction, as {@code volume.glsl} does.
     *
     * @param stored The stored value, 0 to {@link #MAX_SEE_THROUGH}.
     * @return The extinction, per meter.
     */
    public static double decode(int stored) {
        final double root = (double) stored / MAX_SEE_THROUGH;
        return root * root * MAX_EXTINCTION;
    }

    /**
     * Read back a solid voxel's coverage, as {@code volume.glsl} does.
     *
     * @param stored The stored value, {@link #FIRST_SOLID} to {@link #MAX_STORED}.
     * @return How much of its faces the texture covers, 0 to 1.
     */
    public static double coverage(int stored) {
        return (double) (stored - FIRST_SOLID) / (MAX_STORED - FIRST_SOLID);
    }

    /**
     * Where a brick slot starts in the atlas, in texels.
     *
     * @param slot The slot.
     * @return x, y, z.
     */
    public static int[] slotOrigin(int slot) {
        return new int[] {
            slot % SLOTS_X * BRICK,
            slot / SLOTS_X % SLOTS_Y * BRICK,
            slot / (SLOTS_X * SLOTS_Y) * BRICK
        };
    }

    /** Static helpers only. */
    private VolumeMath() {}

    /**
     * Where the hand over from sections' own blocks to their bricks starts, for the shaders.
     *
     * @param volumeDistance The volume distance, 0 or less for no volumes.
     * @return The distance in meters, or the largest float when there are no volumes.
     */
    public static float fadeStart(double volumeDistance) {
        return volumeDistance <= 0 ? Float.MAX_VALUE : (float) (volumeDistance - FADE_LENGTH / 2);
    }

    /**
     * Where the hand over from sections' own blocks to their bricks ends, past which the blocks
     * draw nothing.
     *
     * @param volumeDistance The volume distance, more than 0.
     * @return The distance in meters.
     */
    public static double fadeEnd(double volumeDistance) {
        return volumeDistance + FADE_LENGTH / 2;
    }
}
