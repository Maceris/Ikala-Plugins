package com.ikalagaming.characters.data;

import lombok.NonNull;

/**
 * Where problems with one definition go. Paths are relative to the definition, starting with {@code
 * $}, like {@code $.slots[0]}.
 *
 * @author Ches Burks
 */
public interface Report {
    /**
     * Report something that makes the definition unusable.
     *
     * @param path Where, relative to the definition.
     * @param message What is wrong.
     */
    void error(@NonNull String path, @NonNull String message);

    /**
     * Report something that is probably a mistake but still works.
     *
     * @param path Where, relative to the definition.
     * @param message What is wrong.
     */
    void warning(@NonNull String path, @NonNull String message);
}
