package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Wind waves: the spectrum, its spreading, and the profiles sampled from it. */
class WaveMathTest {

    /**
     * Jarzynski and Olano's PCG hash, written out here as the shader has it.
     *
     * @param value What to hash, as 32 unsigned bits.
     * @return The hash, as 32 unsigned bits.
     */
    private static int pcg(int value) {
        final long state = (value * 747796405L + 2891336453L) & 0xFFFFFFFFL;
        final long word = (((state >>> ((state >>> 28) + 4)) ^ state) * 277803737L) & 0xFFFFFFFFL;
        return (int) ((word >>> 22) ^ word);
    }

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

    /**
     * In shallow water the same wave is shorter, its energy slower, tending to sqrt(g h), and
     * taller as its energy bunches up; in deep water nothing changes.
     */
    @Test
    void testDepth() {
        final double k = 2 * Math.PI / 20;
        final double w = WaveMath.frequency(k);
        assertEquals(k, WaveMath.wavenumberAt(w, Double.POSITIVE_INFINITY), 1e-9);
        assertEquals(k, WaveMath.wavenumberAt(w, 100), 1e-6, "deep enough");
        final double shallow = WaveMath.wavenumberAt(w, 1);
        // About sqrt(g h) slow: a 20 m wave is some 10.6 m long in a meter of water
        assertTrue(shallow > 1.8 * k, "shorter: " + shallow);
        assertEquals(w, WaveMath.frequency(shallow, 1), 1e-9);
        assertEquals(Math.sqrt(9.81 * 0.5), WaveMath.groupSpeed(2 * Math.PI / 200, 0.5), 0.02);
        assertEquals(
                WaveMath.frequency(k) / (2 * k),
                WaveMath.groupSpeed(k, Double.POSITIVE_INFINITY),
                1e-4);
        assertTrue(WaveMath.shoaling(k, 1) > 1);
        assertTrue(WaveMath.shoaling(k, 0.05) <= WaveMath.MAX_SHOALING);
        assertEquals(1, WaveMath.shoaling(k, Double.POSITIVE_INFINITY));

        final WaveMath.Waves deep = WaveMath.sample(WIND, OPEN, 2);
        final WaveMath.Waves atOne = WaveMath.atDepth(deep, 1);
        for (int i = 0; i < deep.wavenumbers().length; ++i) {
            assertTrue(atOne.wavenumbers()[i] >= deep.wavenumbers()[i] - 1e-9);
            final double harmonic =
                    atOne.wavenumbers()[i] * WaveMath.PROFILE_LENGTH / (2 * Math.PI);
            assertEquals(Math.rint(harmonic), harmonic, 1e-9);
            // Turning in step with the deep water's
            assertEquals(deep.frequencies()[i], atOne.frequencies()[i]);
        }
        assertTrue(WaveMath.significantHeight(atOne) > WaveMath.significantHeight(deep));
        assertEquals(deep, WaveMath.atDepth(deep, Double.POSITIVE_INFINITY));
        assertArrayEquals(new int[] {0, 1, 2}, levelsUsed(deep));
        assertEquals(0, WaveMath.changedShare(deep, deep));

        // Ripples a few decimeters long are deep water's even at 1 m
        final double fundamental = 2 * Math.PI / WaveMath.PROFILE_LENGTH;
        final double[] wavenumbers = new double[RIPPLE_WAVES];
        final double[] amplitudes = new double[RIPPLE_WAVES];
        for (int i = 0; i < RIPPLE_WAVES; ++i) {
            wavenumbers[i] = (RIPPLE_HARMONIC + i) * fundamental;
            amplitudes[i] = 0.01;
        }
        final WaveMath.Waves ripples =
                new WaveMath.Waves(wavenumbers, amplitudes, new double[RIPPLE_WAVES]);
        assertArrayEquals(new int[] {0, 0, 0}, levelsUsed(ripples));
    }

    /** Ripple-like waves in the levels test. */
    private static final int RIPPLE_WAVES = 8;

