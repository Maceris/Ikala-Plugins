package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.util.IntIntTreeMap;
import com.ikalagaming.util.LongTreeSet;

/**
 * Hands out ranges of a linear space, like the vertices of a shared vertex buffer. Ranges are
 * measured in elements, not bytes, so they can be used directly as offsets in draw commands.
 *
 * <p>Allocation takes the smallest free range that fits (the lowest, among equal sizes), which
 * leaves big ranges whole for big meshes, and freeing merges a range with free neighbors on both
 * sides. When nothing fits, the space grows to at least double its size, or by a quarter once it is
 * large, and the caller grows the real buffer to match.
 *
 * <p>The bookkeeping is in primitive trees, so allocating and freeing make no garbage: meshes come
 * and go every frame while a world streams.
 *
 * <p>Not thread safe; callers hold their own lock.
 */
public class RangeAllocator {
    /** What {@link #fitting(int)} returns when nothing fits. */
    private static final long NO_FIT = -1;

    /**
     * How much a large space grows by at a time, as a fraction of its size: a quarter, so a buffer
     * of a gigabyte doesn't grow to two for a few more meshes.
     */
    public static final int LARGE_GROWTH_DIVISOR = 4;

    /** The most elements the space can ever hold. */
    private final int maxCapacity;

    /** The size from which the space grows by {@link #LARGE_GROWTH_DIVISOR} instead of doubling. */
    private final int largeCapacity;

    /** Free ranges' lengths by where they start, to merge neighbors. */
    private final IntIntTreeMap freeByOffset = new IntIntTreeMap();

    /**
     * Free ranges by size and then offset, packed by {@link LongTreeSet#pack}, so the smallest
     * fitting ranges come first.
     */
    private final LongTreeSet freeBySize = new LongTreeSet();

    /** The length of each allocated range, by where it starts. */
    private final IntIntTreeMap allocated = new IntIntTreeMap();

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
        this(initialCapacity, maxCapacity, maxCapacity);
    }

    /**
     * Create an empty space that grows in smaller steps once it is large.
     *
     * @param initialCapacity How many elements it starts out holding.
     * @param maxCapacity The most it can ever grow to.
     * @param largeCapacity From what size it grows by a {@link #LARGE_GROWTH_DIVISOR} part at a
     *     time instead of doubling.
     * @throws IllegalArgumentException If the capacities are negative, or the initial one is over
     *     the maximum.
     */
    public RangeAllocator(int initialCapacity, int maxCapacity, int largeCapacity) {
        if (initialCapacity < 0 || maxCapacity < initialCapacity) {
            throw new IllegalArgumentException(
                    "Bad capacities: " + initialCapacity + " of at most " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
        this.largeCapacity = largeCapacity;
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
        long fit = fitting(length);
        if (fit == NO_FIT) {
            if (!grow(length)) {
                return -1;
            }
            fit = fitting(length);
        }
        final int offset = LongTreeSet.low(fit);
        final int fitLength = LongTreeSet.high(fit);
        removeFree(offset, fitLength);
        if (fitLength > length) {
            addFree(offset + length, fitLength - length);
        }
        allocated.put(offset, length);
        used += length;
        return offset;
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
        // Lengths are never 0, so 0 means none
        if (allocated.get(offset, 0) != length) {
            throw new IllegalArgumentException(
                    "No allocated range of " + length + " elements at " + offset);
        }
        allocated.remove(offset);
        used -= length;
        int start = offset;
        int end = offset + length;
        final int before = freeByOffset.lowerEntry(offset);
        if (before != IntIntTreeMap.NONE) {
            final int beforeStart = freeByOffset.key(before);
            final int beforeLength = freeByOffset.value(before);
            if (beforeStart + beforeLength == start) {
                removeFree(beforeStart, beforeLength);
                start = beforeStart;
            }
        }
        final int afterLength = freeByOffset.get(end, 0);
        if (afterLength > 0) {
            removeFree(end, afterLength);
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
        return freeBySize.isEmpty() ? 0 : LongTreeSet.high(freeBySize.last());
    }

    /**
     * The smallest free range that fits a length, the lowest such if several are the same size.
     *
     * @param length The length needed.
     * @return The range packed as its length and offset, or {@link #NO_FIT} if none fits.
     */
    private long fitting(int length) {
        final int entry = freeBySize.ceilingEntry(LongTreeSet.pack(length, 0));
        return entry == LongTreeSet.NONE ? NO_FIT : freeBySize.value(entry);
    }

    /**
     * Grow the space to at least double its size, or by a {@link #LARGE_GROWTH_DIVISOR} part once
     * it is large, and enough for an allocation at its end.
     *
     * @param length The allocation that needs to fit.
     * @return False if it can't fit even at the maximum capacity.
     */
    private boolean grow(int length) {
        // A free range at the end counts toward the new allocation
        final int last = freeByOffset.lastEntry();
        final int lastStart = last == IntIntTreeMap.NONE ? 0 : freeByOffset.key(last);
        final int lastLength = last == IntIntTreeMap.NONE ? 0 : freeByOffset.value(last);
        final int freeAtEnd = lastStart + lastLength == capacity ? lastLength : 0;
        long needed = (long) capacity + length - freeAtEnd;
        long stepped =
                capacity >= largeCapacity
                        ? (long) capacity + capacity / LARGE_GROWTH_DIVISOR
                        : Math.max(1L, (long) capacity * 2);
        long next = Math.min(maxCapacity, Math.max(needed, stepped));
        if (next < needed) {
            return false;
        }
        int added = (int) (next - capacity);
        int start = capacity;
        capacity = (int) next;
        if (freeAtEnd > 0) {
            removeFree(lastStart, freeAtEnd);
            start = lastStart;
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
            freeBySize.add(LongTreeSet.pack(length, offset));
        }
    }

    /**
     * Stop tracking a free range.
     *
     * @param offset Where it starts.
     * @param length How long it is.
     */
    private void removeFree(int offset, int length) {
        freeByOffset.remove(offset);
        freeBySize.remove(LongTreeSet.pack(length, offset));
    }
}
