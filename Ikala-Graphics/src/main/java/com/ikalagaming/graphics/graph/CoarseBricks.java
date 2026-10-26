package com.ikalagaming.graphics.graph;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Coarse volume bricks, which stand in for far sections' bricks: a brick of a level covers 2^level
 * sections along each side with the same {@link VolumeMath#BRICK} cubed voxels, each 2^level meters
 * wide, made by downsampling the sections' own bricks, so the atlas's slots cover far more ground
 * where a voxel is too small on screen to tell apart. Pure Java, so it runs on any thread and is
 * tested without a GPU.
 *
 * <p>A coarse voxel stands for the fine voxels inside it. It is solid, as a block's face is drawn,
 * when at least half of them are, with their average coverage and their most common material;
 * otherwise it holds their average extinction, a solid one counting as {@link
 * VolumeMath#MAX_EXTINCTION}, which keeps how much light a ray loses crossing the cube on average.
 * Its color is the fine voxels' colors weighted by how much light each stops.
 */
public final class CoarseBricks {

    /** The coarsest level, whose voxels are 2^this meters wide. */
    public static final int MAX_LEVEL = 2;

    /** Voxels in a brick. */
    private static final int VOXELS = VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;

    /** Values per texel. */
    private static final int TEXEL_VALUES = 4;

    /** Values of color in a texel. */
    private static final int COLOR_VALUES = 3;

    /** The largest value of a byte channel. */
    private static final double CHANNEL = 255;

    /** Bits in an int, for the cell masks. */
    private static final int BITS = Integer.SIZE;

    /** Ints of cell masks: which cells hold something solid, then which hold anything. */
    private static final int MASK_INTS = 4;

    /** Where the masks of cells holding anything start. */
    private static final int ANY_MASKS = 2;

    /** The mask of one voxel's surface bits. */
    private static final int SURFACE_MASK = (1 << VolumeVoxelizer.SURFACE_BITS) - 1;

    /** Static helpers only. */
    private CoarseBricks() {}

    /**
     * How many sections a brick of a level covers along each side.
     *
     * @param level The level.
     * @return 2^level.
     */
    public static int span(int level) {
        return 1 << level;
    }

    /**
     * The least distance at which bricks of each level draw, in meters: where a voxel of that level
     * projects to no more than the threshold, and never inside a minimum, so the finest bricks
     * cover everything near. Level 0 draws from 0.
     *
     * @param pixelsPerMeter Pixels a meter covers at a distance of one meter.
     * @param thresholdPixels The most pixels a voxel may cover.
     * @param minimum The least distance any coarse level draws at, in meters.
     * @return Each level's distance, from level 0 to {@link #MAX_LEVEL}, never decreasing.
     */
    public static double[] distances(
            double pixelsPerMeter, double thresholdPixels, double minimum) {
        final double[] out = new double[MAX_LEVEL + 1];
        for (int level = 1; level <= MAX_LEVEL; ++level) {
            final double projected =
                    span(level) * VolumeMath.VOXEL_SIZE * pixelsPerMeter / thresholdPixels;
            out[level] = Math.max(Math.max(minimum, projected), out[level - 1]);
        }
        return out;
    }

    /**
     * Whether a cell draws at a level or coarser, with a margin so it doesn't flip back and forth
     * at the edge: once it does, it keeps doing so until the margin nearer.
     *
     * @param distance How far the cell's box is from the camera, in meters.
     * @param from Where the level starts drawing, in meters.
     * @param was Whether it drew at the level or coarser before.
     * @param margin The margin, in meters.
     * @return Whether it does now.
     */
    public static boolean reaches(double distance, double from, boolean was, double margin) {
        return was ? distance >= from - margin : distance >= from + margin;
    }

    /**
     * Downsample the bricks of the sections a coarse brick covers.
     *
     * @param level The coarse brick's level, 1 to {@link #MAX_LEVEL}.
     * @param children Each covered section's brick, or null for none, indexed x fastest, then y,
     *     then z, by its place in sections from the coarse brick's corner, {@link #span}(level)
     *     cubed of them.
     * @return The coarse brick, or null if none of the sections has anything.
     */
    public static VolumeVoxelizer.Brick downsample(
            int level, VolumeVoxelizer.Brick @NonNull [] children) {
        final int span = span(level);
        if (level < 1 || level > MAX_LEVEL || children.length != span * span * span) {
            throw new IllegalArgumentException("Bad level or child count: " + level);
        }
        boolean any = false;
        boolean missing = false;
        for (VolumeVoxelizer.Brick child : children) {
            any |= child != null;
            missing |= child != null && child.missingMaterial();
        }
        if (!any) {
            return null;
        }
        final byte[] texels = new byte[VOXELS * TEXEL_VALUES];
        final int[] masks = new int[MASK_INTS];
        final short[] materials = new short[VOXELS];
        final int[] surfaces = new int[VolumeVoxelizer.SURFACE_INTS];
        // Coarse voxels each child's brick fills along a side
        final int perChild = VolumeMath.BRICK / span;
        final int fine = span * span * span;
        final short[] solidMaterials = new short[fine];
        final double[] color = new double[COLOR_VALUES];
        boolean anything = false;
        for (int z = 0; z < VolumeMath.BRICK; ++z) {
            for (int y = 0; y < VolumeMath.BRICK; ++y) {
                for (int x = 0; x < VolumeMath.BRICK; ++x) {
                    final VolumeVoxelizer.Brick child =
                            children[x / perChild + span * (y / perChild + span * (z / perChild))];
                    if (child == null) {
                        continue;
                    }
                    final int coarse = index(x, y, z);
                    // The fine voxels inside, from this corner in the child's brick
                    final int fx = x % perChild * span;
                    final int fy = y % perChild * span;
                    final int fz = z % perChild * span;
                    int solid = 0;
                    double coverage = 0;
                    double extinction = 0;
                    double weight = 0;
                    Arrays.fill(color, 0);
                    int surface = 0;
                    for (int dz = 0; dz < span; ++dz) {
                        for (int dy = 0; dy < span; ++dy) {
                            for (int dx = 0; dx < span; ++dx) {
                                final int voxel = index(fx + dx, fy + dy, fz + dz);
                                final int texel = voxel * TEXEL_VALUES;
                                final int stored =
                                        Byte.toUnsignedInt(child.texels()[texel + COLOR_VALUES]);
                                final double stops;
                                if (VolumeMath.isSolid(stored)) {
                                    solidMaterials[solid] = child.materials()[voxel];
                                    ++solid;
                                    coverage += VolumeMath.coverage(stored);
                                    stops = VolumeMath.MAX_EXTINCTION;
                                } else {
                                    stops = VolumeMath.decode(stored);
                                }
                                extinction += stops;
                                if (stops > 0) {
                                    for (int a = 0; a < COLOR_VALUES; ++a) {
                                        color[a] +=
                                                stops
                                                        * Byte.toUnsignedInt(
                                                                child.texels()[texel + a]);
                                    }
                                    weight += stops;
                                }
                                surface |=
                                        child.surfaces()[voxel / VolumeVoxelizer.SURFACES_PER_INT]
                                                        >>> (voxel
                                                                % VolumeVoxelizer.SURFACES_PER_INT
                                                                * VolumeVoxelizer.SURFACE_BITS)
                                                & SURFACE_MASK;
                            }
                        }
                    }
                    surfaces[coarse / VolumeVoxelizer.SURFACES_PER_INT] |=
                            surface
                                    << (coarse
                                            % VolumeVoxelizer.SURFACES_PER_INT
                                            * VolumeVoxelizer.SURFACE_BITS);
                    anything |= surface != 0;
                    final boolean isSolid = 2 * solid >= fine;
                    final double average = extinction / fine;
                    final int texel = coarse * TEXEL_VALUES;
                    for (int a = 0; a < COLOR_VALUES; ++a) {
                        texels[texel + a] =
                                (byte)
                                        Math.round(
                                                weight > 0
                                                        ? Math.clamp(color[a] / weight, 0, CHANNEL)
                                                        : 0);
                    }
                    texels[texel + COLOR_VALUES] =
                            (byte)
                                    (isSolid
                                            ? VolumeMath.encodeSolid(coverage / solid)
                                            : VolumeMath.encode(average));
                    if (isSolid) {
                        materials[coarse] = mostCommon(solidMaterials, solid);
                    }
                    if (isSolid || average > 0) {
                        anything = true;
                        final int cell =
                                x / VolumeVoxelizer.CELL
                                        + VolumeVoxelizer.CELLS
                                                * (y / VolumeVoxelizer.CELL
                                                        + VolumeVoxelizer.CELLS
                                                                * (z / VolumeVoxelizer.CELL));
                        final int bit = 1 << (cell % BITS);
                        if (isSolid) {
                            masks[cell / BITS] |= bit;
                        }
                        masks[ANY_MASKS + cell / BITS] |= bit;
                    }
                }
            }
        }
        if (!anything && !missing) {
            return null;
        }
        return new VolumeVoxelizer.Brick(texels, masks, materials, surfaces, missing);
    }

    /**
     * The most common nonzero value among the first few of some, the smallest on a tie.
     *
     * @param values The values, changed: sorted.
     * @param count How many to look at.
     * @return The value, or 0 if there are only zeros.
     */
    private static short mostCommon(short @NonNull [] values, int count) {
        Arrays.sort(values, 0, count);
        short best = 0;
        int bestRun = 0;
        int run = 0;
        for (int i = 0; i < count; ++i) {
            run = i > 0 && values[i] == values[i - 1] ? run + 1 : 1;
            if (values[i] != 0 && run > bestRun) {
                best = values[i];
                bestRun = run;
            }
        }
        return best;
    }

    /**
     * A voxel's index in a brick, x fastest, then y, then z.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The index.
     */
    private static int index(int x, int y, int z) {
        return x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z);
    }
}
