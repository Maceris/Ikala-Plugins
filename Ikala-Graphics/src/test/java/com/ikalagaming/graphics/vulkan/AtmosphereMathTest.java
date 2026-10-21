package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.PhysicalSky;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Tests for {@link AtmosphereMath}. */
class AtmosphereMathTest {

    private static final double EPSILON = 1e-6;

    @Test
    void transmittanceMappingRoundTrips() {
        for (double v : new double[] {0.01, 0.2, 0.5, 0.9, 1}) {
            for (double u : new double[] {0, 0.1, 0.5, 0.99}) {
                final double[] rMu = AtmosphereMath.transmittanceParameters(u, v);
                final double[] uv = AtmosphereMath.transmittanceUv(rMu[0], rMu[1]);
                assertArrayEquals(new double[] {u, v}, uv, 1e-5, "At " + u + ", " + v);
            }
        }
        // Straight up from the ground is the first column, along the bottom row
        final double[] up = AtmosphereMath.transmittanceUv(AtmosphereMath.BOTTOM_RADIUS, 1);
        assertArrayEquals(new double[] {0, 0}, up, EPSILON);
    }

    @Test
    void skyViewMappingRoundTrips() {
        final double r = AtmosphereMath.BOTTOM_RADIUS + 0.2;
        for (double v : new double[] {0, 0.1, 0.3, 0.49, 0.51, 0.75, 1}) {
            for (double u : new double[] {0, 0.25, 0.5, 1}) {
                final double[] cosines = AtmosphereMath.skyViewParameters(u, v, r);
                final double[] uv = AtmosphereMath.skyViewUv(r, cosines[0], cosines[1]);
                assertArrayEquals(new double[] {u, v}, uv, 1e-6, "At " + u + ", " + v);
            }
        }
        // The middle row is the horizon, straight up the top and toward the sun the left
        final double horizon = Math.cos(AtmosphereMath.zenithHorizonAngle(r));
        assertEquals(0.5, AtmosphereMath.skyViewUv(r, horizon, 1)[1], EPSILON);
        assertEquals(0, AtmosphereMath.skyViewUv(r, 1, 1)[1], EPSILON);
        assertEquals(0, AtmosphereMath.skyViewUv(r, 1, 1)[0], EPSILON);
        assertTrue(horizon < 0, "From above the ground the horizon dips below level");
    }

    @Test
    void sunlightThinsThroughTheAtmosphere() {
        final double ground = AtmosphereMath.BOTTOM_RADIUS;
        // Straight up: each medium's density integrated over the height, which for air and haze
        // is their scale height, and for ozone's 30 km tent of peak 1 is 15 km
        final double[] air = {5.802e-3, 13.558e-3, 33.1e-3};
        final double[] ozone = {0.650e-3, 1.881e-3, 0.085e-3};
        final Vector3f up = AtmosphereMath.transmittance(ground, 1);
        final float[] got = {up.x, up.y, up.z};
        for (int c = 0; c < 3; ++c) {
            final double depth = air[c] * 8 + 4.440e-3 * 1.2 + ozone[c] * 15;
            assertEquals(Math.exp(-depth), got[c], 0.002, "Channel " + c);
        }
        assertTrue(up.z < up.y && up.y < up.x, "Blue is scattered most, so the sun is warm");
        // Low, through much more air, it reddens
        final Vector3f low =
                AtmosphereMath.transmittance(ground + 0.001, Math.sin(Math.toRadians(2)));
        assertTrue(low.x > 0 && low.z / low.x < up.z / up.x, "Redder near the horizon");
        // Below the horizon the planet is in the way
        assertEquals(new Vector3f(), AtmosphereMath.transmittance(ground + 0.001, -0.2));
    }

    @Test
    void theCameraStandsOnTheGround() {
        final PhysicalSky sky = new PhysicalSky();
        sky.setGroundHeight(64);
        assertEquals(
                AtmosphereMath.BOTTOM_RADIUS + 0.136,
                AtmosphereMath.cameraRadius(sky, 200),
                EPSILON);
        assertEquals(
                AtmosphereMath.BOTTOM_RADIUS + 0.001,
                AtmosphereMath.cameraRadius(sky, -500),
                EPSILON,
                "Never below the ground");
    }

    private static Vector3f toward(double elevation, double azimuth) {
        final double e = Math.toRadians(elevation);
        final double a = Math.toRadians(azimuth);
        return new Vector3f(
                (float) (Math.cos(e) * Math.cos(a)),
                (float) Math.sin(e),
                (float) (Math.cos(e) * Math.sin(a)));
    }

    @Test
    void theEnvironmentFollowsTheSunInSteps() {
        final Vector3f down = AtmosphereMath.towardSun(new Vector3f(0.3f, -0.85f, 0.4f));
        assertEquals(1, down.length(), 1e-6);
        assertTrue(down.y > 0, "Toward the sun is up when its light travels down");
        // At the middle of a step: 30 degrees up, 40 around
        final Vector3f sun = toward(30, 40);
        final double r = AtmosphereMath.BOTTOM_RADIUS + 0.1;
        final int[] steps = AtmosphereMath.steps(sun, r);
        assertArrayEquals(new int[] {120, 160, 2}, steps);
        assertArrayEquals(steps, AtmosphereMath.steps(toward(30.05, 40.05), r), "Within a step");
        final Vector3f moved = toward(31, 40);
        assertNotEquals(steps[0], AtmosphereMath.steps(moved, r)[0], "A degree is four steps");
        assertNotEquals(
                steps[2], AtmosphereMath.steps(sun, r + 0.1)[2], "A hundred meters up is two");
    }
}
