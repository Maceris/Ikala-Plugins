package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A table of fixed-size entries in device-local memory that shaders read, kept across frames and
 * updated by copying in only the entries that changed. It grows by doubling, copying its contents
 * across on the GPU, and starts out cleared to zero so entries that were never written read as
 * empty.
 *
 * <p>Render thread only. Callers pack changed entries into {@link Entries}, then record them with
 * {@link #record}, between {@link #barrierBefore} and {@link #barrierAfter}, so several tables
 * share one pair of barriers.
 */
@Slf4j
public final class DeviceTable {

    /** Usage: read by shaders, written by copies and fills, and copied when it grows. */
    private static final int USAGE =
            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    /** What the table holds, for log messages. */
    private final String name;

    /**
     * The size of one entry, in bytes. -- GETTER -- The size of one entry.
     *
     * @return The entry size in bytes.
     */
    @Getter private final int entrySize;

    /**
     * The table. Replaced when it grows, so look it up each frame. -- GETTER -- The table.
     *
     * @return The current table buffer.
     */
    @Getter private SharedBuffer buffer;

    /** Whether the table has been cleared, since new device memory holds whatever it held. */
    private boolean cleared;

    /**
     * Changed entries packed back to back, with where each one goes. Reuse one per frame, or make
     * one per upload, and {@link #free()} it afterward.
     */
    public static final class Entries {
        /** The size of one entry, in bytes. */
        private final int entrySize;

        /** The packed entries. */
        private ByteBuffer data;

        /** Where each entry goes, as an index into the table. */
        private int[] indices = new int[64];

        /**
         * How many entries are packed. -- GETTER -- How many entries are packed.
         *
         * @return The entry count.
         */
        @Getter private int count;

        /**
         * Make an empty set of entries.
         *
         * @param entrySize The size of one entry, in bytes.
         */
        public Entries(int entrySize) {
            this.entrySize = entrySize;
            data = MemoryUtil.memAlloc(indices.length * entrySize);
        }

        /**
         * Start the next entry.
         *
         * @param index Where it goes in the table.
         * @return A buffer positioned at the start of the entry, with room for it. Write with
         *     absolute puts from that position, which is left for the next entry.
         */
        public ByteBuffer next(int index) {
            if (count == indices.length) {
                indices = Arrays.copyOf(indices, count * 2);
                data = MemoryUtil.memRealloc(data, count * 2 * entrySize);
            }
            indices[count] = index;
            data.limit(data.capacity()).position(count * entrySize);
            MemoryUtil.memSet(MemoryUtil.memAddress(data), 0, entrySize);
            count += 1;
            return data;
        }

        /** Free the native memory. */
        public void free() {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * Create a table.
     *
     * @param state The Vulkan state.
     * @param name What the table holds, for log messages.
     * @param entrySize The size of one entry, in bytes.
     * @param initialEntries How many entries it starts out holding.
     */
    public DeviceTable(
            @NonNull VulkanState state, @NonNull String name, int entrySize, int initialEntries) {
        this.name = name;
        this.entrySize = entrySize;
        buffer =
                SharedBuffer.allocateDeviceLocal(
                        (long) Math.max(1, initialEntries) * entrySize, state, USAGE);
    }

    /**
     * Whether recording would do anything.
     *
     * @param capacity How many entries the table must hold.
     * @param entries The changed entries.
     * @return False if the table is cleared, big enough, and nothing changed.
     */
    public boolean hasWork(int capacity, @NonNull Entries entries) {
        return !cleared
                || (long) capacity * entrySize > buffer.allocationInfo.size()
                || entries.count > 0;
    }

    /**
     * Clear the table the first time, grow it if needed, and copy in the changed entries. Call
     * between {@link #barrierBefore} and {@link #barrierAfter}.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer, recording.
     * @param capacity How many entries the table must hold.
     * @param entries The changed entries.
     * @param stack The stack to allocate on.
     */
    public void record(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            int capacity,
            @NonNull Entries entries,
            @NonNull MemoryStack stack) {
        if (!cleared) {
            vkCmdFillBuffer(commandBuffer, buffer.buffer, 0, buffer.allocationInfo.size(), 0);
            cleared = true;
            transferBarrier(commandBuffer, stack);
        }
        final long needed = (long) capacity * entrySize;
        if (needed > buffer.allocationInfo.size()) {
            buffer = grown(state, commandBuffer, buffer, needed, stack);
            transferBarrier(commandBuffer, stack);
        }
        final int count = entries.count;
        if (count == 0) {
            return;
        }
        entries.data.position(0).limit(count * entrySize);
        final StagingRing.Staging staging = state.stagingRing.stage(state, entries.data);
        state.deferFree(() -> state.stagingRing.free(state, staging));
        VkBufferCopy.Buffer regions = VkBufferCopy.calloc(count, stack);
        for (int i = 0; i < count; ++i) {
            regions.get(i)
                    .srcOffset(staging.offset() + (long) i * entrySize)
                    .dstOffset((long) entries.indices[i] * entrySize)
                    .size(entrySize);
        }
        vkCmdCopyBuffer(commandBuffer, staging.buffer(), buffer.buffer, regions);
    }

    /**
     * Replace the table with a bigger one, copying its contents across and clearing the new part.
     * The old buffer is freed once this frame is done.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     * @param old The table.
     * @param needed How many bytes it must hold.
     * @param stack The stack to allocate on.
     * @return The table to use from now on.
     */
    private SharedBuffer grown(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull SharedBuffer old,
            long needed,
            @NonNull MemoryStack stack) {
        final long oldSize = old.allocationInfo.size();
        final long size = Math.max(needed, oldSize * 2);
        log.debug("Growing the {} table from {} to {} bytes", name, oldSize, size);
        SharedBuffer bigger = SharedBuffer.allocateDeviceLocal(size, state, old.usage);
        VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
        region.get(0).srcOffset(0).dstOffset(0).size(oldSize);
        vkCmdCopyBuffer(commandBuffer, old.buffer, bigger.buffer, region);
        vkCmdFillBuffer(commandBuffer, bigger.buffer, oldSize, size - oldSize, 0);
        state.deferFree(() -> SharedBuffer.free(old, state));
        return bigger;
    }

    /**
     * Make earlier frames' reads and copies finish before tables are written.
     *
     * @param commandBuffer The frame's command buffer.
     * @param stack The stack to allocate on.
     */
    public static void barrierBefore(
            @NonNull VkCommandBuffer commandBuffer, @NonNull MemoryStack stack) {
        memoryBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_COPY_BIT
                        | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                stack);
    }

    /**
     * Make the tables' new contents visible to the shaders that read them.
     *
     * @param commandBuffer The frame's command buffer.
     * @param stack The stack to allocate on.
     */
    public static void barrierAfter(
            @NonNull VkCommandBuffer commandBuffer, @NonNull MemoryStack stack) {
        memoryBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                stack);
    }

    /**
     * Order transfers that touch the same table one after another.
     *
     * @param commandBuffer The command buffer.
     * @param stack The stack to allocate on.
     */
    private static void transferBarrier(
            @NonNull VkCommandBuffer commandBuffer, @NonNull MemoryStack stack) {
        memoryBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                stack);
    }

    /**
     * Record a global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param stack The stack to allocate on.
     */
    static void memoryBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            @NonNull MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }

    /**
     * Free the table, when shutting down. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(buffer, state);
    }
}
