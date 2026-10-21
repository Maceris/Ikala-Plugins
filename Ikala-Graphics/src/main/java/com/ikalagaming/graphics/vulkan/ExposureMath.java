package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

/**
 * Automatic exposure as {@code auto_exposure.comp} works it out; keep them in step with this.
 *
 * <p>The scene's luminance before tone mapping is counted in a histogram of its log2, bin 0 for
 * pixels too dark to matter. The average log2 luminance of the pixels between two percentiles,
 * leaving out the darkest half and the brightest few, is what an eye adapts to, over time, faster
 * toward brighter than darker. The exposure brings that adapted luminance most of the way to middle
 * gray, {@link #ADAPTATION} of it in stops, so a scene that already averages middle gray keeps the
 * exposure 1 while darker ones stay a little darker and brighter ones a little brighter, as they
 * look to an eye, and within limits, so night stays dark.
 */
public final class ExposureMath {

    /** How many bins the histogram has, bin 0 for pixels too dark to meter. */
    public static final int HISTOGRAM_BINS = 256;

    /** The darkest log2 luminance metered. */
    public static final float MIN_LOG2_LUMINANCE = -12;

    /** The brightest log2 luminance metered; brighter pixels count in the last bin. */
    public static final float MAX_LOG2_LUMINANCE = 8;

    /** The fraction of metered pixels, darkest first, left out. */
    public static final float LOW_PERCENTILE = 0.5f;

    /** The fraction of metered pixels, darkest first, past which the brightest are left out. */
    public static final float HIGH_PERCENTILE = 0.95f;

    /** The luminance the adapted average is brought toward. */
    public static final float MIDDLE_GRAY = 0.18f;

    /**
     * How much of the difference from middle gray, in stops, the exposure takes away: 1 would make
     * every scene average middle gray, a dim forest as bright as a sunlit field.
     */
    public static final float ADAPTATION = 0.75f;

    /**
     * How fast the eye adapts toward a brighter scene: the fraction of the way left it covers in a
     * second is 1 - e^-speed.
     */
    public static final float SPEED_UP = 3;

    /** How fast the eye adapts toward a darker scene, slower, as eyes do. */
    public static final float SPEED_DOWN = 1;

    /** The least exposure, 4 stops down, so the brightest scenes still read as bright. */
    public static final float MIN_EXPOSURE = 1f / 16;

    /** The most exposure, 4 stops up, so night stays dark rather than turning to day. */
    public static final float MAX_EXPOSURE = 16;

    /**
     * The bin a luminance falls in.
     *
     * @param luminance The luminance.
     * @return The bin.
     */
    public static int binOf(float luminance) {
        if (luminance < Math.pow(2, MIN_LOG2_LUMINANCE)) {
            return 0;
        }
        final double t =
                (Math.log(luminance) / Math.log(2) - MIN_LOG2_LUMINANCE)
                        / (MAX_LOG2_LUMINANCE - MIN_LOG2_LUMINANCE);
        return (int) (Math.clamp(t, 0, 1) * (HISTOGRAM_BINS - 2) + 1);
    }

    /**
     * The log2 luminance a bin stands for, its middle.
     *
     * @param bin The bin, from 1.
     * @return The log2 luminance.
     */
    public static float log2LuminanceOf(int bin) {
        final float t = (bin - 0.5f) / (HISTOGRAM_BINS - 2);
        return MIN_LOG2_LUMINANCE + t * (MAX_LOG2_LUMINANCE - MIN_LOG2_LUMINANCE);
    }

    /**
     * The average log2 luminance between the percentiles.
     *
     * @param bins The histogram.
     * @return The average, or NaN if nothing was metered.
     */
    public static double meter(int @NonNull [] bins) {
        double total = 0;
        for (int bin = 1; bin < HISTOGRAM_BINS; ++bin) {
            total += bins[bin];
        }
        final double low = LOW_PERCENTILE * total;
        final double high = HIGH_PERCENTILE * total;
        double below = 0;
        double weighted = 0;
        double kept = 0;
        for (int bin = 1; bin < HISTOGRAM_BINS; ++bin) {
            final double above = below + bins[bin];
            final double inside = Math.max(Math.min(above, high) - Math.max(below, low), 0);
            weighted += inside * log2LuminanceOf(bin);
            kept += inside;
            below = above;
        }
        return kept > 0 ? weighted / kept : Double.NaN;
    }

    /**
     * Move the adapted luminance toward the target, over a frame.
     *
     * @param adapted The adapted log2 luminance.
     * @param target The metered log2 luminance.
     * @param seconds The frame's length.
     * @return The new adapted log2 luminance.
     */
    public static double adapt(double adapted, double target, double seconds) {
        final double speed = target > adapted ? SPEED_UP : SPEED_DOWN;
        return adapted + (target - adapted) * (1 - Math.exp(-seconds * speed));
    }

    /**
     * The exposure for an adapted luminance.
     *
     * @param adapted The adapted log2 luminance.
     * @return What the scene color is multiplied by, before compensation.
     */
    public static double exposure(double adapted) {
        final double gray = Math.log(MIDDLE_GRAY) / Math.log(2);
        final double stops = (gray - adapted) * ADAPTATION;
        return Math.clamp(Math.pow(2, stops), MIN_EXPOSURE, MAX_EXPOSURE);
    }

    /** Private constructor so this class is not instantiated. */
    private ExposureMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
