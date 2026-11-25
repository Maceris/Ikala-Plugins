package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for the world's clock: which world frames and ticks each command frame runs. */
class TestWorldClock {

    /**
     * Run command frames.
     *
     * @param start The clock before.
     * @param commandFrames How many command frames.
     * @param worldFramesEach Filled with how many world frames each ran.
     * @return The clock after.
     */
    private static WorldClock run(
            WorldClock start, int commandFrames, List<Integer> worldFramesEach) {
        WorldClock clock = start;
        for (int i = 0; i < commandFrames; ++i) {
            final int due = clock.framesDue();
            clock = clock.afterCommandFrame();
            for (int w = 0; w < due; ++w) {
                clock = clock.afterWorldFrame();
            }
            worldFramesEach.add(due);
        }
        return clock;
    }

    /**
     * At the normal rate, one world frame runs each command frame, and 8 ticks run in every 25,
     * every third or fourth world frame: exactly 20 a second of 62.5 frames.
     */
    @Test
    void testNormalRate() {
        final List<Integer> each = new ArrayList<>();
        WorldClock clock = WorldClock.START;
        final List<Long> tickFrames = new ArrayList<>();
        for (int i = 0; i < 125; ++i) {
            final List<Integer> one = new ArrayList<>();
            final long ticksBefore = clock.worldTicks();
            clock = run(clock, 1, one);
            each.addAll(one);
            if (clock.worldTicks() > ticksBefore) {
                tickFrames.add(clock.worldFrames());
            }
        }
        assertTrue(each.stream().allMatch(n -> n == 1), each.toString());
        // Two seconds: 125 world frames, 40 ticks
        assertEquals(125, clock.worldFrames());
        assertEquals(40, clock.worldTicks());
        assertEquals(List.of(4L, 7L, 10L, 13L, 16L, 19L, 22L, 25L), tickFrames.subList(0, 8));
        for (int i = 1; i < tickFrames.size(); ++i) {
            final long gap = tickFrames.get(i) - tickFrames.get(i - 1);
            assertTrue(gap == 3 || gap == 4, "gap " + gap);
        }
    }

    /** Faster and slower rates run more or fewer world frames, never a different step. */
    @Test
    void testRates() {
        final List<Integer> fast = new ArrayList<>();
        WorldClock clock = run(WorldClock.START.withRate(40), 10, fast);
        assertEquals(List.of(2, 2, 2, 2, 2, 2, 2, 2, 2, 2), fast);
        assertEquals(20, clock.worldFrames());

        final List<Integer> half = new ArrayList<>();
        run(WorldClock.START.withRate(10), 6, half);
        assertEquals(List.of(0, 1, 0, 1, 0, 1), half);

        // A quarter speed runs a world frame every 4 command frames
        final List<Integer> quarter = new ArrayList<>();
        clock = run(WorldClock.START.withRate(5), 100, quarter);
        assertEquals(25, clock.worldFrames());
        assertEquals(8, clock.worldTicks());
    }

    /** A frozen world runs no world frames; stepping runs one a command frame until done. */
    @Test
    void testFreezeAndStep() {
        final List<Integer> frozen = new ArrayList<>();
        WorldClock clock = run(WorldClock.START.withFrozen(true), 10, frozen);
        assertTrue(frozen.stream().allMatch(n -> n == 0));
        assertEquals(0, clock.worldFrames());

        // Two ticks take 7 world frames from the start: ticks at world frames 4 and 7
        final List<Integer> stepping = new ArrayList<>();
        clock = run(clock.stepping(2), 10, stepping);
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1, 0, 0, 0), stepping);
        assertEquals(2, clock.worldTicks());
        assertTrue(clock.frozen());
        assertEquals(0, clock.effectiveRate());

        final List<Integer> after = new ArrayList<>();
        clock = run(clock.withFrozen(false), 3, after);
        assertEquals(List.of(1, 1, 1), after);
        assertEquals(WorldClock.NORMAL_RATE, clock.effectiveRate());
    }

    /** Sprinting leaves the schedule to the server, and ends after its ticks. */
    @Test
    void testSprint() {
        WorldClock clock = WorldClock.START.sprinting(3);
        assertTrue(clock.isSprinting());
        assertEquals(0, clock.framesDue());
        assertEquals(0, clock.effectiveRate());
        int worldFrames = 0;
        while (clock.isSprinting()) {
            clock = clock.afterWorldFrame();
            ++worldFrames;
        }
        // The third tick runs in world frame 10
        assertEquals(10, worldFrames);
        assertEquals(3, clock.worldTicks());
        assertFalse(clock.isSprinting());
        assertEquals(1, clock.framesDue());
    }

    /** Rates outside 1 to the maximum are refused. */
    @Test
    void testBadRates() {
        assertThrows(IllegalArgumentException.class, () -> WorldClock.START.withRate(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldClock.START.withRate(WorldClock.MAX_RATE + 1));
    }
}
