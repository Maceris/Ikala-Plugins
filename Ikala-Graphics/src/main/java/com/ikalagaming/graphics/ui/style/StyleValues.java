package com.ikalagaming.graphics.ui.style;

import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;

import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Converts written values, from theme files or tokens, into the type a property takes. The same
 * token can feed different properties, so {@code spacing.medium: 8} can be a gap (a length), a
 * padding (insets) or a font size (a number) depending on where it is used.
 */
final class StyleValues {

    /**
     * Convert a value to the type a property takes.
     *
     * @param value The value: a typed value, a number, a string, or a list for insets.
     * @param type The type wanted.
     * @return The converted value.
     * @throws IllegalArgumentException If the value can't be that type.
     */
    static Object convert(@NonNull Object value, StyleKey.@NonNull ValueType type) {
        return switch (type) {
            case COLOR -> color(value);
            case LENGTH -> length(value);
            case INSETS -> insets(value);
            case NUMBER -> number(value);
            case NUMBERS -> numbers(value);
            case WORDS -> words(value);
        };
    }

    /**
     * Whether a written value refers to a token.
     *
     * @param value The value.
     * @return The token, or null if it is a literal.
     */
    static Token tokenOf(Object value) {
        if (value instanceof Token token) {
            return token;
        }
        if (value instanceof String text && text.startsWith("$") && text.length() > 1) {
            return new Token(text.substring(1));
        }
        return null;
    }

    /**
     * Convert a color: a packed int, or {@code #RRGGBB} or {@code #RRGGBBAA}.
     *
     * @param value The value.
     * @return The packed color, red in the high byte.
     */
    private static int color(Object value) {
        if (value instanceof Integer packed) {
            return packed;
        }
        if (value instanceof String text && text.startsWith("#")) {
            String hex = text.substring(1);
            if (hex.length() == 6 || hex.length() == 8) {
                try {
                    long parsed = Long.parseLong(hex, 16);
                    return (int) (hex.length() == 6 ? parsed << 8 | 0xFF : parsed);
                } catch (NumberFormatException e) {
                    // Reported below
                }
            }
        }
        throw new IllegalArgumentException("not a color, use #RRGGBB or #RRGGBBAA: " + value);
    }

    /**
     * Convert a length: a number of UI units, or a string like {@code 8}, {@code 8u}, {@code 50%}
     * or {@code 1.5em}.
     *
     * @param value The value.
     * @return The length.
     */
    private static Length length(Object value) {
        if (value instanceof Length length) {
            return length;
        }
        if (value instanceof Number number) {
            return Length.u(number.floatValue());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            try {
                if (trimmed.endsWith("%")) {
                    return Length.percent(parse(trimmed, 1) / 100);
                }
                if (trimmed.endsWith("em")) {
                    return Length.em(parse(trimmed, 2));
                }
                if (trimmed.endsWith("u")) {
                    return Length.u(parse(trimmed, 1));
                }
                return Length.u(Float.parseFloat(trimmed));
            } catch (NumberFormatException e) {
                // Reported below
            }
        }
        throw new IllegalArgumentException(
                "not a length, use a number of units, 8u, 50% or 1.5em: " + value);
    }

    /**
     * Parse the number before a unit suffix.
     *
     * @param text The text.
     * @param suffix How many characters the unit takes.
     * @return The number.
     */
    private static float parse(String text, int suffix) {
        return Float.parseFloat(text.substring(0, text.length() - suffix).trim());
    }

    /**
     * Convert insets: one length for every side, or a list of 1, 2 or 4 lengths in CSS order (top,
     * right, bottom, left).
     *
     * @param value The value.
     * @return The insets.
     */
    private static Insets insets(Object value) {
        if (value instanceof Insets insets) {
            return insets;
        }
        if (value instanceof List<?> list) {
            return switch (list.size()) {
                case 1 -> Insets.all(length(list.get(0)));
                case 2 -> Insets.symmetric(length(list.get(1)), length(list.get(0)));
                case 4 ->
                        new Insets(
                                length(list.get(3)),
                                length(list.get(0)),
                                length(list.get(1)),
                                length(list.get(2)));
                default ->
                        throw new IllegalArgumentException(
                                "padding takes 1, 2 or 4 values, not " + list.size());
            };
        }
        return Insets.all(length(value));
    }

    /**
     * Convert a plain number.
     *
     * @param value The value.
     * @return The number.
     */
    private static Float number(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException e) {
                // Reported below
            }
        }
        throw new IllegalArgumentException("not a number: " + value);
    }

    /**
     * Convert a list of numbers: a YAML list, one number, or numbers separated by spaces.
     *
     * @param value The value.
     * @return The numbers, which can't be modified.
     */
    private static List<Float> numbers(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(StyleValues::number).toList();
        }
        if (value instanceof String text && text.trim().contains(" ")) {
            return Arrays.stream(text.trim().split("\\s+")).map(StyleValues::number).toList();
        }
        return List.of(number(value));
    }

    /**
     * Convert a keyword, with any arguments after it, like {@code round 6}.
     *
     * @param value The value.
     * @return The words, with single spaces between them.
     */
    private static String words(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return String.join(" ", text.trim().split("\\s+"));
        }
        throw new IllegalArgumentException("not a keyword: " + value);
    }

    /** Static helpers only. */
    private StyleValues() {}
}
