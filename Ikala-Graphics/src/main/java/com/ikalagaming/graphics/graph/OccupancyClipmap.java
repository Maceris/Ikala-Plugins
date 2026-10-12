package com.ikalagaming.graphics.graph;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the occupancy volume's levels are in the world. Each level is a cube of {@link #SIZE}
 * voxels around the camera, finer near and coarser far, addressed toroidally: a voxel's texel is
 * its world voxel coordinate modulo the size, so when a level moves only the voxels it newly covers
 * have to be written, and the rest stay where they are.
 *
 * <p>A level's origin, its minimum corner in its own voxels, is snapped to a grid {@link #SNAP}
 * voxels wide, so it moves in steps rather than every frame. Pure Java, so it is tested without a
 * GPU. Render thread only.
 */
public class OccupancyClipmap {

    /** How many levels there are. */
    public static final int LEVELS = 3;

    /** The width of each level, in its voxels. */
    public static final int SIZE = 128;

    /** How wide each level's voxels are, in meters, finest first. */
    public static final int[] VOXEL_SIZES = {1, 2, 4};

    /** How many voxels a level moves at a time. */
    public static final int SNAP = 8;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * A box of voxels in one level, in its voxel coordinates.
     *
     * @param level The level.
     * @param min The minimum corner, inclusive.
     * @param max The maximum corner, exclusive.
     */
    public record Box(int level, long @NonNull [] min, long @NonNull [] max) {

        /**
         * How many voxels it holds.
         *
         * @return The count.
         */
        public long volume() {
            long volume = 1;
            for (int axis = 0; axis < AXES; ++axis) {
                volume *= Math.max(0, max[axis] - min[axis]);
            }
            return volume;
        }

        /**
         * The box in the world, in meters.
         *
         * @return Min x, y, z then max x, y, z.
         */
        public double[] world() {
            final int size = VOXEL_SIZES[level];
            return new double[] {
                (double) min[0] * size,
                (double) min[1] * size,
                (double) min[2] * size,
                (double) max[0] * size,
                (double) max[1] * size,
                (double) max[2] * size
            };
        }
    }

    /** Each level's origin, in its voxels. */
    private final long[][] origins = new long[LEVELS][AXES];

    /** Whether each level has been placed and holds anything. */
    private final boolean[] valid = new boolean[LEVELS];

    /**
     * A level's origin.
     *
     * @param level The level.
     * @return Its minimum corner, in its voxels. Don't modify it.
     */
    public long[] origin(int level) {
        return origins[level];
    }

    /**
     * Whether a level has been placed.
     *
     * @param level The level.
     * @return True once it has.
     */
    public boolean isValid(int level) {
        return valid[level];
    }

    /**
     * Every voxel a level covers now.
     *
     * @param level The level.
     * @return The box, in the level's voxels.
     */
    public Box whole(int level) {
        return box(level, origins[level], add(origins[level], SIZE));
    }

    /** Forget every level, so each is written whole next time. */
    public void invalidateAll() {
        Arrays.fill(valid, false);
    }

    /**
     * Where a level would be for the camera where it is: the camera's voxel, snapped, near the
     * middle.
     *
     * @param level The level.
     * @param camera The camera's world position.
     * @return The origin, in the level's voxels.
     */
    static long[] originFor(int level, @NonNull Vector3dc camera) {
        final int size = VOXEL_SIZES[level];
        final long[] origin = new long[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            final long voxel = (long) Math.floor(camera.get(axis) / size);
            origin[axis] = Math.floorDiv(voxel - SIZE / 2, SNAP) * SNAP;
        }
        return origin;
    }

    /**
     * Move the levels to the camera, and say which voxels each newly covers: the whole level the
     * first time or after a jump past its size, otherwise up to one slab an axis.
     *
     * @param camera The camera's world position.
     * @return The boxes to clear and write, in each level's voxels.
     */
    public List<Box> update(@NonNull Vector3dc camera) {
        final List<Box> exposed = new ArrayList<>();
        for (int level = 0; level < LEVELS; ++level) {
            final long[] next = originFor(level, camera);
            final long[] previous = origins[level];
            boolean jumped = !valid[level];
            for (int axis = 0; axis < AXES && !jumped; ++axis) {
                jumped = Math.abs(next[axis] - previous[axis]) >= SIZE;
            }
            if (jumped) {
                exposed.add(box(level, next, add(next, SIZE)));
            } else {
                // Peel one slab off an axis at a time, so the slabs don't overlap
                final long[] min = next.clone();
                final long[] max = add(next, SIZE);
                for (int axis = 0; axis < AXES; ++axis) {
                    if (next[axis] == previous[axis]) {
                        continue;
                    }
                    final long[] slabMin = min.clone();
                    final long[] slabMax = max.clone();
                    if (next[axis] > previous[axis]) {
                        slabMin[axis] = previous[axis] + SIZE;
                        max[axis] = previous[axis] + SIZE;
                    } else {
                        slabMax[axis] = previous[axis];
                        min[axis] = previous[axis];
                    }
                    exposed.add(box(level, slabMin, slabMax));
                }
            }
            System.arraycopy(next, 0, origins[level], 0, AXES);
            valid[level] = true;
        }
        return exposed;
    }

    /**
     * The voxels a box in the world covers in each placed level, clipped to the level.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner.
     * @return One box a level it reaches into, in that level's voxels.
     */
    public List<Box> boxesFor(double @NonNull [] min, double @NonNull [] max) {
        final List<Box> out = new ArrayList<>();
        for (int level = 0; level < LEVELS; ++level) {
            if (!valid[level]) {
                continue;
            }
            final int size = VOXEL_SIZES[level];
            final long[] low = new long[AXES];
            final long[] high = new long[AXES];
            boolean empty = false;
            for (int axis = 0; axis < AXES; ++axis) {
                low[axis] = Math.max(origins[level][axis], (long) Math.floor(min[axis] / size));
                high[axis] =
                        Math.min(origins[level][axis] + SIZE, (long) Math.ceil(max[axis] / size));
                empty |= high[axis] <= low[axis];
            }
            if (!empty) {
                out.add(box(level, low, high));
            }
        }
        return out;
    }

    /**
     * A voxel's texel along one axis: its coordinate modulo the size.
     *
     * @param voxel The voxel coordinate.
     * @return The texel, from 0 to {@link #SIZE} - 1.
     */
    public static int texel(long voxel) {
        return (int) Math.floorMod(voxel, SIZE);
    }

    /**
     * Make a box.
     *
     * @param level The level.
     * @param min The minimum corner.
     * @param max The maximum corner.
     * @return The box.
     */
    private static Box box(int level, long @NonNull [] min, long @NonNull [] max) {
        return new Box(level, min.clone(), max.clone());
    }

    /**
     * A corner moved by the same amount along every axis.
     *
     * @param corner The corner.
     * @param amount The amount.
     * @return The moved corner.
     */
    private static long[] add(long @NonNull [] corner, long amount) {
        final long[] out = corner.clone();
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] += amount;
        }
        return out;
    }
}
