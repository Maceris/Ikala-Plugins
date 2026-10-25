package com.ikalagaming.characters.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An sRGB color, written as {@code #rrggbb}.
 *
 * @param red Red, 0 to 255.
 * @param green Green, 0 to 255.
 * @param blue Blue, 0 to 255.
 * @author Ches Burks
 */
public record Color(int red, int green, int blue) {
    /** What a color looks like. */
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Reads and writes colors. */
    static final ValueCodec<Color> CODEC =
            new ValueCodec<>() {
                @Override
                public Color read(
                        @NonNull JsonElement json, @NonNull String path, @NonNull Report report) {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                        String text = json.getAsString();
                        if (HEX.matcher(text).matches()) {
                            return parse(text);
                        }
                    }
                    report.error(path, "Expected a color like \"#c58c62\", not " + json);
                    return null;
                }

                @Override
                public JsonElement write(@NonNull Color value) {
                    return new JsonPrimitive(value.toString());
                }

                @Override
                public JsonObject schema() {
                    JsonObject schema = new JsonObject();
                    schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_STRING);
                    schema.addProperty(SchemaKeys.PATTERN, "^#[0-9a-fA-F]{6}$");
                    return schema;
                }
            };

    /**
     * Create a color.
     *
     * @param red Red, 0 to 255.
     * @param green Green, 0 to 255.
     * @param blue Blue, 0 to 255.
     * @throws IllegalArgumentException If a channel is out of range.
     */
    public Color {
        if ((red | green | blue) < 0 || red > 255 || green > 255 || blue > 255) {
            throw new IllegalArgumentException("Color channels go from 0 to 255");
        }
    }

    /**
     * Parse a color written as {@code #rrggbb}.
     *
     * @param text The text.
     * @return The color.
     * @throws IllegalArgumentException If it isn't a color.
     */
    public static Color parse(@NonNull String text) {
        if (!HEX.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a color like #c58c62");
        }
        int value = Integer.parseInt(text.substring(1), 16);
        return new Color((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
    }
}
