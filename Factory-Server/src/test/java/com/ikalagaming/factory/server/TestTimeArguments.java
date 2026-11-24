package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.WorldTime;

import org.junit.jupiter.api.Test;

/** Tests for the times {@code /time} reads. */
class TestTimeArguments {

    /** Names, clock times and hours all give a time of day. */
    @Test
    void testTimeOfDay() {
        assertEquals(12 * WorldTime.HOUR_TICKS, TimeArguments.timeOfDay("noon").getAsLong());
        assertEquals(0, TimeArguments.timeOfDay("Midnight").getAsLong());
        assertEquals(
                18 * WorldTime.HOUR_TICKS + WorldTime.HOUR_TICKS / 2,
                TimeArguments.timeOfDay("18:30").getAsLong());
        assertEquals(
                6 * WorldTime.HOUR_TICKS + WorldTime.HOUR_TICKS / 2,
                TimeArguments.timeOfDay("6.5").getAsLong());
        assertEquals(0, TimeArguments.timeOfDay("24").getAsLong());
        assertTrue(TimeArguments.timeOfDay("25").isEmpty());
        assertTrue(TimeArguments.timeOfDay("12:75").isEmpty());
        assertTrue(TimeArguments.timeOfDay("teatime").isEmpty());
    }

    /** Lengths are ticks, hours or days, and can go back. */
    @Test
    void testLength() {
        assertEquals(500, TimeArguments.length("500").getAsLong());
        assertEquals(3 * WorldTime.HOUR_TICKS, TimeArguments.length("3h").getAsLong());
        assertEquals(-2 * WorldTime.DAY_TICKS, TimeArguments.length("-2d").getAsLong());
        assertTrue(TimeArguments.length("soon").isEmpty());
        assertTrue(TimeArguments.length("NaNh").isEmpty());
    }

    /** Setting a time of day keeps the day, and times read as a day and a clock. */
    @Test
    void testSameDayAndDescribe() {
        final long now = 2 * WorldTime.DAY_TICKS + 5;
        final long evening = TimeArguments.sameDay(now, 18 * WorldTime.HOUR_TICKS);
        assertEquals(2, WorldTime.day(evening));
        assertEquals("Day 3, 18:00", TimeArguments.describe(evening));
        assertEquals("Day 1, 00:00", TimeArguments.describe(0));
    }
}
