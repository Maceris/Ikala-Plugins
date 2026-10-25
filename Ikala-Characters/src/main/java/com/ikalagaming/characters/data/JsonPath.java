package com.ikalagaming.characters.data;

import lombok.NonNull;

/**
 * Builds JSON paths in Gson's form, like {@code $.slots[0].bones}, for problem reports. Build them
 * from field name constants rather than writing them out, so renaming a field can't leave a path
 * behind.
 *
 * @author Ches Burks
 */
public final class JsonPath {
    /** The path of the whole value. */
    public static final String ROOT = "$";

    private JsonPath() {}

    /**
     * The path of a field of the root.
     *
     * @param field The field's name.
     * @return The path, like {@code $.slots}.
     */
    public static String at(@NonNull String field) {
        return field(ROOT, field);
    }

    /**
     * The path of an item in a list field of the root.
     *
     * @param field The field's name.
     * @param index The item's index.
     * @return The path, like {@code $.slots[0]}.
     */
    public static String at(@NonNull String field, int index) {
        return index(at(field), index);
    }

    /**
     * The path of a field, or of a map entry.
     *
     * @param parent The path of the object.
     * @param field The field's name or map key.
     * @return The path, like {@code $.slots[0].bones}.
     */
    public static String field(@NonNull String parent, @NonNull String field) {
        return parent + "." + field;
    }

    /**
     * The path of a list item.
     *
     * @param parent The path of the list.
     * @param index The item's index.
     * @return The path, like {@code $.slots[0].bones[1]}.
     */
    public static String index(@NonNull String parent, int index) {
        return parent + "[" + index + "]";
    }
}
