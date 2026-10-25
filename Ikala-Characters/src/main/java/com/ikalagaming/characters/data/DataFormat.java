package com.ikalagaming.characters.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps JSON onto records and back, and describes the JSON as a JSON Schema, all from the records'
 * components. Each component is a JSON field of the same name.
 *
 * <p>Supported types are records, {@code List}, {@code Map} with string keys, {@code String},
 * {@code int}, {@code float}, {@code double}, {@code boolean}, enums (written in lower camel case,
 * so {@code LOOK_AT} is {@code "lookAt"}), and the types with a {@link ValueCodec}. Fields are
 * required unless marked {@link Optional} or given a {@link Default}, and unknown fields are
 * errors, so typos don't go unnoticed.
 *
 * @author Ches Burks
 */
public final class DataFormat {
    /** The field that names a definition's parent, handled before records are read. */
    public static final String EXTENDS = "extends";

    /** Codecs for the value types that aren't read field by field. */
    private static final Map<Class<?>, ValueCodec<?>> CODECS =
            Map.of(
                    Id.class,
                    new IdCodec(),
                    Color.class,
                    Color.CODEC,
                    Vec3.class,
                    Vec3.CODEC,
                    Range.class,
                    Range.CODEC);

    private DataFormat() {}

    /** Reads and writes IDs as strings. */
    private static final class IdCodec implements ValueCodec<Id> {
        @Override
        public Id read(@NonNull JsonElement json, @NonNull String path, @NonNull Report report) {
            if (!isString(json)) {
                report.error(path, "Expected an ID like \"ikala:biped\", not " + json);
                return null;
            }
            try {
                return Id.parse(json.getAsString());
            } catch (IllegalArgumentException e) {
                report.error(path, e.getMessage());
                return null;
            }
        }

        @Override
        public JsonElement write(@NonNull Id value) {
            return new JsonPrimitive(value.toString());
        }

