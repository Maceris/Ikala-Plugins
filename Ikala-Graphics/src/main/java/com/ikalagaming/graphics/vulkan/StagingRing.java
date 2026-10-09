package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

import lombok.NonNull;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * A persistently mapped buffer that upload data is copied into before the render thread records
 * copies from it to images or buffers. Any thread can stage data; the render thread frees it once
 * the frame that read it has finished on the GPU.
 *
 * <p>Data that doesn't fit, either because it is large or because the ring is full, gets a buffer
 * of its own instead, so staging never blocks.
 */
public class StagingRing {

    /** The size of the ring, in bytes. */
    public static final long CAPACITY = 64L * 1024 * 1024;

    /** Anything larger than this many bytes gets its own buffer instead of using the ring. */
    public static final long MAX_RING_ALLOCATION = CAPACITY / 4;

    /** Offsets into the ring are aligned to this, which works for any texel or buffer copy. */
    private static final long ALIGNMENT = 16;

    /**
     * Staged data, ready to be copied from.
     *
     * @param buffer The VkBuffer holding the data.
     * @param offset The offset of the data in the buffer, in bytes.
     * @param size The size of the data, in bytes.
     * @param ringAllocation The range of the ring, or null if this has its own buffer.
     * @param dedicated The buffer of its own, or null if this is in the ring.
     */
    public record Staging(
            long buffer,
            long offset,
            long size,
            @Nullable RingAllocator.Allocation ringAllocation,
            @Nullable SharedBuffer dedicated) {}

    /** The ring buffer. */
    private final SharedBuffer ring;

    /** Tracks which ranges of the ring are in use. Guarded by {@link #lock}. */
    private final RingAllocator allocator;

    /** Guards {@link #allocator}, since any thread can stage data. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Create the ring buffer.
     *
     * @param state The Vulkan state.
     */
    public StagingRing(@NonNull VulkanState state) {
        ring = SharedBuffer.allocate(CAPACITY, state, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        allocator = new RingAllocator(CAPACITY);
    }

    /**
     * Copy data into staging memory. Safe from any thread.
     *
     * @param state The Vulkan state.
     * @param data The data to copy, from its position to its limit. Its position is unchanged.
     * @return Where the data was staged.
     */
    public Staging stage(@NonNull VulkanState state, @NonNull ByteBuffer data) {
        final int size = data.remaining();
        RingAllocator.Allocation allocation = null;
        if (size > 0 && size <= MAX_RING_ALLOCATION) {
            lock.lock();
            try {
                allocation = allocator.allocate(size, ALIGNMENT);
            } finally {
                lock.unlock();
            }
        }
        final Staging result;
        final long destination;
        if (allocation != null) {
            result = new Staging(ring.buffer, allocation.getOffset(), size, allocation, null);
            destination = ring.allocationInfo.pMappedData() + allocation.getOffset();
        } else {
            SharedBuffer dedicated =
                    SharedBuffer.allocate(
                            Math.max(size, 1), state, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            result = new Staging(dedicated.buffer, 0, size, null, dedicated);
            destination = dedicated.allocationInfo.pMappedData();
        }
        if (size > 0) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(data), destination, size);
        }
        return result;
    }

    /**
     * Free staged data. Only call this once the GPU is done reading it.
     *
     * @param state The Vulkan state.
     * @param staging The staged data.
     */
    public void free(@NonNull VulkanState state, @NonNull Staging staging) {
        if (staging.ringAllocation() != null) {
            lock.lock();
            try {
                allocator.free(staging.ringAllocation());
            } finally {
                lock.unlock();
            }
        }
        if (staging.dedicated() != null) {
            SharedBuffer.free(staging.dedicated(), state);
        }
    }

    /**
     * Destroy the ring buffer. The GPU must be idle, and nothing may stage data afterward.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(ring, state);
    }
}
