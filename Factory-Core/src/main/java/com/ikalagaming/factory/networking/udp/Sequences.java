package com.ikalagaming.factory.networking.udp;

/**
 * 16-bit sequence numbers, which wrap. One is newer than another when it is less than half the
 * range ahead of it.
 */
final class Sequences {

    /** The range of a sequence number. */
    static final int RANGE = 1 << 16;

    /** Half the range. */
    private static final int HALF = RANGE / 2;

    /** Not made. */
    private Sequences() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Whether one sequence number is newer than another.
     *
     * @param a The one.
     * @param b The other.
     * @return True if a comes after b.
     */
    static boolean newer(int a, int b) {
        final int diff = (a - b) & (RANGE - 1);
        return diff != 0 && diff < HALF;
    }

    /**
     * How far ahead one sequence number is of another.
     *
     * @param a The later one.
     * @param b The earlier one.
     * @return The distance, from 0 to {@link #RANGE} - 1.
     */
    static int distance(int a, int b) {
        return (a - b) & (RANGE - 1);
    }

    /**
     * The sequence number after one.
     *
     * @param a The number.
     * @param steps How many steps on.
     * @return The number that many steps on, wrapped.
     */
    static int add(int a, int steps) {
        return (a + steps) & (RANGE - 1);
    }
}
