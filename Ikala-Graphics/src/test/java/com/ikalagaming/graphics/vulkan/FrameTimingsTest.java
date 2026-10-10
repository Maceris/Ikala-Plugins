package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Turning timestamps into milliseconds, and summarizing them. */
class FrameTimingsTest {

    @Test
    void ticksBecomeMilliseconds() {
        // A tick of 1 ns: a million ticks is a millisecond
        assertEquals(1.0, FrameTimings.millisecondsBetween(5, 1_000_005, 64, 1), 1e-12);
        // A tick of 32 ns
        assertEquals(3.2, FrameTimings.millisecondsBetween(0, 100_000, 64, 32), 1e-9);
    }

    @Test
    void aCounterThatWrapsStillCountsForward() {
        // 36 valid bits: from just below the top to just past zero is 32 ticks
        final long top = (1L << 36) - 16;
        assertEquals(32e-6, FrameTimings.millisecondsBetween(top, 16, 36, 1), 1e-12);
    }

    @Test
    void summariesGiveTheAveragePercentileAndWorst() {
        double[] samples = new double[100];
        for (int i = 0; i < samples.length; ++i) {
            samples[i] = i + 1;
        }
        FrameTimings.Timing timing = FrameTimings.summarize("stage", samples);
        assertEquals(50.5, timing.average(), 1e-9);
        // The nearest rank: the 95th of 100 sorted values
        assertEquals(95, timing.p95(), 1e-9);
        assertEquals(100, timing.max(), 1e-9);
        FrameTimings.Timing none = FrameTimings.summarize("nothing", new double[0]);
        assertEquals(0, none.average());
    }

    @Test
    void theRollingWindowKeepsOnlyTheLatestFrames() {
        FrameTimings.Rolling rolling = new FrameTimings.Rolling();
        for (int i = 0; i < FrameTimings.WINDOW; ++i) {
            rolling.add(100);
        }
        for (int i = 0; i < FrameTimings.WINDOW; ++i) {
            rolling.add(1);
        }
        assertEquals(1, rolling.summarize("stage").max(), 1e-9);
    }
}
