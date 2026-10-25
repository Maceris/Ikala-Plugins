package com.ikalagaming.characters.data;

import lombok.NonNull;

/**
 * Something wrong with character content, and where it is.
 *
 * @param severity How bad it is.
 * @param file The file it is in, as shown to people.
 * @param line The line, starting at 1, or 0 if unknown.
 * @param column The column, starting at 1, or 0 if unknown.
 * @param path The JSON path within the file, like {@code $.slots[0].bones}.
 * @param message What is wrong.
 * @author Ches Burks
 */
public record Problem(
        @NonNull Severity severity,
        @NonNull String file,
        int line,
        int column,
        @NonNull String path,
        @NonNull String message) {

    /** How bad a problem is. */
    public enum Severity {
        /** The definition it is in can't be used. */
        ERROR,
        /** Probably a mistake, but the definition still works. */
        WARNING
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(file);
        if (line > 0) {
            text.append(':').append(line).append(':').append(column);
        }
        text.append(": ")
                .append(severity == Severity.ERROR ? "error" : "warning")
                .append(": ")
                .append(message);
        if (!path.isEmpty()) {
            text.append(" (at ").append(path).append(')');
        }
        return text.toString();
    }
}
