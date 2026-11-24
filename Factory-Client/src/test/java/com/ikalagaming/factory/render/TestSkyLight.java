package com.ikalagaming.factory.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.WorldTime;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Tests for the sky's lights by the world's clock. */
class TestSkyLight {

    private static final double EPSILON = 1e-6;

    /** Ticks at an hour of a day. */
    private static double at(int day, double hour) {
        return day * WorldTime.DAY_TICKS + hour * WorldTime.HOUR_TICKS;
    }

    /** The first midnight is a full moon, the moon opposite the sun. */
    @Test
    void testFullMoonOppositeSun() {
        final double ticks = at(0, 0);
        final double[] sun = WorldTime.towardSun(ticks);
        final double[] moon = SkyLight.towardMoon(ticks);
        for (int i = 0; i < 3; ++i) {
            assertEquals(-sun[i], moon[i], EPSILON);
        }
        assertEquals(SkyLight.FULL_MOON_LUX, SkyLight.moonLux(ticks), 0.01f);
    }

    /** Halfway through the month the moon is new, with the sun. */
    @Test
    void testNewMoonWithSun() {
        final double ticks = at(SkyLight.LUNAR_DAYS / 2, 0);
        final double[] sun = WorldTime.towardSun(ticks);
        final double[] moon = SkyLight.towardMoon(ticks);
        for (int i = 0; i < 3; ++i) {
            assertEquals(sun[i], moon[i], EPSILON);
        }
        assertTrue(SkyLight.moonLux(ticks) < 0.001f * SkyLight.FULL_MOON_LUX);
    }

    /** A quarter moon gives about a tenth of a full moon's light, not half. */
    @Test
    void testQuarterMoon() {
        final float quarter = SkyLight.moonLux(at(SkyLight.LUNAR_DAYS / 4, 0));
        assertTrue(quarter > 0.06f * SkyLight.FULL_MOON_LUX, "quarter " + quarter);
        assertTrue(quarter < 0.15f * SkyLight.FULL_MOON_LUX, "quarter " + quarter);
    }

    /** Twilight dims steadily from sunset until night. */
    @Test
    void testTwilight() {
        float previous = Float.MAX_VALUE;
        for (int degrees = 0; degrees <= 18; ++degrees) {
            final float lux = SkyLight.twilightLux((float) -Math.sin(Math.toRadians(degrees)));
            assertTrue(lux < previous, degrees + " degrees: " + lux);
            previous = lux;
        }
        assertEquals(0, SkyLight.twilightLux((float) -Math.sin(Math.toRadians(18))), 1e-9);
        assertEquals(0, SkyLight.twilightLux(-0.5f));
        // Civil twilight ends with a few lux
        final float civil = SkyLight.twilightLux((float) -Math.sin(Math.toRadians(6)));
        assertTrue(civil > 1 && civil < 10, "civil " + civil);
    }

    /** The moon only takes the light once the sun's is gone. */
    @Test
    void testHandover() {
        assertEquals(0, SkyLight.handover(SkyLight.FADE_START), EPSILON);
        assertEquals(0, SkyLight.handover(0.5f), EPSILON);
        assertEquals(1, SkyLight.handover(SkyLight.HANDOVER_END), EPSILON);
        assertEquals(0, SkyLight.fade(SkyLight.FADE_START), EPSILON);
    }

    /** By day nothing is pre-exposed, so the day looks as it did. */
    @Test
    void testNoPreExposureByDay() {
        assertEquals(1, SkyLight.preExposure(SkyLight.SUN_LUX), EPSILON);
        assertEquals(1, SkyLight.compensation(1), EPSILON);
        // A moonlit night is pre-exposed a lot, and shows darker than day all the same
        final float moonlit = SkyLight.FULL_MOON_LUX;
        final float preExposure = SkyLight.preExposure(moonlit);
        assertTrue(preExposure > 1000, "pre-exposure " + preExposure);
        assertTrue(SkyLight.compensation(preExposure) < 1);
    }

    /** The stars turn with the sun: the star sphere seen toward the sun stays the same. */
    @Test
    void testStarsTurnWithSun() {
        final Quaternionf rotation = new Quaternionf();
        Vector3f first = null;
        for (double hour = 0; hour < 24; hour += 3) {
            final double[] toward = WorldTime.towardSun(at(0, hour));
            SkyLight.starRotation(at(0, hour), rotation);
            final Vector3f onStars =
                    rotation.transform(
                            new Vector3f((float) toward[0], (float) toward[1], (float) toward[2]));
            if (first == null) {
                first = onStars;
            } else {
                assertEquals(0, first.distance(onStars), 1e-4, "hour " + hour);
            }
        }
    }
}
