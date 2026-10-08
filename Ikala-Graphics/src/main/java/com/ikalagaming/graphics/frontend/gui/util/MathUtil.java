package com.ikalagaming.graphics.frontend.gui.util;

/**
 * Math helpers that behave like ImGui's. Unlike {@link Math#clamp(double, double, double)}, the
 * clamp functions never throw when the minimum is larger than the maximum, which happens with
 * layouts that are too small to fit their contents.
 */
public class MathUtil {
    /**
     * Clamp a value to a range, the same as ImGui's ImClamp(). If the minimum is larger than the
     * maximum, values below the minimum produce the minimum and others the maximum.
     *
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @return The clamped value.
     */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    /**
     * Clamp a value to a range, the same as ImGui's ImClamp().
     *
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @return The clamped value.
     * @see #clamp(float, float, float)
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }

    /**
     * Clamp a value to a range, the same as ImGui's ImClamp().
     *
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @return The clamped value.
     * @see #clamp(float, float, float)
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    /**
     * Clamp a long value to an int range, the same as ImGui's ImClamp(). Useful for clamping sums
     * that might overflow an int.
     *
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @return The clamped value.
     * @see #clamp(float, float, float)
     */
    public static int clamp(long value, int min, int max) {
        return value < min ? min : value > max ? max : (int) value;
    }

    /**
     * Clamp a value to a range, the same as ImGui's ImClamp().
     *
     * @param value The value.
     * @param min The minimum.
     * @param max The maximum.
     * @return The clamped value.
     * @see #clamp(float, float, float)
     */
    public static long clamp(long value, long min, long max) {
        return value < min ? min : value > max ? max : value;
    }

    /** Private constructor so this is not instantiated. */
    private MathUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
