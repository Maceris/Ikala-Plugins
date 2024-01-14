package com.ikalagaming.factory.world.gen;

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
public record BiomeParameters(ParameterRange temperature, ParameterRange height,
	ParameterRange erosion, ParameterRange vegetation,
	ParameterRange weirdness) {}