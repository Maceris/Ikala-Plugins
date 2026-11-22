package com.ikalagaming.factory.sim;

import lombok.NonNull;

/**
 * Moving a box through solid blocks along one axis. A box covers [min, max) on each axis, and a
 * block b covers [b, b + 1) meters, so a box resting on a face touches the block without being in
 * it.
 */
final class BoxSweep {

    /** The x axis, as an index into a box's corners. */
    static final int X = 0;

    /** The y axis. */
    static final int Y = 1;

    /** The z axis. */
    static final int Z = 2;

    /** Not made. */
    private BoxSweep() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * How far a box can move along an axis before it meets a solid block. A box that is already in
     * a solid block on that side stays where it is rather than being pushed back.
     *
     * @param view The blocks.
     * @param min The box's low corner, in units.
     * @param max The box's high corner, in units, past the low one on every axis.
     * @param axis Which axis: {@link #X}, {@link #Y} or {@link #Z}.
     * @param distance How far to move, in units; negative to move down the axis.
     * @return How far it can move, between 0 and the distance.
     */
    static long move(
            @NonNull BlockView view,
            long @NonNull [] min,
            long @NonNull [] max,
            int axis,
            long distance) {
        if (distance > 0) {
            final long first = Fixed.block(max[axis]);
            final long last = Fixed.block(max[axis] + distance - 1);
            for (long layer = first; layer <= last; ++layer) {
                if (layerSolid(view, min, max, axis, layer)) {
                    return Math.max(0, (layer << Fixed.SHIFT) - max[axis]);
                }
            }
        } else if (distance < 0) {
            final long first = Fixed.block(min[axis] - 1);
            final long last = Fixed.block(min[axis] + distance);
            for (long layer = first; layer >= last; --layer) {
                if (layerSolid(view, min, max, axis, layer)) {
                    return Math.min(0, ((layer + 1) << Fixed.SHIFT) - min[axis]);
                }
            }
        }
        return distance;
    }

    /**
     * Whether any block in one layer across an axis, within the box's reach on the other two axes,
     * is solid.
     *
     * @param view The blocks.
     * @param min The box's low corner.
     * @param max The box's high corner.
     * @param axis The axis the layer is across.
     * @param layer The layer's block coordinate on that axis.
     * @return True if one is solid.
     */
    private static boolean layerSolid(
            BlockView view, long[] min, long[] max, int axis, long layer) {
        final int a = (axis + 1) % 3;
        final int b = (axis + 2) % 3;
        final long fromA = Fixed.block(min[a]);
        final long toA = Fixed.block(max[a] - 1);
        final long fromB = Fixed.block(min[b]);
        final long toB = Fixed.block(max[b] - 1);
        final int[] at = new int[3];
        at[axis] = (int) layer;
        for (long i = fromA; i <= toA; ++i) {
            at[a] = (int) i;
            for (long j = fromB; j <= toB; ++j) {
                at[b] = (int) j;
                if (view.solid(at[X], at[Y], at[Z])) {
                    return true;
                }
            }
        }
        return false;
    }
}
