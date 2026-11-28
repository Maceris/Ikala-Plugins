package com.ikalagaming.factory.kvt;

import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Reads and writes KVT content files through {@link Schema}s: a file's keys handed to its schema as
 * plain values, and values written back as KVT, only those that differ from their defaults, in the
 * schema's order, keeping the file's leading comment, its other keys, and the number types it
 * already used.
 */
public final class KvtSchemas {

    /** Not made. */
    private KvtSchemas() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * A tree's keys and their values as plain Java values, in the tree's order.
     *
     * @param root The tree.
     * @return The values by key.
     */
    public static Map<String, Object> raw(@NonNull Node root) {
        final Map<String, Object> raw = new LinkedHashMap<>();
        for (String key : root.getKeys()) {
            final Object value = root.get(key);
            if (value != null) {
                raw.put(key, decimal(value));
            }
        }
        return raw;
    }

    /**
     * A value with floats as the doubles their text reads as, so 0.1F is 0.1, not
     * 0.10000000149011612, in lists too.
     *
     * @param value The value as the tree holds it.
     * @return The value.
     */
    private static Object decimal(Object value) {
        if (value instanceof Float number) {
            return Double.parseDouble(number.toString());
        }
        if (value instanceof List<?> list && list.stream().anyMatch(Float.class::isInstance)) {
            return list.stream().map(KvtSchemas::decimal).toList();
        }
        return value;
    }

    /**
     * Read a tree through a schema.
     *
     * @param root The tree.
     * @param schema The schema.
     * @return The values, with what is wrong.
     */
    public static Schema.Values read(@NonNull Node root, @NonNull Schema schema) {
        return schema.read(raw(root));
    }

    /**
     * Write values as a KVT file's text, numbers as doubles where the file has none of a field.
     *
     * @param original The file's text now, whose leading comment, other keys and number types are
     *     kept, or null for a new file.
     * @param schema The schema.
     * @param values The values by field name, as {@link Schema.Values#given()} holds them.
     * @return The new text.
     */
    public static String write(
            @Nullable String original,
            @NonNull Schema schema,
            @NonNull Map<String, Object> values) {
        return write(original, schema, values, NodeType.DOUBLE);
    }

    /**
     * Write values as a KVT file's text.
     *
     * @param original The file's text now, whose leading comment, other keys and number types are
     *     kept, or null for a new file.
     * @param schema The schema.
     * @param values The values by field name, as {@link Schema.Values#given()} holds them.
     * @param numbers How to store a number the file has no type for yet: {@link NodeType#FLOAT} or
     *     {@link NodeType#DOUBLE}.
     * @return The new text.
     */
    public static String write(
            @Nullable String original,
            @NonNull Schema schema,
            @NonNull Map<String, Object> values,
            @NonNull NodeType numbers) {
        final Node old = parse(original).orElseGet(Node::new);
        final List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : schema.differing(values).entrySet()) {
            final Field field = schema.field(entry.getKey());
            parts.add(
                    Node.formatKey(entry.getKey())
                            + ":"
                            + valueText(
                                    field, entry.getValue(), old.getType(entry.getKey()), numbers));
        }
        // Keys the schema doesn't know stay as they were
        for (String key : old.getKeys()) {
            if (schema.field(key) == null) {
                parts.add(Node.formatKey(key) + ":" + old.values.get(key));
            }
        }
        final String header = header(original);
        final boolean newline = original == null || original.endsWith("\n");
        return header + "{" + String.join(", ", parts) + "}" + (newline ? "\n" : "");
    }

    /**
     * A file's text up to its tree: its leading comment and the space after it.
     *
     * @param original The text, or null.
     * @return The header, empty if there is none.
     */
    static String header(@Nullable String original) {
        if (original == null) {
            return "";
        }
        final int open = original.indexOf('{');
        return open < 0 ? "" : original.substring(0, open);
    }

    /**
     * Parse a file's text, if there is any and it is valid.
     *
     * @param text The text, or null.
     * @return The tree, or empty.
     */
    private static Optional<Node> parse(@Nullable String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return TreeStringSerialization.fromString(text);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * A value as KVT text.
     *
     * @param field Its field.
     * @param value The value, of the field's Java type.
     * @param had The type the file stored it as, if it had it.
     * @param numbers How to store a number without a type yet.
     * @return The text.
     */
    private static String valueText(
            Field field, Object value, Optional<NodeType> had, NodeType numbers) {
        return switch (field.kind()) {
            case TEXT, CHOICE, REFERENCE ->
                    new ValueNode<>(NodeType.STRING, (String) value).toString();
            case FLAG -> value.toString();
            case NUMBER -> {
                final NodeType type =
                        had.filter(t -> t == NodeType.FLOAT || t == NodeType.DOUBLE)
                                .orElse(numbers);
                final double number = ((Number) value).doubleValue();
                yield type == NodeType.FLOAT
                        ? new ValueNode<>(NodeType.FLOAT, (float) number).toString()
                        : new ValueNode<>(NodeType.DOUBLE, number).toString();
            }
            case INTEGER -> {
                final long number = ((Number) value).longValue();
                yield had.orElse(NodeType.INTEGER) == NodeType.LONG
                        ? new ValueNode<>(NodeType.LONG, number).toString()
                        : new ValueNode<>(NodeType.INTEGER, (int) number).toString();
            }
            case TEXT_LIST -> {
                final List<String> texts = new ArrayList<>();
                for (Object text : (List<?>) value) {
                    texts.add(new ValueNode<>(NodeType.STRING, (String) text).toString());
                }
                yield "[T;" + String.join(", ", texts) + "]";
            }
            case NUMBER_LIST -> {
                final boolean floats =
                        had.filter(t -> t == NodeType.FLOAT_ARRAY).isPresent()
                                || had.isEmpty() && numbers == NodeType.FLOAT;
                final List<String> texts = new ArrayList<>();
                for (Object number : (List<?>) value) {
                    final double d = ((Number) number).doubleValue();
                    texts.add(floats ? Float.toString((float) d) : Double.toString(d));
                }
                yield (floats ? "[F;" : "[D;") + String.join(", ", texts) + "]";
            }
        };
    }
}
