package com.ikalagaming.graphics.scene;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Values in numbered slots, each with a generation that goes up when the slot is freed, so a handle
 * of slot and generation goes stale rather than naming whatever takes the slot next. Freed slots
 * are taken again, the most recently freed first. Generations and free slots are kept as ints, so
 * adding and freeing box nothing.
 *
 * <p>Not thread safe: the registries using it hold their own locks.
 *
 * @param <T> The type of value.
 */
public final class SlotMap<T> {
    /** How many generations there is room for at first. */
    private static final int INITIAL_CAPACITY = 16;

    /** Each slot's value, null when free. */
    private final List<T> values = new ArrayList<>();

    /** How many times each slot was freed; only the first {@link #values}' size are in use. */
    private int[] generations = new int[INITIAL_CAPACITY];

    /** Slots free to take again. */
    private final IntArrayList free = new IntArrayList();

    /** How many slots hold values. */
    private int size;

    /**
     * Put a value in a slot, a free one if there is one.
     *
     * @param value The value.
     * @return Its slot, whose handle generation is {@link #generation(int)}.
     */
    public int add(@NonNull T value) {
        final int slot;
        if (free.isEmpty()) {
            slot = values.size();
            values.add(value);
            if (slot >= generations.length) {
                generations = Arrays.copyOf(generations, generations.length * 2);
            }
        } else {
            slot = free.pop();
            values.set(slot, value);
        }
        ++size;
        return slot;
    }

    /**
     * A slot's generation now.
     *
     * @param slot The slot, below {@link #slots()}.
     * @return Its generation.
     */
    public int generation(int slot) {
        return generations[slot];
    }

    /**
     * A slot's value.
     *
     * @param slot The slot, below {@link #slots()}.
     * @return Its value, or null if it is free.
     */
    public @Nullable T get(int slot) {
        return values.get(slot);
    }

    /**
     * The value a handle names, if it is still live.
     *
     * @param slot The handle's slot, which may be out of range.
     * @param generation The handle's generation.
     * @return The value, or null if the slot is out of range, free, or of a later generation.
     */
    public @Nullable T get(int slot, int generation) {
        if (slot < 0 || slot >= values.size() || generations[slot] != generation) {
            return null;
        }
        return values.get(slot);
    }

    /**
     * Whether a handle still names a value.
     *
     * @param slot The handle's slot, which may be out of range.
     * @param generation The handle's generation.
     * @return True if it does.
     */
    public boolean isLive(int slot, int generation) {
        return get(slot, generation) != null;
    }

    /**
     * Replace the value in a used slot.
     *
     * @param slot The slot, which must hold a value.
     * @param value The new value.
     */
    public void set(int slot, @NonNull T value) {
        if (values.get(slot) == null) {
            throw new IllegalStateException("Slot " + slot + " is free");
        }
        values.set(slot, value);
    }

    /**
     * Free a used slot, making its handles stale.
     *
     * @param slot The slot, which must hold a value.
     * @return The value it held.
     */
    public T free(int slot) {
        final T value = values.get(slot);
        if (value == null) {
            throw new IllegalStateException("Slot " + slot + " is free already");
        }
        values.set(slot, null);
        ++generations[slot];
        free.addInt(slot);
        --size;
        return value;
    }

    /**
     * How many slots there are, used or free: loop to this with {@link #get(int)} to visit every
     * value without allocating.
     *
     * @return The slot count.
     */
    public int slots() {
        return values.size();
    }

    /**
     * How many slots hold values.
     *
     * @return The count.
     */
    public int size() {
        return size;
    }
}
