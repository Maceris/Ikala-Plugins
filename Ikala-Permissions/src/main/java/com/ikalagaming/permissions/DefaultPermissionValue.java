package com.ikalagaming.permissions;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains mappings of values to either true or false. This is used in loading from YAML files.
 *
 * @author Ches Burks
 */
public enum DefaultPermissionValue {
    /** Defaults to true */
    TRUE("true", "t", "yes"),

    /** Defaults to false */
    FALSE("false", "f", "no");

    private static final Map<String, Boolean> lookup = new HashMap<>();
    private static final Map<String, DefaultPermissionValue> values = new HashMap<>();

    static {
        for (String name : TRUE.namesArray) {
            lookup.put(name, true);
            values.put(name, TRUE);
        }
        for (String name : FALSE.namesArray) {
            lookup.put(name, false);
            values.put(name, FALSE);
        }
    }

    /**
     * Looks up a DefaultPermissionValue by name
     *
     * @param name Name of the default
     * @return Specified value, or FALSE if it does not exist
     */
    public static DefaultPermissionValue getByName(String name) {
        if (values.containsKey(name)) {
            return values.get(name.toLowerCase().replaceAll("[^a-z!]", ""));
        }
        return FALSE;
    }

    /**
     * Returns true if the given name is registered to a value.
     *
     * @param name Name of the default
     * @return true if the name exists, false otherwise
     */
    public static boolean isValid(String name) {
        return lookup.containsKey(name);
    }

    private final String[] namesArray;

    DefaultPermissionValue(String... names) {
        namesArray = names;
    }

    @Override
    public String toString() {
        return namesArray[0];
    }

    /**
     * Returns the boolean value for this object.
     *
     * @return a boolean representing the value of this object
     */
    public boolean value() {
        switch (this) {
            case TRUE:
                return true;
            case FALSE:
            default:
                return false;
        }
    }
}
