package com.ikalagaming.converter.index;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A kind of file the asset index knows: which files are of it, where IDs of it live in a content
 * folder, and what such a file references. Types are registered with the index, so it knows nothing
 * of any one game's content.
 */
public interface AssetType {
    /**
     * The type's name, as the editor shows it, like {@code texture}.
     *
     * @return The name.
     */
    String name();

    /**
     * The folder under a content folder holding files of this type by ID, like {@code textures}, or
     * null for a type that has no IDs.
     *
     * @return The folder, or null.
     */
    default @Nullable String kind() {
        return null;
    }

    /**
     * The extensions a file of an ID may have, in the order they are looked for, like {@code .ika}
     * then {@code .png}.
     *
     * @return The extensions.
     */
    default List<String> extensions() {
        return List.of();
    }

    /**
     * Whether a file is of this type.
     *
     * @param root The root it is under.
     * @param relative Its path under the root, in forward slashes.
     * @return True if it is.
     */
    boolean matches(AssetIndex.@NonNull Root root, @NonNull String relative);

    /**
     * What a file of this type references.
     *
     * @param file The file.
     * @param root The root it is under.
     * @param relative Its path under the root, in forward slashes.
     * @return The references.
     * @throws IOException If the file can't be read.
     */
    default List<AssetIndex.Ref> references(
            @NonNull Path file, AssetIndex.@NonNull Root root, @NonNull String relative)
            throws IOException {
        return List.of();
    }
}
