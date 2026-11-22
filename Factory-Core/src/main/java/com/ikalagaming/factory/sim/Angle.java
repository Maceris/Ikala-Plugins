package com.ikalagaming.factory.sim;

/**
 * Binary angles: 16-bit fractions of a turn, so {@link #TURN} (65536) is a full turn and angles
 * wrap for free. Sines and cosines come from a table worked out with integer math only, so every
 * platform has the same table.
 */
public final class Angle {

    /** A full turn. */
    public static final int TURN = 1 << 16;

    /** A quarter turn. */
    public static final int QUARTER = TURN / 4;

    /** A half turn. */
    public static final int HALF = TURN / 2;

    /** Bits below the point in the table's working values. */
    private static final int WORK_SHIFT = 30;

    /** Pi with {@value #WORK_SHIFT} bits below the point. */
    private static final long PI_WORK = 3_373_259_426L;

    /** How many terms of the series to sum; the next one is below 10^-7 at a quarter turn. */
    private static final int TERMS = 7;

    /** The sine of each angle from 0 to a quarter turn, with {@link Fixed#ONE} as one. */
    private static final int[] QUARTER_SINE = quarterSine();

    /** Not made. */
    private Angle() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * An angle wrapped into one turn.
     *
     * @param angle The angle, in turns / 65536, of any size.
     * @return The same angle from 0 up to, not including, {@link #TURN}.
     */
    public static int wrap(long angle) {
        return (int) (angle & (TURN - 1));
    }

    /**
     * The sine of an angle.
     *
     * @param angle The angle, in turns / 65536, of any size.
     * @return The sine, with {@link Fixed#ONE} as one.
     */
    public static int sin(long angle) {
        final int a = wrap(angle);
        if (a <= QUARTER) {
            return QUARTER_SINE[a];
        }
        if (a <= HALF) {
            return QUARTER_SINE[HALF - a];
        }
        if (a <= HALF + QUARTER) {
            return -QUARTER_SINE[a - HALF];
        }
        return -QUARTER_SINE[TURN - a];
    }

    /**
     * The cosine of an angle.
     *
     * @param angle The angle, in turns / 65536, of any size.
     * @return The cosine, with {@link Fixed#ONE} as one.
     */
    public static int cos(long angle) {
        return sin(angle + QUARTER);
    }

    /**
     * Work out the quarter-turn sine table from the sine's power series, in integers.
     *
     * @return The table, {@link #QUARTER} + 1 entries.
     */
    private static int[] quarterSine() {
        final int[] table = new int[QUARTER + 1];
        for (int a = 0; a <= QUARTER; ++a) {
            // The angle in radians: a / TURN of 2 pi
            final long x = Math.floorDiv(2 * PI_WORK * a + TURN / 2, TURN);
            final long x2 = (x * x) >> WORK_SHIFT;
            long term = x;
            long sum = x;
            for (int k = 1; k < TERMS; ++k) {
                term = Math.floorDiv(-((term * x2) >> WORK_SHIFT), (2L * k) * (2L * k + 1));
                sum += term;
            }
            final int shift = WORK_SHIFT - Fixed.SHIFT;
            table[a] = (int) ((sum + (1L << (shift - 1))) >> shift);
        }
        table[QUARTER] = (int) Fixed.ONE;
        return table;
    }
}
