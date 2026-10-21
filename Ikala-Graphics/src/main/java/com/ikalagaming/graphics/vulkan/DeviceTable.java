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

    /**
     * Usage: read by shaders, written by copies, fills and the scatter dispatch, and copied when it
     * grows.
     */
    private static final int USAGE =
            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    /**
     * The most runs of consecutive entries written with a copy region each; past this the entries
     * are scattered by a compute dispatch, see {@link TableScatter}, since drivers and validation
     * slow down with many regions: tens of thousands took over 100 ms when the camera jumped.
     */
    static final int SCATTER_RUNS = 32;

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

    /**
     * How many bytes the table's buffer was made with. Its allocation may be bigger, but copies and
     * fills must stay within the buffer.
     */
    private long bufferBytes;

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

        /**
         * Put the entries in index order, an index written more than once keeping its last entry,
         * so consecutive indices make one copy region however they were written: entries for one
         * piece of work are often written scattered, and drivers slow down with many regions.
         */
        void sortByIndex() {
            boolean sorted = true;
            for (int i = 1; i < count && sorted; ++i) {
                sorted = indices[i] > indices[i - 1];
            }
            if (sorted) {
                return;
            }
            // The index high, the write order low, so the last write of an index sorts last
            final long[] order = new long[count];
            for (int i = 0; i < count; ++i) {
                order[i] = ((long) indices[i] << Integer.SIZE) | i;
            }
            Arrays.sort(order);
            final ByteBuffer packed = MemoryUtil.memAlloc(indices.length * entrySize);
            final int[] packedIndices = new int[indices.length];
            int kept = 0;
            for (int k = 0; k < count; ++k) {
                final int index = (int) (order[k] >>> Integer.SIZE);
                if (k + 1 < count && (int) (order[k + 1] >>> Integer.SIZE) == index) {
                    continue;
                }
                final int from = (int) order[k];
                MemoryUtil.memCopy(
                        MemoryUtil.memAddress0(data) + (long) from * entrySize,
                        MemoryUtil.memAddress0(packed) + (long) kept * entrySize,
                        entrySize);
                packedIndices[kept++] = index;
            }
            MemoryUtil.memFree(data);
            data = packed;
            indices = packedIndices;
            count = kept;
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
        bufferBytes = (long) Math.max(1, initialEntries) * entrySize;
        buffer = SharedBuffer.allocateDeviceLocal(bufferBytes, state, USAGE);
    }

    /**
     * Whether recording would do anything.
     *
     * @param capacity How many entries the table must hold.
     * @param entries The changed entries.
     * @return False if the table is cleared, big enough, and nothing changed.
     */
    public boolean hasWork(int capacity, @NonNull Entries entries) {
        return !cleared || (long) capacity * entrySize > bufferBytes || entries.count > 0;
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
            vkCmdFillBuffer(commandBuffer, buffer.buffer, 0, bufferBytes, 0);
            cleared = true;
            transferBarrier(commandBuffer, stack);
        }
        final long needed = (long) capacity * entrySize;
        if (needed > bufferBytes) {
            final long size = Math.max(needed, bufferBytes * 2);
            buffer = grown(state, commandBuffer, buffer, bufferBytes, size, stack);
            bufferBytes = size;
            transferBarrier(commandBuffer, stack);
        }
        if (entries.count == 0) {
            return;
        }
        entries.sortByIndex();
        final int count = entries.count;
        entries.data.position(0).limit(count * entrySize);
        final StagingRing.Staging staging = state.stagingRing.stage(state, entries.data);
        state.deferFree(() -> state.stagingRing.free(state, staging));
        if (runsOf(entries.indices, count) > SCATTER_RUNS && state.tableScatter != null) {
            scatter(state, commandBuffer, entries, staging, stack);
            return;
        }
        // Entries packed for consecutive indices go in one region, since whole ranges are often
        // written at once and drivers and validation slow down with many regions. Even so there
        // can be far more than the stack holds, as when a whole world is placed.
        VkBufferCopy.Buffer regions = VkBufferCopy.calloc(count);
        try {
            int used = 0;
            int runStart = 0;
            for (int i = 1; i <= count; ++i) {
                if (i < count && entries.indices[i] == entries.indices[i - 1] + 1) {
                    continue;
                }
                regions.get(used++)
                        .srcOffset(staging.offset() + (long) runStart * entrySize)
                        .dstOffset((long) entries.indices[runStart] * entrySize)
                        .size((long) (i - runStart) * entrySize);
                runStart = i;
            }
            regions.limit(used);
            vkCmdCopyBuffer(commandBuffer, staging.buffer(), buffer.buffer, regions);
        } finally {
            regions.limit(regions.capacity());
            regions.free();
        }
    }

    /**
     * How many runs of consecutive indices sorted entries fall in, each a copy region.
     *
     * @param indices The entries' indices, sorted.
     * @param count How many entries.
     * @return The runs, at least 1.
     */
    static int runsOf(int @NonNull [] indices, int count) {
        int runs = 1;
        for (int i = 1; i < count; ++i) {
            if (indices[i] != indices[i - 1] + 1) {
                runs += 1;
            }
        }
        return runs;
    }

    /**
     * Write entries scattered over many runs with a compute dispatch rather than a copy region a
     * run, see {@link TableScatter}.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     * @param entries The entries, sorted, each index once.
     * @param staging Where the packed entries are staged.
     * @param stack The stack to allocate on.
     */
    private void scatter(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull Entries entries,
            StagingRing.@NonNull Staging staging,
            @NonNull MemoryStack stack) {
        final int count = entries.count;
        final ByteBuffer indexBytes = MemoryUtil.memAlloc(count * Integer.BYTES);
        final StagingRing.Staging indexStaging;
        try {
            indexBytes.asIntBuffer().put(entries.indices, 0, count);
            indexStaging = state.stagingRing.stage(state, indexBytes);
        } finally {
            MemoryUtil.memFree(indexBytes);
        }
        state.deferFree(() -> state.stagingRing.free(state, indexStaging));
        // After any clear or growth of the table this frame
        memoryBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                stack);
        state.tableScatter.record(
                commandBuffer,
                staging.deviceAddress(),
                indexStaging.deviceAddress(),
                buffer.deviceAddress,
                count,
                entrySize,
                stack);
    }

    /**
     * Replace the table with a bigger one, copying its contents across and clearing the new part.
     * The old buffer is freed once this frame is done.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     * @param old The table.
     * @param oldSize How many bytes the old buffer was made with.
     * @param size How many bytes the new one holds.
     * @param stack The stack to allocate on.
     * @return The table to use from now on.
     */
    private SharedBuffer grown(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull SharedBuffer old,
            long oldSize,
            long size,
            @NonNull MemoryStack stack) {
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
     * Make earlier frames' reads and copies finish before tables are written, and the writes of
     * tables shaders keep up themselves visible to the copy when one grows.
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
                VK_ACCESS_2_TRANSFER_WRITE_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT
                        | VK_PIPELINE_STAGE_2_CLEAR_BIT
                        | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT
                        | VK_ACCESS_2_TRANSFER_WRITE_BIT
                        | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
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
                VK_PIPELINE_STAGE_2_COPY_BIT
                        | VK_PIPELINE_STAGE_2_CLEAR_BIT
                        | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
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
