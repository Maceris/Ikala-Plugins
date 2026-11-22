package com.ikalagaming.factory.sim;

/**
 * The command frame clock. Movement, abilities and anything else that is predicted and rolled back
 * steps once per frame, by exactly one frame, whatever the wall clock does. It is a separate clock
 * from the world tick. See the Command Frames design doc.
 */
public final class Frames {

    /** How long a frame lasts, in milliseconds. */
    public static final int FRAME_MILLIS = 16;

    /** How long a frame lasts, in nanoseconds. */
    public static final long FRAME_NANOS = FRAME_MILLIS * 1_000_000L;

    /** Milliseconds in a second. */
    static final long MILLIS_PER_SECOND = 1000;

    /** Not made. */
    private Frames() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * How many whole frames fit in a time.
     *
     * @param millis The time, in milliseconds, not negative.
     * @return The frames, rounded down.
     */
    public static long ofMillis(long millis) {
        return millis / FRAME_MILLIS;
    }
}
