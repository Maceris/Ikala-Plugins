package com.ikalagaming.graphics.backend.vulkan;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.TreeSet;

/**
 * A buddy allocator for a shared buffer, used to maintain large sparse buffers of the same kind of
 * elements.
 */
public class BufferAllocator {
    /** The maximum possible size of a buffer. */
    public static final int MAX_BUFFER_SIZE = 1 << 30;

    // TODO(ches) finish this
    private record Pair(int lowerBound, int upperBound) {}

    public final SharedBuffer buffer;
    private final int minBlockSize;
    private int maxBlockSize;
    private int maxOrder;
    private int totalMemory;
    private List<TreeSet<Integer>> freeLists;

    public BufferAllocator(@NonNull SharedBuffer buffer, int minBlockSize) {
        this.buffer = buffer;
        this.minBlockSize = minBlockSize;
        if (buffer.allocationInfo.size() > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                    "We cannot handle a buffer of size " + buffer.allocationInfo.size());
        }
        this.totalMemory = (int) buffer.allocationInfo.size();
        this.maxOrder = log2(totalMemory / minBlockSize);
    }

    private int findSlot(int size) {
        // TODO(ches) finish this
        return -1;
    }

    public void free(long handle) {
        // TODO(ches) finish this
    }

    private void free(int address, int size) {
        // TODO(ches) finish this

    }

    private int log2(int bits) {
        return Integer.numberOfTrailingZeros(bits);
    }

    private int nextPowerOf2(int n) {
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }

    private void reallocate() {
        // TODO(ches) finish this
        // for when we need to make a larger buffer
    }

    public long store(@NonNull ByteBuffer data) {
        // TODO(ches) finish this
        int size = data.limit();

        return 0;
    }
}
