package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.sim.Frames;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Tests for the server's two-clock loop. */
@Slf4j
class TestServerLoop {

    /**
     * Frames and ticks run when due, each on its own clock, and the next deadline is the earlier.
     */
    @Test
    void testClocks() {
        final List<String> steps = new ArrayList<>();
        final ServerLoop loop =
                new ServerLoop(() -> steps.add("f"), () -> steps.add("t"), 0, false);
        assertEquals(Frames.FRAME_NANOS, loop.runDue(0));
        assertEquals(List.of("f", "t"), steps);
        steps.clear();
        // 48 ms: frames at 16, 32 and 48 are due; the tick at 50 is not
        assertEquals(50_000_000L, loop.runDue(48_000_000L));
        assertEquals(List.of("f", "f", "f"), steps);
        steps.clear();
        assertEquals(64_000_000L, loop.runDue(50_000_000L));
        assertEquals(List.of("t"), steps);
        assertEquals(0, loop.getDroppedFrames());
    }

    /** A loop far behind runs a few steps to catch up, then drops the rest. */
    @Test
    void testFallingBehind() {
        final int[] frames = {0};
        final int[] ticks = {0};
        final ServerLoop loop = new ServerLoop(() -> ++frames[0], () -> ++ticks[0], 0, false);
        final long now = TimeUnit.SECONDS.toNanos(1);
        final long next = loop.runDue(now);
        assertEquals(ServerLoop.MAX_CATCH_UP, frames[0]);
        assertEquals(ServerLoop.MAX_CATCH_UP, ticks[0]);
        // 1 s is 62.5 frames: 0..992 ms are 63 frames, 5 run, 58 dropped
        assertEquals(58, loop.getDroppedFrames());
        assertEquals(16, loop.getDroppedTicks());
        assertTrue(next > now && next - now <= Frames.FRAME_NANOS, "next " + next);
    }

    /**
     * Run for real for a second, parking only and precise: the rates are right, and frames start
     * well within a frame of their deadlines.
     *
     * @param precise Whether the loop yields until each deadline.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRealTime(boolean precise) throws InterruptedException {
        final long start = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20);
        final long[] frameTimes = new long[200];
        final int[] frames = {0};
        final int[] ticks = {0};
        final ServerLoop loop =
                new ServerLoop(
                        () -> {
                            if (frames[0] < frameTimes.length) {
                                frameTimes[frames[0]] = System.nanoTime();
                            }
                            ++frames[0];
                        },
                        () -> ++ticks[0],
                        start,
                        precise);
        final Thread thread = new Thread(loop, "Test loop");
        thread.start();
        TimeUnit.MILLISECONDS.sleep(1020);
        loop.stop();
        thread.join();
        final int frameCount = frames[0];
        final int tickCount = ticks[0];
        assertTrue(Math.abs(frameCount - 63) <= 2, frameCount + " frames");
        assertTrue(Math.abs(tickCount - 20) <= 2, tickCount + " ticks");
        long worst = 0;
        long total = 0;
        final int measured = Math.min(frameCount, frameTimes.length);
        for (int i = 0; i < measured; ++i) {
            final long late = frameTimes[i] - (start + i * Frames.FRAME_NANOS);
            worst = Math.max(worst, late);
            total += late;
        }
        log.info(
                "{}: {} frames, {} ticks; frames started {} us late on average, {} us at worst",
                precise ? "Precise" : "Parking",
                frameCount,
                tickCount,
                total / measured / 1000,
                worst / 1000);
        assertTrue(worst < Frames.FRAME_NANOS, "a frame started " + worst / 1000 + " us late");
    }
}
