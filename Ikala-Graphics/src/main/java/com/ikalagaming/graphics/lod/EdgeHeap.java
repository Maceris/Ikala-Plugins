package com.ikalagaming.graphics.lod;

import java.util.Arrays;

/**
 * A binary min-heap of candidate collapses in flat arrays: a cost, the two vertices, and each
 * vertex's version when the cost was worked out. Entries go stale when a vertex changes; the
 * simplifier checks the versions when it pops an entry and drops stale ones, which is cheaper than
 * finding and fixing entries in place.
 */
final class EdgeHeap {

    /** Ints per entry: two vertices and their two versions. */
    private static final int ENTRY_INTS = 4;

    /** How many entries there is room for at first. */
    private static final int INITIAL_CAPACITY = 1024;

    /** The costs. */
    private double[] costs = new double[INITIAL_CAPACITY];

    /** The vertices, two per entry, then their versions, two per entry. */
    private int[] data = new int[INITIAL_CAPACITY * ENTRY_INTS];

    /** How many entries there are. */
    private int size;

    /** The popped entry's first vertex. */
    int first;

    /** The popped entry's second vertex. */
    int second;

    /** The popped entry's first vertex's version. */
    int firstVersion;

    /** The popped entry's second vertex's version. */
    int secondVersion;

    /** The popped entry's cost. */
    double cost;

    /**
     * Whether there is nothing left.
     *
     * @return True if empty.
     */
    boolean isEmpty() {
        return size == 0;
    }

    /**
     * Add an entry.
     *
     * @param entryCost The cost.
     * @param a The first vertex.
     * @param b The second vertex.
     * @param aVersion The first vertex's version.
     * @param bVersion The second vertex's version.
     */
    void push(double entryCost, int a, int b, int aVersion, int bVersion) {
        if (size == costs.length) {
            costs = Arrays.copyOf(costs, size * 2);
            data = Arrays.copyOf(data, size * 2 * ENTRY_INTS);
        }
        int at = size++;
        while (at > 0) {
            final int parent = (at - 1) >>> 1;
            if (costs[parent] <= entryCost) {
                break;
            }
            move(parent, at);
            at = parent;
        }
        write(at, entryCost, a, b, aVersion, bVersion);
    }

    /** Remove the cheapest entry, leaving it in the public fields. */
    void pop() {
        cost = costs[0];
        first = data[0];
        second = data[1];
        firstVersion = data[2];
        secondVersion = data[3];
        --size;
        if (size == 0) {
            return;
        }
        final double lastCost = costs[size];
        final int a = data[size * ENTRY_INTS];
        final int b = data[size * ENTRY_INTS + 1];
        final int av = data[size * ENTRY_INTS + 2];
        final int bv = data[size * ENTRY_INTS + 3];
        int at = 0;
        while (true) {
            int child = at * 2 + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && costs[child + 1] < costs[child]) {
                ++child;
            }
            if (costs[child] >= lastCost) {
                break;
            }
            move(child, at);
            at = child;
        }
        write(at, lastCost, a, b, av, bv);
    }

    /**
     * Move an entry.
     *
     * @param from Where it is.
     * @param to Where it goes.
     */
    private void move(int from, int to) {
        costs[to] = costs[from];
        System.arraycopy(data, from * ENTRY_INTS, data, to * ENTRY_INTS, ENTRY_INTS);
    }

    /**
     * Write an entry.
     *
     * @param at Where.
     * @param entryCost The cost.
     * @param a The first vertex.
     * @param b The second vertex.
     * @param aVersion The first vertex's version.
     * @param bVersion The second vertex's version.
     */
    private void write(int at, double entryCost, int a, int b, int aVersion, int bVersion) {
        costs[at] = entryCost;
        data[at * ENTRY_INTS] = a;
        data[at * ENTRY_INTS + 1] = b;
        data[at * ENTRY_INTS + 2] = aVersion;
        data[at * ENTRY_INTS + 3] = bVersion;
    }
}
