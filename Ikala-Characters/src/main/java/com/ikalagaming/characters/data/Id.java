package com.ikalagaming.characters.data;

import lombok.NonNull;

import java.util.regex.Pattern;

/**
 * A namespaced definition ID, like {@code ikala:part/biped/forearm_robot}. The namespace belongs to
 * the plugin or mod that defines it, and the path names the definition within that namespace.
 *
 * @param namespace The namespace, lowercase letters, digits, underscores and hyphens.
 * @param path The path, lowercase letters, digits, underscores, hyphens, dots and slashes, with no
 *     empty segments.
 * @author Ches Burks
 */
public record Id(@NonNull String namespace, @NonNull String path) implements Comparable<Id> {
    /** What a namespace may contain. */
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_-]+");

    /** What a path may contain: segments separated by single slashes. */
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    /**
     * Create an ID.
     *
     * @param namespace The namespace.
     * @param path The path.
     * @throws IllegalArgumentException If either part has characters it may not.
     */
    public Id {
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "The namespace '"
                            + namespace
                            + "' may only use lowercase letters, digits, _ and -");
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "The path '"
                            + path
                            + "' may only use lowercase letters, digits, _, -, . and single /"
                            + " between parts");
        }
    }

    /**
     * Parse an ID written as {@code namespace:path}.
     *
     * @param text The text.
     * @return The ID.
     * @throws IllegalArgumentException If it isn't a valid ID.
     */
    public static Id parse(@NonNull String text) {
        int colon = text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "'" + text + "' is not an ID, which is written as namespace:path");
        }
        return new Id(text.substring(0, colon), text.substring(colon + 1));
    }

    /**
     * Whether this ID matches a pattern, which is either an exact ID or one ending in {@code *},
     * which matches any ID that starts with the rest, like {@code ikala:slider/biped/*}.
     *
     * @param pattern The pattern.
     * @return Whether this ID matches it.
     */
    public boolean matches(@NonNull String pattern) {
        String text = toString();
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return text.equals(pattern);
    }

    @Override
    public int compareTo(Id other) {
        int namespaces = namespace.compareTo(other.namespace);
        return namespaces != 0 ? namespaces : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
