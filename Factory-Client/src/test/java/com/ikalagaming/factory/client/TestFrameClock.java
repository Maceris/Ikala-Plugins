package com.ikalagaming.factory.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.sim.Frames;

import org.junit.jupiter.api.Test;

/** Tests for the client's frame clock. */
class TestFrameClock {

    /** A millisecond in nanoseconds. */
    private static final long MS = 1_000_000;

    /**
     * A report with no echo, from a client a fixed distance ahead.
     *
     * @param clock The clock.
     * @param serverFrame The server's frame.
     * @param ahead How far ahead the client is.
     * @param buffered The server's buffer.
     * @param misses The server's misses.
     * @param now The time.
     * @return The resync frame, or none.
     */
    private static long report(
            FrameClock clock, long serverFrame, long ahead, int buffered, long misses, long now) {
        return clock.onServer(serverFrame, serverFrame + ahead, buffered, misses, 0, 0, now);
    }

    /** Frames come due at the normal rate until the server reports anything. */
    @Test
    void testPacing() {
        final FrameClock clock = new FrameClock();
        clock.start(0);
        assertEquals(1, clock.due(0, 8));
        assertEquals(0, clock.due(Frames.FRAME_NANOS - 1, 8));
        assertEquals(3, clock.due(4 * Frames.FRAME_NANOS - 1, 8));
        // Behind by much more than the cap: the rest stay due
        assertEquals(8, clock.due(100 * Frames.FRAME_NANOS, 8));
        assertEquals(8, clock.due(100 * Frames.FRAME_NANOS, 8));
    }

    /** A full buffer stretches frames, a starved one shrinks them, within the limit. */
    @Test
    void testDilation() {
        final FrameClock clock = new FrameClock();
        clock.start(0);
        for (int i = 0; i < 100; ++i) {
            assertEquals(FrameClock.NO_RESYNC, report(clock, i, 3, 6, 0, i * MS));
        }
        assertTrue(clock.getFrameNanos() > Frames.FRAME_NANOS, "a full buffer didn't slow down");
        assertTrue(clock.getFrameNanos() <= Frames.FRAME_NANOS * (1 + FrameClock.MAX_DILATION) + 1);
        for (int i = 100; i < 300; ++i) {
            report(clock, i, 3, 0, 0, i * MS);
        }
        assertTrue(clock.getFrameNanos() < Frames.FRAME_NANOS, "a starved buffer didn't speed up");
        assertTrue(clock.getFrameNanos() >= Frames.FRAME_NANOS * (1 - FrameClock.MAX_DILATION) - 1);
        assertTrue(clock.rate() > 1);
    }

    /** Misses raise the target buffer, and calm brings it back down. */
    @Test
    void testTarget() {
        final FrameClock clock = new FrameClock();
        clock.start(0);
        assertEquals(FrameClock.MIN_TARGET, clock.getTarget());
        report(clock, 1, 3, 1, 1, MS);
        report(clock, 2, 3, 1, 3, 2 * MS);
        assertEquals(FrameClock.MIN_TARGET + 2, clock.getTarget());
        for (int i = 0; i < 10; ++i) {
            report(clock, 3 + i, 3, 1, 3, 2 * MS + (i + 1) * (FrameClock.CALM_NANOS + MS));
        }
        assertEquals(FrameClock.MIN_TARGET, clock.getTarget());
    }

    /** The round trip is the echo's age less the time the server held it. */
    @Test
    void testRoundTrip() {
        final FrameClock clock = new FrameClock();
        clock.start(0);
        assertEquals(-1, clock.roundTripMillis());
        clock.onServer(50, 60, 1, 0, 1000 * MS, 30 * MS, 1130 * MS);
        assertEquals(100, clock.roundTripMillis(), 0.001);
        // 100 ms is 6.25 frames, so 7, plus the target
        assertEquals(7 + FrameClock.MIN_TARGET, clock.lead());
    }

    /** A client short of its lead, or far past it, jumps to the lead. */
    @Test
    void testResync() {
        final FrameClock clock = new FrameClock();
        clock.start(0);
        clock.onServer(50, 60, 1, 0, 1000 * MS, 0, 1100 * MS);
        final long lead = clock.lead();
        // Behind the server
        assertEquals(500 + lead, report(clock, 500, -20, 1, 0, 1200 * MS));
        // Short of the lead
        assertEquals(
                600 + lead,
                report(clock, 600, lead - FrameClock.RESYNC_BEHIND - 1, 1, 0, 1300 * MS));
        // Within it
        assertEquals(FrameClock.NO_RESYNC, report(clock, 700, lead, 1, 0, 1400 * MS));
        // Far past it
        assertEquals(
                800 + lead,
                report(clock, 800, lead + FrameClock.RESYNC_AHEAD + 1, 1, 0, 1500 * MS));
        assertEquals(3, clock.getResyncs());
        assertEquals(Frames.FRAME_NANOS, clock.getFrameNanos());
    }
}
