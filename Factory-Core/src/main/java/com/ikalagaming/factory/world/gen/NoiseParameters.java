package com.ikalagaming.factory.world.gen;

import com.ikalagaming.random.RandomGen;

/**
 * Used to specify the simplex noise parameters used to generate a specific
 * noise map.
 *
 * @author Ches Burks
 *
 * @param scale The scale of the noise. A reasonable example is 0.001, should be
 *            in the range (0, 1).
 * @param octaves The number of octaves of noise to use. Must be > 0, should be
 *            < 16.
 */
public record NoiseParameters(double scale, int octaves) {

	/**
	 * Generate the simplex noise value at specified coordinates, given the
	 * specified parameters.
	 *
	 * @param seed The seed to use for the noise function.
	 * @param x The x coordinate.
	 * @param y The y coordinate.
	 * @return The noise value in the range [0, 1].
	 */
	public double generate(final long seed, final int x, final int y) {
		return RandomGen.generateSimplexNoise(seed, x, y, this.scale,
			this.octaves);
	}

	public static final NoiseParameters TEMPERATURE =
		new NoiseParameters(0.001, 4);
	public static final NoiseParameters HEIGHT = new NoiseParameters(0.002, 10);
	public static final NoiseParameters EROSION = new NoiseParameters(0.002, 6);
	public static final NoiseParameters VEGETATION =
		new NoiseParameters(0.001, 5);
	public static final NoiseParameters WEIRDNESS =
		new NoiseParameters(0.002, 6);
}