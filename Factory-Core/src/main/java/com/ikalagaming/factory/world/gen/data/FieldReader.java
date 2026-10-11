package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.NodeType;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads one value inside a data file, remembering where it is, so every problem can name the file
 * and the field path, like {@code args[1].arg}. Reading a missing or wrongly typed value records a
 * diagnostic and gives a harmless default, so a whole file can be checked in one pass.
 */
public final class FieldReader {

    /** Collects problems, and knows which file is being read. */
    public interface Problems {
        /**
         * Record a problem.
         *
         * @param file The file's data ID.
         * @param field The field's path in the file.
         * @param severity How bad it is.
         * @param code The message key.
         * @param args The message's values.
         */
        void report(
                @NonNull String file,
                @NonNull String field,
                Diagnostic.Severity severity,
                @NonNull String code,
                Object... args);
    }

    /**
     * The data ID of the file. -- GETTER -- The file's data ID.
     *
     * @return The ID.
     */
    @Getter private final String file;

    /**
     * Where the value is inside the file. -- GETTER -- The field path.
     *
     * @return The path, empty for the file itself.
     */
    @Getter private final String path;

    /** The value: a {@link Node}, a list, a number, a string, a boolean, or null if missing. */
    private final Object value;

    /** Where problems go. */
    private final Problems problems;

    /**
     * Read a value.
     *
     * @param file The file's data ID.
     * @param path Where the value is inside the file.
     * @param value The value, or null if it is missing.
     * @param problems Where problems go.
     */
    public FieldReader(
            @NonNull String file, @NonNull String path, Object value, @NonNull Problems problems) {
        this.file = file;
        this.path = path;
        this.value = value;
        this.problems = problems;
    }

    /**
     * Whether the value is there at all.
     *
     * @return True if it isn't missing.
     */
    public boolean exists() {
        return value != null;
    }

    /**
     * Whether the value is an object.
     *
     * @return True for {@code {...}}.
     */
    public boolean isNode() {
        return value instanceof Node;
    }

    /**
     * Whether the value is a number.
     *
     * @return True for any numeric type.
     */
    public boolean isNumber() {
        return value instanceof Number;
    }

    /**
     * Whether the value is a string.
     *
     * @return True for {@code "..."}.
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * Whether the value is a list.
     *
     * @return True for {@code [X;...]}.
     */
    public boolean isList() {
        return value instanceof List<?>;
    }

    /**
     * Whether an object has a field.
     *
     * @param key The field name.
     * @return True if this is an object with that field.
     */
    public boolean has(@NonNull String key) {
        return value instanceof Node node && node.hasChild(key);
    }

    /**
     * A field of this object. Missing if it isn't there, or this isn't an object.
     *
     * @param key The field name.
     * @return The field's reader.
     */
    public FieldReader get(@NonNull String key) {
        Object child = null;
        if (value instanceof Node node && node.hasChild(key)) {
            final Optional<NodeType> type = node.getType(key);
            child =
                    type.isPresent() && type.get() == NodeType.NODE
                            ? node.getNode(key)
                            : node.get(key);
        }
        return new FieldReader(file, path.isEmpty() ? key : path + "." + key, child, problems);
    }

    /**
     * The field names of this object.
     *
     * @return The names, sorted, or empty if this isn't an object.
     */
    public List<String> keys() {
        return value instanceof Node node ? node.getKeys() : List.of();
    }

    /**
     * Warn about fields this object has but nothing reads, which are usually misspellings.
     *
     * @param known The fields that mean something here.
     */
    public void warnUnknownFields(@NonNull Set<String> known) {
        for (String key : keys()) {
            if (!known.contains(key)) {
                problems.report(
                        file,
                        path.isEmpty() ? key : path + "." + key,
                        Diagnostic.Severity.WARNING,
                        "WORLDGEN_UNKNOWN_FIELD",
                        key);
            }
        }
    }

