package com.ikalagaming.graphics.lod;

/**
 * A map from long keys to int values, with open addressing and no boxing. Keys here are usually two
 * ints packed into a long, whose own hash (the two halves XORed) collides badly; this mixes every
 * bit first.
 */
public final class LongIntMap {

    /** What {@link #get} and {@link #putIfAbsent} return for a key that isn't there. */
    public static final int MISSING = -1;

    /** The smallest table. */
    private static final int MIN_CAPACITY = 16;

    /** The table grows once it is more than half full. */
    private static final int LOAD_SHIFT = 1;

    /** The first constant of the MurmurHash3 finalizer. */
    private static final long MIX_A = 0xff51afd7ed558ccdL;

    /** The second constant of the MurmurHash3 finalizer. */
    private static final long MIX_B = 0xc4ceb9fe1a85ec53L;

    /** The shift of the MurmurHash3 finalizer. */
    private static final int MIX_SHIFT = 33;

    /** The keys. */
    private long[] keys;

    /** The values. */
    private int[] values;

    /** Whether each slot is in use. */
    private boolean[] used;

    /** How many keys there are. */
    private int size;

    /**
     * Make a map.
     *
     * @param expected About how many keys it will hold.
     */
    public LongIntMap(int expected) {
        allocate(Integer.highestOneBit(Math.max(MIN_CAPACITY, expected << LOAD_SHIFT) - 1) << 1);
    }

    /**
     * Make room.
     *
     * @param capacity The table size, a power of two.
     */
    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new int[capacity];
        used = new boolean[capacity];
    }

    /**
     * Spread a key's bits, so that keys differing in any bit land in different slots.
     *
     * @param key The key.
     * @return The mixed bits.
     */
    private static int mix(long key) {
        long h = key;
        h ^= h >>> MIX_SHIFT;
        h *= MIX_A;
        h ^= h >>> MIX_SHIFT;
        h *= MIX_B;
        h ^= h >>> MIX_SHIFT;
        return (int) h;
    }

    /**
     * Find a key's slot, or the empty slot where it would go.
     *
     * @param key The key.
     * @return The slot.
     */
    private int slot(long key) {
        final int mask = keys.length - 1;
        int i = mix(key) & mask;
        while (used[i] && keys[i] != key) {
            i = (i + 1) & mask;
        }
        return i;
    }

    /**
     * A key's value.
     *
     * @param key The key.
     * @return Its value, or {@link #MISSING}.
     */
    public int get(long key) {
        final int i = slot(key);
        return used[i] ? values[i] : MISSING;
    }

    /**
     * Add a key unless it is already there.
     *
     * @param key The key.
     * @param value Its value, if added.
     * @return The value already there, or {@link #MISSING} if the key was added.
     */
    public int putIfAbsent(long key, int value) {
        int i = slot(key);
        if (used[i]) {
            return values[i];
        }
        if ((size + 1) << LOAD_SHIFT > keys.length) {
            grow();
            i = slot(key);
        }
        used[i] = true;
        keys[i] = key;
        values[i] = value;
        ++size;
        return MISSING;
    }

    /**
     * Set a key's value, adding the key if it isn't there.
     *
     * @param key The key.
     * @param value Its value.
     */
    public void put(long key, int value) {
        int i = slot(key);
        if (!used[i]) {
            if ((size + 1) << LOAD_SHIFT > keys.length) {
                grow();
                i = slot(key);
            }
            used[i] = true;
            keys[i] = key;
            ++size;
        }
        values[i] = value;
    }

    /** Double the table. */
    private void grow() {
        final long[] oldKeys = keys;
        final int[] oldValues = values;
        final boolean[] oldUsed = used;
        allocate(keys.length << 1);
        for (int i = 0; i < oldKeys.length; ++i) {
            if (oldUsed[i]) {
                final int j = slot(oldKeys[i]);
                used[j] = true;
                keys[j] = oldKeys[i];
                values[j] = oldValues[i];
            }
        }
    }

    /**
     * How many keys there are.
     *
     * @return The count.
     */
    public int size() {
        return size;
    }

    /**
     * Pack two ints into a key.
     *
     * @param a The high int.
     * @param b The low int.
     * @return The key.
     */
    public static long pair(int a, int b) {
        return ((long) a << Integer.SIZE) | Integer.toUnsignedLong(b);
    }
}
