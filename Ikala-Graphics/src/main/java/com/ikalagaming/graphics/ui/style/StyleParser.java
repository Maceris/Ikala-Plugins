package com.ikalagaming.graphics.ui.style;

import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;

import lombok.NonNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Parses style values written in YAML, for themes and UI specs alike. Problems are reported as
 * {@link ThemeException}s that start with where the value is, like {@code
 * classes.danger.background: not a color}.
 */
public final class StyleParser {

    /**
     * Parse a style: a map of property names to values, with state overrides like {@code hovered}.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param tokenExists Checks that a referenced token exists, or null to check later, when the
     *     style is used.
     * @return The style.
     * @throws ThemeException If the style is broken.
     */
    public static Style style(
            @NonNull Object value, @NonNull String path, Predicate<String> tokenExists) {
        return style(value, path, tokenExists, true);
    }

    /**
     * Parse a style.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param tokenExists Checks that a referenced token exists, or null to skip the check.
     * @param allowStates Whether state overrides are allowed, which they aren't inside a state.
     * @return The style.
     */
    private static Style style(
            Object value, String path, Predicate<String> tokenExists, boolean allowStates) {
        Style.Builder builder = Style.builder();
        for (Map.Entry<String, Object> entry : map(value, path).entrySet()) {
            String where = path + "." + entry.getKey();
            StyleKey key = StyleKey.byName(entry.getKey());
            if (key != null) {
                builder.set(key, value(entry.getValue(), key.getType(), where, tokenExists));
                continue;
            }
            StyleState state = StyleState.byName(entry.getKey());
            if (state != null && allowStates) {
                builder.state(state, style(entry.getValue(), where, tokenExists, false));
                continue;
            }
            throw new ThemeException(where + ": unknown style property '" + entry.getKey() + "'");
        }
        return builder.build();
    }

    /**
     * Parse one value: a {@code $token} reference, or a literal checked against the type.
     *
     * @param value The YAML value.
     * @param type The type the value must have.
     * @param where Where it is, for messages.
     * @param tokenExists Checks that a referenced token exists, or null to skip the check.
     * @return A {@link Token}, or the converted literal.
     * @throws ThemeException If the value is missing, the wrong type, or an unknown token.
     */
    public static Object value(
            Object value,
            StyleKey.@NonNull ValueType type,
            @NonNull String where,
            Predicate<String> tokenExists) {
        if (value == null) {
            throw new ThemeException(where + ": missing value");
        }
        Token token = StyleValues.tokenOf(value);
        if (token != null) {
            if (tokenExists != null && !tokenExists.test(token.name())) {
                throw new ThemeException(where + ": unknown token " + token);
            }
            return token;
        }
        try {
            return StyleValues.convert(value, type);
        } catch (IllegalArgumentException e) {
            throw new ThemeException(where + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parse a length literal: a number of UI units, or {@code 8u}, {@code 50%} or {@code 1.5em}.
     *
     * @param value The YAML value.
     * @param where Where it is, for messages.
     * @return The length.
     * @throws ThemeException If it isn't a length.
     */
    public static Length length(@NonNull Object value, @NonNull String where) {
        return (Length) literal(value, StyleKey.ValueType.LENGTH, where);
    }

    /**
     * Parse insets: one length, or a list of 1, 2 or 4 in CSS order.
     *
     * @param value The YAML value.
     * @param where Where it is, for messages.
     * @return The insets.
     * @throws ThemeException If they aren't insets.
     */
    public static Insets insets(@NonNull Object value, @NonNull String where) {
        return (Insets) literal(value, StyleKey.ValueType.INSETS, where);
    }

    /**
     * Whether a written value refers to a theme token.
     *
     * @param value The YAML value.
     * @return True if it is a {@code $token} reference.
     */
    public static boolean isToken(Object value) {
        return StyleValues.tokenOf(value) != null;
    }

    /**
     * Convert a literal, rejecting token references.
     *
     * @param value The YAML value.
     * @param type The type wanted.
     * @param where Where it is, for messages.
     * @return The converted value.
     */
    private static Object literal(Object value, StyleKey.ValueType type, String where) {
        if (isToken(value)) {
            throw new ThemeException(where + ": a theme token can't be used here");
        }
        try {
            return StyleValues.convert(value, type);
        } catch (IllegalArgumentException e) {
            throw new ThemeException(where + ": " + e.getMessage(), e);
        }
    }

    /**
     * Read a YAML map with string keys.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The map.
     * @throws ThemeException If it isn't a map with string keys.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value, @NonNull String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ThemeException(path + ": expected a map of names to values");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new ThemeException(path + ": the key " + key + " is not a name");
            }
        }
        return (Map<String, Object>) map;
    }

    /** Static parsing only. */
    private StyleParser() {}
}
