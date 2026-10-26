package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Wind waves: the spectrum, its spreading, and the profiles sampled from it. */
class WaveMathTest {

    /** A moderate breeze, in meters a second. */
    private static final double WIND = 8;

    /** The open sea. */
    private static final double OPEN = Double.POSITIVE_INFINITY;

    /** Long waves go as sqrt(g k); short ones are faster, held up by surface tension. */
    @Test
    void testDispersion() {
        assertEquals(Math.sqrt(9.81 * 0.1), WaveMath.frequency(0.1), 1e-6);
        final double ripple = 2 * Math.PI / 0.01;
        assertTrue(WaveMath.frequency(ripple) > 1.5 * Math.sqrt(9.81 * ripple));
    }

    /** The directions' energies sum to the whole, and none go against the wind. */
    @Test
    void testSpreading() {
        final double[] amplitudes = WaveMath.directionAmplitudes(0);
        double sum = 0;
        for (double amplitude : amplitudes) {
            sum += amplitude * amplitude;
        }
        assertEquals(1, sum, 1e-9);
        assertEquals(0, amplitudes[WaveMath.DIRECTIONS / 2], 1e-12);
        assertTrue(amplitudes[0] > amplitudes[1]);
        final double[] turned = WaveMath.directionAmplitudes(Math.PI / 2);
        assertEquals(amplitudes[0], turned[WaveMath.DIRECTIONS / 4], 1e-9);
    }

    /**
     * A fully developed sea's waves add up to Pierson-Moskowitz's significant height, 4 sqrt(alpha
     * g^2 / (5 wp^4)), to within what the sampling and the profile's length change.
     */
    @Test
    void testDevelopedSeaHeight() {
        final double peak = WaveMath.peakFrequency(WIND, OPEN);
        assertEquals(0.855 * 9.81 / WIND, peak, 1e-9);
        final double expected = 4 * Math.sqrt(0.0081 * 9.81 * 9.81 / (5 * Math.pow(peak, 4)));
        final double height = WaveMath.significantHeight(WaveMath.sample(WIND, OPEN, 1));
        assertEquals(expected, height, 0.1 * expected);
        // About 0.22 U^2 / g for a fully developed sea
        assertEquals(0.22 * WIND * WIND / 9.81, expected, 0.15 * expected);
    }

    /** Over a short fetch the sea is still growing: smaller, shorter waves. */
    @Test
    void testFetch() {
        final double lake = 2000;
        assertTrue(WaveMath.peakFrequency(WIND, lake) > WaveMath.peakFrequency(WIND, OPEN));
        assertTrue(
                WaveMath.significantHeight(WaveMath.sample(WIND, lake, 1))
                        < 0.5 * WaveMath.significantHeight(WaveMath.sample(WIND, OPEN, 1)));
        assertEquals(0, WaveMath.spectrum(1, 0, OPEN));
    }

    /**
     * A profile tiles over its length, its waves are whole harmonics, and coarser layers leave out
     * the short waves, whose slopes become roughness.
     */
    @Test
    void testProfile() {
        final WaveMath.Waves waves = WaveMath.sample(WIND, OPEN, 7);
        for (double k : waves.wavenumbers()) {
            final double harmonic = k * WaveMath.PROFILE_LENGTH / (2 * Math.PI);
            assertEquals(Math.rint(harmonic), harmonic, 1e-9);
        }
        for (double d : new double[] {0, 3.7, 91}) {
            assertEquals(
                    WaveMath.profile(waves, 0, d, 12.5),
                    WaveMath.profile(waves, 0, d + WaveMath.PROFILE_LENGTH, 12.5),
                    1e-9);
        }
        assertEquals(0, WaveMath.missingSlopeVariance(waves, 0));
        double last = 0;
        for (int layer = 1; layer < WaveMath.LAYERS; ++layer) {
            final double variance = WaveMath.missingSlopeVariance(waves, layer);
            assertTrue(variance > last);
            last = variance;
        }
        // Phases wrap to a turn, the same wave as the unwrapped one
        final double time = 1e6 + 0.3;
        final double k = waves.wavenumbers()[5];
        assertEquals(
                Math.cos(waves.phases()[5] - WaveMath.frequency(k) * time),
                Math.cos(WaveMath.phaseAt(waves, 5, time)),
                1e-6);
    }
}
