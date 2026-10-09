package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A value that UI specs can bind to. Setting it tells everything bound to it, so the UI updates
 * without polling. Safe to use from any thread; UI that is bound to it updates on the render
 * thread.
 *
 * <p>Graphics only listens to an observable while a spec using it is open, and stops when the spec
 * is closed or its plugin unloads, so an observable never keeps unloaded UI alive.
 *
 * @param <T> The type of value.
 */
public class Observable<T> {
    /** The current value. */
    private final AtomicReference<T> value;

    /** Told about each change. */
    private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Create an observable.
     *
     * @param initial The starting value, which may be null.
     */
    public Observable(T initial) {
        value = new AtomicReference<>(initial);
    }

    /**
     * Create an observable.
     *
     * @param initial The starting value, which may be null.
     * @return The observable.
     * @param <T> The type of value.
     */
    public static <T> Observable<T> of(T initial) {
        return new Observable<>(initial);
    }

    /**
     * The current value.
     *
     * @return The value.
     */
    public T get() {
        return value.get();
    }

    /**
     * Change the value, telling listeners if it is different.
     *
     * @param newValue The new value, which may be null.
     */
    public void set(T newValue) {
        // Swapping in one step means two threads can't both see the old value and both notify for
        // a change only one of them made
        T old = value.getAndSet(newValue);
        if (Objects.equals(old, newValue)) {
            return;
        }
        for (Consumer<? super T> listener : listeners) {
            listener.accept(newValue);
        }
    }

    /**
     * Listen for changes.
     *
     * @param listener Told the new value after each change, on the thread that changed it.
     * @return A handle that stops listening when closed.
     */
    public Subscription subscribe(@NonNull Consumer<? super T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * How many listeners there are, for tests.
     *
     * @return The number of listeners.
     */
    public int listenerCount() {
        return listeners.size();
    }

    @Override
    public String toString() {
        return "Observable[" + value.get() + "]";
    }
}
