package com.ikalagaming.graphics.scene.lights;

/**
 * How far point and spot lights reach.
 *
 * <p>A light falls off with the inverse square of the distance, windowed so that it reaches exactly
 * zero at its range: {@code intensity / d^2 · clamp(1 − (d / range)^4, 0, 1)}. Without a range
 * given, a light reaches as far as its un-windowed light stays above {@link #CUTOFF}, which is
 * {@code sqrt(intensity / CUTOFF)}. Every range is kept between {@link #MIN_RANGE} and {@link
 * #MAX_RANGE} so a light only ever touches a bounded part of the view.
 */
public final class LightRange {

    /**
     * The light level where a light's default range ends. With this, a light of intensity 1 reaches
     * 10 m, the distance every light reached before ranges were per light, and one of intensity 4
     * reaches 20 m.
     */
    public static final float CUTOFF = 0.01f;

    /** The shortest a light can reach, in meters: one block. */
    public static final float MIN_RANGE = 1;

    /**
     * The farthest a light can reach, in meters: four 16 m sections, a third of the default 12
     * section view distance.
     */
    public static final float MAX_RANGE = 4.0f * 16;

    /**
     * The range of a light given only its intensity.
     *
     * @param intensity The light's intensity.
     * @return How far it reaches, in meters.
     */
    public static float defaultRange(float intensity) {
        return clamp((float) Math.sqrt(Math.max(intensity, 0) / CUTOFF));
    }

    /**
     * Keep a range within the limits.
     *
     * @param range The range, in meters.
     * @return The range clamped to [{@link #MIN_RANGE}, {@link #MAX_RANGE}].
     */
    public static float clamp(float range) {
        if (Float.isNaN(range)) {
            return MIN_RANGE;
        }
        return Math.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    /**
     * How much of a light's intensity arrives at a distance, as lights.frag works it out.
     *
     * @param distance The distance from the light, in meters.
     * @param range The light's range, in meters.
     * @return The scale on the light's intensity.
     */
    public static float falloff(float distance, float range) {
        final float ratio = distance / range;
        final float r2 = ratio * ratio;
        final float window = Math.clamp(1 - r2 * r2, 0.0f, 1.0f);
        return window / (distance * distance);
    }

    private LightRange() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
