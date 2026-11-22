package com.ikalagaming.ecs;

import java.util.Arrays;

/**
 * Values by entity, sorted by entity id so walking them always goes in the same order.
 *
 * <p>Freezing hands out the current arrays as a {@link State} without copying them. The next change
 * copies them first, so a frozen state never changes. A snapshot of an unchanged table costs
 * nothing, and a changed one is copied once per snapshot, not once per change.
 */
final class Table {

    /** The capacity of a new table. */
    private static final int INITIAL_CAPACITY = 8;

    /** An empty state. */
    static final State EMPTY = new State(new long[0], new Object[0], 0);

    /**
     * A table's contents at one moment, never changed afterward.
     *
     * @param ids The entity ids, sorted, in the first {@code size} places.
     * @param values The values, in the same order.
     * @param size How many entries there are.
     */
    record State(long[] ids, Object[] values, int size) {

        /**
         * Where an entity is.
         *
         * @param id The entity.
         * @return The index, or a negative number if it is not here.
         */
        int indexOf(long id) {
            return Arrays.binarySearch(ids, 0, size, id);
        }

        /**
         * An entity's value.
         *
         * @param id The entity.
         * @return The value, or null if it has none.
         */
        Object get(long id) {
            final int i = indexOf(id);
            return i < 0 ? null : values[i];
        }

        /**
         * Whether two states hold the same entities with equal values.
         *
         * @param other The other state.
         * @return True if they match.
         */
        boolean sameAs(State other) {
            if (other == this) {
                return true;
            }
            return size == other.size
                    && Arrays.equals(ids, 0, size, other.ids, 0, size)
                    && Arrays.equals(values, 0, size, other.values, 0, size);
        }

        /**
         * A hash of the entities and values, consistent with {@link #sameAs}.
         *
         * @return The hash.
         */
        int contentHash() {
            int hash = 1;
            for (int i = 0; i < size; ++i) {
                hash = 31 * hash + Long.hashCode(ids[i]);
                hash = 31 * hash + values[i].hashCode();
            }
            return hash;
        }
    }

    /** The entity ids, sorted. */
    private long[] ids = EMPTY.ids;

    /** The values, in the same order. */
    private Object[] values = EMPTY.values;

    /** How many entries there are. */
    private int size;

    /** Whether a frozen state shares the arrays, so they must be copied before a change. */
    private boolean shared = true;

    /**
     * How many entries there are.
     *
     * @return The count.
     */
    int size() {
        return size;
    }

    /**
     * An entity's value.
     *
     * @param id The entity.
     * @return The value, or null if it has none.
     */
    Object get(long id) {
        final int i = Arrays.binarySearch(ids, 0, size, id);
        return i < 0 ? null : values[i];
    }

    /**
     * Whether an entity has a value.
     *
     * @param id The entity.
     * @return True if it does.
     */
    boolean has(long id) {
        return Arrays.binarySearch(ids, 0, size, id) >= 0;
    }

    /**
     * Set an entity's value, adding it if it has none.
     *
     * @param id The entity.
     * @param value The value, not null.
     */
    void put(long id, Object value) {
        final int i = Arrays.binarySearch(ids, 0, size, id);
        if (i >= 0) {
            own(size);
            values[i] = value;
            return;
        }
        final int at = -i - 1;
        own(size + 1);
        System.arraycopy(ids, at, ids, at + 1, size - at);
        System.arraycopy(values, at, values, at + 1, size - at);
        ids[at] = id;
        values[at] = value;
        ++size;
    }

    /**
     * Remove an entity's value.
     *
     * @param id The entity.
     * @return True if it had one.
     */
    boolean remove(long id) {
        final int i = Arrays.binarySearch(ids, 0, size, id);
        if (i < 0) {
            return false;
        }
        own(size);
        System.arraycopy(ids, i + 1, ids, i, size - i - 1);
        System.arraycopy(values, i + 1, values, i, size - i - 1);
        --size;
        values[size] = null;
        return true;
    }

    /** Remove everything. */
    void clear() {
        ids = EMPTY.ids;
        values = EMPTY.values;
        size = 0;
        shared = true;
    }

    /**
     * The current contents, which later changes won't touch.
     *
     * @return The state.
     */
    State freeze() {
        if (size == 0) {
            return EMPTY;
        }
        shared = true;
        return new State(ids, values, size);
    }

    /**
     * Take a frozen state as the contents.
     *
     * @param state The state, which is shared and copied before any change.
     */
    void load(State state) {
        ids = state.ids();
        values = state.values();
        size = state.size();
        shared = true;
    }

    /**
     * Make sure the arrays belong to this table and hold at least a number of entries.
     *
     * @param needed How many entries they must hold.
     */
    private void own(int needed) {
        if (!shared && needed <= ids.length) {
            return;
        }
        int capacity = Math.max(ids.length, INITIAL_CAPACITY);
        while (capacity < needed) {
            capacity *= 2;
        }
        ids = Arrays.copyOf(ids, capacity);
        values = Arrays.copyOf(values, capacity);
        shared = false;
    }
}
