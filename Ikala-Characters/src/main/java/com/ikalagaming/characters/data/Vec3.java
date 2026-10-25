package com.ikalagaming.characters.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;

/**
 * Three numbers, written as {@code [x, y, z]}: an offset in meters, or rotations in degrees.
 *
 * @param x The first number.
 * @param y The second number.
 * @param z The third number.
 * @author Ches Burks
 */
public record Vec3(float x, float y, float z) {
    /** All zeroes. */
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    /** Reads and writes vectors. */
    static final ValueCodec<Vec3> CODEC =
            new ValueCodec<>() {
                @Override
                public Vec3 read(
                        @NonNull JsonElement json, @NonNull String path, @NonNull Report report) {
                    float[] values = DataFormat.readFloats(json, 3);
                    if (values == null) {
                        report.error(path, "Expected three numbers like [0, 0.1, 0], not " + json);
                        return null;
                    }
                    return new Vec3(values[0], values[1], values[2]);
                }

                @Override
                public JsonElement write(@NonNull Vec3 value) {
                    JsonArray array = new JsonArray();
                    array.add(value.x());
                    array.add(value.y());
                    array.add(value.z());
                    return array;
                }

                @Override
                public JsonObject schema() {
                    return DataFormat.numberArraySchema(3);
                }
            };
}
