package com.ikalagaming.characters.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;

/**
 * Reads, writes and describes one type of value that isn't a record, list, map or plain value, like
 * an {@link Id} written as a string.
 *
 * @param <T> The type of value.
 * @author Ches Burks
 */
public interface ValueCodec<T> {
    /**
     * Read a value.
     *
     * @param json The JSON, never null.
     * @param path Where it is, for problems.
     * @param report Where problems go.
     * @return The value, or null after reporting why it can't be read.
     */
    T read(@NonNull JsonElement json, @NonNull String path, @NonNull Report report);

    /**
     * Write a value.
     *
     * @param value The value.
     * @return The JSON.
     */
    JsonElement write(@NonNull T value);

    /**
     * Describe the JSON this reads, as a JSON Schema.
     *
     * @return The schema.
     */
    JsonObject schema();
}
