package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Tests for {@link EnvironmentMath}. */
class EnvironmentMathTest {

    /** The harmonics level's size in the shader. */
    private static final int SIZE = Environment.SIZE >> Environment.HARMONICS_LEVEL;

    private static void assertDirection(Vector3d expected, Vector3d actual) {
        assertEquals(0, expected.distance(actual), 1e-12, expected + " against " + actual);
    }

    @Test
    void aSkyOfOneColorLightsEveryWayAlike() {
        final double[] c = EnvironmentMath.project(d -> 0.4, SIZE);
        assertEquals(
                0.4,
                EnvironmentMath.average(c),
                1e-3,
                "The texels' solid angles sum to 4 pi, within the midpoint rule");
        for (Vector3d n :
                new Vector3d[] {
                    new Vector3d(0, 1, 0),
                    new Vector3d(0, -1, 0),
                    new Vector3d(1, 1, 1).normalize(),
                    new Vector3d(-0.3, 0.2, 0.9).normalize()
                }) {
            assertEquals(0.4, EnvironmentMath.diffuse(c, n), 1e-3, "Facing " + n);
        }
    }

    @Test
    void aSkyOverheadLightsWhatFacesUp() {
        // Radiance the cosine of the angle from straight up, nothing below the horizon
        final double[] c = EnvironmentMath.project(d -> Math.max(d.y(), 0), SIZE);
        // Facing up: the integral of cos^2 over the hemisphere, 2 pi / 3, over pi
        assertEquals(2.0 / 3.0, EnvironmentMath.diffuse(c, new Vector3d(0, 1, 0)), 0.02);
        // Facing down it sees none of it; the second band leaves a little
        assertEquals(0, EnvironmentMath.diffuse(c, new Vector3d(0, -1, 0)), 0.03);
        // Facing sideways: the integral of y x over the quarter where both are positive, 2/3,
        // over pi
        assertEquals(
                2 / (3 * Math.PI),
                EnvironmentMath.diffuse(c, new Vector3d(1, 0, 0)),
                0.03,
                "Facing sideways");
        assertEquals(0.25, EnvironmentMath.average(c), 1e-3, "Half the sphere, cosine on average");
    }

    @Test
    void equirectangularSkiesLookForwardFromTheMiddle() {
        final double e = 1e-12;
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new double[] {0.5, 0.5}, EnvironmentMath.equirectUv(new Vector3d(0, 0, -1)), e);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new double[] {0.75, 0.5}, EnvironmentMath.equirectUv(new Vector3d(1, 0, 0)), e);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new double[] {0.25, 0.5}, EnvironmentMath.equirectUv(new Vector3d(-1, 0, 0)), e);
        assertEquals(0, EnvironmentMath.equirectUv(new Vector3d(0, 1, 0))[1], e, "Up is the top");
        assertEquals(1, EnvironmentMath.equirectUv(new Vector3d(0, -1, 0))[1], e);
        // Behind is the seam, at either side
        final double behind = EnvironmentMath.equirectUv(new Vector3d(0.001, 0, 1).normalize())[0];
        assertEquals(1, behind, 1e-3);
    }

    @Test
    void cubeDirectionsMatchVulkansFaces() {
        // The middle of each face points along its axis
        assertDirection(new Vector3d(1, 0, 0), EnvironmentMath.cubeDirection(0, 0, 0));
        assertDirection(new Vector3d(-1, 0, 0), EnvironmentMath.cubeDirection(1, 0, 0));
        assertDirection(new Vector3d(0, 1, 0), EnvironmentMath.cubeDirection(2, 0, 0));
        assertDirection(new Vector3d(0, -1, 0), EnvironmentMath.cubeDirection(3, 0, 0));
        assertDirection(new Vector3d(0, 0, 1), EnvironmentMath.cubeDirection(4, 0, 0));
        assertDirection(new Vector3d(0, 0, -1), EnvironmentMath.cubeDirection(5, 0, 0));
        // +x: s runs toward -z and t toward -y, as the spec's major axis table has it
        assertDirection(new Vector3d(1, -1, -1), EnvironmentMath.cubeDirection(0, 1, 1));
        // +y: s toward +x, t toward +z
        assertDirection(new Vector3d(1, 1, 1), EnvironmentMath.cubeDirection(2, 1, 1));
    }
}
