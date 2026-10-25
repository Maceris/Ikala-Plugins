package com.ikalagaming.characters.definition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Checks that the {@code FIELD_} name constants still match the record fields they name, so
 * renaming a field without its constant fails here instead of pointing problems at the wrong path.
 *
 * @author Ches Burks
 */
class TestFieldConstants {
    /** What field name constants start with. */
    private static final String PREFIX = "FIELD_";

    /**
     * Every definition record and the records inside them.
     *
     * @return The records.
     */
    private static Set<Class<?>> records() {
        Set<Class<?>> found = new HashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        for (Kind kind : Kind.values()) {
            pending.add(kind.getType());
        }
        while (!pending.isEmpty()) {
            Class<?> type = pending.pop();
            if (!type.isRecord() || !found.add(type)) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                addTypes(component.getGenericType(), pending);
            }
        }
        return found;
    }

    /**
     * Queue the classes in a field's type, including list and map arguments.
     *
     * @param type The type.
     * @param pending Where to queue them.
     */
    private static void addTypes(Type type, Deque<Class<?>> pending) {
        if (type instanceof Class<?> c) {
            pending.add(c);
        } else if (type instanceof ParameterizedType parameterized) {
            Arrays.stream(parameterized.getActualTypeArguments())
                    .forEach(argument -> addTypes(argument, pending));
        }
    }

    /**
     * The constant name a field should have, like FIELD_MIRROR_OF for mirrorOf.
     *
     * @param field The field's name.
     * @return The constant's name.
     */
    private static String constantName(String field) {
        return PREFIX + field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    /** Every FIELD_ constant names a field of its own record, and is named after it. */
    @Test
    void testConstantsMatchFields() throws IllegalAccessException {
        int checked = 0;
        for (Class<?> type : records()) {
            Set<String> components = new HashSet<>();
            for (RecordComponent component : type.getRecordComponents()) {
                components.add(component.getName());
            }
            for (Field field : type.getDeclaredFields()) {
                if (!field.getName().startsWith(PREFIX)
                        || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String value = (String) field.get(null);
                String where = type.getSimpleName() + "." + field.getName();
                Assertions.assertTrue(
                        components.contains(value),
                        where + " is \"" + value + "\", which isn't a field of the record");
                Assertions.assertEquals(constantName(value), field.getName(), where);
                ++checked;
            }
        }
        Assertions.assertTrue(checked > 20, "Only found " + checked + " constants");
    }

    /** Every definition has the field the shared ID constant names. */
    @Test
    void testIdField() {
        for (Kind kind : Kind.values()) {
            Assertions.assertTrue(
                    Arrays.stream(kind.getType().getRecordComponents())
                            .anyMatch(c -> Definition.FIELD_ID.equals(c.getName())),
                    kind.getType().getSimpleName());
        }
    }
}
