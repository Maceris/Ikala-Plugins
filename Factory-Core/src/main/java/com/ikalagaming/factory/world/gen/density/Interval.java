package com.ikalagaming.factory.world.gen.density;

/**
 * The range a density function's output can take over a region: interval arithmetic, used to find
 * chunks that are certainly solid or certainly air without sampling them. Every operation gives a
 * range that contains every possible result, though not always the tightest one.
 *
 * @param min The smallest possible value.
 * @param max The largest possible value.
 */
public record Interval(double min, double max) {

    /** Every value. */
    public static final Interval EVERYTHING =
            new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    /** The range noise outputs. */
    public static final Interval UNIT = new Interval(-1, 1);

    /**
     * A single value.
     *
     * @param value The value.
     * @return The interval holding only it.
     */
    public static Interval of(double value) {
        return new Interval(value, value);
    }

    /**
     * Whether a value is inside.
     *
     * @param value The value.
     * @return True if min ≤ value ≤ max.
     */
    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    /**
     * The sum of a value from each.
     *
     * @param other The other interval.
     * @return The range of the sum.
     */
    public Interval add(Interval other) {
        return new Interval(min + other.min, max + other.max);
    }

    /**
     * The product of a value from each.
     *
     * @param other The other interval.
     * @return The range of the product.
     */
    public Interval mul(Interval other) {
        final double a = safeMul(min, other.min);
        final double b = safeMul(min, other.max);
        final double c = safeMul(max, other.min);
        final double d = safeMul(max, other.max);
        return new Interval(
                Math.min(Math.min(a, b), Math.min(c, d)), Math.max(Math.max(a, b), Math.max(c, d)));
    }

    /**
     * Multiply, treating zero times infinity as zero, which is right for bounds.
     *
     * @param a A value.
     * @param b Another value.
     * @return The product.
     */
    private static double safeMul(double a, double b) {
        return a == 0 || b == 0 ? 0 : a * b;
    }

    /**
     * Scale by a constant.
     *
     * @param factor The constant.
     * @return The scaled range.
     */
    public Interval scale(double factor) {
        return mul(of(factor));
    }

    /**
     * The smaller of a value from each.
     *
     * @param other The other interval.
     * @return The range of the minimum.
     */
    public Interval min(Interval other) {
        return new Interval(Math.min(min, other.min), Math.min(max, other.max));
    }

    /**
     * The larger of a value from each.
     *
     * @param other The other interval.
     * @return The range of the maximum.
     */
    public Interval max(Interval other) {
        return new Interval(Math.max(min, other.min), Math.max(max, other.max));
    }

    /**
     * The smallest interval holding both.
     *
     * @param other The other interval.
     * @return The union's hull.
     */
    public Interval union(Interval other) {
        return new Interval(Math.min(min, other.min), Math.max(max, other.max));
    }

    /**
     * The negated range.
     *
     * @return The range of -v.
     */
    public Interval neg() {
        return new Interval(-max, -min);
    }

    /**
     * The absolute value's range.
     *
     * @return The range of |v|.
     */
    public Interval abs() {
        if (min >= 0) {
            return this;
        }
        if (max <= 0) {
            return neg();
        }
        return new Interval(0, Math.max(-min, max));
    }

    /**
     * The square's range.
     *
     * @return The range of v^2.
     */
    public Interval square() {
        final Interval magnitude = abs();
        return new Interval(magnitude.min * magnitude.min, magnitude.max * magnitude.max);
    }

    /**
     * The cube's range. Cubing keeps order, so the ends map to the ends.
     *
     * @return The range of v^3.
     */
    public Interval cube() {
        return new Interval(min * min * min, max * max * max);
    }

    /**
     * The range after clamping.
     *
     * @param low The clamp's minimum.
     * @param high The clamp's maximum.
     * @return The clamped range.
     */
    public Interval clamp(double low, double high) {
        return new Interval(Math.clamp(min, low, high), Math.clamp(max, low, high));
    }
}
