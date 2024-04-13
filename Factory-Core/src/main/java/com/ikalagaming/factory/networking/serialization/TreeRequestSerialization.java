package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Converts KVT data to and from requests. There are several limitations on the kinds of objects
 * that can be serialized this way. They must have a constructor including all fields with the same
 * names as the fields (see {@link lombok.AllArgsConstructor @AllArgsConstructor}), and only have
 * fields that can be represented by KVTs. It is expected that all arrays are represented as lists.
 * Any child objects must have the same properties.
 */
@Slf4j
public class TreeRequestSerialization {
    private static final Map<Class<?>, Map<String, Field>> fieldCache = new HashMap<>();

    /**
     * Convert an object into KVT data. There are several limitations on the kinds of objects that
     * can be serialized this way. They must have a constructor including all fields with the same
     * names as the fields (see {@link lombok.AllArgsConstructor @AllArgsConstructor}), and only
     * have fields that can be represented by KVTs. It is expected that all arrays are represented
     * as lists. Any child objects * must have the same properties.
     *
     * @param input The input object.
     * @return An optional containing KVT data, or an empty optional if we could not convert the
     *     object.
     */
    public static Optional<Node> fromObject(@NonNull Object input) {
        var result = new Node();
        try {
            addToNode(input, result);
        } catch (IllegalAccessException e) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "ERROR_ENCODING_KVT",
                            FactoryPlugin.getResourceBundle(),
                            input.getClass().getSimpleName()));
            return Optional.empty();
        }

        return Optional.of(result);
    }

    /**
     * Add an object to a specified node, which may recursively handle sub-objects.
     *
     * @param input The input object.
     * @param node The node that corresponds to this object in a KVT.
     * @throws IllegalAccessException If we cannot make fields accessible or read from them.
     */
    private static void addToNode(@NonNull Object input, @NonNull Node node)
            throws IllegalAccessException {
        var clazz = input.getClass();
        var fields =
                fieldCache.computeIfAbsent(
                        clazz, TreeRequestSerialization::findFieldsAndSetAccessible);

        for (var entry : fields.entrySet()) {
            var name = entry.getKey();
            var field = entry.getValue();
            var type = field.getType();
            if (fetchSingleValueFromObject(input, node, type, name, field)) {
                continue;
            }
            if (List.class.isAssignableFrom(type)
                    && (fetchListFromObject(input, node, type, name, field))) {
                continue;
            }
            node.addNode(name);
        }
    }

    /**
     * Fetch a list of values from an object and store it in KVT.
     *
     * @param input The object we are converting.
     * @param node The KVT node we are storing data in.
     * @param type The type of the field.
     * @param name The name of the field.
     * @param field The field itself.
     * @return If we found a value that could be mapped.
     * @throws IllegalAccessException If we have issues casting things.
     */
    private static boolean fetchListFromObject(
            Object input, Node node, Class<?> type, String name, Field field)
            throws IllegalAccessException {
        Class<?> entryType =
                ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].getClass();

        List<?> list = (List<?>) field.get(input);

        if (entryType == Short.class) {
            node.addShortArray(name, (List<Short>) list);
            return true;
        }
        if (entryType == Integer.class) {
            node.addIntegerArray(name, (List<Integer>) list);
            return true;
        }
        if (entryType == Long.class) {
            node.addLongArray(name, (List<Long>) list);
            return true;
        }
        if (entryType == Float.class) {
            node.addFloatArray(name, (List<Float>) list);
            return true;
        }
        if (entryType == Double.class) {
            node.addDoubleArray(name, (List<Double>) list);
            return true;
        }
        if (entryType == Boolean.class) {
            node.addBooleanArray(name, (List<Boolean>) list);
            return true;
        }
        if (entryType == String.class) {
            node.addStringArray(name, (List<String>) list);
            return true;
        }
        if (entryType.isEnum()) {
            var enumList = (Class<List<? extends Enum<?>>>) type;
            var value = enumList.cast(field.get(input)).stream().map(Enum::name).toList();
            node.addStringArray(name, value);
            return true;
        }
        if (entryType == Node.class) {
            node.addNodeArray(name, (List<Node>) list);
            return true;
        }
        return false;
    }

    /**
     * Fetch a single value from an object and store it in KVT.
     *
     * @param input The object we are converting.
     * @param node The KVT node we are storing data in.
     * @param type The type of the field.
     * @param name The name of the field.
     * @param field The field itself.
     * @return If we found a value that could be mapped.
     * @throws IllegalAccessException If we have issues casting things.
     */
    private static boolean fetchSingleValueFromObject(
            Object input, Node node, Class<?> type, String name, Field field)
            throws IllegalAccessException {
        if (type == short.class || type == Short.class) {
            node.addShort(name, field.getShort(input));
            return true;
        }
        if (type == int.class || type == Integer.class) {
            node.addInteger(name, field.getInt(input));
            return true;
        }
        if (type == long.class || type == Long.class) {
            node.addLong(name, field.getLong(input));
            return true;
        }
        if (type == float.class || type == Float.class) {
            node.addFloat(name, field.getFloat(input));
            return true;
        }
        if (type == double.class || type == Double.class) {
            node.addDouble(name, field.getDouble(input));
            return true;
        }
        if (type == boolean.class || type == Boolean.class) {
            node.addBoolean(name, field.getBoolean(input));
            return true;
        }
        if (type == String.class) {
            node.addString(name, (String) field.get(input));
            return true;
        }
        if (type.isEnum()) {
            var enumSubclass = (Class<? extends Enum<?>>) type;
            var value = enumSubclass.cast(field.get(input)).name();
            node.addString(name, value);
            return true;
        }
        if (type == Node.class) {
            node.addNode(name, (Node) field.get(input));
            return true;
        }
        return false;
    }

    /**
     * Find all the fields in a class and make them accessible, then fill out a map from names to
     * field and return that.
     *
     * @param clazz The class we want fields for.
     * @return A map from name to corresponding field, which has been made accessible.
     */
    private static Map<String, Field> findFieldsAndSetAccessible(@NonNull Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        Map<String, Field> fieldMap = new TreeMap<>();
        for (var field : fields) {
            field.setAccessible(true); // NOSONAR
            fieldMap.put(field.getName(), field);
        }
        return fieldMap;
    }

    public static <T> T toObject(final @NonNull KVT tree, Class<T> type) {
        // special handling - enums, objects, and KVT/Node objects

        return null;
    }

    /** Private constructor so that this class is not instantiated. */
    private TreeRequestSerialization() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
