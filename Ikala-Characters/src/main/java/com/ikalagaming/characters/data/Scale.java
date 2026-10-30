package com.ikalagaming.characters.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.NonNull;

/**
 * How much something grows along x, y and z, written as one number when it grows the same along
 * all three, like {@code 1.2}, or as {@code [x, y, z]}.
 *
 * @param x How much it grows across.
 * @param y How much it grows up.
 * @param z How much it grows forward.
 * @author Ches Burks
 */
public record Scale(float x, float y, float z) {
    /** No growth. */
    public static final Scale ONE = new Scale(1, 1, 1);

    /** Reads and writes scales. */
    static final ValueCodec<Scale> CODEC =
            new ValueCodec<>() {
                @Override
                public Scale read(
                        @NonNull JsonElement json, @NonNull String path, @NonNull Report report) {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                        float value = json.getAsFloat();
                        if (Float.isFinite(value)) {
                            return of(value);
                        }
                    }
                    float[] values = DataFormat.readFloats(json, 3);
                    if (values == null) {
                        report.error(
                                path,
                                "Expected a number like 1.2, or three like [1, 1.2, 1], not "
                                        + json);
                        return null;
                    }
                    return new Scale(values[0], values[1], values[2]);
                }

                @Override
                public JsonElement write(@NonNull Scale value) {
                    if (value.isUniform()) {
                        return new JsonPrimitive(value.x());
                    }
                    JsonArray array = new JsonArray();
                    array.add(value.x());
                    array.add(value.y());
                    array.add(value.z());
                    return array;
                }

                @Override
                public JsonObject schema() {
                    JsonObject number = new JsonObject();
                    number.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_NUMBER);
                    JsonArray either = new JsonArray();
                    either.add(number);
                    either.add(DataFormat.numberArraySchema(3));
                    JsonObject schema = new JsonObject();
                    schema.add(SchemaKeys.ONE_OF, either);
                    return schema;
                }
            };

    /**
     * The same growth along every axis.
     *
     * @param value How much it grows.
     * @return The scale.
     */
    public static Scale of(float value) {
        return new Scale(value, value, value);
    }

    /**
     * Whether it grows the same along every axis.
     *
     * @return True if x, y and z are equal.
     */
    public boolean isUniform() {
        return x == y && y == z;
    }

    /**
     * Whether it grows, rather than flattening or flipping, along every axis.
     *
     * @return True if x, y and z are all above 0.
     */
    public boolean isPositive() {
        return x > 0 && y > 0 && z > 0;
    }
}
