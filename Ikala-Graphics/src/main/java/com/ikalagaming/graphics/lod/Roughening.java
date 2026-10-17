package com.ikalagaming.graphics.lod;

/**
 * Roughness that stands for normals blended away: where a far surface takes one normal for many
 * that differed, its highlights must spread as far as theirs did together, or it shines like a
 * mirror where the input was bumpy. The spread is read from the mean of the unit normals: the
 * shorter it is, the more they differed. Fitting a von Mises-Fisher lobe to that mean length r
 * gives its sharpness, kappa = (3r - r^3) / (1 - r^2) (Banerjee et al. 2005, as used for normal map
 * filtering by Han et al. 2007), and its variance, 2 / kappa, adds to the microfacet lobe's
 * alpha^2, a convolution of the two lobes, as appearance-preserving scene aggregation roughens far
 * surfaces. Alpha is the roughness squared, as the shaders take it. Match roughening.glsl.
 */
public final class Roughening {

    /** The longest mean length trusted below 1, so kappa stays finite. */
    private static final double MOST_ALIGNED = 1 - 1e-5;

    /**
     * A roughness widened for normals that differed.
     *
     * @param roughness The surface's own roughness, 0 to 1.
     * @param meanLength How long the mean of the unit normals is, 0 to 1: 1 when they all agreed.
     * @return The roughness, at most 1.
     */
    public static double widen(double roughness, double meanLength) {
        final double r = Math.clamp(meanLength, 0, MOST_ALIGNED);
        if (r <= 0) {
            return 1;
        }
        final double kappa = (3 * r - r * r * r) / (1 - r * r);
        final double alpha = roughness * roughness;
        final double widened = alpha * alpha + 2 / kappa;
        return Math.min(1, Math.sqrt(Math.sqrt(widened)));
    }

    /** Static helpers only. */
    private Roughening() {}
}
