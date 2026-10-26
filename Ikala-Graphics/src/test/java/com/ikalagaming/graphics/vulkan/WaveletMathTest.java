package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The wave amplitude grid's reflection, disturbances and ripples. */
class WaveletMathTest {

    /** A quarter turn, in directions. */
    private static final int QUARTER = WaveMath.DIRECTIONS / 4;

    /**
     * A shore facing +x sends waves going +x back from the ones that came in going -x, and a
     * glancing wave keeps its sideways part.
     */
    @Test
    void testReflection() {
        final int east = 0;
        final int west = WaveMath.DIRECTIONS / 2;
        assertEquals(west, WaveletMath.reflectedFrom(east, 1, 0));
        assertEquals(east, WaveletMath.reflectedFrom(west, 1, 0));
        // Going +x and +z, from going -x and +z
        assertEquals(west - 2, WaveletMath.reflectedFrom(2, 1, 0));
        // Mirroring twice comes back
        for (int b = 0; b < WaveMath.DIRECTIONS; ++b) {
            final double s = Math.sqrt(0.5);
            assertEquals(b, WaveletMath.reflectedFrom(WaveletMath.reflectedFrom(b, s, s), s, s));
        }
        assertEquals(QUARTER, WaveletMath.reflectedFrom(QUARTER, 1, 0), "along the shore");
    }

    /** A disturbance sends ripples outward from its middle, fading to its edge. */
    @Test
    void testDisturbance() {
        assertEquals(0.5, WaveletMath.disturbanceShare(1, 0, 2, 0), 1e-12);
        assertEquals(0, WaveletMath.disturbanceShare(-1, 0, 2, 0), "not inward");
        assertEquals(0, WaveletMath.disturbanceShare(3, 0, 2, 0), "not past its edge");
        assertEquals(1, WaveletMath.disturbanceShare(0, 0, 2, QUARTER), "all ways at its middle");
        assertTrue(
                WaveletMath.disturbanceShare(1, 0.5, 2, 1)
                        > WaveletMath.disturbanceShare(1, 0.5, 2, 3));
    }

    /** Ripples are a meter's root mean square height per unit amplitude, at whole harmonics. */
    @Test
    void testRipples() {
        final WaveMath.Waves ripples = WaveletMath.ripples(3);
        assertEquals(4, WaveMath.significantHeight(ripples), 1e-9);
        for (double k : ripples.wavenumbers()) {
            final double wavelength = 2 * Math.PI / k;
            assertTrue(wavelength >= 0.079 && wavelength <= 1.51, "wavelength " + wavelength);
            final double harmonic = k * WaveMath.PROFILE_LENGTH / (2 * Math.PI);
            assertEquals(Math.rint(harmonic), harmonic, 1e-9);
        }
    }
}
