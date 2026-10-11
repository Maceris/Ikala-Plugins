package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Density nodes that combine or reshape values. */
public final class Arithmetic {

    /**
     * The union of the axes a list of nodes depends on.
     *
     * @param nodes The nodes.
     * @return The combined axes.
     */
    static int axesOf(@NonNull List<DensityNode> nodes) {
        int axes = 0;
        for (DensityNode node : nodes) {
            axes |= node.axes();
        }
        return axes;
    }

    /** How a list of values folds into one. */
    public enum FoldOp {
        /** The sum. */
        ADD,
        /** The product. */
        MUL,
        /** The smallest. */
        MIN,
        /** The largest. */
        MAX,
        /** The smallest, blended where they're close. */
        SMOOTH_MIN,
        /** The largest, blended where they're close. */
        SMOOTH_MAX
    }

    /**
     * Several values folded into one, left to right.
     *
     * @param op How they fold.
     * @param args The values, at least one.
     * @param k How far apart values blend, for the smooth folds.
     */
    public record Fold(@NonNull FoldOp op, @NonNull List<DensityNode> args, double k)
            implements DensityNode {

        /** The polynomial smooth minimum's largest dip, as a share of k (Quilez). */
        private static final double SMOOTH_DEPTH = 0.25;

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            double result = args.get(0).value(x, y, z, cache);
            for (int i = 1; i < args.size(); ++i) {
                final double next = args.get(i).value(x, y, z, cache);
                result =
                        switch (op) {
                            case ADD -> result + next;
                            case MUL -> result * next;
                            case MIN -> Math.min(result, next);
                            case MAX -> Math.max(result, next);
                            case SMOOTH_MIN -> smoothMin(result, next);
                            case SMOOTH_MAX -> -smoothMin(-result, -next);
                        };
            }
            return result;
        }

        /**
         * The polynomial smooth minimum: the minimum, dipping by up to k / 4 where the two are
         * within k of each other.
         *
         * @param a A value.
         * @param b Another value.
         * @return The blended minimum.
         */
        private double smoothMin(double a, double b) {
            final double h = Math.max(k - Math.abs(a - b), 0) / k;
            return Math.min(a, b) - h * h * k * SMOOTH_DEPTH;
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            Interval result = args.get(0).bounds(box);
            for (int i = 1; i < args.size(); ++i) {
                final Interval next = args.get(i).bounds(box);
                result =
                        switch (op) {
                            case ADD -> result.add(next);
                            case MUL -> result.mul(next);
                            case MIN -> result.min(next);
                            case MAX -> result.max(next);
                            case SMOOTH_MIN -> {
                                final Interval plain = result.min(next);
                                yield new Interval(plain.min() - k * SMOOTH_DEPTH, plain.max());
                            }
                            case SMOOTH_MAX -> {
                                final Interval plain = result.max(next);
                                yield new Interval(plain.min(), plain.max() + k * SMOOTH_DEPTH);
                            }
                        };
            }
            return result;
        }

        @Override
        public int axes() {
            return axesOf(args);
        }

        @Override
        public String type() {
            return op.name().toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public List<DensityNode> children() {
            return args;
        }
    }

    /** A function of one value. */
    public enum UnaryOp {
        /** The negation. */
        NEG,
        /** The magnitude. */
        ABS,
        /** The square. */
        SQUARE,
        /** The cube. */
        CUBE
    }

