package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Density nodes that change where their child is evaluated, not its value. Bounds follow by working
 * out the box the child sees.
 */
public final class Transforms {

    /** How many axes coordinates have. */
    static final int AXES = DensityNode.AXIS_COUNT;

    /** The axis bits in x, y, z order. */
    static final int[] AXIS_BITS = {DensityNode.X, DensityNode.Y, DensityNode.Z};

    /**
     * Moves a pattern: the child is evaluated at the point minus the offset.
     *
     * @param arg The child.
     * @param offset How far to move it along x, y and z.
     */
    public record Translate(@NonNull DensityNode arg, double @NonNull [] offset)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(x - offset[0], y - offset[1], z - offset[2], cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            return new double[] {x - offset[0], y - offset[1], z - offset[2]};
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            return new Box(
                    box.minX() - offset[0],
                    box.minY() - offset[1],
                    box.minZ() - offset[2],
                    box.maxX() - offset[0],
                    box.maxY() - offset[1],
                    box.maxZ() - offset[2]);
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
            return "translate";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * Stretches a pattern: the child is evaluated at the point divided by the factor, so a factor
     * of 2 makes it twice as big.
     *
     * @param arg The child.
     * @param factor The stretch along x, y and z, none of them zero.
     */
    public record Scale(@NonNull DensityNode arg, double @NonNull [] factor)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(x / factor[0], y / factor[1], z / factor[2], cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            return new double[] {x / factor[0], y / factor[1], z / factor[2]};
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            Interval[] ranges = new Interval[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                ranges[axis] = box.range(AXIS_BITS[axis]).scale(1 / factor[axis]);
            }
            return Box.of(ranges[0], ranges[1], ranges[2]);
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
            return "scale";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * Turns a pattern about an axis through the origin, to break up grid alignment. The sine and
     * cosine are worked out once, when the data loads.
     *
     * @param arg The child.
     * @param axis The axis turned about, {@link DensityNode#X}, {@link DensityNode#Y} or {@link
     *     DensityNode#Z}.
     * @param cos The cosine of the angle.
     * @param sin The sine of the angle.
     */
    public record Rotate(@NonNull DensityNode arg, int axis, double cos, double sin)
            implements DensityNode {

        /**
         * The two axes that turn, as indices into x, y, z, in the order that turns counter
         * clockwise looking down the axis.
         *
         * @return The first and second axis indices.
         */
        private int[] plane() {
            return switch (axis) {
                case X -> new int[] {1, 2};
                case Y -> new int[] {2, 0};
                default -> new int[] {0, 1};
            };
        }

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            double[] p = {x, y, z};
            final int[] uv = plane();
            // The pattern turns by the angle, so points turn back by it
            final double u = p[uv[0]];
            final double v = p[uv[1]];
            p[uv[0]] = cos * u + sin * v;
            p[uv[1]] = -sin * u + cos * v;
            return arg.value(p[0], p[1], p[2], cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            double[] p = {x, y, z};
            final int[] uv = plane();
            final double u = p[uv[0]];
            final double v = p[uv[1]];
            p[uv[0]] = cos * u + sin * v;
            p[uv[1]] = -sin * u + cos * v;
            return p;
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            Interval[] ranges = {
                box.range(X), box.range(Y), box.range(Z),
            };
            final int[] uv = plane();
            final Interval u = ranges[uv[0]];
            final Interval v = ranges[uv[1]];
            ranges[uv[0]] = u.scale(cos).add(v.scale(sin));
            ranges[uv[1]] = u.scale(-sin).add(v.scale(cos));
            return Box.of(ranges[0], ranges[1], ranges[2]);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return arg.bounds(childBox(0, box));
        }

        @Override
        public int axes() {
            final int[] uv = plane();
            final int turned = AXIS_BITS[uv[0]] | AXIS_BITS[uv[1]];
            // Turning mixes the two axes in the plane, so needing either means needing both
            return (arg.axes() & turned) != 0 ? arg.axes() | turned : arg.axes();
        }

        @Override
        public String type() {
            return "rotate";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * Domain warping: the child is evaluated at the point pushed by three other functions, for
     * organic swirls and twisted tendrils.
     *
     * @param arg The child.
     * @param by How far to push along x, y and z, each scaled by the amplitude.
     * @param amplitude How strong the push is, in blocks.
     */
    public record Warp(@NonNull DensityNode arg, @NonNull List<DensityNode> by, double amplitude)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(
                    x + amplitude * by.get(0).value(x, y, z, cache),
                    y + amplitude * by.get(1).value(x, y, z, cache),
                    z + amplitude * by.get(2).value(x, y, z, cache),
                    cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            if (child != 0) {
                return new double[] {x, y, z};
            }
            return new double[] {
                x + amplitude * by.get(0).value(x, y, z, cache),
                y + amplitude * by.get(1).value(x, y, z, cache),
                z + amplitude * by.get(2).value(x, y, z, cache)
            };
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            double[] reach = new double[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                final Interval push = by.get(axis).bounds(box).scale(amplitude);
                reach[axis] = Math.max(Math.abs(push.min()), Math.abs(push.max()));
            }
            return box.expand(reach[0], reach[1], reach[2]);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return arg.bounds(childBox(0, box));
        }

        @Override
        public int axes() {
            int axes = arg.axes();
            for (int axis = 0; axis < AXES; ++axis) {
                if ((arg.axes() & AXIS_BITS[axis]) != 0) {
                    axes |= by.get(axis).axes();
                }
            }
            return axes;
        }

        @Override
        public String type() {
            return "warp";
        }

        @Override
        public List<DensityNode> children() {
            List<DensityNode> all = new ArrayList<>(by.size() + 1);
            all.add(arg);
            all.addAll(by);
            return all;
        }
    }

    /**
     * Endless tiling: each coordinate wraps into [0, period).
     *
     * @param arg The child.
     * @param period The tile size along x, y and z, or 0 for no tiling on that axis.
     */
    public record Repeat(@NonNull DensityNode arg, double @NonNull [] period)
            implements DensityNode {

        /**
         * Wrap one coordinate.
         *
         * @param value The coordinate.
         * @param size The tile size, or 0 for none.
         * @return The wrapped coordinate.
         */
        private static double wrap(double value, double size) {
            return size <= 0 ? value : value - size * StrictMath.floor(value / size);
        }

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(wrap(x, period[0]), wrap(y, period[1]), wrap(z, period[2]), cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            return new double[] {wrap(x, period[0]), wrap(y, period[1]), wrap(z, period[2])};
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            Interval[] ranges = new Interval[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                final Interval range = box.range(AXIS_BITS[axis]);
                final double size = period[axis];
                if (size <= 0) {
                    ranges[axis] = range;
                    continue;
                }
                final double tile = StrictMath.floor(range.min() / size);
                // Inside one tile it shifts; across a tile edge it can be anywhere in the tile
                ranges[axis] =
                        range.max() < (tile + 1) * size
                                ? new Interval(
                                        wrap(range.min(), size),
                                        wrap(range.min(), size) + range.max() - range.min())
                                : new Interval(0, size);
            }
            return Box.of(ranges[0], ranges[1], ranges[2]);
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
            return "repeat";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * Symmetry: the child is evaluated at the magnitude of some coordinates, mirroring it across
     * zero on those axes.
     *
     * @param arg The child.
     * @param mirrored The mirrored axes, as a mask of {@link DensityNode#X}, {@link DensityNode#Y}
     *     and {@link DensityNode#Z}.
     */
    public record Mirror(@NonNull DensityNode arg, int mirrored) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(
                    (mirrored & X) != 0 ? Math.abs(x) : x,
                    (mirrored & Y) != 0 ? Math.abs(y) : y,
                    (mirrored & Z) != 0 ? Math.abs(z) : z,
                    cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            return new double[] {
                (mirrored & X) != 0 ? Math.abs(x) : x,
                (mirrored & Y) != 0 ? Math.abs(y) : y,
                (mirrored & Z) != 0 ? Math.abs(z) : z
            };
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            Interval[] ranges = new Interval[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                final Interval range = box.range(AXIS_BITS[axis]);
                ranges[axis] = (mirrored & AXIS_BITS[axis]) != 0 ? range.abs() : range;
            }
            return Box.of(ranges[0], ranges[1], ranges[2]);
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
            return "mirror";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * Blocky or terraced results: each coordinate is rounded down to a multiple of a step.
     *
     * @param arg The child.
     * @param step The step along x, y and z, or 0 for none on that axis.
     */
    public record Quantize(@NonNull DensityNode arg, double @NonNull [] step)
            implements DensityNode {

        /**
         * Round one coordinate down to the step.
         *
         * @param value The coordinate.
         * @param size The step, or 0 for none.
         * @return The rounded coordinate.
         */
        private static double snap(double value, double size) {
            return size <= 0 ? value : size * StrictMath.floor(value / size);
        }

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(snap(x, step[0]), snap(y, step[1]), snap(z, step[2]), cache);
        }

        @Override
        public double[] childPoint(int child, double x, double y, double z, EvalCache cache) {
            return new double[] {snap(x, step[0]), snap(y, step[1]), snap(z, step[2])};
        }

        @Override
        public Box childBox(int child, @NonNull Box box) {
            if (child != 0) {
                return box;
            }
            Interval[] ranges = new Interval[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                final Interval range = box.range(AXIS_BITS[axis]);
                ranges[axis] =
                        new Interval(snap(range.min(), step[axis]), snap(range.max(), step[axis]));
            }
            return Box.of(ranges[0], ranges[1], ranges[2]);
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
            return "quantize";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    private Transforms() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