    /** The lowest ripple-like wave's harmonic of the profile, about 0.4 m long. */
    private static final int RIPPLE_HARMONIC = 400;

    /**
     * Which depth's profiles each depth level reads for some deep water waves.
     *
     * @param deep The waves in deep water.
     * @return The levels, see {@link WaveMath#levelsUsed}.
     */
    private static int[] levelsUsed(WaveMath.Waves deep) {
        final WaveMath.Waves[] atDepths = new WaveMath.Waves[WaveMath.DEPTHS];
        for (int level = 0; level < WaveMath.DEPTHS; ++level) {
            atDepths[level] = WaveMath.atDepth(deep, WaveMath.DEPTH_LEVELS[level]);
        }
        return WaveMath.levelsUsed(atDepths);
    }

    /**
     * Whitecaps cover a few percent of the sea in a strong wind and none in a calm, and the foam
     * Jacobian puts that share of a normal Jacobian under it.
     */
    @Test
    void testFoamJacobian() {
        assertEquals(0.032, WaveMath.whitecapCoverage(14), 0.003);
        assertEquals(0, WaveMath.whitecapCoverage(0));
        assertTrue(WaveMath.whitecapCoverage(100) <= WaveMath.MAX_WHITECAP_COVERAGE);
        final WaveMath.Waves waves = WaveMath.sample(14, OPEN, 3);
        final double variance = WaveMath.jacobianVariance(waves, 0);
        assertTrue(WaveMath.jacobianVariance(waves, 4) < variance, "long waves are less steep");
        final double threshold = WaveMath.foamJacobian(waves, 14);
        assertTrue(threshold < 1);
        final double under = WaveMath.normalCdf((threshold - 1) / Math.sqrt(variance));
        assertEquals(WaveMath.whitecapCoverage(14) * WaveMath.ACTIVE_WHITECAP_SHARE, under, 1e-9);
        // Close to the normal distribution in the tail: 3.3 standard deviations out is 0.048%
        assertEquals(4.8e-4, WaveMath.normalCdf(-3.3), 1e-4);
        assertEquals(0.5, WaveMath.normalCdf(0), 1e-12);
        assertEquals(-2, WaveMath.inverseNormalCdf(WaveMath.normalCdf(-2)), 1e-9);
        assertEquals(WaveMath.NO_FOAM_JACOBIAN, WaveMath.foamJacobian(waves, 0));
        assertTrue(WaveMath.foamJacobian(waves, 20) > threshold, "more foam in more wind");
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
                    WaveMath.profile(waves, 3, 0, d, 12.5),
                    WaveMath.profile(waves, 3, 0, d + WaveMath.PROFILE_LENGTH, 12.5),
                    1e-9);
        }
        assertEquals(0, WaveMath.missingSlopeVariance(waves, 0));
        double last = 0;
        for (int layer = 1; layer < WaveMath.LAYERS; ++layer) {
            final double variance = WaveMath.missingSlopeVariance(waves, layer);
            assertTrue(variance > last);
            last = variance;
        }
        // Each direction has its own profile
        assertTrue(
                Math.abs(WaveMath.profile(waves, 0, 0, 5, 1) - WaveMath.profile(waves, 1, 0, 5, 1))
                        > 1e-3);
        for (int b = 0; b < WaveMath.DIRECTIONS; ++b) {
            final double phase = WaveMath.directionPhase(b, 7);
            assertTrue(phase >= 0 && phase < 2 * Math.PI);
        }
        // The same hash as the shader's: PCG of 1 * WAVES + 7
        assertEquals(
                2 * Math.PI * (pcg(WaveMath.WAVES + 7) >>> 8) / (double) (1 << 24),
                WaveMath.directionPhase(1, 7),
                1e-12);
        // Phases wrap to a turn, the same wave as the unwrapped one
        final double time = 1e6 + 0.3;
        final double k = waves.wavenumbers()[5];
        assertEquals(
                Math.cos(waves.phases()[5] - WaveMath.frequency(k) * time),
                Math.cos(WaveMath.phaseAt(waves, 5, time)),
                1e-6);
    }
}
