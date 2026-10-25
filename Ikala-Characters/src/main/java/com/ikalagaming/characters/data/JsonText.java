package com.ikalagaming.characters.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lombok.NonNull;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JSON text leniently, so comments are allowed, and finds where a JSON path is in the text
 * for error messages, since parsed trees don't keep positions.
 *
 * @author Ches Burks
 */
public final class JsonText {
    /** Gson's position text, which its readers and exceptions end with. */
    private static final Pattern POSITION = Pattern.compile(" at line (\\d+) column (\\d+)");

    /**
     * A line and column in the text, starting at 1, or 0 if unknown.
     *
     * @param line The line.
     * @param column The column.
     */
    public record Position(int line, int column) {
        /** An unknown position. */
        public static final Position UNKNOWN = new Position(0, 0);
    }

    /**
     * A problem parsing the text.
     *
     * @param position Where.
     * @param path The JSON path at that point.
     * @param message What is wrong.
     */
    public record ParseError(@NonNull Position position, @NonNull String path, String message) {}

    private JsonText() {}

    /**
     * Find where Gson says a reader or exception is, from its description.
     *
     * @param description The reader's {@code toString()} or the exception's message.
     * @return The position, or {@link Position#UNKNOWN}.
     */
    private static Position position(String description) {
        if (description == null) {
            return Position.UNKNOWN;
        }
        Matcher matcher = POSITION.matcher(description);
        if (!matcher.find()) {
            return Position.UNKNOWN;
        }
        return new Position(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /**
     * Create a lenient reader, which allows comments.
     *
     * @param text The text.
     * @return The reader.
     */
    private static JsonReader reader(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LENIENT);
        return reader;
    }

    /**
     * Parse text into a tree. Keys repeated in one object are errors, since the later value would
     * silently win.
     *
     * @param text The JSON text.
     * @param errors Where problems go. Duplicate keys are reported here and parsing continues.
     * @return The tree, or null if the text isn't JSON, after reporting why.
     */
    public static JsonElement parse(@NonNull String text, @NonNull Consumer<ParseError> errors) {
        JsonReader reader = reader(text);
        try {
            JsonElement result = read(reader, errors);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                errors.accept(
                        new ParseError(
                                position(reader.toString()),
                                reader.getPath(),
                                "More text after the end of the JSON value"));
                return null;
            }
            return result;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            Matcher matcher = POSITION.matcher(message);
            String reason = matcher.find() ? message.substring(0, matcher.start()) : message;
            errors.accept(new ParseError(position(message), reader.getPath(), reason));
            return null;
        }
    }

    /**
     * Read one value into a tree.
     *
     * @param reader The reader, before the value.
     * @param errors Where duplicate keys are reported.
     * @return The value.
     * @throws IOException If the text isn't valid JSON.
     */
    private static JsonElement read(JsonReader reader, Consumer<ParseError> errors)
            throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        errors.accept(
                                new ParseError(
                                        position(reader.toString()),
                                        reader.getPath(),
                                        "The key '" + name + "' is repeated in this object"));
                    }
                    object.add(name, read(reader, errors));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(read(reader, errors));
                }
                reader.endArray();
                return array;
            }
            case STRING -> {
                return new JsonPrimitive(reader.nextString());
            }
            case NUMBER -> {
                String number = reader.nextString();
                try {
                    return new JsonPrimitive(new BigDecimal(number));
                } catch (NumberFormatException e) {
                    // Lenient reading allows NaN and the infinities, which BigDecimal can't hold
                    return new JsonPrimitive(Double.parseDouble(number));
                }
            }
            case BOOLEAN -> {
                return new JsonPrimitive(reader.nextBoolean());
            }
            case NULL -> {
                reader.nextNull();
                return JsonNull.INSTANCE;
            }
            default ->
                    throw new IllegalStateException(
                            "Expected a value but found " + reader.peek() + reader);
        }
    }

    /**
     * Find where a value is in the text, for error messages. This reads the text again, so only
     * call it when reporting a problem.
     *
     * @param text The JSON text.
     * @param path The value's JSON path, in Gson's form, like {@code $.slots[0].bones[1]}.
     * @return Its position, or {@link Position#UNKNOWN} if it isn't there.
     */
    public static Position locate(@NonNull String text, @NonNull String path) {
        JsonReader reader = reader(text);
        try {
            Position found = find(reader, path);
            return found == null ? Position.UNKNOWN : found;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            return Position.UNKNOWN;
        }
    }

    /**
     * Find a path within one value.
     *
     * @param reader The reader, before the value.
     * @param path The path to find.
     * @return Its position, or null if it isn't within this value.
     * @throws IOException If the text isn't valid JSON.
     */
    private static Position find(JsonReader reader, String path) throws IOException {
        JsonToken token = reader.peek();
        if (reader.getPath().equals(path)) {
            return position(reader.toString());
        }
        switch (token) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                while (reader.hasNext()) {
                    reader.nextName();
                    Position found = find(reader, path);
                    if (found != null) {
                        return found;
                    }
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    Position found = find(reader, path);
                    if (found != null) {
                        return found;
                    }
                }
                reader.endArray();
            }
            default -> reader.skipValue();
        }
        return null;
    }
}
