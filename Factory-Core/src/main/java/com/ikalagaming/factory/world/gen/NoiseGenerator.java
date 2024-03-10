package com.ikalagaming.factory.world.gen;

/**
 * Specifies the type of a noise generator that we expect to use for world generation.
 *
 * @author Ches Burks
 */
@FunctionalInterface
public interface NoiseGenerator {

    /**
     * Generate noise at a specific position.
     *
     * @param seed The seed to use for noise generation.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param scale The scale of the noise.
     * @param octaves The number of octaves of noise to use.
     * @return The resulting noise value, mapped to the range [0, 1].
     */
    double getNoise(
            final long seed, final int x, final int y, final double scale, final int octaves);
}
