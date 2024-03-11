package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

/**
 * Specifies a range of values that are valid for the generation of a biome. All parameters are
 * between 0 and 1 inclusive.
 *
 * @author Ches Burks
 * @param temperature The range of temperatures, representing -50c to +50c.
 * @param height The range of heights, ranging from -512 to +512.
 * @param erosion The amount of erosion.
 * @param precipitation The amount of rainfall, representing 0cm/year to over 500.
 * @param weirdness How weird or rare the biome is.
 */
public record BiomeParameters(
        @NonNull ParameterRange temperature,
        @NonNull ParameterRange height,
        @NonNull ParameterRange erosion,
        @NonNull ParameterRange precipitation,
        @NonNull ParameterRange weirdness) {

    /**
     * Checks if the provided parameters match the ranges specified by this biome.
     *
     * @param parameters The biome parameters we are checking.
     * @return True if all the provided parameters are within the specified ranges.
     */
    public boolean contains(final @NonNull ParameterPack parameters) {
        return temperature.contains(parameters.temperature())
                && height.contains(parameters.height())
                && erosion.contains(parameters.erosion())
                && precipitation.contains(parameters.precipitation())
                && weirdness.contains(parameters.weirdness());
    }

    /**
     * Compare biome parameters by how broad of a range they cover.
     *
     * @param first The first biome parameters.
     * @param second The second biome parameters.
     * @return A value of -1 if the first covers more range, 0 if they're about equal, and 1 if the
     *     first covers less range.
     */
    public static int compareSpecificity(
            @NonNull BiomeParameters first, @NonNull BiomeParameters second) {
        final float epsilon = 0.001f;
        final float rangeDifference = first.getTotalRange() - second.getTotalRange();
        if (rangeDifference < -epsilon) {
            return -1;
        }
        if (rangeDifference > epsilon) {
            return 1;
        }
        return 0;
    }

    /**
     * Calculates the sum of all the parameter widths. Loose estimate of how broad of a range this
     * spans in the vector space.
     *
     * @return The sum of parameter ranges.
     */
    private float getTotalRange() {
        return temperature.getWidth()
                + height.getWidth()
                + erosion.getWidth()
                + precipitation.getWidth()
                + weirdness.getWidth();
    }

    /**
     * Calculate the distance from the given parameter pack to the midpoint of these parameters.
     *
     * @param parameters The values we want to compare with the midpoints of this range.
     * @return The total distance in all dimensions.
     */
    public float getDistance(@NonNull final ParameterPack parameters) {

        return Math.abs(parameters.temperature() - temperature().getMidpoint())
                + Math.abs(parameters.height() - height().getMidpoint())
                + Math.abs(parameters.erosion() - erosion().getMidpoint())
                + Math.abs(parameters.precipitation() - precipitation().getMidpoint())
                + Math.abs(parameters.weirdness() - weirdness().getMidpoint());
    }
}
