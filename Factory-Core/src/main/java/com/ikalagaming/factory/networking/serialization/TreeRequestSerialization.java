package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.util.SafeResourceLoader;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Converts KVT data to and from requests. There are several limitations on the kinds of objects that can be serialized
 * this way. They must have a constructor including all fields with the same names as the fields
 * (see {@link lombok.AllArgsConstructor @AllArgsConstructor}), and only have fields that can be represented by KVTs.
 * It is expected that all arrays are represented as lists.
 */
@Slf4j
public class TreeRequestSerialization {
    private static Map<Class<?>, Map<String, Field>> fieldCache = new HashMap<>();

    public static <T> Optional<Node> fromObject(@NonNull T input) {
        var result = new Node();
        try {
            addToNode(input, result);
        } catch (IllegalAccessException e) {
            log.error(SafeResourceLoader.getStringFormatted("ERROR_ENCODING_KVT",
                    FactoryPlugin.getResourceBundle(), input.getClass().getSimpleName()));
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static void addToNode(@NonNull Object input, @NonNull Node node) throws IllegalAccessException {
        var clazz = input.getClass();
        var fields = fieldCache.computeIfAbsent(clazz, TreeRequestSerialization::findFieldsAndSetAccessible);

        for (var entry : fields.entrySet()) {
            var name = entry.getKey();
            var field = entry.getValue();
            var type = field.getType();
            if (type == short.class || type == Short.class) {
                node.addShort(name, field.getShort(input));
                continue;
            }
            if (type == int.class || type == Integer.class) {
                node.addInteger(name, field.getInt(input));
                continue;
            }
            if (type == long.class || type == Long.class) {
                node.addLong(name, field.getLong(input));
                continue;
            }
            if (type == float.class || type == Float.class) {
                node.addFloat(name, field.getFloat(input));
                continue;
            }
            if (type == double.class || type == Double.class) {
                node.addDouble(name, field.getDouble(input));
                continue;
            }
            if (type == boolean.class || type == Boolean.class) {
                node.addBoolean(name, field.getBoolean(input));
                continue;
            }
            if (type == String.class) {
                node.addString(name, (String) field.get(input));
                continue;
            }
            if (type.isEnum()) {
                var enumSubclass = (Class<? extends Enum<?>>) type;
                var value = enumSubclass.cast(field.get(input)).name();
                node.addString(name, value);
                continue;
            }
            if (type == Node.class) {
                node.addNode(name, (Node) field.get(input));
                continue;
            }
            if (List.class.isAssignableFrom(type)) {

                //TODO(ches) lists
            }


            node.addNode(name);


        }
    }

    /**
     * Find all the fields in a class and make them accessible, then fill out a map from names to field and return that.
     *
     * @param clazz The class we want fields for.
     * @return A map from name to corresponding field, which has been made accessible.
     */
    private static Map<String, Field> findFieldsAndSetAccessible(@NonNull Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        Map<String, Field> fieldMap = new TreeMap<>();
        for (var field : fields) {
            field.setAccessible(true);//NOSONAR
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
