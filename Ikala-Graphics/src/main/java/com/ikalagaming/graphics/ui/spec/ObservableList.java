package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A list that UI specs can repeat a template over. Its value is always an unmodifiable snapshot,
 * and each change replaces the whole snapshot, so listeners never see a list change under them.
 *
 * @param <T> The type of item.
 */
public class ObservableList<T> extends Observable<List<T>> {
    /** Makes read-modify-write changes atomic. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Create an empty list. */
    public ObservableList() {
        super(List.of());
    }

    /**
     * Create a list.
     *
     * @param items The starting items, which are copied.
     */
    public ObservableList(@NonNull Collection<? extends T> items) {
        super(List.copyOf(items));
    }

    @Override
    public void set(List<T> items) {
        super.set(items == null ? List.of() : List.copyOf(items));
    }

    /**
     * Add an item at the end.
     *
     * @param item The item.
     */
    public void add(@NonNull T item) {
        lock.lock();
        try {
            List<T> next = new ArrayList<>(get());
            next.add(item);
            set(next);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove the first equal item, if there is one.
     *
     * @param item The item.
     */
    public void remove(@NonNull T item) {
        lock.lock();
        try {
            List<T> next = new ArrayList<>(get());
            if (next.remove(item)) {
                set(next);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Remove every item. */
    public void clear() {
        set(List.of());
    }
}
