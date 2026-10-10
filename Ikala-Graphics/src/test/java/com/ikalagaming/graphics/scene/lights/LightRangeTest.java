package com.ikalagaming.graphics.scene.lights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How far lights reach, and how they fade out. */
class LightRangeTest {

    @Test
    void theDefaultRangeFollowsTheIntensity() {
        // Intensity 1 reaches the 10 m every light used to
        assertEquals(10, LightRange.defaultRange(1), 1e-5);
        assertEquals(20, LightRange.defaultRange(4), 1e-5);
        // A quarter of the brightness, half the reach
        assertEquals(5, LightRange.defaultRange(0.25f), 1e-5);
    }

    @Test
    void rangesStayWithinTheLimits() {
        assertEquals(LightRange.MIN_RANGE, LightRange.defaultRange(0));
        assertEquals(LightRange.MIN_RANGE, LightRange.defaultRange(-3));
        assertEquals(LightRange.MAX_RANGE, LightRange.defaultRange(1_000_000));
        assertEquals(LightRange.MIN_RANGE, LightRange.clamp(0.1f));
        assertEquals(LightRange.MAX_RANGE, LightRange.clamp(500));
        assertEquals(12, LightRange.clamp(12));
        assertEquals(LightRange.MIN_RANGE, LightRange.clamp(Float.NaN));
    }

    @Test
    void theFalloffReachesZeroAtTheRange() {
        final float range = 10;
        assertEquals(0, LightRange.falloff(range, range));
        assertEquals(0, LightRange.falloff(range * 2, range));
        assertTrue(LightRange.falloff(range * 0.99f, range) > 0);
        // Close in it is the plain inverse square
        assertEquals(1, LightRange.falloff(1, range), 1e-3);
    }

    @Test
    void theFalloffOnlyEverDecreases() {
        final float range = 20;
        final int steps = 1000;
        float previous = Float.MAX_VALUE;
        for (int i = 1; i <= steps; ++i) {
            float distance = range * i / steps;
            float value = LightRange.falloff(distance, range);
            assertTrue(value <= previous, "At " + distance);
            previous = value;
        }
    }
}
