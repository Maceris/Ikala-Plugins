package com.ikalagaming.factory.render;

import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.scene.LightUnits;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.ExposureMath;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lights the world by its clock: the physical sky in place of a skybox, a sun that moves with the
 * time of day, a moon that goes through its phases, the night sky's own glow and stars, and auto
 * exposure so the eye adapts from noon to midnight. Main thread only.
 *
 * <p>Light is physical, as Graphics takes it (see {@link LightUnits}): the sun gives {@value
 * #SUN_LUX} lux, a full moon {@value #FULL_MOON_LUX} lux, twilight follows measured sky illuminance
 * by the sun's depth below the horizon, and a moonless night gets {@value #NIGHT_LUX} lux of
 * starlight and airglow. The scene has one directional light, which casts the cascaded shadows: it
 * is the sun by day, and is handed to the moon once the sun has set.
 *
 * <p>Night is millions of times darker than day, below what half floats hold well, so the scene's
 * pre-exposure rises at dusk and the exposure compensation takes that back out, less the part the
 * eye would have adapted to anyway, see {@link #compensation}. Graphics pre-exposes every light,
 * glowing blocks included, so they keep their brightness against the night.
 */
public final class SkyLight {

    /** The sun's color. The physical sky reddens it near the horizon. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 1, 1);

    /** The sun's illuminance when it is well above the horizon, in lux. */
    static final float SUN_LUX = LightUnits.DAYLIGHT_LUX;

    /** How high the sun is, as the up part of its direction, when its light starts to show. */
    static final float FADE_START = -0.02f;

    /** How high the sun is when its light is full. */
    static final float FADE_END = 0.06f;

    /**
     * How high the sun is, below the horizon, once the moon has the directional light fully: from
     * when the sun's light is gone down to here the moon's fades in, so its shadows don't pop.
     */
    static final float HANDOVER_END = -0.08f;

    /** A full moon's illuminance well above the horizon, in lux. */
    static final float FULL_MOON_LUX = 0.25f;

    /** The moonlight's color: sunlight reflected off gray-brown regolith, a little redder. */
    private static final Vector3f MOON_COLOR = luminanceOne(new Vector3f(1, 0.94f, 0.86f));

    /** Days from one full moon to the next. */
    static final int LUNAR_DAYS = 8;

    /** The night sky's own light on a moonless night, airglow and starlight, in lux. */
    static final float NIGHT_LUX = 0.002f;

    /** The night glow's color, a little blue, normalized to luminance 1. */
    private static final Vector3f NIGHT_COLOR = luminanceOne(new Vector3f(0.9f, 0.95f, 1.1f));

    /** The twilight sky's color, blue, as ozone takes the orange out, luminance 1. */
    private static final Vector3f TWILIGHT_COLOR = luminanceOne(new Vector3f(0.7f, 0.85f, 1.25f));

    /**
     * The clear sky's illuminance on the ground after sunset, as log10 lux, every {@value
     * #TWILIGHT_STEP_DEGREES} degrees from the sun on the horizon down to 18 degrees below, where
     * night begins. After the published civil, nautical and astronomical twilight curves.
     */
    private static final float[] TWILIGHT_LOG_LUX = {
        2.6f, 2.0f, 1.2f, 0.5f, -0.4f, -1.3f, -2.1f, -2.6f, -3.0f, Float.NEGATIVE_INFINITY
    };

    /** The degrees between entries of {@link #TWILIGHT_LOG_LUX}. */
    static final float TWILIGHT_STEP_DEGREES = 2;

    /** The illuminance of a magnitude 0 star above the atmosphere, in lux. */
    static final float MAGNITUDE_ZERO_LUX = 2.54e-6f;

    /**
     * The sky's light, in lux, below which the pre-exposure rises: a sun this weak is a few degrees
     * above the horizon, so the day is drawn at the reference pre-exposure.
     */
    static final float PRE_EXPOSURE_LUX = SUN_LUX / 10;

    /** How far the sun's path leans, about which the sky turns. */
    private static final double TILT = Math.toRadians(WorldTime.SUN_TILT_DEGREES);

    /** The scene being lit. */
    private final Scene scene;

    /** The sky, changed in place as time passes. */
    private final PhysicalSky sky = new PhysicalSky();

    /** The sun or the moon, changed in place as time passes. */
    private final DirectionalLight light =
            new DirectionalLight(new Vector3f(SUN_COLOR), new Vector3f(0, -1, 0), 0);

    /** What the scene had before, to put back. */
    private final PhysicalSky previousSky;

    private final DirectionalLight previousSun;
    private final boolean previousAutoExposure;
    private final float previousCompensation;
    private final float previousPreExposure;

    /**
     * Light a scene by the world's clock.
     *
     * @param scene The scene.
     */
    public SkyLight(@NonNull Scene scene) {
        this.scene = scene;
        previousSky = scene.getPhysicalSky();
        previousSun = scene.getSceneLights().getDirLight();
        previousAutoExposure = scene.isAutoExposure();
        previousCompensation = scene.getExposureCompensation();
        previousPreExposure = scene.getPreExposure();
        scene.setPhysicalSky(sky);
        scene.getSceneLights().setDirLight(light);
        scene.setAutoExposure(true);
    }

    /**
     * Move the sun, the moon and the stars to a time.
     *
     * @param ticks The world's clock, in ticks with a fraction.
     */
    public void update(double ticks) {
        final double[] towardSun = WorldTime.towardSun(ticks);
        final float sunUp = (float) towardSun[1];
        final float sunLight = SUN_LUX * fade(sunUp);

        final double[] towardMoon = towardMoon(ticks);
        final float moonLight = moonLux(ticks) * fade((float) towardMoon[1]) * handover(sunUp);

        // The glow: twilight while the sun's own sky fades, then the night's airglow and stars
        final float twilight = twilightLux(sunUp) * (1 - fade(sunUp));

        final float preExposure = preExposure(sunLight + moonLight + twilight + NIGHT_LUX);

        final double[] toward;
        if (sunLight > 0) {
            toward = towardSun;
            light.getColor().set(SUN_COLOR);
            light.setIntensity(sunLight);
        } else {
            toward = towardMoon;
            light.getColor().set(MOON_COLOR);
            light.setIntensity(moonLight);
        }
        // The light travels away from the sun or moon
        light.getDirection().set((float) -toward[0], (float) -toward[1], (float) -toward[2]);

        // Uniform luminance L lights the ground with pi L
        final Vector3f glow = sky.getGlow();
        glow.set(TWILIGHT_COLOR).mul(twilight);
        glow.fma(NIGHT_LUX, NIGHT_COLOR).div((float) Math.PI);
        sky.setStarIlluminance(MAGNITUDE_ZERO_LUX);
        starRotation(ticks, sky.getStarRotation());

        scene.setPreExposure(LightUnits.REFERENCE_PRE_EXPOSURE * preExposure);
        scene.setExposureCompensation(
                previousCompensation + (float) (Math.log(compensation(preExposure)) / Math.log(2)));
    }

    /**
     * How much of the sun's light shows at a height above the horizon.
     *
     * @param up The up part of the direction toward the sun.
     * @return From 0 below the horizon to 1 once it is clear of it.
     */
    static float fade(float up) {
        final float t = Math.clamp((up - FADE_START) / (FADE_END - FADE_START), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /**
     * How much of the moon's light the directional light carries as the sun sets.
     *
     * @param sunUp The up part of the direction toward the sun.
     * @return 0 while the sun lights the scene, up to 1 once it is well below the horizon.
     */
    static float handover(float sunUp) {
        final float t = Math.clamp((FADE_START - sunUp) / (FADE_START - HANDOVER_END), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /**
     * How far through its month the moon is: 0 at full moon, 0.5 at new moon. New worlds start at
     * full moon.
     *
     * @param ticks The time.
     * @return The fraction, from 0 up to 1.
     */
    static double lunarFraction(double ticks) {
        final double month = (double) LUNAR_DAYS * WorldTime.DAY_TICKS;
        final double fraction = ticks / month;
        return fraction - Math.floor(fraction);
    }

    /**
     * Where the moon is: a unit vector toward it. It follows the sun's path, opposite the sun at
     * full moon and falling behind it day by day, so a waning moon rises later each night.
     *
     * @param ticks The time.
     * @return The direction as x, y and z.
     */
    static double[] towardMoon(double ticks) {
        // How far behind the sun it is, half a turn at full moon
        final double behind = 0.5 + lunarFraction(ticks);
        return WorldTime.towardSun(ticks - behind * WorldTime.DAY_TICKS);
    }

    /**
     * The moon's illuminance well above the horizon, by its phase: Allen's magnitudes by phase
     * angle, which fall off much faster than the lit fraction, a quarter moon a tenth of a full
     * one.
     *
     * @param ticks The time.
     * @return The illuminance in lux.
     */
    static float moonLux(double ticks) {
        // The phase angle: 0 at full moon, 180 at new moon
        final double phase = 360 * Math.min(lunarFraction(ticks), 1 - lunarFraction(ticks));
        final double magnitudes = 0.026 * phase + 4e-9 * Math.pow(phase, 4);
        return (float) (FULL_MOON_LUX * Math.pow(10, -0.4 * magnitudes));
    }

    /**
     * The clear twilight sky's illuminance with the sun below the horizon.
     *
     * @param sunUp The up part of the direction toward the sun.
     * @return The illuminance in lux, 0 for night.
     */
    static float twilightLux(float sunUp) {
        final double degrees = -Math.toDegrees(Math.asin(Math.clamp(sunUp, -1f, 1f)));
        final double at = Math.max(degrees, 0) / TWILIGHT_STEP_DEGREES;
        final int below = (int) Math.floor(at);
        if (below >= TWILIGHT_LOG_LUX.length - 1) {
            return 0;
        }
        final double t = at - below;
        final float low = TWILIGHT_LOG_LUX[below];
        final float high = TWILIGHT_LOG_LUX[below + 1];
        if (Float.isInfinite(high)) {
            // The last step fades out rather than toward minus infinity
            return (float) (Math.pow(10, low) * (1 - t));
        }
        return (float) Math.pow(10, low + (high - low) * t);
    }

    /**
     * Turn the stars to a time: once a day about the celestial pole, the axis of the sun's path,
     * with the sun.
     *
     * @param ticks The time.
     * @param rotation Where to put the rotation from world directions to the stars'.
     */
    static void starRotation(double ticks, @NonNull Quaternionf rotation) {
        final double turn = 2 * Math.PI * WorldTime.hour(ticks) / WorldTime.DAY_HOURS;
        // Perpendicular to the sun's path, which runs through east and the tilted zenith
        rotation.identity()
                .rotateAxis((float) -turn, 0, (float) -Math.sin(TILT), (float) Math.cos(TILT));
    }

    /**
     * How much brighter than the reference pre-exposure the scene's lights are drawn.
     *
     * @param lux The sky's light, in lux.
     * @return 1 by day, more as it gets dark.
     */
    static float preExposure(float lux) {
        return Math.max(PRE_EXPOSURE_LUX / Math.max(lux, Float.MIN_NORMAL), 1);
    }

    /**
     * What the exposure is multiplied by to take a pre-exposure out. The automatic exposure only
     * takes {@link ExposureMath#ADAPTATION} of a change in brightness away, so a scene k times
     * brighter shows k^(1 - adaptation) times brighter, which this undoes: the night shows as an
     * eye partly adapted to it would see it.
     *
     * @param preExposure The pre-exposure.
     * @return The factor.
     */
    static float compensation(float preExposure) {
        return (float) Math.pow(preExposure, -(1 - ExposureMath.ADAPTATION));
    }

    /**
     * Scale a linear color to Rec. 709 luminance 1.
     *
     * @param color The color, changed.
     * @return The color.
     */
    private static Vector3f luminanceOne(@NonNull Vector3f color) {
        return color.div(0.2126f * color.x + 0.7152f * color.y + 0.0722f * color.z);
    }

    /** Put the scene's light back as it was. */
    public void restore() {
        scene.setPhysicalSky(previousSky);
        scene.getSceneLights().setDirLight(previousSun);
        scene.setAutoExposure(previousAutoExposure);
        scene.setExposureCompensation(previousCompensation);
        scene.setPreExposure(previousPreExposure);
    }
}
