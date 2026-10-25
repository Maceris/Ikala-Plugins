package com.ikalagaming.characters.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;

/**
 * A range of numbers, written as {@code [min, max]}.
 *
 * @param min The smallest value.
 * @param max The largest value, at least min.
 * @author Ches Burks
 */
public record Range(float min, float max) {
    /** Reads and writes ranges. */
    static final ValueCodec<Range> CODEC =
            new ValueCodec<>() {
                @Override
                public Range read(
                        @NonNull JsonElement json, @NonNull String path, @NonNull Report report) {
                    float[] values = DataFormat.readFloats(json, 2);
                    if (values == null || values[0] > values[1]) {
                        report.error(
                                path, "Expected a range like [-1, 1], smallest first, not " + json);
                        return null;
                    }
                    return new Range(values[0], values[1]);
                }

                @Override
                public JsonElement write(@NonNull Range value) {
                    JsonArray array = new JsonArray();
                    array.add(value.min());
                    array.add(value.max());
                    return array;
                }

                @Override
                public JsonObject schema() {
                    return DataFormat.numberArraySchema(2);
                }
            };

    /**
     * Create a range.
     *
     * @param min The smallest value.
     * @param max The largest value.
     * @throws IllegalArgumentException If max is below min.
     */
    public Range {
        if (max < min) {
            throw new IllegalArgumentException("The range " + min + " to " + max + " is reversed");
        }
    }

    /**
     * Whether a value is in this range, ends included.
     *
     * @param value The value.
     * @return Whether it is in range.
     */
    public boolean contains(float value) {
        return value >= min && value <= max;
    }
}
