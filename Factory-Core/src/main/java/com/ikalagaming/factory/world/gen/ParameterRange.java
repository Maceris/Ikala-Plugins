package com.ikalagaming.factory.world.gen;

/**
 * Specifies the range of values that a biome will have.
 *
 * @author Ches Burks
 * @param min The minimum valid value for a biome. Must be >= 0 and &lt;= max.
 * @param max The maximum valid value for a biome. Must be &lt;=1 and >= min.
 */
public record ParameterRange(float min, float max) {

    /**
     * Create a new parameter range and check the values are reasonable.
     *
     * @param min The minimum valid value for a biome. Must be >= 0 and &lt;= max.
     * @param max The maximum valid value for a biome. Must be &lt;=0 and >= min.
     */
    public ParameterRange(final float min, final float max) {
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