    /**
     * A function of one value.
     *
     * @param op The function.
     * @param arg The value.
     */
    public record Unary(@NonNull UnaryOp op, @NonNull DensityNode arg) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            final double v = arg.value(x, y, z, cache);
            return switch (op) {
                case NEG -> -v;
                case ABS -> Math.abs(v);
                case SQUARE -> v * v;
                case CUBE -> v * v * v;
            };
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            final Interval v = arg.bounds(box);
            return switch (op) {
                case NEG -> v.neg();
                case ABS -> v.abs();
                case SQUARE -> v.square();
                case CUBE -> v.cube();
            };
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return op.name().toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * A value kept within a range.
     *
     * @param arg The value.
     * @param min The smallest result.
     * @param max The largest result.
     */
    public record Clamp(@NonNull DensityNode arg, double min, double max) implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return Math.clamp(arg.value(x, y, z, cache), min, max);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return arg.bounds(box).clamp(min, max);
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return "clamp";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * A value mapped linearly from one range to another, not clamped.
     *
     * @param arg The value.
     * @param fromMin What maps to {@code toMin}.
     * @param fromMax What maps to {@code toMax}, not equal to {@code fromMin}.
     * @param toMin The result at {@code fromMin}.
     * @param toMax The result at {@code fromMax}.
     */
    public record Remap(
            @NonNull DensityNode arg, double fromMin, double fromMax, double toMin, double toMax)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return toMin
                    + (arg.value(x, y, z, cache) - fromMin) * (toMax - toMin) / (fromMax - fromMin);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return arg.bounds(box)
                    .add(Interval.of(-fromMin))
                    .scale((toMax - toMin) / (fromMax - fromMin))
                    .add(Interval.of(toMin));
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return "remap";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * A shaping curve through points, flat beyond the first and last. Cubic curves are monotone
     * (Fritsch-Carlson), so they never overshoot their points and the curve's range over any span
     * is set by the points inside it and its ends.
     */
    public static final class Spline implements DensityNode {

        /**
         * The limit on the tangents' size relative to the slope, past which a monotone cubic
         * overshoots (Fritsch and Carlson 1980).
         */
        private static final double MONOTONE_LIMIT = 3;

        /** The value the curve reads. */
        private final DensityNode arg;

        /** The points' x values, increasing. */
        private final double[] xs;

        /** The points' y values. */
        private final double[] ys;

        /** The tangent at each point, or null for a linear curve. */
        private final double[] tangents;

        /**
         * Set up a curve.
         *
         * @param arg The value the curve reads.
         * @param xs The points' x values, at least one, strictly increasing.
         * @param ys The points' y values.
         * @param cubic True for a smooth monotone cubic, false for straight lines.
         */
        public Spline(
                @NonNull DensityNode arg,
                double @NonNull [] xs,
                double @NonNull [] ys,
                boolean cubic) {
            this.arg = arg;
            this.xs = xs.clone();
            this.ys = ys.clone();
            this.tangents = cubic && xs.length > 1 ? monotoneTangents(xs, ys) : null;
        }

        /**
         * Tangents that keep each segment of the cubic monotone.
         *
         * @param xs The points' x values.
         * @param ys The points' y values.
         * @return The tangent at each point.
         */
        private static double[] monotoneTangents(double[] xs, double[] ys) {
            final int n = xs.length;
            double[] slopes = new double[n - 1];
            for (int i = 0; i < n - 1; ++i) {
                slopes[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
            }
            double[] m = new double[n];
            m[0] = slopes[0];
            m[n - 1] = slopes[n - 2];
            for (int i = 1; i < n - 1; ++i) {
                m[i] = slopes[i - 1] * slopes[i] <= 0 ? 0 : (slopes[i - 1] + slopes[i]) / 2;
            }
            for (int i = 0; i < n - 1; ++i) {
                if (slopes[i] == 0) {
                    m[i] = 0;
                    m[i + 1] = 0;
                    continue;
                }
                final double alpha = m[i] / slopes[i];
                final double beta = m[i + 1] / slopes[i];
                final double size = alpha * alpha + beta * beta;
                if (size > MONOTONE_LIMIT * MONOTONE_LIMIT) {
                    final double tau = MONOTONE_LIMIT / StrictMath.sqrt(size);
                    m[i] = tau * alpha * slopes[i];
                    m[i + 1] = tau * beta * slopes[i];
                }
            }
            return m;
        }

        /**
         * The curve at a value.
         *
         * @param v The value.
         * @return The curve's output.
         */
        double curve(double v) {
            if (v <= xs[0]) {
                return ys[0];
            }
            final int last = xs.length - 1;
            if (v >= xs[last]) {
                return ys[last];
            }
            int i = java.util.Arrays.binarySearch(xs, v);
            if (i >= 0) {
                return ys[i];
            }
            // The segment from point i to i + 1
            i = -i - 2;
            final double width = xs[i + 1] - xs[i];
            final double t = (v - xs[i]) / width;
            if (tangents == null) {
                return ys[i] + t * (ys[i + 1] - ys[i]);
            }
            // Cubic Hermite basis
            final double t2 = t * t;
            final double t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * ys[i]
                    + (t3 - 2 * t2 + t) * width * tangents[i]
                    + (-2 * t3 + 3 * t2) * ys[i + 1]
                    + (t3 - t2) * width * tangents[i + 1];
        }

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return curve(arg.value(x, y, z, cache));
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            final Interval in = arg.bounds(box);
            final double low = curve(Math.max(in.min(), -Double.MAX_VALUE));
            final double high = curve(Math.min(in.max(), Double.MAX_VALUE));
            double min = Math.min(low, high);
            double max = Math.max(low, high);
            // Every segment is monotone, so extremes are at the ends or at points inside
            for (int i = 0; i < xs.length; ++i) {
                if (in.contains(xs[i])) {
                    min = Math.min(min, ys[i]);
                    max = Math.max(max, ys[i]);
                }
            }
            return new Interval(min, max);
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return "spline";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * A blend between two values.
     *
     * @param t How far from a to b, 0 for a and 1 for b, not clamped.
     * @param a The value at 0.
     * @param b The value at 1.
     */
    public record Lerp(@NonNull DensityNode t, @NonNull DensityNode a, @NonNull DensityNode b)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            final double from = a.value(x, y, z, cache);
            return from + t.value(x, y, z, cache) * (b.value(x, y, z, cache) - from);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            final Interval from = a.bounds(box);
            return from.add(t.bounds(box).mul(b.bounds(box).add(from.neg())));
        }

