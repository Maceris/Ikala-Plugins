package com.ikalagaming.factory.world.gen.noise;

import com.ikalagaming.factory.world.gen.WorldgenHash;
import com.ikalagaming.random.OpenSimplex2S;

import lombok.NonNull;

import java.util.List;

/**
 * Smooth blobs from OpenSimplex2S, optionally summed over octaves. Each octave gets its own seed
 * derived from the field's, and the sum is normalized back into [-1, 1].
 */
public final class SimplexNoise implements Noise {

    /** How octaves are combined. */
    public enum Mode {
        /** The plain sum: rolling hills, climate. */
        FBM,
        /** One minus the magnitude, each octave weighted by the one before: sharp ridgelines. */
        RIDGED,
        /** The magnitude: puffy clouds and dunes. */
        BILLOW
    }

    /** How many axes coordinates have. */
    private static final int AXES = 3;

    /** The seed of each octave. */
    private final long[] seeds;

    /** How much each octave's coordinates are multiplied by, per axis, octave-major. */
    private final double[] frequencies;

    /** How much each octave counts. */
    private final double[] amplitudes;

    /** The sum of the amplitudes, which normalizes the total. */
    private final double amplitudeSum;

    /** How octaves are combined. */
    private final Mode mode;

    /** Whether y is ignored. */
    private final boolean flat;

    /**
     * Set up a noise field.
     *
     * @param seed The field's seed, derived from the world seed and the salt.
     * @param flat True for 2D noise, which ignores y.
     * @param wavelength The feature size in blocks along x, y and z.
     * @param mode How octaves are combined.
     * @param octaves How many octaves, at least 1.
     * @param lacunarity How much each octave's frequency is multiplied by.
     * @param gain How much each octave's amplitude is multiplied by.
     */
    public SimplexNoise(
            long seed,
            boolean flat,
            double @NonNull [] wavelength,
            @NonNull Mode mode,
            int octaves,
            double lacunarity,
            double gain) {
        this.flat = flat;
        this.mode = mode;
        seeds = new long[octaves];
        frequencies = new double[octaves * AXES];
        amplitudes = new double[octaves];
        double sum = 0;
        double frequency = 1;
        double amplitude = 1;
        for (int i = 0; i < octaves; ++i) {
            seeds[i] = WorldgenHash.mix64(seed + i);
            for (int axis = 0; axis < AXES; ++axis) {
                frequencies[i * AXES + axis] = frequency / wavelength[axis];
            }
            amplitudes[i] = amplitude;
            sum += amplitude;
            frequency *= lacunarity;
            amplitude *= gain;
        }
        amplitudeSum = sum;
    }

    @Override
    public double sample(int channel, double x, double y, double z) {
        double total = 0;
        double weight = 1;
        for (int i = 0; i < seeds.length; ++i) {
            final double n = octave(i, x, y, z);
            total +=
                    amplitudes[i]
                            * switch (mode) {
                                case FBM -> n;
                                case BILLOW -> Math.abs(n);
                                case RIDGED -> {
                                    final double ridge = (1 - Math.abs(n)) * weight;
                                    weight = Math.clamp(ridge, 0, 1);
                                    yield ridge;
                                }
                            };
        }
        final double normalized = total / amplitudeSum;
        // Ridged and billow sum magnitudes from 0 to 1, so they're stretched to -1 to 1
        final double result = mode == Mode.FBM ? normalized : normalized * 2 - 1;
        return Math.clamp(result, -1, 1);
    }

    /**
     * One octave's raw noise.
     *
     * @param octave The octave.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @return The noise, about -1 to 1.
     */
    private double octave(int octave, double x, double y, double z) {
        final int base = octave * AXES;
        final double fx = x * frequencies[base];
        final double fz = z * frequencies[base + 2];
        if (flat) {
            return OpenSimplex2S.noise2(seeds[octave], fx, fz);
        }
        // Y is the vertical axis, so the variant with the best XZ isotropy
        return OpenSimplex2S.noise3_ImproveXZ(seeds[octave], fx, y * frequencies[base + 1], fz);
    }

    @Override
    public boolean is2D() {
        return flat;
    }

    @Override
    public List<String> channels() {
        return List.of(VALUE_CHANNEL);
    }
}
