package com.ikalagaming.characters.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.NonNull;

/**
 * Reading glTF JSON fields with errors that name what is wrong. Gson's own getters throw a null
 * pointer for a missing field and an unsupported or illegal state exception for one of the wrong
 * shape, which say nothing about the file; these throw {@link IllegalArgumentException}, as the
 * rest of the glTF reader does for a file it can't read.
 */
final class GltfJson {

    /**
     * A field that must be there.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or null.
     */
    static JsonElement field(@NonNull JsonObject object, @NonNull String name) {
        final JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("The glTF JSON has no \"" + name + "\" field");
        }
        return value;
    }

    /**
     * A whole number field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not a number.
     */
    static int integer(@NonNull JsonObject object, @NonNull String name) {
        return integer(field(object, name), name);
    }

    /**
     * A whole number.
     *
     * @param element The element.
     * @param what What it is, for the error.
     * @return Its value.
     * @throws IllegalArgumentException If it isn't a number.
     */
    static int integer(@NonNull JsonElement element, @NonNull String what) {
        return primitiveNumber(element, what).getAsInt();
    }

    /**
     * A long whole number field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not a number.
     */
    static long longInteger(@NonNull JsonObject object, @NonNull String name) {
        return primitiveNumber(field(object, name), name).getAsLong();
    }

    /**
     * A number field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not a number.
     */
    static float number(@NonNull JsonObject object, @NonNull String name) {
        return number(field(object, name), name);
    }

    /**
     * A number.
     *
     * @param element The element.
     * @param what What it is, for the error.
     * @return Its value.
     * @throws IllegalArgumentException If it isn't a number.
     */
    static float number(@NonNull JsonElement element, @NonNull String what) {
        return primitiveNumber(element, what).getAsFloat();
    }

    /**
     * A true or false field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not a boolean.
     */
    static boolean bool(@NonNull JsonObject object, @NonNull String name) {
        final JsonElement value = field(object, name);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw wrongShape(name, "true or false");
        }
        return primitive.getAsBoolean();
    }

    /**
     * A text field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not a string.
     */
    static String string(@NonNull JsonObject object, @NonNull String name) {
        return string(field(object, name), name);
    }

    /**
     * Text.
     *
     * @param element The element.
     * @param what What it is, for the error.
     * @return Its value.
     * @throws IllegalArgumentException If it isn't a string.
     */
    static String string(@NonNull JsonElement element, @NonNull String what) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw wrongShape(what, "text");
        }
        return primitive.getAsString();
    }

    /**
     * An object field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not an object.
     */
    static JsonObject object(@NonNull JsonObject object, @NonNull String name) {
        return object(field(object, name), name);
    }

    /**
     * An object.
     *
     * @param element The element.
     * @param what What it is, for the error.
     * @return Its value.
     * @throws IllegalArgumentException If it isn't an object.
     */
    static JsonObject object(@NonNull JsonElement element, @NonNull String what) {
        if (!element.isJsonObject()) {
            throw wrongShape(what, "an object");
        }
        return element.getAsJsonObject();
    }

    /**
     * An array field.
     *
     * @param object The object holding it.
     * @param name The field's name.
     * @return Its value.
     * @throws IllegalArgumentException If it is missing or not an array.
     */
    static JsonArray array(@NonNull JsonObject object, @NonNull String name) {
        final JsonElement value = field(object, name);
        if (!value.isJsonArray()) {
            throw wrongShape(name, "an array");
        }
        return value.getAsJsonArray();
    }

    /**
     * A number element.
     *
     * @param element The element.
     * @param what What it is, for the error.
     * @return It as a primitive number.
     * @throws IllegalArgumentException If it isn't a number.
     */
    private static JsonPrimitive primitiveNumber(@NonNull JsonElement element, String what) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw wrongShape(what, "a number");
        }
        return primitive;
    }

    /**
     * The error for a value of the wrong shape.
     *
     * @param what What the value is.
     * @param wanted What it should be.
     * @return The exception to throw.
     */
    private static IllegalArgumentException wrongShape(String what, String wanted) {
        return new IllegalArgumentException(
                "The glTF JSON's \"" + what + "\" isn't " + wanted + " as it should be");
    }

    /** Static helpers only. */
    private GltfJson() {}
}
