package com.ikalagaming.factory.world.gen;

/**
 * The range of a parameter's values that a biome is valid for, in the parameter's own units
 * (usually -1 to 1, the range of a density function).
 *
 * @author Ches Burks
 * @param min The minimum valid value for a biome. Must be finite and &lt;= max.
 * @param max The maximum valid value for a biome. Must be finite and >= min.
 */
public record ParameterRange(float min, float max) {

    /**
     * Create a new parameter range and check the values are reasonable.
     *
     * @param min The minimum valid value for a biome. Must be finite and &lt;= max.
     * @param max The maximum valid value for a biome. Must be finite and >= min.
     */
    public ParameterRange(final float min, final float max) {
        if (!Float.isFinite(min) || !Float.isFinite(max)) {
            throw new IllegalArgumentException("Range values must be finite numbers");
        }
        if (max < min) {
            throw new IllegalArgumentException("Max value is smaller than min");
        }

        this.min = min;
        this.max = max;
    }

    /**
     * Check if a value is within the specified range.
     *
     * @param value The value to check for
     * @return True if the value is between the min and max values (inclusive).
     */
    public boolean contains(final double value) {
        return value >= min && value <= max;
    }

    /**
     * Returns the total width of the range, or max - min.
     *
     * @return The width of the range.
     */
    public float getWidth() {
        return max - min;
    }

    /**
     * Returns the value that is midway between the min and max values.
     *
     * @return The midpoint of the range.
     */
    public float getMidpoint() {
        return (min + max) / 2;
    }
}
