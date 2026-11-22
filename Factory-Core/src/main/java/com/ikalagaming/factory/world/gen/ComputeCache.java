package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Values worked out once and shared between the threads generating chunks, such as a cell's tree
 * candidates or a tree's layout. Every value is a pure function of its key, so a value that was
 * dropped to save memory is simply worked out again, with the same result.
 *
 * <p>The first thread to ask for a key works it out; others asking meanwhile wait for it rather
 * than doing the same work. Once more than the capacity is held, the oldest are dropped.
 *
 * @param <K> The key.
 * @param <V> The value.
 */
public final class ComputeCache<K, V> {

    /** The values, or the work in progress on them. */
    private final Map<K, CompletableFuture<V>> values = new ConcurrentHashMap<>();

    /** Keys in the order they were added, for dropping the oldest. */
    private final Queue<K> order = new ConcurrentLinkedQueue<>();

    /** How many values are held. */
    private final AtomicInteger size = new AtomicInteger();

    /** How many values to hold before dropping the oldest. */
    private final int capacity;

    /** Works out a value. */
    private final Function<K, V> compute;

    /**
     * An empty cache.
     *
     * @param capacity How many values to hold before dropping the oldest.
     * @param compute Works out a value from its key; must be a pure function of it.
     */
    public ComputeCache(int capacity, @NonNull Function<K, V> compute) {
        if (capacity < 1) {
            throw new IllegalArgumentException("A cache needs room for at least one value");
        }
        this.capacity = capacity;
        this.compute = compute;
    }

    /**
     * A key's value, worked out now if no thread has yet.
     *
     * @param key The key.
     * @return The value.
     */
    public V get(@NonNull K key) {
        final CompletableFuture<V> existing = values.get(key);
        if (existing != null) {
            return join(existing);
        }
        final CompletableFuture<V> mine = new CompletableFuture<>();
        final CompletableFuture<V> raced = values.putIfAbsent(key, mine);
        if (raced != null) {
            return join(raced);
        }
        order.add(key);
        if (size.incrementAndGet() > capacity) {
            dropOldest();
        }
        try {
            final V value = compute.apply(key);
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error e) {
            if (values.remove(key, mine)) {
                size.decrementAndGet();
            }
            mine.completeExceptionally(e);
            throw e;
        }
    }

    /**
     * Wait for a value, passing on whatever went wrong working it out.
     *
     * @param future The value being worked out.
     * @return The value.
     */
    private static <V> V join(@NonNull CompletableFuture<V> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    /** Drop the oldest values until the cache is back within its capacity. */
    private void dropOldest() {
        while (size.get() > capacity) {
            final K oldest = order.poll();
            if (oldest == null) {
                return;
            }
            if (values.remove(oldest) != null) {
                size.decrementAndGet();
            }
        }
    }

    /**
     * How many values are held, for tests and statistics.
     *
     * @return The count.
     */
    public int size() {
        return values.size();
    }
}
