package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The GPU's copy of every instance in the scene, one entry per {@link InstanceRegistry} slot, kept
 * in device-local memory across frames. Only the entries that changed are copied in, at the start
 * of a frame, so a scene that isn't moving costs nothing to keep up to date.
 *
 * <p>World positions are doubles on the CPU. Each entry stores one as two floats, a high part and
 * the low part left over, and the camera is split the same way, so shaders work out camera-relative
 * positions as {@code (high - cameraHigh) + (low - cameraLow)}: exact near the camera wherever it
 * is, without doubles on the GPU. See {@link #high(double)} and {@link #low(double)}.
 *
 * <p>Entry layout (std430), {@link #ENTRY_SIZE} bytes:
 *
 * <pre>
 * vec4 positionHigh;  // xyz: high part of the world position, w: uniform scale
 * vec4 positionLow;   // xyz: low part of the world position, w: unused
 * vec4 rotation;      // quaternion x, y, z, w
 * uvec4 info;         // x: flags (FLAG_ALIVE, visibility mask in bits 8-15), yzw: reserved
 * </pre>
 */
@Slf4j
public class InstanceTable {

    /** The size of one entry, in bytes. */
    public static final int ENTRY_SIZE = 4 * 4 * Float.BYTES;

    /** Where the high part of the position and the scale start in an entry. */
    public static final int POSITION_HIGH_OFFSET = 0;

    /** Where the low part of the position starts in an entry. */
    public static final int POSITION_LOW_OFFSET = 4 * Float.BYTES;

    /** Where the rotation starts in an entry. */
    public static final int ROTATION_OFFSET = 2 * 4 * Float.BYTES;

    /** Where the flags start in an entry. */
    public static final int INFO_OFFSET = 3 * 4 * Float.BYTES;

    /** Set in the flags when the slot holds an instance. */
    public static final int FLAG_ALIVE = 1;

    /** Where the visibility mask starts in the flags, for ray tracing to filter instances by. */
    public static final int VISIBILITY_MASK_SHIFT = 8;

    /** Every visibility bit, which is what instances get until something needs fewer. */
    public static final int VISIBLE_TO_ALL = 0xFF;

    /** How many slots the table starts out holding. */
    public static final int INITIAL_SLOTS = 1024;

    /** Usage: read by compute, written by copies, and copied when it grows. */
    private static final int USAGE =
            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    /**
     * Which instances are where. -- GETTER -- Which instances are where.
     *
     * @return The instance registry.
     */
    @Getter private final InstanceRegistry registry = new InstanceRegistry();

    /**
     * The table. Replaced when it grows, so look it up each frame. -- GETTER -- The table.
     *
     * @return The current table buffer.
     */
    @Getter private SharedBuffer entries;

    /** Removed instances whose slots can be reused once frames in flight are done with them. */
    private final Queue<Integer> retiring = new ConcurrentLinkedQueue<>();

    /** Whether the table has been cleared, since new device memory holds whatever it held. */
    private boolean cleared;

    /**
     * Create the table.
     *
     * @param state The Vulkan state.
     */
    public InstanceTable(@NonNull VulkanState state) {
        entries = SharedBuffer.allocateDeviceLocal((long) INITIAL_SLOTS * ENTRY_SIZE, state, USAGE);
    }

    /**
     * The high part of a coordinate: the nearest float.
     *
     * @param value The coordinate.
     * @return The high part.
     */
    public static float high(double value) {
        return (float) value;
    }

    /**
     * The low part of a coordinate: what the high part leaves over, as a float.
     *
     * @param value The coordinate.
     * @return The low part.
     */
    public static float low(double value) {
        return (float) (value - (float) value);
    }

    /**
     * Write one entry.
     *
     * @param out Where to write it, at its position, which is left unchanged.
     * @param alive Whether the slot holds an instance.
     * @param position The world position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     */
    public static void writeEntry(
            @NonNull ByteBuffer out,
            boolean alive,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        final int base = out.position();
        out.putFloat(base + POSITION_HIGH_OFFSET, high(position.x()));
        out.putFloat(base + POSITION_HIGH_OFFSET + Float.BYTES, high(position.y()));
        out.putFloat(base + POSITION_HIGH_OFFSET + 2 * Float.BYTES, high(position.z()));
        out.putFloat(base + POSITION_HIGH_OFFSET + 3 * Float.BYTES, scale);
        out.putFloat(base + POSITION_LOW_OFFSET, low(position.x()));
        out.putFloat(base + POSITION_LOW_OFFSET + Float.BYTES, low(position.y()));
        out.putFloat(base + POSITION_LOW_OFFSET + 2 * Float.BYTES, low(position.z()));
        out.putFloat(base + POSITION_LOW_OFFSET + 3 * Float.BYTES, 0);
        out.putFloat(base + ROTATION_OFFSET, rotation.x());
        out.putFloat(base + ROTATION_OFFSET + Float.BYTES, rotation.y());
        out.putFloat(base + ROTATION_OFFSET + 2 * Float.BYTES, rotation.z());
        out.putFloat(base + ROTATION_OFFSET + 3 * Float.BYTES, rotation.w());
        final int flags = alive ? FLAG_ALIVE | VISIBLE_TO_ALL << VISIBILITY_MASK_SHIFT : 0;
        out.putInt(base + INFO_OFFSET, flags);
        out.putInt(base + INFO_OFFSET + Integer.BYTES, 0);
        out.putInt(base + INFO_OFFSET + 2 * Integer.BYTES, 0);
        out.putInt(base + INFO_OFFSET + 3 * Integer.BYTES, 0);
    }

    /**
     * Note that an instance was removed, so its slot is reused once no frame in flight can read it.
     * Safe from any thread.
     *
     * @param slot The slot {@link InstanceRegistry} returned, or -1 for nothing.
     */
    public void retire(int slot) {
        if (slot >= 0) {
            retiring.add(slot);
        }
    }

    /**
     * Grow the table if needed and copy in the entries that changed. Render thread only, at the
     * start of a frame, before anything reads the table.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer, recording.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        Integer slot;
        while ((slot = retiring.poll()) != null) {
            final int free = slot;
            state.deferFree(() -> registry.free(free));
        }

        // Packed back to back, then copied one region per slot
        final Packed packed = new Packed();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            registry.takeChanged(packed::add);
            // Read after taking the changes, so it covers every slot they mention
            final long needed = (long) registry.getSlotCapacity() * ENTRY_SIZE;
            final boolean grow = needed > entries.allocationInfo.size();
            if (cleared && !grow && packed.count == 0) {
                return;
            }
            // Earlier frames' reads and copies finish before the table is written
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                    VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    stack);
            if (!cleared) {
                // So slots that were never written read as not alive
                vkCmdFillBuffer(commandBuffer, entries.buffer, 0, entries.allocationInfo.size(), 0);
                cleared = true;
                memoryBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_2_CLEAR_BIT,
                        VK_ACCESS_2_TRANSFER_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                        VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                        stack);
            }
            if (grow) {
                entries = grown(state, commandBuffer, entries, needed, stack);
            }
            if (packed.count > 0) {
                packed.data.position(0).limit(packed.count * ENTRY_SIZE);
                final StagingRing.Staging staging = state.stagingRing.stage(state, packed.data);
                state.deferFree(() -> state.stagingRing.free(state, staging));
                VkBufferCopy.Buffer regions = VkBufferCopy.calloc(packed.count, stack);
                for (int i = 0; i < packed.count; ++i) {
                    regions.get(i)
                            .srcOffset(staging.offset() + (long) i * ENTRY_SIZE)
                            .dstOffset((long) packed.slots[i] * ENTRY_SIZE)
                            .size(ENTRY_SIZE);
                }
                vkCmdCopyBuffer(commandBuffer, staging.buffer(), entries.buffer, regions);
            }
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COPY_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    stack);
        } finally {
            packed.free();
        }
    }

    /** Changed entries packed back to back, with the slot each one goes to. */
    private static final class Packed {
        /** The packed entries. */
        private ByteBuffer data = MemoryUtil.memAlloc(64 * ENTRY_SIZE);

        /** The slot of each entry. */
        private int[] slots = new int[64];

        /** How many entries there are. */
        private int count;

        /**
         * Pack one entry, growing the space as needed.
         *
         * @param slot The slot.
         * @param alive Whether the slot holds an instance.
         * @param position The world position.
         * @param rotation The rotation.
         * @param scale The uniform scale.
         */
        void add(
                int slot,
                boolean alive,
                @NonNull Vector3dc position,
                @NonNull Quaternionfc rotation,
                float scale) {
            if (count == slots.length) {
                slots = Arrays.copyOf(slots, count * 2);
                data = MemoryUtil.memRealloc(data, count * 2 * ENTRY_SIZE);
            }
            data.limit(data.capacity()).position(count * ENTRY_SIZE);
            writeEntry(data, alive, position, rotation, scale);
            slots[count] = slot;
            count += 1;
        }

        /** Free the native memory. */
        void free() {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * Replace the table with a bigger one, copying its contents across on the GPU and clearing the
     * new part, so slots never written read as not alive. The old buffer is freed once this frame
     * is done.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     * @param buffer The table.
     * @param needed How many bytes it must hold.
     * @param stack The stack to allocate on.
     * @return The table to use from now on.
     */
    private static SharedBuffer grown(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull SharedBuffer buffer,
            long needed,
            @NonNull MemoryStack stack) {
        final long oldSize = buffer.allocationInfo.size();
        final long size = Math.max(needed, oldSize * 2);
        log.debug("Growing the instance table from {} to {} bytes", oldSize, size);
        SharedBuffer bigger = SharedBuffer.allocateDeviceLocal(size, state, buffer.usage);
        VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
        region.get(0).srcOffset(0).dstOffset(0).size(oldSize);
        vkCmdCopyBuffer(commandBuffer, buffer.buffer, bigger.buffer, region);
        vkCmdFillBuffer(commandBuffer, bigger.buffer, oldSize, size - oldSize, 0);
        state.deferFree(() -> SharedBuffer.free(buffer, state));
        return bigger;
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
    private static void memoryBarrier(
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
        retiring.clear();
        SharedBuffer.free(entries, state);
    }
}
