package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link ExposureMath}. */
class ExposureMathTest {

    /** How close a metered log2 luminance comes to the truth: half a bin. */
    private static final double HALF_BIN =
            (ExposureMath.MAX_LOG2_LUMINANCE - ExposureMath.MIN_LOG2_LUMINANCE)
                    / (ExposureMath.HISTOGRAM_BINS - 2.0)
                    / 2;

    private static int[] histogramOf(float... luminances) {
        final int[] bins = new int[ExposureMath.HISTOGRAM_BINS];
        for (float luminance : luminances) {
            bins[ExposureMath.binOf(luminance)] += 1;
        }
        return bins;
    }

    @Test
    void anEvenSceneMetersItsLuminance() {
        final float[] pixels = new float[1000];
        java.util.Arrays.fill(pixels, 0.5f);
        final double metered = ExposureMath.meter(histogramOf(pixels));
        assertEquals(-1, metered, HALF_BIN + 1e-6);
        // Brought most of the way to middle gray: three quarters of the stops between them
        final double stops = Math.log(0.18 / 0.5) / Math.log(2);
        assertEquals(Math.pow(2, stops * 0.75), ExposureMath.exposure(metered), 0.02);
        assertEquals(
                1,
                ExposureMath.exposure(Math.log(0.18) / Math.log(2)),
                1e-6,
                "Middle gray keeps the exposure as it was");
    }

    @Test
    void theDarkestHalfAndTheBrightestFewAreLeftOut() {
        // Half black shadow, most of the rest at 1, a few dazzling highlights
        final float[] pixels = new float[1000];
        for (int i = 0; i < pixels.length; ++i) {
            pixels[i] = i < 500 ? 0.001f : i < 980 ? 1 : 1000;
        }
        final double metered = ExposureMath.meter(histogramOf(pixels));
        assertEquals(0, metered, HALF_BIN + 1e-6, "Only the pixels at 1 are metered");
        assertTrue(Double.isNaN(ExposureMath.meter(histogramOf(0, 0, 0))), "Black isn't metered");
    }

    @Test
    void theEyeAdaptsFasterToLight() {
        final double brighter = ExposureMath.adapt(0, 4, 0.1);
        final double darker = ExposureMath.adapt(4, 0, 0.1);
        assertTrue(brighter > 0 && brighter < 4);
        assertTrue(4 - brighter > 0, "Not all the way in a frame");
        assertTrue(brighter - 0 > 4 - darker, "Up faster than down");
        // Over a long time it gets there
        double adapted = 0;
        for (int frame = 0; frame < 600; ++frame) {
            adapted = ExposureMath.adapt(adapted, -6, 1 / 60.0);
        }
        assertEquals(-6, adapted, 0.01);
    }

    @Test
    void nightStaysDark() {
        assertEquals(ExposureMath.MAX_EXPOSURE, ExposureMath.exposure(-20), 1e-9);
        assertEquals(ExposureMath.MIN_EXPOSURE, ExposureMath.exposure(10), 1e-9);
    }
}
