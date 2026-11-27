package com.ikalagaming.graphics.scene;

/**
 * The units light is given in, and how they reach the GPU. Light is physical: illuminance from the
 * sun, moon and stars in lux, luminous intensity of point and spot lights in candela, and luminance
 * of emitting surfaces, particles, media and the sky's glow in candela per square meter. Exposure
 * is in EV100, as a camera's.
 *
 * <p>Daylight is about 100,000 lux and a moonless night 0.002, more than the half floats the
 * renderer draws into hold. So every light is multiplied by the scene's pre-exposure as it is
 * uploaded ({@link Scene#getPreExposure()}), and the exposure divides it back out. At {@link
 * #REFERENCE_PRE_EXPOSURE}, full sun comes to {@value #REFERENCE_SUN} on the GPU, the scale the
 * shaders were tuned for; a scene lit only by the moon raises it so its light stays well inside
 * what half floats hold.
 */
public final class LightUnits {

    /** The ratio of a camera's saturation-based sensitivity, with its 1.2 headroom, for EV100. */
    private static final double SATURATION = 1.2;

    /** Full daylight, in lux. */
    public static final float DAYLIGHT_LUX = 100_000;

    /** What full sun comes to on the GPU at the reference pre-exposure. */
    public static final float REFERENCE_SUN = 3;

    /**
     * The pre-exposure the renderer was tuned at: full daylight comes to {@value #REFERENCE_SUN}.
     * Things baked to keep, like level of detail emission, are stored at this scale.
     */
    public static final float REFERENCE_PRE_EXPOSURE = REFERENCE_SUN / DAYLIGHT_LUX;

    /** The light below which the pre-exposure grows: a tenth of daylight, in lux. */
    private static final float PRE_EXPOSURE_LUX = DAYLIGHT_LUX / 10;

    /**
     * The EV100 that shows a scene at the reference pre-exposure as an exposure of 1 did before
     * light was physical: about 14.8, a sunny day's.
     */
    public static final float DEFAULT_EV100 = ev100(REFERENCE_PRE_EXPOSURE);

    /**
     * What a physical light value is multiplied by on its way to the screen, for an EV100: the
     * brightest luminance a camera at that exposure keeps is {@code 1.2 * 2^EV}.
     *
     * @param ev100 The exposure value at ISO 100.
     * @return The multiplier on luminance in cd/m^2.
     */
    public static float exposure(float ev100) {
        return (float) (1 / (SATURATION * Math.pow(2, ev100)));
    }

    /**
     * The EV100 whose {@link #exposure(float)} is a multiplier.
     *
     * @param multiplier The multiplier on luminance in cd/m^2, above 0.
     * @return The exposure value at ISO 100.
     */
    public static float ev100(float multiplier) {
        return (float) (Math.log(1 / (SATURATION * multiplier)) / Math.log(2));
    }

    /**
     * The pre-exposure for a scene lit by about this much light: the reference one in daylight, and
     * more in dimmer light, so a night keeps its precision on the GPU. A tenth of daylight or more
     * keeps the reference. The exposure shown doesn't change with it, see {@code
     * Scene.displayExposure}.
     *
     * @param lux About how much light reaches a lit surface.
     * @return The pre-exposure.
     */
    public static float preExposure(float lux) {
        return REFERENCE_PRE_EXPOSURE
                * Math.max(PRE_EXPOSURE_LUX / Math.max(lux, Float.MIN_NORMAL), 1);
    }

    /**
     * A value in the units the renderer was tuned at, where full sun is {@value #REFERENCE_SUN}, in
     * physical units. For bringing old numbers over.
     *
     * @param reference The value at the reference scale.
     * @return The physical value: lux, candela or cd/m^2, as the value was.
     */
    public static float physical(float reference) {
        return reference / REFERENCE_PRE_EXPOSURE;
    }

    /**
     * A physical value at the scale the renderer was tuned at, the inverse of {@link
     * #physical(float)}. For controls that still show the old numbers.
     *
     * @param physical The value in lux, candela or cd/m^2.
     * @return The value at the reference scale, where full sun is {@value #REFERENCE_SUN}.
     */
    public static float reference(float physical) {
        return physical * REFERENCE_PRE_EXPOSURE;
    }

    /** Not to be made. */
    private LightUnits() {}
}
