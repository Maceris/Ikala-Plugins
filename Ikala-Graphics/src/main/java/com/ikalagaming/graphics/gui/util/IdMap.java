package com.ikalagaming.graphics.gui.util;

import com.ikalagaming.util.IntArrayList;
import com.ikalagaming.util.IntIntTreeMap;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Values by int ID, like a {@code Map<Integer, T>} without boxing the keys: IDs are looked up many
 * times a frame, and each boxed ID outside the small-integer cache was a new object. Values sit in
 * a list, with slots of removed ones reused.
 *
 * <p>Not thread safe.
 *
 * @param <T> The type of value.
 */
public final class IdMap<T> {
    /** Each ID's slot in {@link #values}. */
    private final IntIntTreeMap slots = new IntIntTreeMap();

    /** The values by slot, null in free slots. */
    private final List<T> values = new ArrayList<>();

    /** The free slots. */
    private final IntArrayList free = new IntArrayList();

    /**
     * An ID's value.
     *
     * @param id The ID.
     * @return Its value, or null if it has none.
     */
    public T get(int id) {
        final int slot = slots.get(id, -1);
        return slot < 0 ? null : values.get(slot);
    }

    /**
     * Set an ID's value.
     *
     * @param id The ID.
     * @param value The value, not null.
     */
    public void put(int id, @NonNull T value) {
        int slot = slots.get(id, -1);
        if (slot < 0) {
            if (free.isEmpty()) {
                slot = values.size();
                values.add(value);
            } else {
                slot = free.pop();
                values.set(slot, value);
            }
            slots.put(id, slot);
        } else {
            values.set(slot, value);
        }
    }

    /**
     * Remove an ID's value.
     *
     * @param id The ID.
     * @return The value it had, or null if none.
     */
    public T remove(int id) {
        final int slot = slots.get(id, -1);
        if (slot < 0) {
            return null;
        }
        slots.remove(id);
        final T value = values.get(slot);
        values.set(slot, null);
        free.addInt(slot);
        return value;
    }

    /**
     * How many IDs have values.
     *
     * @return The count.
     */
    public int size() {
        return slots.size();
    }

    /**
     * Whether no ID has a value.
     *
     * @return True if empty.
     */
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** Remove every value. */
    public void clear() {
        slots.clear();
        values.clear();
        free.clear();
    }

    /**
     * Visit every value, in no particular order. The map must not change meanwhile.
     *
     * @param action What to call for each.
     */
    public void forEachValue(@NonNull Consumer<? super T> action) {
        for (T value : values) {
            if (value != null) {
                action.accept(value);
            }
        }
    }

    /**
     * Copy the values, for tools and tests rather than every frame.
     *
     * @return The values, in no particular order.
     */
    public List<T> values() {
        final List<T> copy = new ArrayList<>(slots.size());
        forEachValue(copy::add);
        return copy;
    }

    /**
     * Remove the values that match a test.
     *
     * @param test Whether to remove a value.
     * @param idOf The ID a value is stored under.
     */
    public void removeIf(@NonNull Predicate<? super T> test, @NonNull ToIntFunction<T> idOf) {
        for (int slot = 0; slot < values.size(); ++slot) {
            final T value = values.get(slot);
            if (value != null && test.test(value)) {
                slots.remove(idOf.applyAsInt(value));
                values.set(slot, null);
                free.addInt(slot);
            }
        }
    }
}
