package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.PhysicalSky;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The physical sky's mappings and steps as {@code atmosphere.glsl} has them; keep them in step with
 * it. Distances in kilometers.
 */
public final class AtmosphereMath {

    /** The ground's radius. */
    public static final double BOTTOM_RADIUS = 6360;

    /** The atmosphere's top radius. */
    public static final double TOP_RADIUS = 6460;

    /** Meters in a kilometer. */
    private static final double METERS_PER_KILOMETER = 1000;

    /** The least height above the ground the camera is taken to be at, in meters. */
    private static final double LEAST_HEIGHT = 1;

    /**
     * How far the sun moves before the environment follows it, in degrees, as the cached shadow
     * cascades do.
     */
    public static final double SUN_STEP_DEGREES = 0.25;

    /** How far the camera climbs before the environment follows it, in meters. */
    public static final double ALTITUDE_STEP = 50;

    /** Values in a pair of texture coordinates or parameters. */
    private static final int PAIR = 2;

    /**
     * The camera's distance from the planet's center.
     *
     * @param sky The physical sky.
     * @param cameraY The camera's world height, in meters.
     * @return The distance, in kilometers.
     */
    public static double cameraRadius(@NonNull PhysicalSky sky, double cameraY) {
        final double height = Math.max(cameraY - sky.getGroundHeight(), LEAST_HEIGHT);
        return BOTTOM_RADIUS + height / METERS_PER_KILOMETER;
    }

    /**
     * The unit direction toward the sun, from the way its light travels.
     *
     * @param lightDirection The way the light travels.
     * @return Toward the sun.
     */
    public static Vector3f towardSun(@NonNull Vector3fc lightDirection) {
        final Vector3f toward = new Vector3f(lightDirection).negate();
        return toward.lengthSquared() > 0 ? toward.normalize() : new Vector3f(0, 1, 0);
    }

    /**
     * The steps the sun's direction and the camera's height are in, so the environment is captured
     * again only once they move one.
     *
     * @param towardSun The unit direction toward the sun.
     * @param radius The camera's distance from the center, in kilometers.
     * @return The sun's elevation and azimuth steps and the altitude step.
     */
    public static int[] steps(@NonNull Vector3fc towardSun, double radius) {
        final double elevation = Math.toDegrees(Math.asin(Math.clamp(towardSun.y(), -1, 1)));
        final double azimuth = Math.toDegrees(Math.atan2(towardSun.z(), towardSun.x()));
        return new int[] {
            (int) Math.round(elevation / SUN_STEP_DEGREES),
            (int) Math.round(azimuth / SUN_STEP_DEGREES),
            (int) Math.round((radius - BOTTOM_RADIUS) * METERS_PER_KILOMETER / ALTITUDE_STEP)
        };
    }

    /**
     * How far along a ray it meets a sphere, the far side if it starts inside, or -1 for never.
     *
     * @param r The ray's start's distance from the center.
     * @param mu The cosine of its angle from straight up.
     * @param radius The sphere's radius.
     * @return The distance.
     */
    public static double distanceToSphere(double r, double mu, double radius) {
        final double discriminant = r * r * (mu * mu - 1) + radius * radius;
        return discriminant < 0 ? -1 : -r * mu + Math.sqrt(discriminant);
    }

    /**
     * Bruneton's mapping of a height and direction to the transmittance table.
     *
     * @param r The distance from the center.
     * @param mu The cosine of the angle from straight up.
     * @return u and v.
     */
    public static double[] transmittanceUv(double r, double mu) {
        final double h = Math.sqrt(TOP_RADIUS * TOP_RADIUS - BOTTOM_RADIUS * BOTTOM_RADIUS);
        final double rho = Math.sqrt(Math.max(r * r - BOTTOM_RADIUS * BOTTOM_RADIUS, 0));
        final double d = Math.max(distanceToSphere(r, mu, TOP_RADIUS), 0);
        final double dMin = TOP_RADIUS - r;
        final double dMax = rho + h;
        return new double[] {(d - dMin) / (dMax - dMin), rho / h};
    }

    /**
     * The height and direction of a point of the transmittance table, the inverse of {@link
     * #transmittanceUv}.
     *
     * @param u Across.
     * @param v Down.
     * @return r and mu.
     */
    public static double[] transmittanceParameters(double u, double v) {
        final double h = Math.sqrt(TOP_RADIUS * TOP_RADIUS - BOTTOM_RADIUS * BOTTOM_RADIUS);
        final double rho = h * v;
        final double r = Math.sqrt(rho * rho + BOTTOM_RADIUS * BOTTOM_RADIUS);
        final double dMin = TOP_RADIUS - r;
        final double dMax = rho + h;
        final double d = dMin + u * (dMax - dMin);
        final double mu = d == 0 ? 1 : (h * h - rho * rho - d * d) / (2 * r * d);
        return new double[] {r, Math.clamp(mu, -1, 1)};
    }

    /**
     * The angle of the horizon from straight up at a height.
     *
     * @param r The distance from the center.
     * @return The angle, in radians, past a right angle.
     */
    public static double zenithHorizonAngle(double r) {
        final double horizon = Math.sqrt(Math.max(r * r - BOTTOM_RADIUS * BOTTOM_RADIUS, 0));
        return Math.PI - Math.acos(horizon / r);
    }

    /**
     * Hillaire's mapping of a view direction to the sky view table, before keeping to texel
     * centers.
     *
     * @param r The camera's distance from the center.
     * @param viewZenithCos The cosine of the view's angle from straight up.
     * @param lightViewCos The cosine of the angle around from the sun.
     * @return u and v, from 0 to 1.
     */
    public static double[] skyViewUv(double r, double viewZenithCos, double lightViewCos) {
        final double horizonAngle = zenithHorizonAngle(r);
        final double beta = Math.PI - horizonAngle;
        final double viewZenith = Math.acos(Math.clamp(viewZenithCos, -1, 1));
        final double v;
        if (viewZenith < horizonAngle) {
            v = 0.5 * (1 - Math.sqrt(Math.max(1 - viewZenith / horizonAngle, 0)));
        } else {
            v = 0.5 + 0.5 * Math.sqrt(Math.max((viewZenith - horizonAngle) / beta, 0));
        }
        final double u = Math.sqrt(Math.clamp(-lightViewCos * 0.5 + 0.5, 0, 1));
        return new double[] {u, v};
    }

    /**
     * The view direction of a point of the sky view table, the inverse of {@link #skyViewUv}.
     *
     * @param u Across, from 0 to 1.
     * @param v Down, from 0 to 1.
     * @param r The camera's distance from the center.
     * @return The cosine of the angle from straight up and of the angle around from the sun.
     */
    public static double[] skyViewParameters(double u, double v, double r) {
        final double horizonAngle = zenithHorizonAngle(r);
        final double beta = Math.PI - horizonAngle;
        final double viewZenith;
        if (v < 0.5) {
            final double coord = 1 - 2 * v;
            viewZenith = horizonAngle * (1 - coord * coord);
        } else {
            final double coord = 2 * v - 1;
            viewZenith = horizonAngle + beta * coord * coord;
        }
        final double[] out = new double[PAIR];
        out[0] = Math.cos(viewZenith);
        out[1] = -(u * u * 2 - 1);
        return out;
    }

    /** Private constructor so this class is not instantiated. */
    private AtmosphereMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
