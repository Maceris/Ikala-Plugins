package com.ikalagaming.graphics.scene.lights;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A directional light's direction as two angles, the way a person points at the sun: how far around
 * the vertical axis it is, 0 to 360 degrees, and how far above the horizon, -90 to 90 degrees.
 * Around 0 is toward -z, the way the default camera looks, and 90 toward +x. Directions here are
 * the way the light travels, as {@link DirectionalLight#getDirection()} has them, so the light
 * comes from the angles given.
 */
public final class LightAngles {

    /** Degrees in a full turn. */
    public static final double FULL_TURN = 360;

    /** The most a light inclines either way, in degrees: straight down or straight up. */
    public static final double MAX_INCLINE = 90;

    /** Hours in a day. */
    public static final double DAY_HOURS = 24;

    /** When the sun rises in {@link #atHour}, in hours. */
    private static final double SUNRISE = 6;

    /** How high the sun gets at noon in {@link #atHour}, in degrees. */
    public static final double NOON_INCLINE = 60;

    /** Not made. */
    private LightAngles() {}

    /**
     * The direction a light travels, coming from two angles.
     *
     * @param around Where around the vertical axis it comes from, in degrees.
     * @param incline How high above the horizon it comes from, in degrees.
     * @return The unit direction it travels.
     */
    public static Vector3f direction(double around, double incline) {
        final double turn = Math.toRadians(around);
        final double up = Math.toRadians(incline);
        return new Vector3f(
                (float) (-Math.sin(turn) * Math.cos(up)),
                (float) -Math.sin(up),
                (float) (Math.cos(turn) * Math.cos(up)));
    }

    /**
     * Where around the vertical axis a light comes from.
     *
     * @param direction The direction it travels.
     * @return Degrees from 0 to 360, 0 for a light straight up or down, or none.
     */
    public static double around(@NonNull Vector3fc direction) {
        if (direction.x() == 0 && direction.z() == 0) {
            return 0;
        }
        final double degrees = Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
        return degrees < 0 ? degrees + FULL_TURN : degrees;
    }

    /**
     * How high above the horizon a light comes from.
     *
     * @param direction The direction it travels.
     * @return Degrees from -90 to 90, 0 for none.
     */
    public static double incline(@NonNull Vector3fc direction) {
        final double length = direction.length();
        if (length == 0) {
            return 0;
        }
        return Math.toDegrees(Math.asin(Math.clamp(-direction.y() / length, -1, 1)));
    }

    /**
     * A simple day: the sun rises at 6, is {@value #NOON_INCLINE} degrees up at noon, sets at 18,
     * and is as far below the horizon at midnight, staying around where a light comes from. Used to
     * light a physical sky at a time apart from the sun, see {@link
     * com.ikalagaming.graphics.scene.PhysicalSky#getSunDirection()}.
     *
     * @param hour The hour, 0 to 24.
     * @param light The direction of the light whose place around the sun keeps.
     * @return The direction the sun's light travels at that hour.
     */
    public static Vector3f atHour(double hour, @NonNull Vector3fc light) {
        final double incline = NOON_INCLINE * Math.sin(Math.PI * 2 * (hour - SUNRISE) / DAY_HOURS);
        return direction(around(light), incline);
    }
}
