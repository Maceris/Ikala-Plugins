package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

/**
 * Specifies a range of values that are valid for the generation of a biome. All
 * parameters are between 0 and 1 inclusive.
 * 
 * @author Ches Burks
 *
 * @param temperature The range of temperatures, representing -50c to +50c.
 * @param height The range of heights, ranging from -512 to +512.
 * @param erosion The amount of erosion.
 * @param vegetation The amount of vegetation.
 * @param weirdness How weird or rare the biome is.
 */
public record BiomeParameters(@NonNull ParameterRange temperature,
	@NonNull ParameterRange height, @NonNull ParameterRange erosion,
	@NonNull ParameterRange vegetation, @NonNull ParameterRange weirdness) {

	/**
	 * Checks if the provided parameters match the ranges specified by this
	 * biome.
	 * 
	 * @param parameters The biome parameters we are checking.
	 * @return True if all the provided parameters are within the specified
	 *         ranges.
	 */
	public boolean contains(final @NonNull ParameterPack parameters) {
		return temperature.contains(parameters.temperature())
			&& height.contains(parameters.height())
			&& erosion.contains(parameters.erosion())
			&& vegetation.contains(parameters.vegetation())
			&& weirdness.contains(parameters.weirdness());
	}
}