package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.List;

/**
 * A child sampled on a coarse lattice and interpolated between the samples: both a speed-up, since
 * the child runs once per lattice point instead of once per block, and a smoothing. Lattice points
 * are at multiples of the cell size in world coordinates, so neighboring chunks agree.
 *
 * <p>As {@code blur}, each lattice sample is also the average of the child at the point and half a
 * cell either way along each axis, a large-scale average for things like how enclosed by rock a
 * place is.
 *
 * <p>Axes the child ignores aren't interpolated along. Lattice samples are cached for the chunk.
 */
public final class Interpolated implements DensityNode {

    /** How far from a lattice point a blur averages, as a share of the cell. */
    private static final double BLUR_REACH = 0.5;

    /** The child sampled on the lattice. */
    private final DensityNode arg;

    /** The lattice cell size along x, y and z, in blocks. */
    private final double[] cell;

    /** Whether each lattice sample averages its neighborhood. */
    private final boolean blur;

    /** The cache slot for lattice samples, or negative for none. */
    private final int slot;

    /**
     * Set up a lattice.
     *
     * @param arg The child.
     * @param cell The cell size along x, y and z, each at least 1.
     * @param blur True to average around each lattice point.
     * @param slot The cache slot for lattice samples, or negative for none.
     */
    public Interpolated(@NonNull DensityNode arg, double @NonNull [] cell, boolean blur, int slot) {
        this.arg = arg;
        this.cell = cell.clone();
        this.blur = blur;
        this.slot = slot;
    }

    /**
     * Whether an axis is interpolated along.
     *
     * @param axis The axis index, 0 to 2.
     * @return True if the child depends on it.
     */
    private boolean uses(int axis) {
        return (arg.axes() & Transforms.AXIS_BITS[axis]) != 0;
    }

    @Override
    public double value(double x, double y, double z, EvalCache cache) {
        final double[] p = {x, y, z};
        double[] base = new double[Transforms.AXES];
        double[] t = new double[Transforms.AXES];
        for (int axis = 0; axis < Transforms.AXES; ++axis) {
            if (uses(axis)) {
                base[axis] = StrictMath.floor(p[axis] / cell[axis]) * cell[axis];
                t[axis] = (p[axis] - base[axis]) / cell[axis];
            } else {
                // Ignored, so any value will do; 0 keeps the cache keys shared
                base[axis] = 0;
            }
        }
        double result = 0;
        // Each of the 8 corners, weighted by how close the point is to it
        for (int corner = 0; corner < 1 << Transforms.AXES; ++corner) {
            double weight = 1;
            double[] at = new double[Transforms.AXES];
            boolean skip = false;
            for (int axis = 0; axis < Transforms.AXES; ++axis) {
                final boolean far = (corner >> axis & 1) != 0;
                if (!uses(axis)) {
                    if (far) {
                        skip = true;
                    }
                    at[axis] = base[axis];
                    continue;
                }
                at[axis] = base[axis] + (far ? cell[axis] : 0);
                weight *= far ? t[axis] : 1 - t[axis];
            }
            if (skip || weight == 0) {
                continue;
            }
            result += weight * sample(at[0], at[1], at[2], cache);
        }
        return result;
    }

    /**
     * The child at a lattice point, averaged around it for a blur, cached for the chunk.
     *
     * @param x The lattice point's x.
     * @param y The lattice point's y.
     * @param z The lattice point's z.
     * @param cache The chunk's cache, or null.
     * @return The sample.
     */
    private double sample(double x, double y, double z, EvalCache cache) {
        if (cache == null || slot < 0) {
            return compute(x, y, z, cache);
        }
        return cache.get(slot, x, y, z, () -> compute(x, y, z, cache));
    }

    /**
     * Work out a lattice sample.
     *
     * @param x The lattice point's x.
     * @param y The lattice point's y.
     * @param z The lattice point's z.
     * @param cache The chunk's cache, or null.
     * @return The sample.
     */
    private double compute(double x, double y, double z, EvalCache cache) {
        double sum = arg.value(x, y, z, cache);
        if (!blur) {
            return sum;
        }
        int count = 1;
        final double[] p = {x, y, z};
        for (int axis = 0; axis < Transforms.AXES; ++axis) {
            if (!uses(axis)) {
                continue;
            }
            for (int sign = -1; sign <= 1; sign += 2) {
                double[] q = p.clone();
                q[axis] += sign * cell[axis] * BLUR_REACH;
                sum += arg.value(q[0], q[1], q[2], cache);
                ++count;
            }
        }
        return sum / count;
    }

    @Override
    public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
        // Sampled at the lattice points around the point, not at it
        return null;
    }

    @Override
    public Box childBox(int child, @NonNull Box box) {
        // Every value blends lattice samples from the cells around the box, and a blurred sample
        // reaches half a cell further
        double[] margin = new double[Transforms.AXES];
        for (int axis = 0; axis < Transforms.AXES; ++axis) {
            margin[axis] = uses(axis) ? cell[axis] * (1 + (blur ? BLUR_REACH : 0)) : 0;
        }
        return box.expand(margin[0], margin[1], margin[2]);
    }

    @Override
    public Interval bounds(@NonNull Box box) {
        return arg.bounds(childBox(0, box));
    }

    @Override
    public int axes() {
        return arg.axes();
    }

    @Override
    public String type() {
        return blur ? "blur" : "interpolated";
    }

    @Override
    public List<DensityNode> children() {
        return List.of(arg);
    }
}
