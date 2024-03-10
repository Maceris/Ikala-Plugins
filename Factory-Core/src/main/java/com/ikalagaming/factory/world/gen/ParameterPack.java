package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

/**
 * A set of biome parameters that have been generated for a particular world coordinate. Used to
 * bundle all the values together for processing.
 *
 * @author Ches Burks
 * @param temperature The temperature value.
 * @param height The height value.
 * @param erosion The erosion value.
 * @param precipitation The precipitation value.
 * @param weirdness The weirdness value.
 */
public record ParameterPack(
        double temperature, double height, double erosion, double precipitation, double weirdness) {

    /**
     * Generate the parameter pack at specified coordinates.
     *
     * @param generator The noise generator to use.
     * @param seed The seed to use for the noise function.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return The set of all relevant noise values, all in the range [0, 1].
     */
    public static ParameterPack generateParameters(
            @NonNull NoiseGenerator generator, final long seed, final int x, final int y) {

        return new ParameterPack(
                generate(generator, seed, x, y, NoiseParameters.TEMPERATURE),
                generate(generator, seed, x, y, NoiseParameters.HEIGHT),
                generate(generator, seed, x, y, NoiseParameters.EROSION),
                generate(generator, seed, x, y, NoiseParameters.PRECIPITATION),
                generate(generator, seed, x, y, NoiseParameters.WEIRDNESS));
    }

    /**
     * Generate the simplex noise value at specified coordinates, given the specified parameters.
     *
     * @param generator The noise generator to use.
     * @param seed The seed to use for the noise function.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param parameters The parameters to use.
     * @return The noise value in the range [0, 1].
     */
    private static double generate(
            @NonNull NoiseGenerator generator,
            final long seed,
            final int x,
            final int y,
            @NonNull NoiseParameters parameters) {
        return generator.getNoise(seed, x, y, parameters.scale(), parameters.octaves());
    }
}