    /**
     * The value as a string, required.
     *
     * @return The string, or empty if it is missing or not a string.
     */
    public String string() {
        if (value instanceof String text) {
            return text;
        }
        expected("WORLDGEN_EXPECTED_STRING");
        return "";
    }

    /**
     * The value as a string, if it is there.
     *
     * @param fallback What to use if it is missing.
     * @return The string.
     */
    public String string(@NonNull String fallback) {
        return exists() ? string() : fallback;
    }

    /**
     * The value as a boolean, if it is there.
     *
     * @param fallback What to use if it is missing.
     * @return The boolean, or the fallback if it is missing or not a boolean.
     */
    public boolean bool(boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        error("WORLDGEN_EXPECTED_BOOLEAN");
        return fallback;
    }

    /**
     * The value as a number, required.
     *
     * @return The number, or 0 if it is missing or not a number.
     */
    public double number() {
        if (value instanceof Number number) {
            final double result = number.doubleValue();
            if (Double.isFinite(result)) {
                return result;
            }
        }
        expected("WORLDGEN_EXPECTED_NUMBER");
        return 0;
    }

    /**
     * The value as a number, if it is there.
     *
     * @param fallback What to use if it is missing.
     * @return The number.
     */
    public double number(double fallback) {
        return exists() ? number() : fallback;
    }

    /**
     * The value as a whole number, required.
     *
     * @return The number, or 0 if it is missing or not a whole number.
     */
    public int integer() {
        final double number = number();
        if (number != Math.rint(number) || Math.abs(number) > Integer.MAX_VALUE) {
            error("WORLDGEN_EXPECTED_INTEGER");
            return 0;
        }
        return (int) number;
    }

    /**
     * The value as a whole number, if it is there.
     *
     * @param fallback What to use if it is missing.
     * @return The number.
     */
    public int integer(int fallback) {
        return exists() ? integer() : fallback;
    }

    /**
     * The value as a list of readers, one per element.
     *
     * @return The elements, or empty if it is missing or not a list.
     */
    public List<FieldReader> list() {
        if (!(value instanceof List<?> elements)) {
            expected("WORLDGEN_EXPECTED_LIST");
            return List.of();
        }
        List<FieldReader> result = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); ++i) {
            result.add(new FieldReader(file, path + "[" + i + "]", elements.get(i), problems));
        }
        return result;
    }

    /**
     * The value as a fixed number of numbers, from a numeric list. A single number fills every
     * slot, like a {@code wavelength} the same on every axis.
     *
     * @param count How many numbers there must be.
     * @return The numbers, zeros if it is missing or malformed.
     */
    public double[] numbers(int count) {
        double[] result = new double[count];
        if (value instanceof Number) {
            java.util.Arrays.fill(result, number());
            return result;
        }
        final List<FieldReader> elements = list();
        if (elements.size() != count) {
            error("WORLDGEN_WRONG_COUNT", count, elements.size());
            return result;
        }
        for (int i = 0; i < count; ++i) {
            result[i] = elements.get(i).number();
        }
        return result;
    }

    /**
     * The value as a list of strings.
     *
     * @return The strings, or empty if it is missing or malformed.
     */
    public List<String> strings() {
        return list().stream().map(FieldReader::string).toList();
    }

    /**
     * Record an error at this field.
     *
     * @param code The message key.
     * @param args The message's values.
     */
    public void error(@NonNull String code, Object... args) {
        problems.report(file, path, Diagnostic.Severity.ERROR, code, args);
    }

    /**
     * Record a warning at this field.
     *
     * @param code The message key.
     * @param args The message's values.
     */
    public void warn(@NonNull String code, Object... args) {
        problems.report(file, path, Diagnostic.Severity.WARNING, code, args);
    }

    /**
     * Record that the value isn't what was expected, or is missing.
     *
     * @param code The message key for what was expected.
     */
    private void expected(@NonNull String code) {
        error(value == null ? "WORLDGEN_MISSING_FIELD" : code);
    }
}
