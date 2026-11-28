package com.ikalagaming.converter.index;

import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * How a kind of file's values are read and written through its {@link Schema}, for the Details
 * panel: the schema, and the file's format.
 */
public interface AssetValues {
    /**
     * The fields the files have.
     *
     * @return The schema.
     */
    Schema schema();

    /**
     * Read a file's values.
     *
     * @param file The file.
     * @return The values, with what is wrong with them.
     * @throws IOException If it can't be read.
     */
    Schema.Values read(@NonNull Path file) throws IOException;

    /**
     * Write a file's values, only those that differ from their defaults where the format allows,
     * keeping what the schema doesn't cover, like a file's comment or a material's textures.
     *
     * @param file The file.
     * @param values The values by field name; a missing one is its default.
     * @throws IOException If it can't be written.
     */
    void write(@NonNull Path file, @NonNull Map<String, Object> values) throws IOException;
}
