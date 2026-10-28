package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Shadows through see-through surfaces, from the moments of their depths. */
class TranslucentShadowMathTest {

    /** How close the shares must be to what lies in front, given the moments' bias. */
    private static final double TOLERANCE = 0.02;

    /**
     * The moments of surfaces at depths, each with its absorbance, over their total.
     *
     * @param depths Where each is.
     * @param absorbances How much each absorbs.
     * @return The normalized moments.
     */
    private static double[] momentsOf(double[] depths, double[] absorbances) {
        final double[] sum = new double[TranslucentShadowMath.MOMENTS];
        final double[] one = new double[TranslucentShadowMath.MOMENTS];
        double total = 0;
        for (int i = 0; i < depths.length; ++i) {
            TranslucentShadowMath.moments(absorbances[i], depths[i], one);
            for (int k = 0; k < one.length; ++k) {
                sum[k] += one[k];
            }
            total += absorbances[i];
        }
        for (int k = 0; k < sum.length; ++k) {
            sum[k] /= total;
        }
        return sum;
    }

    /** One pane: nothing of it in front of what is before it, all of it behind it. */
    @Test
    void testOnePane() {
        final double[] moments = momentsOf(new double[] {0.4}, new double[] {1});
        assertEquals(0, TranslucentShadowMath.shareInFront(moments, 0.3), TOLERANCE);
        assertEquals(1, TranslucentShadowMath.shareInFront(moments, 0.5), TOLERANCE);
    }

    /** Between two panes, only the first; behind both, both; before both, neither. */
    @Test
    void testTwoPanes() {
        final double[] moments = momentsOf(new double[] {0.3, 0.6}, new double[] {1, 3});
        assertEquals(0, TranslucentShadowMath.shareInFront(moments, 0.2), TOLERANCE);
        assertEquals(0.25, TranslucentShadowMath.shareInFront(moments, 0.45), TOLERANCE);
        assertEquals(1, TranslucentShadowMath.shareInFront(moments, 0.7), TOLERANCE);
    }

    /** The share in front never falls going deeper, through a spread of panes. */
    @Test
    void testShareGrowsWithDepth() {
        final double[] moments =
                momentsOf(new double[] {0.1, 0.35, 0.5, 0.8}, new double[] {0.5, 1, 2, 0.25});
        double last = 0;
        for (double depth = 0; depth <= 1; depth += 0.01) {
            final double share = TranslucentShadowMath.shareInFront(moments, depth);
            assertTrue(share >= last - 1e-9, "at " + depth);
            last = share;
        }
    }

    /**
     * A pane passes what it doesn't cover, and its color of what it does: clear glass all, a red
     * pane red, a black one only its gaps.
     */
    @Test
    void testTransmittance() {
        assertEquals(1, TranslucentShadowMath.transmittance(1, 0.6), 1e-12);
        assertEquals(0.4, TranslucentShadowMath.transmittance(0, 0.6), 1e-12);
        assertEquals(0.4 + 0.6 * 0.8, TranslucentShadowMath.transmittance(0.8, 0.6), 1e-12);
        // Behind it, all of its absorbance counts
        final double absorbance = -Math.log(0.4);
        assertEquals(0.4, TranslucentShadowMath.transmittanceAt(absorbance, 1), 1e-12);
        assertEquals(1, TranslucentShadowMath.transmittanceAt(absorbance, 0), 1e-12);
    }
}
