package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A value a texture graph exposes by name, which Parameter nodes read and, later, instances
 * override: its type, its value, the range a slider offers, and the group and order it is listed
 * in, so every details panel reads the same. Written as one line of YAML, leaving out what is at
 * its default, so a change is a one-line diff:
 *
 * <pre>
 * wear: {type: scalar, value: 0.4, max: 2, step: 0.1, group: Surface, order: 1}
 * </pre>
 *
 * @param name The name, letters, digits and underscores, starting with a letter.
 * @param kind What it holds.
 * @param value Its value, as text in the kind's form.
 * @param min A scalar's lowest value on a slider.
 * @param max A scalar's highest value on a slider.
 * @param step A scalar's step on a slider, 0 for any value.
 * @param choices A choice's options.
 * @param group The group it is listed under, empty for none.
 * @param order Where it is listed in its group, lowest first.
 */
public record GraphParameter(
        @NonNull String name,
        @NonNull Kind kind,
        @NonNull String value,
        float min,
        float max,
        float step,
        @NonNull List<String> choices,
        @NonNull String group,
        int order) {

    /** What a parameter's name may be. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /** The key holding the kind. */
    private static final String TYPE = "type";

    /** The key holding the value. */
    private static final String VALUE = "value";

    /** What a parameter holds. */
    public enum Kind {
        /** A number, offered on a slider between a min and max. */
        SCALAR("scalar", "0"),
        /** A color, {@code #RRGGBB} or {@code #RRGGBBAA}, sRGB. */
        COLOR("color", "#808080"),
        /** On or off; a Switch node reading it evaluates only the branch it picks. */
        SWITCH("switch", "false"),
        /** One of a list of options, read as its index. */
        CHOICE("choice", ""),
        /** An image file, relative to the graph. */
        TEXTURE("texture", ""),
        /** A seed, a whole number, read as a random number from 0 up to 1. */
        SEED("seed", "1");

        /** Its name in files. */
        public final String id;

        /** A new parameter's value. */
        public final String initial;

        Kind(String id, String initial) {
            this.id = id;
            this.initial = initial;
        }

        /**
         * The kind with a name.
         *
         * @param id The name.
         * @return The kind, or null if there is none.
         */
        public static @Nullable Kind of(@Nullable String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /**
     * Check the parameter.
     *
     * @throws IllegalArgumentException If the name isn't allowed.
     */
    public GraphParameter {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "A parameter's name is letters, digits and underscores, starting with a"
                            + " letter, not '"
                            + name
                            + "'");
        }
        choices = List.copyOf(choices);
    }

    /**
     * A new parameter of a kind with its usual settings.
     *
     * @param name The name.
     * @param kind The kind.
     * @return The parameter.
     */
    public static GraphParameter of(@NonNull String name, @NonNull Kind kind) {
        return new GraphParameter(name, kind, kind.initial, 0, 1, 0, List.of(), "", 0);
    }

    /**
     * This parameter with another value.
     *
     * @param newValue The value.
     * @return The changed parameter.
     */
    public GraphParameter withValue(@NonNull String newValue) {
        return new GraphParameter(name, kind, newValue, min, max, step, choices, group, order);
    }

    /**
     * Whether a value is one this parameter can hold.
     *
     * @param text The value.
     * @return Null if it can, else what is wrong.
     */
    public @Nullable String check(@NonNull String text) {
        try {
            switch (kind) {
                case SCALAR -> Float.parseFloat(text.trim());
                case COLOR -> color(text);
                case SWITCH -> {
                    if (!"true".equals(text) && !"false".equals(text)) {
                        return "A switch is true or false";
                    }
                }
                case CHOICE -> {
                    if (!choices.contains(text)) {
                        return "Pick one of " + choices;
                    }
                }
                case SEED -> Long.parseLong(text.trim());
                case TEXTURE -> {
                    // Any file name; a missing file shows on the node that reads it
                }
            }
        } catch (NumberFormatException e) {
            return kind == Kind.SEED ? "A seed is a whole number" : "Not a number: " + text;
        } catch (TextureNode.NodeException e) {
            return e.getMessage();
        }
        return null;
    }

    /**
     * Read a color value: linear red, green, blue and alpha.
     *
     * @param text {@code #RRGGBB} or {@code #RRGGBBAA}.
     * @return The color.
     * @throws TextureNode.NodeException If it isn't a color.
     */
    public static float[] color(@NonNull String text) {
        final String trimmed = text.trim();
        if ((trimmed.length() == 7 || trimmed.length() == 9) && trimmed.charAt(0) == '#') {
            try {
                final long packed = Long.parseLong(trimmed.substring(1), 16);
                final boolean alpha = trimmed.length() == 9;
                final int rgb = (int) (alpha ? packed >> 8 : packed);
                return new float[] {
                    Srgb.decode(rgb >> 16 & 0xFF),
                    Srgb.decode(rgb >> 8 & 0xFF),
                    Srgb.decode(rgb & 0xFF),
                    alpha ? (packed & 0xFF) / (float) Srgb.MAX_BYTE : 1
                };
            } catch (NumberFormatException e) {
                // Falls through to the error below
            }
        }
        throw new TextureNode.NodeException("A color is #RRGGBB or #RRGGBBAA in hex, not " + text);
    }

    /**
     * The parameter as one line of YAML after its name, leaving out settings at their defaults.
     *
     * @return Like {@code {type: scalar, value: 0.5}}.
     */
    public String write() {
        final List<String> parts = new ArrayList<>();
        parts.add(TYPE + ": " + kind.id);
        final boolean bare =
                (kind == Kind.SCALAR || kind == Kind.SWITCH || kind == Kind.SEED)
                        && check(value) == null;
        parts.add(VALUE + ": " + (bare ? value.trim() : quote(value)));
        if (kind == Kind.SCALAR) {
            if (min != 0) {
                parts.add("min: " + number(min));
            }
            if (max != 1) {
                parts.add("max: " + number(max));
            }
            if (step != 0) {
                parts.add("step: " + number(step));
            }
        }
        if (!choices.isEmpty()) {
            parts.add(
                    "choices: ["
                            + String.join(
                                    ", ", choices.stream().map(GraphParameter::quote).toList())
                            + "]");
        }
        if (!group.isEmpty()) {
            parts.add("group: " + quote(group));
        }
        if (order != 0) {
            parts.add("order: " + order);
        }
        return "{" + String.join(", ", parts) + "}";
    }

    /**
     * Read a parameter from its YAML map.
     *
     * @param name Its name.
     * @param map Its settings.
     * @return The parameter.
     * @throws IllegalArgumentException If the name or type is wrong, or the value doesn't fit.
     */
    public static GraphParameter read(@NonNull String name, @NonNull Map<?, ?> map) {
        final Kind kind = Kind.of(String.valueOf(map.get(TYPE)));
        if (kind == null) {
            throw new IllegalArgumentException(
                    name + ": type is one of scalar, color, switch, choice, texture or seed");
        }
        final List<String> choices = new ArrayList<>();
        if (map.get("choices") instanceof List<?> list) {
            for (Object choice : list) {
                choices.add(String.valueOf(choice));
            }
        }
        final Object value = map.get(VALUE);
        final GraphParameter parameter =
                new GraphParameter(
                        name,
                        kind,
                        value == null ? kind.initial : String.valueOf(value),
                        number(map.get("min"), 0),
                        number(map.get("max"), 1),
                        number(map.get("step"), 0),
                        choices,
                        map.get("group") == null ? "" : String.valueOf(map.get("group")),
                        (int) number(map.get("order"), 0));
        final String problem = parameter.check(parameter.value());
        if (problem != null) {
            throw new IllegalArgumentException(name + ": " + problem);
        }
        return parameter;
    }

    /**
     * Read the parameters of a YAML map of them, by name.
     *
     * @param section The map, or null.
     * @param problems Gets each one that couldn't be read, which is left out.
     * @return The parameters, by name, in the file's order.
     */
    public static Map<String, GraphParameter> readAll(
            @Nullable Object section, @NonNull List<String> problems) {
        final Map<String, GraphParameter> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        if (!(section instanceof Map<?, ?> map)) {
            problems.add("parameters is a map of names to settings");
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            final String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> settings)) {
                problems.add(name + ": a parameter is a map of settings");
                continue;
            }
            try {
                out.put(name, read(name, settings));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return out;
    }

    /**
     * Write parameters as a YAML section, sorted by name.
     *
     * @param key The section's key.
     * @param parameters The parameters.
     * @return The lines, or empty for none.
     */
    public static String writeAll(
            @NonNull String key, @NonNull Iterable<GraphParameter> parameters) {
        final java.util.TreeMap<String, GraphParameter> sorted = new java.util.TreeMap<>();
        for (GraphParameter parameter : parameters) {
            sorted.put(parameter.name(), parameter);
        }
        if (sorted.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(key).append(":\n");
        for (GraphParameter parameter : sorted.values()) {
            out.append("  ")
                    .append(parameter.name())
                    .append(": ")
                    .append(parameter.write())
                    .append('\n');
        }
        return out.toString();
    }

    /**
     * A YAML number, or a default.
     *
     * @param value The value read.
     * @param fallback The default.
     * @return The number.
     */
    private static float number(@Nullable Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * A number as short text.
     *
     * @param value The number.
     * @return Without a needless ".0".
     */
    private static String number(float value) {
        return value == Math.rint(value) && Math.abs(value) < 1e7
                ? Long.toString((long) value)
                : Float.toString(value).toLowerCase(Locale.ROOT);
    }

    /**
     * Quote text for YAML.
     *
     * @param text The text.
     * @return It in single quotes.
     */
    static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
