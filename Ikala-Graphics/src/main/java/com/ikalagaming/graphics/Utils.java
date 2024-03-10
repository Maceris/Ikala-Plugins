package com.ikalagaming.graphics;

import java.util.List;

/** Graphics related utility methods. */
public class Utils {
    /**
     * Clamps the float between the given values. NaN is considered less than the minimum.
     *
     * @param value The value we are clamping.
     * @param min The smallest value it can be.
     * @param max The largest value it can be.
     * @return The value clamped between the given min and max values.
     */
    public static float clampFloat(final float value, final float min, final float max) {
        if (Float.isNaN(value) || (value < min)) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * Convert a list of floats to an array, since there is no other easy way to do this in Java.
     *
     * @param list The list of floats.
     * @return The list as an array.
     */
    public static float[] listFloatToArray(List<Float> list) {
        if (list == null) {
            return new float[0];
        }
        int size = list.size();
        float[] output = new float[size];
        for (int i = 0; i < size; ++i) {
            output[i] = list.get(i);
        }
        return output;
    }

    private Utils() {
        // Utility class
    }
}