        @Override
        public JsonObject schema() {
            JsonObject schema = new JsonObject();
            schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_STRING);
            schema.addProperty(SchemaKeys.PATTERN, "^[a-z0-9_-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*$");
            return schema;
        }
    }

    /**
     * Read a definition from a JSON object, ignoring {@value #EXTENDS}, which was already resolved.
     *
     * @param <T> The type of record.
     * @param json The JSON.
     * @param type The record class.
     * @param report Where problems go.
     * @return The record, or null if there were errors.
     */
    public static <T extends Record> T readDefinition(
            @NonNull JsonElement json, @NonNull Class<T> type, @NonNull Report report) {
        return readRecord(json, type, JsonPath.ROOT, report, Set.of(EXTENDS));
    }

    /**
     * Read a value of any supported type.
     *
     * @param json The JSON.
     * @param type The type to read.
     * @param path Where it is, for problems.
     * @param report Where problems go.
     * @return The value, or null if there were errors.
     */
    public static Object read(
            @NonNull JsonElement json,
            @NonNull Type type,
            @NonNull String path,
            @NonNull Report report) {
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            Type[] arguments = parameterized.getActualTypeArguments();
            if (raw == List.class) {
                return readList(json, arguments[0], path, report);
            }
            if (raw == Map.class && arguments[0] == String.class) {
                return readMap(json, arguments[1], path, report);
            }
        } else if (type instanceof Class<?> c) {
            return readClass(json, c, path, report);
        }
        throw new IllegalArgumentException("Unsupported field type " + type);
    }

    /**
     * Read a value of a plain class.
     *
     * @param json The JSON.
     * @param type The class.
     * @param path Where it is.
     * @param report Where problems go.
     * @return The value, or null if there were errors.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readClass(JsonElement json, Class<?> type, String path, Report report) {
        ValueCodec<?> codec = CODECS.get(type);
        if (codec != null) {
            return codec.read(json, path, report);
        }
        if (type == String.class) {
            if (isString(json)) {
                return json.getAsString();
            }
            report.error(path, "Expected a string, not " + json);
            return null;
        }
        if (type == boolean.class || type == Boolean.class) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
                return json.getAsBoolean();
            }
            report.error(path, "Expected true or false, not " + json);
            return null;
        }
        if (type == int.class || type == Integer.class) {
            BigDecimal number = number(json);
            try {
                if (number != null) {
                    return number.intValueExact();
                }
            } catch (ArithmeticException e) {
                // Reported below
            }
            report.error(path, "Expected a whole number, not " + json);
            return null;
        }
        if (type == float.class || type == Float.class) {
            BigDecimal number = number(json);
            if (number != null && Float.isFinite(number.floatValue())) {
                return number.floatValue();
            }
            report.error(path, "Expected a number, not " + json);
            return null;
        }
        if (type == double.class || type == Double.class) {
            BigDecimal number = number(json);
            if (number != null && Double.isFinite(number.doubleValue())) {
                return number.doubleValue();
            }
            report.error(path, "Expected a number, not " + json);
            return null;
        }
        if (type.isEnum()) {
            if (isString(json)) {
                for (Object constant : type.getEnumConstants()) {
                    if (enumName((Enum<?>) constant).equals(json.getAsString())) {
                        return constant;
                    }
                }
            }
            report.error(path, "Expected one of " + enumNames((Class) type) + ", not " + json);
            return null;
        }
        if (type.isRecord()) {
            return readRecord(json, (Class<? extends Record>) type, path, report, Set.of());
        }
        throw new IllegalArgumentException("Unsupported field type " + type);
    }

    /**
     * Read a list.
     *
     * @param json The JSON.
     * @param element The element type.
     * @param path Where it is.
     * @param report Where problems go.
     * @return The list, unmodifiable, or null if there were errors.
     */
    private static List<Object> readList(
            JsonElement json, Type element, String path, Report report) {
        if (!json.isJsonArray()) {
            report.error(path, "Expected a list in [ ], not " + json);
            return null;
        }
        JsonArray array = json.getAsJsonArray();
        List<Object> values = new ArrayList<>(array.size());
        boolean failed = false;
        for (int i = 0; i < array.size(); ++i) {
            JsonElement item = array.get(i);
            String itemPath = JsonPath.index(path, i);
            if (item.isJsonNull()) {
                report.error(itemPath, "Lists can't hold null");
                failed = true;
                continue;
            }
            Object value = read(item, element, itemPath, report);
            if (value == null) {
                failed = true;
            } else {
                values.add(value);
            }
        }
        return failed ? null : Collections.unmodifiableList(values);
    }

    /**
     * Read a map with string keys, keeping the order they were written in.
     *
     * @param json The JSON.
     * @param valueType The value type.
     * @param path Where it is.
     * @param report Where problems go.
     * @return The map, unmodifiable, or null if there were errors.
     */
    private static Map<String, Object> readMap(
            JsonElement json, Type valueType, String path, Report report) {
        if (!json.isJsonObject()) {
            report.error(path, "Expected an object in { }, not " + json);
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        boolean failed = false;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            String entryPath = JsonPath.field(path, entry.getKey());
            if (entry.getValue().isJsonNull()) {
                report.error(entryPath, "Values can't be null");
                failed = true;
                continue;
            }
            Object value = read(entry.getValue(), valueType, entryPath, report);
            if (value == null) {
                failed = true;
            } else {
                values.put(entry.getKey(), value);
            }
        }
        return failed ? null : Collections.unmodifiableMap(values);
    }

    /**
     * Read a record, field by field.
     *
     * @param <T> The record type.
     * @param json The JSON.
     * @param type The record class.
     * @param path Where it is.
     * @param report Where problems go.
     * @param ignored Field names to skip without complaint.
     * @return The record, or null if there were errors.
     */
    private static <T extends Record> T readRecord(
            JsonElement json, Class<T> type, String path, Report report, Set<String> ignored) {
        if (!json.isJsonObject()) {
            report.error(path, "Expected an object in { }, not " + json);
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        RecordComponent[] components = type.getRecordComponents();
        Object[] arguments = new Object[components.length];
        Set<String> known = new TreeSet<>();
        boolean failed = false;

        for (int i = 0; i < components.length; ++i) {
            RecordComponent component = components[i];
            String name = component.getName();
            known.add(name);
            JsonElement value = object.get(name);
            if (value == null || value.isJsonNull()) {
                Default fallback = component.getAnnotation(Default.class);
                if (fallback != null) {
                    value = parseDefault(fallback, type, name);
                } else if (component.isAnnotationPresent(Optional.class)) {
                    arguments[i] = empty(component.getType());
                    continue;
                } else {
                    report.error(path, "Missing the required field '" + name + "'");
                    failed = true;
                    continue;
                }
            }
            Object read =
                    read(value, component.getGenericType(), JsonPath.field(path, name), report);
            if (read == null) {
                failed = true;
            } else {
                arguments[i] = read;
            }
        }

        for (String name : object.keySet()) {
            if (!known.contains(name) && !ignored.contains(name)) {
                report.error(
                        JsonPath.field(path, name),
                        "Unknown field '" + name + "'; the fields here are " + known);
                failed = true;
            }
        }
        if (failed) {
            return null;
        }
        return construct(type, components, arguments, path, report);
    }

    /**
     * Call a record's canonical constructor, which may reject the values.
     *
     * @param <T> The record type.
     * @param type The record class.
     * @param components Its components.
     * @param arguments The values, in component order.
     * @param path Where it is.
     * @param report Where problems go.
     * @return The record, or null if the constructor rejected the values.
     */
    private static <T extends Record> T construct(
            Class<T> type,
            RecordComponent[] components,
            Object[] arguments,
            String path,
            Report report) {
        Class<?>[] parameters = new Class<?>[components.length];
        for (int i = 0; i < components.length; ++i) {
            parameters[i] = components[i].getType();
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException
                    || e.getCause() instanceof NullPointerException) {
                report.error(path, String.valueOf(e.getCause().getMessage()));
                return null;
            }
            throw new IllegalStateException("Couldn't create " + type.getSimpleName(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Couldn't create " + type.getSimpleName(), e);
        }
    }

    /**
     * Parse a {@link Default} annotation's JSON.
     *
     * @param fallback The annotation.
     * @param type The record it is on, for the message if it is broken.
     * @param name The field it is on.
     * @return The JSON.
     * @throws IllegalStateException If the default isn't JSON, which is a bug in the record.
     */
    private static JsonElement parseDefault(Default fallback, Class<?> type, String name) {
        JsonElement value =
                JsonText.parse(
                        fallback.value(),
                        error -> {
                            throw new IllegalStateException(
                                    "The default of "
                                            + type.getSimpleName()
                                            + "."
                                            + name
                                            + " isn't JSON: "
                                            + error.message());
                        });
        if (value == null) {
            throw new IllegalStateException(
                    "The default of " + type.getSimpleName() + "." + name + " isn't JSON");
        }
        return value;
    }

    /**
     * The value of an optional field that was left out.
     *
     * @param type The field's type.
     * @return An empty list or map, false or 0 for primitives, or null.
     */
    private static Object empty(Class<?> type) {
        if (type == List.class) {
            return List.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

    /**
     * Write a value as JSON. Null fields are left out, so the output of a read value reads back the
     * same.
     *
     * @param value The value.
     * @return The JSON.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JsonElement write(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        ValueCodec codec = CODECS.get(value.getClass());
        if (codec != null) {
            return codec.write(value);
        }
        switch (value) {
            case String text -> {
                return new JsonPrimitive(text);
            }
            case Boolean bool -> {
                return new JsonPrimitive(bool);
            }
            case Number number -> {
                return new JsonPrimitive(number);
            }
            case Enum<?> constant -> {
                return new JsonPrimitive(enumName(constant));
            }
            case List<?> list -> {
                JsonArray array = new JsonArray();
                list.forEach(item -> array.add(write(item)));
                return array;
            }
            case Map<?, ?> map -> {
                JsonObject object = new JsonObject();
                map.forEach((key, item) -> object.add((String) key, write(item)));
                return object;
            }
            case Record record -> {
                JsonObject object = new JsonObject();
                for (RecordComponent component : record.getClass().getRecordComponents()) {
                    Object field;
                    try {
                        component.getAccessor().setAccessible(true);
                        field = component.getAccessor().invoke(record);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Couldn't read " + component, e);
                    }
                    if (field != null) {
                        object.add(component.getName(), write(field));
                    }
                }
                return object;
            }
            default -> {}
        }
        throw new IllegalArgumentException("Unsupported value type " + value.getClass());
    }

    /**
     * Describe the JSON files for a kind of definition as a JSON Schema. A file holds one
     * definition or a list of them, and a definition with {@value #EXTENDS} only needs the fields
     * its parent doesn't give it.
     *
     * @param type The definition record.
     * @param title The schema's title.
     * @return The schema.
     */
    public static JsonObject schema(@NonNull Class<? extends Record> type, @NonNull String title) {
        Map<String, JsonObject> definitions = new java.util.TreeMap<>();
        JsonObject reference = schemaOf(type, definitions);

        JsonObject root = definitions.get(type.getSimpleName());
        JsonObject properties = root.getAsJsonObject(SchemaKeys.PROPERTIES);
        JsonObject parent = new JsonObject();
        parent.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_STRING);
        parent.addProperty(
                SchemaKeys.DESCRIPTION,
                "The ID of a definition of the same kind to start from. Fields written here"
                        + " replace the parent's, and objects merge field by field.");
        properties.add(EXTENDS, parent);
        JsonArray required =
                root.has(SchemaKeys.REQUIRED) ? root.getAsJsonArray(SchemaKeys.REQUIRED) : null;
        if (required != null) {
            root.remove(SchemaKeys.REQUIRED);
            JsonObject withParent = new JsonObject();
            JsonArray needsParent = new JsonArray();
            needsParent.add(EXTENDS);
            withParent.add(SchemaKeys.REQUIRED, needsParent);
            JsonObject complete = new JsonObject();
            complete.add(SchemaKeys.REQUIRED, required);
            JsonArray either = new JsonArray();
            either.add(withParent);
            either.add(complete);
            root.add(SchemaKeys.ANY_OF, either);
        }

        JsonObject schema = new JsonObject();
        schema.addProperty(SchemaKeys.SCHEMA, SchemaKeys.DRAFT);
        schema.addProperty(SchemaKeys.TITLE, title);
        Doc doc = type.getAnnotation(Doc.class);
        if (doc != null) {
            schema.addProperty(SchemaKeys.DESCRIPTION, doc.value());
        }
        JsonArray oneOf = new JsonArray();
        oneOf.add(reference);
        JsonObject list = new JsonObject();
        list.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_ARRAY);
        list.add(SchemaKeys.ITEMS, reference.deepCopy());
        oneOf.add(list);
        schema.add(SchemaKeys.ONE_OF, oneOf);
        JsonObject defs = new JsonObject();
        definitions.forEach(defs::add);
        schema.add(SchemaKeys.DEFS, defs);
        return schema;
    }

    /**
     * Describe one type, adding records to the shared definitions.
     *
     * @param type The type.
     * @param definitions Record schemas by simple name.
     * @return The schema, or a reference to the record's definition.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonObject schemaOf(Type type, Map<String, JsonObject> definitions) {
        JsonObject schema = new JsonObject();
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (parameterized.getRawType() == List.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_ARRAY);
                schema.add(SchemaKeys.ITEMS, schemaOf(arguments[0], definitions));
                return schema;
            }
            if (parameterized.getRawType() == Map.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_OBJECT);
                schema.add(SchemaKeys.ADDITIONAL_PROPERTIES, schemaOf(arguments[1], definitions));
                return schema;
            }
        } else if (type instanceof Class<?> c) {
            ValueCodec<?> codec = CODECS.get(c);
            if (codec != null) {
                return codec.schema();
            }
            if (c == String.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_STRING);
            } else if (c == boolean.class || c == Boolean.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_BOOLEAN);
            } else if (c == int.class || c == Integer.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_INTEGER);
            } else if (c == float.class
                    || c == Float.class
                    || c == double.class
                    || c == Double.class) {
                schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_NUMBER);
            } else if (c.isEnum()) {
                JsonArray values = new JsonArray();
                enumNames((Class) c).forEach(name -> values.add((String) name));
                schema.add(SchemaKeys.ENUM, values);
            } else if (c.isRecord()) {
                String name = c.getSimpleName();
                if (!definitions.containsKey(name)) {
                    definitions.put(name, new JsonObject());
                    definitions.put(name, recordSchema(c, definitions));
                }
                schema.addProperty(SchemaKeys.REF, SchemaKeys.DEFS_REF_PREFIX + name);
            } else {
                throw new IllegalArgumentException("Unsupported field type " + type);
            }
            return schema;
        }
        throw new IllegalArgumentException("Unsupported field type " + type);
    }

    /**
     * Describe a record.
     *
     * @param type The record class.
     * @param definitions Record schemas by simple name.
     * @return The schema.
     */
    private static JsonObject recordSchema(Class<?> type, Map<String, JsonObject> definitions) {
        JsonObject schema = new JsonObject();
        schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_OBJECT);
        Doc typeDoc = type.getAnnotation(Doc.class);
        if (typeDoc != null) {
            schema.addProperty(SchemaKeys.DESCRIPTION, typeDoc.value());
        }
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (RecordComponent component : type.getRecordComponents()) {
            JsonObject property = schemaOf(component.getGenericType(), definitions);
            Doc doc = component.getAnnotation(Doc.class);
            if (doc != null) {
                if (property.has(SchemaKeys.REF)) {
                    // Keywords beside a $ref apply too in 2020-12, but some editors ignore them
                    JsonObject wrapped = new JsonObject();
                    JsonArray all = new JsonArray();
                    all.add(property);
                    wrapped.add(SchemaKeys.ALL_OF, all);
                    property = wrapped;
                }
                property.addProperty(SchemaKeys.DESCRIPTION, doc.value());
            }
            Default fallback = component.getAnnotation(Default.class);
            if (fallback != null) {
                property.add(SchemaKeys.DEFAULT, parseDefault(fallback, type, component.getName()));
            } else if (!component.isAnnotationPresent(Optional.class)) {
                required.add(component.getName());
            }
            properties.add(component.getName(), property);
        }
        schema.add(SchemaKeys.PROPERTIES, properties);
        if (!required.isEmpty()) {
            schema.add(SchemaKeys.REQUIRED, required);
        }
        schema.addProperty(SchemaKeys.ADDITIONAL_PROPERTIES, false);
        return schema;
    }

    /**
     * The JSON name of an enum constant: its name in lower camel case.
     *
     * @param constant The constant.
     * @return The name.
     */
    public static String enumName(@NonNull Enum<?> constant) {
        String[] words = constant.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; ++i) {
            if (!words[i].isEmpty()) {
                name.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1));
            }
        }
        return name.toString();
    }

    /**
     * The JSON names of an enum's constants.
     *
     * @param <E> The enum type.
     * @param type The enum class.
     * @return The names, in declaration order.
     */
    private static <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            names.add(enumName(constant));
        }
        return names;
    }

    /**
     * Whether JSON is a string.
     *
     * @param json The JSON.
     * @return Whether it is a string.
     */
    private static boolean isString(JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString();
    }

    /**
     * Read JSON as a number.
     *
     * @param json The JSON.
     * @return The number, or null if it isn't a finite number.
     */
    private static BigDecimal number(JsonElement json) {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return json.getAsBigDecimal();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Read a fixed-length list of numbers.
     *
     * @param json The JSON.
     * @param count How many numbers there must be.
     * @return The numbers, or null if it isn't that many finite numbers.
     */
    static float[] readFloats(JsonElement json, int count) {
        if (!json.isJsonArray() || json.getAsJsonArray().size() != count) {
            return null;
        }
        float[] values = new float[count];
        for (int i = 0; i < count; ++i) {
            BigDecimal number = number(json.getAsJsonArray().get(i));
            if (number == null || !Float.isFinite(number.floatValue())) {
                return null;
            }
            values[i] = number.floatValue();
        }
        return values;
    }

    /**
     * Describe a fixed-length list of numbers.
     *
     * @param count How many numbers.
     * @return The schema.
     */
    static JsonObject numberArraySchema(int count) {
        JsonObject schema = new JsonObject();
        schema.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_ARRAY);
        JsonObject number = new JsonObject();
        number.addProperty(SchemaKeys.TYPE, SchemaKeys.TYPE_NUMBER);
        schema.add(SchemaKeys.ITEMS, number);
        schema.addProperty(SchemaKeys.MIN_ITEMS, count);
        schema.addProperty(SchemaKeys.MAX_ITEMS, count);
        return schema;
    }
}
