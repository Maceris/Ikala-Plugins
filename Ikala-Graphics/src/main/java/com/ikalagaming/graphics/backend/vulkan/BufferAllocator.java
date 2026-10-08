package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.GraphicsManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * A buddy allocator for a shared buffer, used to maintain large sparse buffers of the same kind of
 * elements. Blocks are handed out from a virtual address space of {@link #MAX_BUFFER_SIZE} bytes,
 * preferring the lowest free address, and the real buffer grows as needed to cover every allocated
 * block.
 */
@Slf4j
public class BufferAllocator {
    /** The maximum possible size of a buffer. */
    public static final int MAX_BUFFER_SIZE = 1 << 30;

    /** The buffer we are tracking allocations for. */
    private final SharedBuffer buffer;

    /**
     * The minimum allocation block size, must be a positive power of 2 and should generally be
     * relatively small (~16-64 ish) or memory could get too fragmented.
     */
    private final int minBlockSize;

    /**
     * The largest order we can have, going up to {@link #MAX_BUFFER_SIZE}. Based on the min block
     * size.
     */
    private final int maxOrder;

    /**
     * A list of free lists, where the index in the list is the order, and the set contains all the
     * free block addresses of that size. Sorted so that we can hand out the lowest address first,
     * which keeps the real buffer as small as possible.
     */
    private final List<TreeSet<Integer>> freeLists;

    /** A map from address to order (size) for all allocated blocks. */
    private final Map<Integer, Integer> allocatedBlocks;

    /**
     * Create a new allocator tracking the contents of a shared buffer.
     *
     * @param buffer The buffer we are tracking allocations for. May be resized by the allocator
     *     later.
     * @param minBlockSize The minimum allocation block size, must be a positive power of 2 and
     *     should generally be relatively small (~16-64 ish) or memory could get too fragmented.
     */
    public BufferAllocator(@NonNull SharedBuffer buffer, int minBlockSize) {
        this.buffer = buffer;
        if (minBlockSize <= 0) {
            throw new IllegalArgumentException("Min block size must be positive");
        }
        if ((minBlockSize & (minBlockSize - 1)) != 0) {
            throw new IllegalArgumentException("Min block size must be a power of 2");
        }
        if (minBlockSize > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                    "Min block size is entirely too large: " + minBlockSize);
        }
        this.minBlockSize = minBlockSize;
        if (buffer.allocationInfo.size() > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                    "We cannot handle a buffer of size " + buffer.allocationInfo.size());
        }
        this.maxOrder = log2(MAX_BUFFER_SIZE / minBlockSize);
        this.freeLists = new ArrayList<>();
        // Orders 0 through maxOrder inclusive
        for (int i = 0; i <= this.maxOrder; i++) {
            this.freeLists.add(new TreeSet<>());
        }
        this.allocatedBlocks = new HashMap<>();
        this.freeLists.get(this.maxOrder).add(0);
    }

    /**
     * Allocate a block with the given size. If the buffer can't fit what we want to store, the
     * buffer will be re-allocated to fit, with the old contents copied over (addresses are stable
     * even though the actual GPU-side buffer may change).
     *
     * @param size The memory we want to allocate.
     * @return The memory address in the buffer where the allocation lives.
     */
    public int allocate(int size) {
        return allocate(size, false);
    }

    /**
     * Allocate a block with the given size. If the buffer can't fit what we want to store, the
     * buffer will be re-allocated to fit, with the old contents copied over (addresses are stable
     * even though the actual GPU-side buffer may change).
     *
     * @param size The memory we want to allocate.
     * @param clear Whether to zero out the memory we allocated.
     * @return The memory address in the buffer where the allocation lives.
     */
    public int allocate(int size, boolean clear) {
        if (size <= 0 || size > MAX_BUFFER_SIZE) {
            return -1;
        }

        final int targetSize = Math.max(minBlockSize, nextPowerOf2(size));
        final int targetOrder = log2(targetSize / minBlockSize);

        int currentOrder = targetOrder;
        Integer freeAddress = null;
        while (currentOrder <= maxOrder) {
            freeAddress = freeLists.get(currentOrder).pollFirst();
            if (freeAddress != null) {
                break;
            }
            currentOrder += 1;
        }

        if (freeAddress == null) {
            log.warn(
                    "Ran out of memory in allocator for buffer {}, trying to allocate {} bytes",
                    buffer.deviceAddress,
                    size);
            return -1;
        }

        int blockAddress = freeAddress;
        while (currentOrder > targetOrder) {
            currentOrder -= 1;
            int currentBlockSize = (1 << currentOrder) * minBlockSize;
            int buddyAddress = blockAddress + currentBlockSize;

            freeLists.get(currentOrder).add(buddyAddress);
        }

        allocatedBlocks.put(blockAddress, targetOrder);

        ensureCovered((long) blockAddress + targetSize);

        if (clear) {
            final int ZERO = 0;
            MemoryUtil.memSet(buffer.allocationInfo.pMappedData() + blockAddress, ZERO, targetSize);
        }

        return blockAddress;
    }

    /**
     * Grow the buffer if needed so that it covers the given number of bytes, keeping the existing
     * contents. Grows to at least double the current size to avoid frequent reallocation.
     *
     * @param requiredSize The number of bytes the buffer must be able to hold.
     */
    private void ensureCovered(long requiredSize) {
        final long oldSize = buffer.allocationInfo.size();
        if (requiredSize <= oldSize) {
            return;
        }
        final long nextPowerOf2 = Math.max(requiredSize, Long.highestOneBit(requiredSize - 1) << 1);
        // Blocks never extend past MAX_BUFFER_SIZE, so nextPowerOf2 <= MAX_BUFFER_SIZE here
        final long newSize = Math.clamp(oldSize * 2, nextPowerOf2, MAX_BUFFER_SIZE);
        buffer.ensureFits(
                newSize, (VulkanState) GraphicsManager.getRenderInstance().getState(), true);
    }

    /** Clear out all the data about allocations, leaves the buffer alone. */
    public void clear() {
        this.freeLists.forEach(Set::clear);
        this.allocatedBlocks.clear();
        this.freeLists.get(this.maxOrder).add(0);
    }

    /**
     * Free up an allocation. The underlying buffer is unaffected.
     *
     * @param address A memory address returned by the allocate method.
     * @return Whether we succeeded.
     */
    public boolean free(int address) {
        if (!allocatedBlocks.containsKey(address)) {
            log.debug(
                    "Address {} was not allocated, may be unallocated or offset from the allocation address",
                    address);
            return false;
        }

        int order = allocatedBlocks.remove(address);
        int currentAddress = address;

        while (order < maxOrder) {
            int blockSize = (1 << order) * minBlockSize;
            int buddyAddress = currentAddress ^ blockSize;

            if (freeLists.get(order).contains(buddyAddress)) {
                freeLists.get(order).remove(buddyAddress);

                currentAddress = Math.min(currentAddress, buddyAddress);
                order += 1;
            } else {
                // Buddy cannot be merged, it's not free
                break;
            }
        }

        freeLists.get(order).add(currentAddress);
        return true;
    }

    /**
     * Finds the log base 2 of a number. Only works on positive numbers, negative numbers will
     * result in effectively junk results.
     *
     * @param n The number.
     * @return log_2(n).
     */
    private int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    /**
     * Find the next largest power of 2. Only intended for positive numbers, negative numbers will
     * result in effectively junk results.
     *
     * @param n The number.
     * @return The next largest power of 2, or n if it's already a power of 2.
     */
    private int nextPowerOf2(int n) {
        if ((n & (n - 1)) == 0) {
            // It's already a power of 2
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }

    /**
     * Store data in the given address. Must actually fit in the allocation, and the address must be
     * one returned by the allocator. Anything more niche than storing n bytes in the associated
     * allocation of n bytes should be done by hand.
     *
     * @param address An address that was returned by the allocate method.
     * @param data The data to store, must be a native allocation, so we can get at it with a
     *     memcopy.
     * @return Whether we succeeded.
     */
    public boolean store(int address, @NonNull ByteBuffer data) {
        if (!allocatedBlocks.containsKey(address)) {
            log.warn(
                    "Address {} was not allocated, may be unallocated or offset from the allocation address",
                    address);
            return false;
        }
        final int order = allocatedBlocks.get(address);
        final int blockSize = (1 << order) * minBlockSize;
        final int dataSize = data.limit();
        if (dataSize > blockSize) {
            log.warn(
                    "Trying to write more data ({} bytes) than space was allocated for ({} bytes)",
                    dataSize,
                    blockSize);
            return false;
        }

        MemoryUtil.memCopy(
                MemoryUtil.memAddress(data),
                buffer.allocationInfo.pMappedData() + address,
                dataSize);

        return true;
    }
}
