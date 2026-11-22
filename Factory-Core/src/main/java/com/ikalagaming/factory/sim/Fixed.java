package com.ikalagaming.factory.sim;

/**
 * Fixed-point numbers for the simulation, which never uses floating point so the client and server
 * always get exactly the same answer.
 *
 * <ul>
 *   <li><b>Lengths</b> are {@code long}s in units of 1/65536 m ({@link #ONE} is a meter, which is a
 *       block; {@link #GRID} is the 1/16 m grid).
 *   <li><b>Velocities</b> are units per frame, and <b>accelerations</b> units per frame per frame.
 *       {@link #perFrame} and {@link #perFrameSquared} turn meters per second into them.
 *   <li><b>Fractions</b> such as sines are {@code int}s with {@link #ONE} as one, and {@link #mul}
 *       scales by them.
 * </ul>
 *
 * <p>Rounding is always toward negative infinity unless a method says otherwise: shifts are
 * arithmetic, and nothing uses {@code /} on values that might be negative.
 */
public final class Fixed {

    /** How many bits are below the point. */
    public static final int SHIFT = 16;

    /** One meter, one block, or a fraction of one. */
    public static final long ONE = 1L << SHIFT;

    /** One step of the 1/16 m grid. */
    public static final long GRID = ONE / 16;

    /** Not made. */
    private Fixed() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * A whole number of meters as a length.
     *
     * @param meters The meters.
     * @return The length, in units.
     */
    public static long ofMeters(long meters) {
        return meters << SHIFT;
    }

    /**
     * A fraction of a meter as a length, rounded to the nearest unit (halves up).
     *
     * @param numerator The meters, over the denominator.
     * @param denominator The denominator, positive.
     * @return The length, in units.
     */
    public static long ofMeters(long numerator, long denominator) {
        return Math.floorDiv(2 * numerator * ONE + denominator, 2 * denominator);
    }

    /**
     * The block a length falls in.
     *
     * @param units The length, in units.
     * @return The block coordinate.
     */
    public static long block(long units) {
        return units >> SHIFT;
    }

    /**
     * Scale a value by a fraction.
     *
     * @param value The value.
     * @param fraction The fraction, with {@link #ONE} as one.
     * @return The scaled value, rounded down.
     */
    public static long mul(long value, long fraction) {
        return (value * fraction) >> SHIFT;
    }

    /**
     * A speed in meters per second as units per frame, rounded to the nearest unit (halves up).
     *
     * @param numerator The speed in meters per second, over the denominator.
     * @param denominator The denominator, positive.
     * @return The speed, in units per frame.
     */
    public static long perFrame(long numerator, long denominator) {
        final long scaled = numerator * ONE * Frames.FRAME_MILLIS;
        final long divisor = denominator * Frames.MILLIS_PER_SECOND;
        return Math.floorDiv(2 * scaled + divisor, 2 * divisor);
    }

    /**
     * An acceleration in meters per second per second as units per frame per frame, rounded to the
     * nearest unit (halves up).
     *
     * @param numerator The acceleration in meters per second per second, over the denominator.
     * @param denominator The denominator, positive.
     * @return The acceleration, in units per frame per frame.
     */
    public static long perFrameSquared(long numerator, long denominator) {
        final long scaled = numerator * ONE * Frames.FRAME_MILLIS * Frames.FRAME_MILLIS;
        final long divisor = denominator * Frames.MILLIS_PER_SECOND * Frames.MILLIS_PER_SECOND;
        return Math.floorDiv(2 * scaled + divisor, 2 * divisor);
    }

    /**
     * The integer square root.
     *
     * @param value The value, not negative.
     * @return The largest number whose square is at most the value.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static long sqrt(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("No square root of " + value);
        }
        long remainder = value;
        long root = 0;
        long bit = 1L << 62;
        while (bit > remainder) {
            bit >>= 2;
        }
        while (bit != 0) {
            if (remainder >= root + bit) {
                remainder -= root + bit;
                root = (root >> 1) + bit;
            } else {
                root >>= 1;
            }
            bit >>= 2;
        }
        return root;
    }

    /**
     * The length of a vector.
     *
     * @param x The x part, in units.
     * @param y The y part, in units.
     * @param z The z part, in units.
     * @return The length, in units, rounded down. Parts must stay below about 3 * 10^9 units (45
     *     km) so the squares don't overflow.
     */
    public static long length(long x, long y, long z) {
        return sqrt(x * x + y * y + z * z);
    }
}
