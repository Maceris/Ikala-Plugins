package com.ikalagaming.graphics.ui.spec;

import static com.ikalagaming.graphics.ui.spec.SpecKeys.*;

import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.style.StyleParser;
import com.ikalagaming.graphics.ui.style.ThemeException;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the values UI specs use: sizes, anchors, lengths, choices and lists of names. */
public final class SpecValues {

    /**
     * Read a YAML map with string keys.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The map.
     * @throws SpecException If it isn't a map with string keys.
     */
    public static Map<String, Object> map(Object value, @NonNull String path) {
        try {
            return StyleParser.map(value, path);
        } catch (ThemeException e) {
            throw new SpecException(e.getMessage(), e);
        }
    }

    /**
     * Read a YAML list.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The list.
     * @throws SpecException If it isn't a list.
     */
    public static List<?> list(Object value, @NonNull String path) {
        if (!(value instanceof List<?> list)) {
            throw new SpecException(path + ": expected a list");
        }
        return list;
    }

    /**
     * Read a name, which must be a non-empty string.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The name.
     * @throws SpecException If it isn't a name.
     */
    public static String name(Object value, @NonNull String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new SpecException(path + ": expected a name");
        }
        return text;
    }

    /**
     * Read a list of names, or a single name.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The names.
     */
    public static List<String> names(Object value, @NonNull String path) {
        if (value instanceof String) {
            return List.of(name(value, path));
        }
        List<String> names = new ArrayList<>();
        List<?> list = list(value, path);
        for (int i = 0; i < list.size(); ++i) {
            names.add(name(list.get(i), path + "[" + i + "]"));
        }
        return List.copyOf(names);
    }

    /**
     * Read a length: a number of UI units, or {@code 8u}, {@code 50%} or {@code 1.5em}.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The length.
     * @throws SpecException If it isn't a length.
     */
    public static Length length(Object value, @NonNull String path) {
        if (value == null) {
            throw new SpecException(path + ": missing value");
        }
        try {
            return StyleParser.length(value, path);
        } catch (ThemeException e) {
            throw new SpecException(e.getMessage(), e);
        }
    }

    /**
     * Read insets: one length, or a list of 1, 2 or 4 in CSS order.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The insets.
     * @throws SpecException If they aren't insets.
     */
    public static Insets insets(Object value, @NonNull String path) {
        if (value == null) {
            throw new SpecException(path + ": missing value");
        }
        try {
            return StyleParser.insets(value, path);
        } catch (ThemeException e) {
            throw new SpecException(e.getMessage(), e);
        }
    }

    /**
     * Read a number.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The number.
     * @throws SpecException If it isn't a number.
     */
    public static float number(Object value, @NonNull String path) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        throw new SpecException(path + ": expected a number, not " + value);
    }

    /**
     * Read a fixed number of numbers, written as a list like {@code [10, 20]}.
     *
     * @param value The YAML value.
     * @param count How many numbers there must be.
     * @param path Where it is, for messages.
     * @return The numbers.
     * @throws SpecException If it isn't a list of that many numbers.
     */
    public static float[] numbers(Object value, int count, @NonNull String path) {
        if (!(value instanceof List<?> items) || items.size() != count) {
            throw new SpecException(path + ": expected a list of " + count + " numbers");
        }
        float[] result = new float[count];
        for (int i = 0; i < count; ++i) {
            result[i] = number(items.get(i), path + "[" + i + "]");
        }
        return result;
    }

    /**
     * Read a fixed number of lengths, written as a list like {@code [48, "2em"]}.
     *
     * @param value The YAML value.
     * @param count How many lengths there must be.
     * @param path Where it is, for messages.
     * @return The lengths.
     * @throws SpecException If it isn't a list of that many lengths.
     */
    public static Length[] lengths(Object value, int count, @NonNull String path) {
        if (!(value instanceof List<?> items) || items.size() != count) {
            throw new SpecException(path + ": expected a list of " + count + " lengths");
        }
        Length[] result = new Length[count];
        for (int i = 0; i < count; ++i) {
            result[i] = length(items.get(i), path + "[" + i + "]");
        }
        return result;
    }

    /**
     * Read a yes or no.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The value.
     * @throws SpecException If it isn't true or false.
     */
    public static boolean bool(Object value, @NonNull String path) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new SpecException(path + ": expected true or false, not " + value);
    }

    /**
     * Read one of an enum's values, written in lower case with dashes, like {@code space-between}.
     *
     * @param value The YAML value.
     * @param type The enum.
     * @param path Where it is, for messages.
     * @return The value.
     * @param <E> The enum type.
     * @throws SpecException If it isn't one of the values.
     */
    public static <E extends Enum<E>> E choice(
            Object value, @NonNull Class<E> type, @NonNull String path) {
        String text = name(value, path);
        for (E constant : type.getEnumConstants()) {
            if (spelling(constant).equals(text)) {
                return constant;
            }
        }
        List<String> options = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            options.add(spelling(constant));
        }
        throw new SpecException(path + ": expected one of " + options + ", not " + text);
    }

    /**
     * How an enum value is written in specs.
     *
     * @param constant The value.
     * @return Lower case with dashes.
     */
    private static String spelling(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Read a size: {@code fit}, {@code grow}, a length, {@code {grow: 2, min: 100, max: 400}} or
     * {@code {fit: true, min: 100}}.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The sizing.
     * @throws SpecException If it isn't a size.
     */
    public static Sizing sizing(Object value, @NonNull String path) {
        if (FIT.equals(value)) {
            return Sizing.fit();
        }
        if (GROW.equals(value)) {
            return Sizing.grow();
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = map(value, path);
            Length min = map.containsKey(MIN) ? length(map.get(MIN), at(path, MIN)) : Sizing.NO_MIN;
            Length max = map.containsKey(MAX) ? length(map.get(MAX), at(path, MAX)) : Sizing.NO_MAX;
            for (String key : map.keySet()) {
                if (!List.of(GROW, FIT, MIN, MAX).contains(key)) {
                    throw new SpecException(path + "." + key + ": unknown size option");
                }
            }
            if (map.containsKey(GROW)) {
                Object weight = map.get(GROW);
                float grow = weight instanceof Boolean ? 1 : number(weight, at(path, GROW));
                if (grow <= 0) {
                    throw new SpecException(path + ".grow: must be positive");
                }
                return Sizing.grow(grow, min, max);
            }
            if (map.containsKey(FIT)) {
                return Sizing.fit(min, max);
            }
            throw new SpecException(path + ": a size map needs " + GROW + " or " + FIT);
        }
        return Sizing.fixed(length(value, path));
    }

    /**
     * Read anchors: a preset like {@code fill}, {@code center} or {@code top-right}, {@code {fill:
     * 8}} for a margin, or {@code {at: [0.5, 0.5], offset: [0, -20]}}.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The anchors.
     * @throws SpecException If they aren't anchors.
     */
    public static Anchors anchors(Object value, @NonNull String path) {
        if (value instanceof String preset) {
            return switch (preset) {
                case FILL -> Anchors.fill();
                case CENTER -> Anchors.center();
                case TOP_LEFT -> Anchors.topLeft();
                case TOP -> Anchors.at(0.5f, 0);
                case TOP_RIGHT -> Anchors.topRight();
                case LEFT -> Anchors.at(0, 0.5f);
                case RIGHT -> Anchors.at(1, 0.5f);
                case BOTTOM_LEFT -> Anchors.bottomLeft();
                case BOTTOM -> Anchors.at(0.5f, 1);
                case BOTTOM_RIGHT -> Anchors.bottomRight();
                default ->
                        throw new SpecException(
                                path
                                        + ": unknown anchor preset '"
                                        + preset
                                        + "', use fill, center, top, top-left and so on");
            };
        }
        Map<String, Object> map = map(value, path);
        for (String key : map.keySet()) {
            if (!List.of(FILL, AT, OFFSET).contains(key)) {
                throw new SpecException(path + "." + key + ": unknown anchor option");
            }
        }
        if (map.containsKey(FILL)) {
            return Anchors.fill(length(map.get(FILL), at(path, FILL)));
        }
        if (!map.containsKey(AT)) {
            throw new SpecException(path + ": anchors need a preset, " + FILL + " or " + AT);
        }
        List<?> at = list(map.get(AT), at(path, AT));
        if (at.size() != 2) {
            throw new SpecException(path + ".at: expected [x, y]");
        }
        Anchors anchors =
                Anchors.at(number(at.get(0), path + ".at[0]"), number(at.get(1), path + ".at[1]"));
        if (map.containsKey(OFFSET)) {
            List<?> offset = list(map.get(OFFSET), at(path, OFFSET));
            if (offset.size() != 2) {
                throw new SpecException(path + ".offset: expected [x, y]");
            }
            anchors =
                    anchors.offset(
                            length(offset.get(0), path + ".offset[0]"),
                            length(offset.get(1), path + ".offset[1]"));
        }
        return anchors;
    }

    /** Static parsing only. */
    private SpecValues() {}
}
