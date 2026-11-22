package com.ikalagaming.factory.world;

/**
 * A world's clock. Time is counted in ticks from the world's first midnight; a day is {@value
 * #DAY_TICKS} ticks, 20 minutes at 20 ticks a second. New worlds start at {@value #START_HOUR}
 * o'clock on day 0.
 *
 * <p>The sun rises in the east at 6, is highest at noon and sets in the west at 18. Its path is
 * tilted toward the south by {@value #SUN_TILT_DEGREES} degrees, so it never stands straight
 * overhead and shadows always have some length. +x is east, +y up and +z south.
 */
public final class WorldTime {

    /** Ticks in one day. */
    public static final long DAY_TICKS = 24_000;

    /** Hours in one day. */
    public static final int DAY_HOURS = 24;

    /** Ticks in one hour. */
    public static final long HOUR_TICKS = DAY_TICKS / DAY_HOURS;

    /** The hour new worlds start at. */
    public static final int START_HOUR = 11;

    /** The time new worlds start at. */
    public static final long START_TICKS = START_HOUR * HOUR_TICKS;

    /** When the sun rises, in ticks into the day. */
    public static final long SUNRISE_TICKS = 6 * HOUR_TICKS;

    /** How far the sun's path leans toward the south, in degrees. */
    public static final double SUN_TILT_DEGREES = 30;

    /**
     * Which day a time is on.
     *
     * @param ticks The time.
     * @return The day, 0 for the first.
     */
    public static long day(long ticks) {
        return Math.floorDiv(ticks, DAY_TICKS);
    }

    /**
     * How far into its day a time is.
     *
     * @param ticks The time.
     * @return The ticks since that day's midnight, from 0 up to {@value #DAY_TICKS}.
     */
    public static long timeOfDay(long ticks) {
        return Math.floorMod(ticks, DAY_TICKS);
    }

    /**
     * The hour of a time, with its fraction.
     *
     * @param ticks The time; fractions of a tick are allowed.
     * @return The hour, from 0 up to 24.
     */
    public static double hour(double ticks) {
        final double ofDay = ticks - Math.floor(ticks / DAY_TICKS) * DAY_TICKS;
        return ofDay / HOUR_TICKS;
    }

    /**
     * Where the sun is: a unit vector from the ground toward it. Below the horizon at night.
     *
     * @param ticks The time; fractions of a tick are allowed, for smooth motion between ticks.
     * @return The direction as x, y and z.
     */
    public static double[] towardSun(double ticks) {
        // 0 at sunrise, a half turn at sunset
        final double angle = 2 * Math.PI * (hour(ticks) * HOUR_TICKS - SUNRISE_TICKS) / DAY_TICKS;
        final double tilt = Math.toRadians(SUN_TILT_DEGREES);
        final double up = Math.sin(angle);
        // Already unit length: cos^2 + sin^2 (cos^2 + sin^2) = 1
        return new double[] {Math.cos(angle), up * Math.cos(tilt), up * Math.sin(tilt)};
    }

    /** Private constructor so this class is not instantiated. */
    private WorldTime() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
