package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

/**
 * The math of shadows through see-through surfaces, like glass, as {@code translucent_shadow.glsl}
 * does it, with tests: for each texel of a sun cascade, a translucent map holds the absorbance
 * (minus the log of the transmittance) of everything see-through along the texel's line toward the
 * sun, per color and as one number, and the power moments of the depths it was met at, weighted by
 * it; from those, how much of it lies in front of any depth is bounded as in moment shadow mapping
 * (Peters and Klein 2015, the 4 moment Hamburger version), which moment based order independent
 * transparency (Muenstermann et al. 2018) uses for transmittance and Smolder (Mueller, SIGGRAPH
 * 2026) for shadows. So a surface between two panes is only darkened by the one in front of it.
 *
 * <p>Depths are the cascade's, from 0 at the sun's end to 1.
 */
public final class TranslucentShadowMath {

    /** How many moments of depth the map holds: depth to the first through fourth power. */
    public static final int MOMENTS = 4;

    /**
     * How far the moments are pulled toward those of an even spread, so the system they solve is
     * never singular: Peters and Klein's value for 32-bit floats.
     */
    public static final double MOMENT_BIAS = 3e-5;

    /** The moments of an even spread over 0 to 1 the bias pulls toward, rounded as the paper's. */
    private static final double EVEN = 0.5;

    /** How little absorbance counts as nothing in the way. */
    public static final double MIN_ABSORBANCE = 1e-4;

    /** Static math only. */
    private TranslucentShadowMath() {}

    /**
     * What one see-through surface lets through, per color: the share it doesn't cover goes
     * straight on, and the share it covers passes on its color, as the camera sees a white light
     * through it, see {@code translucent.frag}.
     *
     * @param color Its color, linear, one channel.
     * @param opacity How much of it is covered, 0 to 1.
     * @return The share of light it lets through in that channel.
     */
    public static double transmittance(double color, double opacity) {
        return 1 - opacity + opacity * color;
    }

    /**
     * The moments one surface adds at a depth, weighted by its absorbance.
     *
     * @param absorbance Its absorbance, as one number.
     * @param depth Its depth.
     * @param out Receives the four moments, depth to the first through fourth power.
     */
    public static void moments(double absorbance, double depth, double @NonNull [] out) {
        double power = depth;
        for (int i = 0; i < MOMENTS; ++i) {
            out[i] = absorbance * power;
            power *= depth;
        }
    }

    /**
     * How much of the absorbance in a texel lies in front of a depth, from 0 to 1, from the moments
     * of its depths over its total.
     *
     * @param moments The moments, each over the total absorbance, depth to the first through fourth
     *     power.
     * @param depth The depth.
     * @return The share in front of it.
     */
    public static double shareInFront(double @NonNull [] moments, double depth) {
        final double[] b = new double[MOMENTS];
        for (int i = 0; i < MOMENTS; ++i) {
            b[i] = (1 - MOMENT_BIAS) * moments[i] + MOMENT_BIAS * EVEN;
        }
        // A Cholesky factorization of the Hankel matrix of the moments, its nontrivial entries
        final double l32d22 = -b[0] * b[1] + b[2];
        final double d22 = -b[0] * b[0] + b[1];
        final double squaredVariance = -b[1] * b[1] + b[3];
        final double d33d22 = squaredVariance * d22 - l32d22 * l32d22;
        final double inverseD22 = 1 / d22;
        final double l32 = l32d22 * inverseD22;
        // Solve it for (1, z, z^2), for the polynomial whose roots are the other two depths
        double c0 = 1;
        double c1 = depth;
        double c2 = depth * depth;
        c1 -= b[0];
        c2 -= b[1] + l32 * c1;
        c1 *= inverseD22;
        c2 *= d22 / d33d22;
        c1 -= l32 * c2;
        c0 -= c1 * b[0] + c2 * b[1];
        final double p = c1 / c2;
        final double q = c0 / c2;
        final double root = Math.sqrt(Math.max(0, p * p / 4 - q));
        final double z1 = -p / 2 - root;
        final double z2 = -p / 2 + root;
        final double first;
        final double second;
        final double base;
        final double scale;
        if (z2 < depth) {
            first = z1;
            second = depth;
            base = 1;
            scale = 1;
        } else if (z1 < depth) {
            first = depth;
            second = z1;
            base = 0;
            scale = 1;
        } else {
            return 0;
        }
        final double quotient =
                (first * z2 - b[0] * (first + z2) + b[1]) / ((z2 - second) * (depth - z1));
        return Math.clamp(base + scale * quotient, 0, 1);
    }

    /**
     * What reaches a depth through a texel, in one channel.
     *
     * @param absorbance The texel's total absorbance in that channel.
     * @param share How much of it is in front of the depth, see {@link #shareInFront}.
     * @return The transmittance, 0 to 1.
     */
    public static double transmittanceAt(double absorbance, double share) {
        return Math.exp(-absorbance * share);
    }
}
