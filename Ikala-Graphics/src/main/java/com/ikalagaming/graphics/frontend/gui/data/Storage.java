package com.ikalagaming.graphics.frontend.gui.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Key-value storage for persistent state, such as whether tree nodes are open. Each window has its
 * own storage, keyed by item IDs. Values of different types are stored separately, so the same key
 * can hold an int and a float without conflict.
 */
public class Storage {
    private final Map<Integer, Integer> ints;
    private final Map<Integer, Float> floats;
    private final Map<Integer, Object> objects;

    public Storage() {
        ints = new HashMap<>();
        floats = new HashMap<>();
        objects = new HashMap<>();
    }

    /**
     * Fetch a read-only view of the integer (and boolean) entries, for debugging.
     *
     * @return The integer entries.
     */
    public Map<Integer, Integer> getIntEntries() {
        return Collections.unmodifiableMap(ints);
    }

    /**
     * Fetch a read-only view of the float entries, for debugging.
     *
     * @return The float entries.
     */
    public Map<Integer, Float> getFloatEntries() {
        return Collections.unmodifiableMap(floats);
    }

    /**
     * Fetch a read-only view of the object entries, for debugging.
     *
     * @return The object entries.
     */
    public Map<Integer, Object> getObjectEntries() {
        return Collections.unmodifiableMap(objects);
    }

    /** Remove everything from storage. */
    public void clear() {
        ints.clear();
        floats.clear();
        objects.clear();
    }

    public int getInt(int key, int defaultValue) {
        return ints.getOrDefault(key, defaultValue);
    }

    public void setInt(int key, int value) {
        ints.put(key, value);
    }

    public boolean getBool(int key, boolean defaultValue) {
        return getInt(key, defaultValue ? 1 : 0) != 0;
    }

    public void setBool(int key, boolean value) {
        setInt(key, value ? 1 : 0);
    }

    public float getFloat(int key, float defaultValue) {
        return floats.getOrDefault(key, defaultValue);
    }

    public void setFloat(int key, float value) {
        floats.put(key, value);
    }

    /**
     * Fetch an arbitrary object from storage.
     *
     * @param key The key.
     * @return The object, or null if not present.
     */
    public Object getObject(int key) {
        return objects.get(key);
    }

    public void setObject(int key, Object value) {
        objects.put(key, value);
    }

    /**
     * Check if there is an int stored for the key.
     *
     * @param key The key.
     * @return True if an int value is stored.
     */
    public boolean hasInt(int key) {
        return ints.containsKey(key);
    }

    /** Set all the int values to the given value, for example to open or close all tree nodes. */
    public void setAllInt(int value) {
        ints.replaceAll((key, oldValue) -> value);
    }
}
