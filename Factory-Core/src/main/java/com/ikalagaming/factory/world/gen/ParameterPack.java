package com.ikalagaming.factory.world.gen;

/**
 * A set of biome parameters that have been generated for a particular world
 * coordinate. Used to bundle all the values together for processing.
 * 
 * @author Ches Burks
 *
 * @param temperature The temperature value.
 * @param height The height value.
 * @param erosion The erosion value.
 * @param vegetation The vegetation value.
 * @param weirdness The weirdness value.
 */
public record ParameterPack(double temperature, double height, double erosion,
	double vegetation, double weirdness) {

	/**
	 * Generate the parameter pack at specified coordinates.
	 *
	 * @param seed The seed to use for the noise function.
	 * @param x The x coordinate.
	 * @param y The y coordinate.
	 * @return The set of all relevant noise values, all in the range [0, 1].
	 */
	public static ParameterPack generateParameters(final long seed, final int x,
		final int y) {
		return new ParameterPack(
			NoiseParameters.TEMPERATURE.generate(seed, x, y),
			NoiseParameters.HEIGHT.generate(seed, x, y),
			NoiseParameters.EROSION.generate(seed, x, y),
			NoiseParameters.VEGETATION.generate(seed, x, y),
			NoiseParameters.WEIRDNESS.generate(seed, x, y));
	}

}
