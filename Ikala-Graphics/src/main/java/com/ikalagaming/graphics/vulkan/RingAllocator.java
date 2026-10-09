package com.ikalagaming.graphics.vulkan;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hands out ranges of a fixed size buffer in ring order. Ranges can be freed in any order, but
 * space is only reclaimed once everything allocated before it is freed too, which is fine for
 * staging data that lives for a frame or two.
 *
 * <p>This is only offset bookkeeping, with no locking. {@link StagingRing} wraps it with a lock and
 * a real buffer.
 */
public class RingAllocator {

    /** A range of the ring. */
    @Getter
    public static final class Allocation {
        /**
         * The offset of the range, in bytes.
         *
         * @return The offset.
         */
        private final long offset;

        /**
         * The size of the range, in bytes.
         *
         * @return The size.
         */
        private final long size;

        /** Whether the range was freed. */
        private boolean freed;

        private Allocation(long offset, long size) {
            this.offset = offset;
            this.size = size;
            freed = false;
        }
    }

    /**
     * The size of the ring, in bytes.
     *
     * @return The capacity.
     */
    @Getter private final long capacity;

    /** Live allocations, oldest first. */
    private final Deque<Allocation> live = new ArrayDeque<>();

    /** Where the next allocation starts looking, in bytes. */
    private long head;

    /**
     * Whether the newest allocations wrapped around to the start of the ring, so they sit before
     * the oldest one.
     */
    private boolean wrapped;

    /**
     * Create an empty ring.
     *
     * @param capacity The size of the ring, in bytes.
     */
    public RingAllocator(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("The capacity must be positive");
        }
        this.capacity = capacity;
        head = 0;
        wrapped = false;
    }

    /**
     * Allocate a range.
     *
     * @param size The size in bytes, which must be positive.
     * @param alignment The required alignment of the offset in bytes, a power of two.
     * @return The range, or null if there isn't enough contiguous space right now.
     */
    public Allocation allocate(long size, long alignment) {
        if (size <= 0) {
            throw new IllegalArgumentException("The size must be positive");
        }
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("The alignment must be a power of two");
        }
        if (live.isEmpty()) {
            head = 0;
            wrapped = false;
        }
        long start = alignUp(head, alignment);
        if (!wrapped) {
            if (start + size > capacity) {
                // Not enough room at the end, so try the start, before the oldest allocation
                if (live.isEmpty() || size > live.peekFirst().getOffset()) {
                    return null;
                }
                start = 0;
                wrapped = true;
            }
        } else if (start + size > live.peekFirst().getOffset()) {
            return null;
        }
        Allocation allocation = new Allocation(start, size);
        live.addLast(allocation);
        head = start + size;
        return allocation;
    }

    /**
     * Free a range. Freeing the same range twice does nothing.
     *
     * @param allocation The range to free.
     */
    public void free(@NonNull Allocation allocation) {
        allocation.freed = true;
        while (!live.isEmpty() && live.peekFirst().freed) {
            Allocation oldest = live.pollFirst();
            if (live.isEmpty()) {
                head = 0;
                wrapped = false;
            } else if (wrapped && live.peekFirst().getOffset() < oldest.getOffset()) {
                // The oldest allocation is now one that wrapped around
                wrapped = false;
            }
        }
    }

    /**
     * Count the allocations that haven't been reclaimed yet.
     *
     * @return The number of live allocations, including freed ones still waiting on older ones.
     */
    public int liveCount() {
        return live.size();
    }

    /**
     * Round up to a multiple of a power of two.
     *
     * @param value The value.
     * @param alignment The alignment, a power of two.
     * @return The aligned value.
     */
    private static long alignUp(long value, long alignment) {
        return (value + alignment - 1) & -alignment;
    }
}
