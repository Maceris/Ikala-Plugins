package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the world's clock and where it puts the sun. */
class TestWorldTime {

    private static final double EPSILON = 1e-9;

    @Test
    void testDaysAndHours() {
        assertEquals(24_000, WorldTime.DAY_TICKS);
        assertEquals(11.0, WorldTime.hour(WorldTime.START_TICKS), EPSILON);
        assertEquals(0, WorldTime.day(WorldTime.START_TICKS));
        assertEquals(2, WorldTime.day(2 * WorldTime.DAY_TICKS + 5));
        assertEquals(-1, WorldTime.day(-1));
        assertEquals(WorldTime.DAY_TICKS - 1, WorldTime.timeOfDay(-1));
        assertEquals(12.5, WorldTime.hour(WorldTime.DAY_TICKS * 3 + 12_500), EPSILON);
    }

    @Test
    void testSunPath() {
        long hour = WorldTime.HOUR_TICKS;
        double[] sunrise = WorldTime.towardSun(6 * hour);
        assertEquals(1, sunrise[0], EPSILON, "Rises in the east");
        assertEquals(0, sunrise[1], EPSILON);

        double[] noon = WorldTime.towardSun(12 * hour);
        assertTrue(noon[1] > 0.8, "High at noon");
        assertTrue(noon[2] > 0, "Leans south");
        assertEquals(0, noon[0], EPSILON);

        double[] sunset = WorldTime.towardSun(18 * hour);
        assertEquals(-1, sunset[0], EPSILON, "Sets in the west");

        assertTrue(WorldTime.towardSun(0)[1] < 0, "Below the horizon at midnight");
        double[] start = WorldTime.towardSun(WorldTime.START_TICKS);
        assertEquals(
                1,
                Math.sqrt(start[0] * start[0] + start[1] * start[1] + start[2] * start[2]),
                EPSILON);
    }
}
