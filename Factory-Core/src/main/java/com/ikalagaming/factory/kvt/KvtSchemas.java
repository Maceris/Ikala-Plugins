package com.ikalagaming.factory.kvt;

import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        if (value instanceof Node node) {
            // Nested objects as maps, for schemas of objects
            return raw(node);
        }
        if (value instanceof List<?> list
                && list.stream().anyMatch(v -> v instanceof Float || v instanceof Node)) {
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
            case OBJECT, LIST, TYPED, UNION, MAP -> nestedText(field, value, numbers);
            case TEXT, CHOICE, REFERENCE, COLOR ->
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

    /**
     * An object's, list's, map's, typed object's or union's value as KVT text, on one line.
     *
     * @param field Its field.
     * @param value The value.
     * @param numbers How to store numbers.
     * @return The text.
     * @throws IllegalArgumentException If a list mixes kinds of value KVT can't hold together.
     */
    private static String nestedText(Field field, Object value, NodeType numbers) {
        final Field.Shape shape = Objects.requireNonNull(field.shape(), field.name());
        return switch (field.kind()) {
            case OBJECT -> objectText(shape.objectSchema(), asMap(value), null, numbers);
            case TYPED -> {
                final Map<String, Object> map = asMap(value);
                final String key = shape.variants().key();
                final String type = (String) map.get(key);
                yield objectText(shape.variants().schemaOf(type).orElse(null), map, key, numbers);
            }
            case MAP -> {
                final List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Object> entry : asMap(value).entrySet()) {
                    parts.add(
                            Node.formatKey(entry.getKey())
                                    + ":"
                                    + valueText(
                                            shape.element().named(entry.getKey()),
                                            entry.getValue(),
                                            Optional.empty(),
                                            numbers));
                }
                yield "{" + String.join(", ", parts) + "}";
            }
            case UNION -> valueText(alternative(field, value), value, Optional.empty(), numbers);
            case LIST -> {
                final List<?> list = (List<?>) value;
                final List<String> parts = new ArrayList<>();
                for (Object element : list) {
                    parts.add(valueText(shape.element(), element, Optional.empty(), numbers));
                }
                yield "[" + arrayPrefix(list, numbers) + ";" + String.join(", ", parts) + "]";
            }
            default -> throw new IllegalArgumentException(field.name() + " isn't nested");
        };
    }

    /**
     * An object's text: its fields in schema order, a typed object's type first.
     *
     * @param schema Its fields, or null to write its entries as they are.
     * @param map Its values.
     * @param typeKey Its type field's key, written first, or null.
     * @param numbers How to store numbers.
     * @return The text.
     */
    private static String objectText(
            @Nullable Schema schema,
            Map<String, Object> map,
            @Nullable String typeKey,
            NodeType numbers) {
        final List<String> parts = new ArrayList<>();
        if (typeKey != null) {
            parts.add(
                    Node.formatKey(typeKey)
                            + ":"
                            + new ValueNode<>(NodeType.STRING, (String) map.get(typeKey)));
        }
        if (schema == null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!entry.getKey().equals(typeKey)) {
                    parts.add(Node.formatKey(entry.getKey()) + ":" + entry.getValue());
                }
            }
        } else {
            for (Field field : schema.fields()) {
                final Object value = map.get(field.name());
                if (value != null) {
                    parts.add(
                            Node.formatKey(field.name())
                                    + ":"
                                    + valueText(field, value, Optional.empty(), numbers));
                }
            }
        }
        return "{" + String.join(", ", parts) + "}";
    }

    /**
     * The KVT array prefix for a list's values: nodes, text, or numbers.
     *
     * @param list The values.
     * @param numbers How to store numbers.
     * @return The prefix letter.
     * @throws IllegalArgumentException If it mixes kinds.
     */
    private static String arrayPrefix(List<?> list, NodeType numbers) {
        if (list.stream().allMatch(Map.class::isInstance)) {
            return "N";
        }
        if (list.stream().allMatch(String.class::isInstance)) {
            return "T";
        }
        if (list.stream().allMatch(Number.class::isInstance)) {
            return numbers == NodeType.FLOAT ? "F" : "D";
        }
        throw new IllegalArgumentException(
                "A KVT list holds one kind of value; this mixes objects, text and numbers");
    }

    /**
     * The alternative of a union a value is.
     *
     * @param union The union.
     * @param value The value.
     * @return The alternative, named as the union.
     * @throws IllegalArgumentException If none fits.
     */
    private static Field alternative(Field union, Object value) {
        for (Field alternative : Objects.requireNonNull(union.shape()).alternatives()) {
            final boolean fits =
                    switch (alternative.kind()) {
                        case NUMBER, INTEGER -> value instanceof Number;
                        case FLAG -> value instanceof Boolean;
                        case TEXT, CHOICE, REFERENCE, COLOR -> value instanceof String;
                        case TEXT_LIST, NUMBER_LIST, LIST -> value instanceof List<?>;
                        case OBJECT, TYPED, MAP -> value instanceof Map<?, ?>;
                        case UNION -> false;
                    };
            if (fits) {
                return alternative.named(union.name());
            }
        }
        throw new IllegalArgumentException(union.name() + " can't be " + value);
    }

    /**
     * A value as a map of string keys.
     *
     * @param value The value, a map.
     * @return The map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    /**
     * Edit a file's text in place to hold new values: only what changed between the old and the new
     * values is written, a value replaced where it is, a key added before its object's closing
     * brace or taken out with its comma, so the rest of the text stays as it was.
     *
     * @param original The file's text, holding the old values.
     * @param schema The file's fields.
     * @param before The old values, as read, by field name.
     * @param after The new values, by field name; a missing one is taken out.
     * @param numbers How to store new numbers.
     * @return The new text.
     * @throws IllegalArgumentException If the text isn't valid KVT.
     */
    public static String edit(
            @NonNull String original,
            @NonNull Schema schema,
            @NonNull Map<String, Object> before,
            @NonNull Map<String, Object> after,
            @NonNull NodeType numbers) {
        final KvtText text =
                KvtText.parse(original)
                        .orElseThrow(() -> new IllegalArgumentException("Not valid KVT"));
        editObject(text, "", schema.fields(), before, after, numbers);
        return text.result();
    }

    /**
     * Edit a file whose whole text is one field's value, like a world generation file.
     *
     * @param original The file's text, holding the old value.
     * @param root The field the file is.
     * @param before The old value, as read.
     * @param after The new value.
     * @param numbers How to store new numbers.
     * @return The new text.
     * @throws IllegalArgumentException If the text isn't valid KVT.
     */
    public static String edit(
            @NonNull String original,
            @NonNull Field root,
            @NonNull Object before,
            @NonNull Object after,
            @NonNull NodeType numbers) {
        final KvtText text =
                KvtText.parse(original)
                        .orElseThrow(() -> new IllegalArgumentException("Not valid KVT"));
        editValue(text, "", root, before, after, numbers);
        return text.result();
    }

    /**
     * Edit an object's entries.
     *
     * @param text The text.
     * @param path The object's path.
     * @param fields Its fields.
     * @param before Its old values.
     * @param after Its new values.
     * @param numbers How to store new numbers.
     */
    private static void editObject(
            KvtText text,
            String path,
            List<Field> fields,
            Map<String, Object> before,
            Map<String, Object> after,
            NodeType numbers) {
        for (Field field : fields) {
            editEntry(
                    text, path, field, before.get(field.name()), after.get(field.name()), numbers);
        }
    }

    /**
     * Edit one entry of an object.
     *
     * @param text The text.
     * @param path The object's path.
     * @param field The entry's field, named as its key.
     * @param before Its old value, or null if it had none.
     * @param after Its new value, or null to take it out.
     * @param numbers How to store new numbers.
     */
    private static void editEntry(
            KvtText text,
            String path,
            Field field,
            @Nullable Object before,
            @Nullable Object after,
            NodeType numbers) {
        if (Objects.equals(before, after)) {
            return;
        }
        final String child = path.isEmpty() ? field.name() : path + "." + field.name();
        if (after == null) {
            if (text.has(child)) {
                text.remove(child);
            }
        } else if (before == null || !text.has(child)) {
            text.add(path, field.name(), valueText(field, after, Optional.empty(), numbers));
        } else {
            editValue(text, child, field, before, after, numbers);
        }
    }

    /**
     * Edit a value that is in the text, going into objects and lists to change only what changed.
     *
     * @param text The text.
     * @param path The value's path.
     * @param field Its field.
     * @param before Its old value.
     * @param after Its new value.
     * @param numbers How to store new numbers.
     */
    private static void editValue(
            KvtText text, String path, Field field, Object before, Object after, NodeType numbers) {
        if (before.equals(after)) {
            return;
        }
        final Field.Shape shape = field.shape();
        switch (field.kind()) {
            case OBJECT -> {
                if (before instanceof Map<?, ?> && after instanceof Map<?, ?>) {
                    editObject(
                            text,
                            path,
                            shape.objectSchema().fields(),
                            asMap(before),
                            asMap(after),
                            numbers);
                    return;
                }
            }
            case TYPED -> {
                if (before instanceof Map<?, ?> && after instanceof Map<?, ?>) {
                    final String key = shape.variants().key();
                    final Object type = asMap(after).get(key);
                    final Optional<Schema> schema =
                            type instanceof String name
                                    ? shape.variants().schemaOf(name)
                                    : Optional.empty();
                    if (type != null && type.equals(asMap(before).get(key)) && schema.isPresent()) {
                        editObject(
                                text,
                                path,
                                schema.get().fields(),
                                asMap(before),
                                asMap(after),
                                numbers);
                        return;
                    }
                }
            }
            case MAP -> {
                if (before instanceof Map<?, ?> && after instanceof Map<?, ?>) {
                    final java.util.Set<String> keys =
                            new java.util.LinkedHashSet<>(asMap(before).keySet());
                    keys.addAll(asMap(after).keySet());
                    for (String key : keys) {
                        editEntry(
                                text,
                                path,
                                shape.element().named(key),
                                asMap(before).get(key),
                                asMap(after).get(key),
                                numbers);
                    }
                    return;
                }
            }
            case UNION -> {
                final Field was = alternative(field, before);
                final Field is = alternative(field, after);
                if (was.kind() == is.kind()) {
                    editValue(text, path, is, before, after, numbers);
                    return;
                }
            }
            case LIST -> {
                if (before instanceof List<?> old
                        && after instanceof List<?> now
                        && old.size() == now.size()) {
                    for (int i = 0; i < now.size(); ++i) {
                        editValue(
                                text,
                                path + "[" + i + "]",
                                shape.element().named(field.name()),
                                old.get(i),
                                now.get(i),
                                numbers);
                    }
                    return;
                }
            }
            case NUMBER, INTEGER -> {
                text.replace(path, numberText((Number) after, text.valueText(path).orElse("")));
                return;
            }
            default -> {
                // Replaced whole, below
            }
        }
        final Optional<NodeType> had =
                text.valueText(path)
                        .filter(t -> t.startsWith("[F;"))
                        .map(t -> NodeType.FLOAT_ARRAY);
        text.replace(path, valueText(field, after, had, numbers));
    }

    /**
     * A number written the way the number it replaces was: a float as a float, a whole number
     * without a point if it stays whole.
     *
     * @param value The number.
     * @param old The text it replaces.
     * @return The text.
     */
    static String numberText(Number value, String old) {
        final double number = value.doubleValue();
        final String suffix = old.isEmpty() ? "" : old.substring(old.length() - 1);
        if ("F".equalsIgnoreCase(suffix)) {
            return Float.toString((float) number) + "F";
        }
        if ("D".equalsIgnoreCase(suffix)) {
            return Double.toString(number) + "D";
        }
        final boolean whole =
                !old.isEmpty() && old.chars().allMatch(c -> c == '-' || Character.isDigit(c));
        if (whole && number == Math.rint(number) && Math.abs(number) < Long.MAX_VALUE) {
            return Long.toString((long) number);
        }
        return Double.toString(number);
    }
}
