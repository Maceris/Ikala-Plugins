package com.ikalagaming.factory.world.gen.data;

import lombok.NonNull;

/**
 * A problem found in world generation data, naming the file and the field it is in, so modders can
 * go straight to it.
 *
 * @param file The data ID of the file, like {@code lotomation:overworld/terrain}, or a path for
 *     files that don't have an ID yet.
 * @param field The path of the field inside the file, like {@code args[1].arg}, or empty for the
 *     whole file.
 * @param severity How bad it is.
 * @param code Which problem it is: the key of its message in the strings bundle, like {@code
 *     WORLDGEN_UNKNOWN_TYPE}. Stable, for tools and tests.
 * @param message What is wrong, localized.
 */
public record Diagnostic(
        @NonNull String file,
        @NonNull String field,
        @NonNull Severity severity,
        @NonNull String code,
        @NonNull String message) {

    /** How bad a problem is. */
    public enum Severity {
        /** The file can't be used, and is rejected. */
        ERROR,
        /** Probably a mistake, like a misspelled field, but the file still loads. */
        WARNING
    }

    @Override
    public String toString() {
        final String where = field.isEmpty() ? file : file + " " + field;
        return severity + " " + where + ": " + message;
    }
}
