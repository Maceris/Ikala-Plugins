package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.util.IntIntTreeMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Key-value storage for persistent state, such as whether tree nodes are open. Each window has its
 * own storage, keyed by item IDs. Values of different types are stored separately, so the same key
 * can hold an int and a float without conflict.
 *
 * <p>Ints and floats are kept in primitive maps, floats by their bits: widgets look their state up
 * here every frame, and boxed keys and values were a share of what each frame allocated.
 */
public class Storage {
    private final IntIntTreeMap ints;
    private final IntIntTreeMap floats;
    private final Map<Integer, Object> objects;

    public Storage() {
        ints = new IntIntTreeMap();
        floats = new IntIntTreeMap();
        objects = new HashMap<>();
    }

    /**
     * Copy the integer (and boolean) entries, for debugging.
     *
     * @return The integer entries, by key.
     */
    public Map<Integer, Integer> getIntEntries() {
        final Map<Integer, Integer> copy = new TreeMap<>();
        ints.forEach(copy::put);
        return copy;
    }

    /**
     * Copy the float entries, for debugging.
     *
     * @return The float entries, by key.
     */
    public Map<Integer, Float> getFloatEntries() {
        final Map<Integer, Float> copy = new TreeMap<>();
        floats.forEach((key, bits) -> copy.put(key, Float.intBitsToFloat(bits)));
        return copy;
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
        return ints.get(key, defaultValue);
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
        final int entry = floats.entry(key);
        return entry == IntIntTreeMap.NONE
                ? defaultValue
                : Float.intBitsToFloat(floats.value(entry));
    }

    public void setFloat(int key, float value) {
        floats.put(key, Float.floatToRawIntBits(value));
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
        for (int entry = ints.firstEntry();
                entry != IntIntTreeMap.NONE;
                entry = ints.higherEntry(ints.key(entry))) {
            ints.setValue(entry, value);
        }
    }
}
