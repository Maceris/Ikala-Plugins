package com.ikalagaming.factory.world.gen;

/**
 * Specifies the range of values that a biome will have.
 * 
 * @author Ches Burks
 * @param min The minimum valid value for a biome. Must be >= 0 and <= max.
 * @param max The maximum valid value for a biome. Must be <=0 and >= min.
 *
 */
public record ParameterRange(float min, float max) {

	/**
	 * Create a new parameter range and check the values are reasonable.
	 * 
	 * @param min The minimum valid value for a biome. Must be >= 0 and <= max.
	 * @param max The maximum valid value for a biome. Must be <=0 and >= min.
	 */
	public ParameterRange(float min, float max) {
		if (Float.isNaN(min) || Float.isNaN(max)) {
			throw new IllegalArgumentException("NaN value provided for range");
		}
		if (max < min) {
			throw new IllegalArgumentException("Max value is smaller than min");
		}
		if (min < 0.0f || max > 1.0f) {
			throw new IllegalArgumentException(
				"Parameter range should be between 0 and 1 inclusive");
		}

		this.min = min;
		this.max = max;
	}
}