        @Override
        public int axes() {
            return t.axes() | a.axes() | b.axes();
        }

        @Override
        public String type() {
            return "lerp";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(t, a, b);
        }
    }

    /**
     * One of two values, by which side of a threshold a value is on.
     *
     * @param arg The value tested.
     * @param threshold Values below this give {@code below}.
     * @param below The result below the threshold.
     * @param above The result at or above it.
     */
    public record Step(@NonNull DensityNode arg, double threshold, double below, double above)
            implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return arg.value(x, y, z, cache) < threshold ? below : above;
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            final Interval in = arg.bounds(box);
            if (in.max() < threshold) {
                return Interval.of(below);
            }
            if (in.min() >= threshold) {
                return Interval.of(above);
            }
            return Interval.of(below).union(Interval.of(above));
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return "step";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * 1 where a value is inside a range, easing smoothly to 0 over a distance outside it.
     *
     * @param arg The value tested.
     * @param min The range's start.
     * @param max The range's end.
     * @param falloff How far outside the range it takes to reach 0, or 0 for a hard edge.
     */
    public record RangeMask(@NonNull DensityNode arg, double min, double max, double falloff)
            implements DensityNode {

        /**
         * The mask at a value.
         *
         * @param v The value.
         * @return From 0 to 1.
         */
        double mask(double v) {
            final double outside = v < min ? min - v : Math.max(v - max, 0);
            if (outside == 0) {
                return 1;
            }
            if (falloff <= 0) {
                return 0;
            }
            final double t = Math.clamp(1 - outside / falloff, 0, 1);
            // Smoothstep
            return t * t * (3 - 2 * t);
        }

        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return mask(arg.value(x, y, z, cache));
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            final Interval in = arg.bounds(box);
            final double low = mask(in.min());
            final double high = mask(in.max());
            // Rises to 1 over the range then falls, so the peak is inside or at an end
            final boolean overlaps = in.max() >= min && in.min() <= max;
            return new Interval(Math.min(low, high), overlaps ? 1 : Math.max(low, high));
        }

        @Override
        public int axes() {
            return arg.axes();
        }

        @Override
        public String type() {
            return "range_mask";
        }

        @Override
        public List<DensityNode> children() {
            return List.of(arg);
        }
    }

    /**
     * A copy of a list, so records holding it can't be changed from outside.
     *
     * @param nodes The nodes.
     * @return An unmodifiable copy.
     */
    static List<DensityNode> copy(@NonNull List<DensityNode> nodes) {
        return List.copyOf(new ArrayList<>(nodes));
    }

    private Arithmetic() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
