package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Values remembered while one chunk is generated, so work that doesn't depend on every coordinate
 * is done once: a subtree that ignores y is worked out once per column, and the coarse lattice of
 * an interpolated node once per lattice point. It only ever saves work; a value from the cache is
 * the value that would have been computed.
 *
 * <p>Each cached node has its own slot, given out when data is compiled. Not thread safe: each
 * chunk being generated has its own.
 */
public final class EvalCache {

    /**
     * Where a cached value is.
     *
     * @param slot The node's slot.
     * @param a The first coordinate it depends on.
     * @param b The second coordinate it depends on.
     * @param c The third coordinate it depends on.
     */
    private record Key(int slot, double a, double b, double c) {}

    /** Every remembered value. */
    private final Map<Key, Double> values = new HashMap<>();

    /** How many values were computed rather than found, for instrumentation. */
    private long misses;

    /**
     * A remembered value, or compute and remember it.
     *
     * @param slot The node's slot.
     * @param a The first coordinate the value depends on.
     * @param b The second coordinate the value depends on.
     * @param c The third coordinate the value depends on.
     * @param compute Works out the value.
     * @return The value.
     */
    public double get(int slot, double a, double b, double c, @NonNull DoubleSupplier compute) {
        final Key key = new Key(slot, a, b, c);
        final Double found = values.get(key);
        if (found != null) {
            return found;
        }
        ++misses;
        final double computed = compute.getAsDouble();
        values.put(key, computed);
        return computed;
    }

    /**
     * How many values were worked out rather than remembered.
     *
     * @return The count.
     */
    public long getMisses() {
        return misses;
    }
}
