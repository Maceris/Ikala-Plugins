package com.ikalagaming.graphics.vulkan;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Hands out ranges of a linear space, like the vertices of a shared vertex buffer. Ranges are
 * measured in elements, not bytes, so they can be used directly as offsets in draw commands.
 *
 * <p>Allocation takes the smallest free range that fits (the lowest, among equal sizes), which
 * leaves big ranges whole for big meshes, and freeing merges a range with free neighbors on both
 * sides. When nothing fits, the space grows to at least double its size, and the caller grows the
 * real buffer to match.
 *
 * <p>Not thread safe; callers hold their own lock.
 */
public class RangeAllocator {
    /**
     * A free range, ordered by size and then offset, so the smallest fitting ranges come first.
     *
     * @param offset Where it starts.
     * @param length How long it is.
     */
    private record Free(int offset, int length) implements Comparable<Free> {
        @Override
        public int compareTo(Free other) {
            int byLength = Integer.compare(length, other.length);
            return byLength != 0 ? byLength : Integer.compare(offset, other.offset);
        }
    }

    /** The most elements the space can ever hold. */
    private final int maxCapacity;

    /** Free ranges by where they start, to merge neighbors. */
    private final TreeMap<Integer, Integer> freeByOffset = new TreeMap<>();

    /** Free ranges by size, to find one that fits. */
    private final TreeSet<Free> freeBySize = new TreeSet<>();

    /** The length of each allocated range, by where it starts. */
    private final Map<Integer, Integer> allocated = new HashMap<>();

    /** How many elements the space holds now. */
    private int capacity;

    /** How many elements are allocated. */
    private int used;

    /**
     * Create an empty space.
     *
     * @param initialCapacity How many elements it starts out holding.
     * @param maxCapacity The most it can ever grow to.
     * @throws IllegalArgumentException If the capacities are negative, or the initial one is over
     *     the maximum.
     */
    public RangeAllocator(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 0 || maxCapacity < initialCapacity) {
            throw new IllegalArgumentException(
                    "Bad capacities: " + initialCapacity + " of at most " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
        capacity = initialCapacity;
        addFree(0, initialCapacity);
    }

    /**
     * Allocate a range, growing the space if nothing free fits. Ranges of length zero are allowed
     * and take no space.
     *
     * @param length How many elements.
     * @return Where the range starts, or -1 if it can't fit even at the maximum capacity.
     */
    public int allocate(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Can't allocate " + length + " elements");
        }
        if (length == 0) {
            return 0;
        }
        Free fit = fitting(length);
        if (fit == null) {
            if (!grow(length)) {
                return -1;
            }
            fit = fitting(length);
        }
        removeFree(fit);
        if (fit.length() > length) {
            addFree(fit.offset() + length, fit.length() - length);
        }
        allocated.put(fit.offset(), length);
        used += length;
        return fit.offset();
    }

    /**
     * Free a range, merging it with free neighbors.
     *
     * @param offset Where the range starts, as returned by {@link #allocate(int)}.
     * @param length How long it is. Ranges of length zero are ignored.
     * @throws IllegalArgumentException If no range of that length starts there.
     */
    public void free(int offset, int length) {
        if (length == 0) {
            return;
        }
        Integer known = allocated.get(offset);
        if (known == null || known != length) {
            throw new IllegalArgumentException(
                    "No allocated range of " + length + " elements at " + offset);
        }
        allocated.remove(offset);
        used -= length;
        int start = offset;
        int end = offset + length;
        var before = freeByOffset.floorEntry(offset - 1);
        if (before != null && before.getKey() + before.getValue() == start) {
            removeFree(new Free(before.getKey(), before.getValue()));
            start = before.getKey();
        }
        Integer afterLength = freeByOffset.get(end);
        if (afterLength != null) {
            removeFree(new Free(end, afterLength));
            end += afterLength;
        }
        addFree(start, end - start);
    }

    /**
     * How many elements the space holds now. The real buffer must be at least this big.
     *
     * @return The capacity in elements.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * How many elements are allocated.
     *
     * @return The used element count.
     */
    public int getUsed() {
        return used;
    }

    /**
     * The longest free range, which is the largest allocation that fits without growing.
     *
     * @return Its length in elements, or 0 if nothing is free.
     */
    public int getLargestFree() {
        return freeBySize.isEmpty() ? 0 : freeBySize.last().length();
    }

    /**
     * The smallest free range that fits a length, the lowest such if several are the same size.
     *
     * @param length The length needed.
     * @return The range, or null if none fits.
     */
    private Free fitting(int length) {
        return freeBySize.ceiling(new Free(-1, length));
    }

    /**
     * Grow the space to at least double its size, and enough for an allocation at its end.
     *
     * @param length The allocation that needs to fit.
     * @return False if it can't fit even at the maximum capacity.
     */
    private boolean grow(int length) {
        // A free range at the end counts toward the new allocation
        var last = freeByOffset.lastEntry();
        int freeAtEnd =
                last != null && last.getKey() + last.getValue() == capacity ? last.getValue() : 0;
        long needed = (long) capacity + length - freeAtEnd;
        long doubled = Math.max(1L, (long) capacity * 2);
        long next = Math.min(maxCapacity, Math.max(needed, doubled));
        if (next < needed) {
            return false;
        }
        int added = (int) (next - capacity);
        int start = capacity;
        capacity = (int) next;
        if (freeAtEnd > 0) {
            removeFree(new Free(last.getKey(), freeAtEnd));
            start = last.getKey();
            added += freeAtEnd;
        }
        addFree(start, added);
        return true;
    }

    /**
     * Track a free range.
     *
     * @param offset Where it starts.
     * @param length How long it is; nothing is tracked if zero.
     */
    private void addFree(int offset, int length) {
        if (length > 0) {
            freeByOffset.put(offset, length);
            freeBySize.add(new Free(offset, length));
        }
    }

    /**
     * Stop tracking a free range.
     *
     * @param range The range.
     */
    private void removeFree(Free range) {
        freeByOffset.remove(range.offset());
        freeBySize.remove(range);
    }
}
