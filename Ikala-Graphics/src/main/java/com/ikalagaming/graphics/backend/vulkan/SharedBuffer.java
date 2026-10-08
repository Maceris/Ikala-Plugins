package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.frontend.Buffer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;

import java.nio.LongBuffer;

/**
 * Buffer that is shared between the CPU and the GPU. The memory is always host visible, host
 * coherent, and persistently mapped, so it can be written through {@link
 * VmaAllocationInfo#pMappedData()} without staging or flushing.
 */
@Slf4j
public class SharedBuffer implements Buffer {

    /**
     * Usage flags that every shared buffer gets in addition to what is requested. Device addresses
     * are always available, and transfer destination allows vkCmdUpdateBuffer and copies.
     */
    public static final int BASE_USAGE =
            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;

    /**
     * A buffer that has been replaced but may still be in use by a frame in flight.
     *
     * @param buffer The VkBuffer handle.
     * @param allocation The VMA allocation handle.
     */
    public record RetiredBuffer(long buffer, long allocation) {}

    /** VMA handle for the allocation. */
    public long allocation = VK_NULL_HANDLE;

    /** VMA allocation info. */
    public VmaAllocationInfo allocationInfo = VmaAllocationInfo.create();

    /** The buffer handle on the CPU side. VK_NULL_HANDLE if there is no buffer. */
    public long buffer = VK_NULL_HANDLE;

    /** The device address on the GPU side. */
    public long deviceAddress = VK_NULL_HANDLE;

    /** Whether the buffer has been updated, and would need bindings updated. */
    public boolean updated = false;

    /**
     * The VkBufferUsageFlags the buffer was created with, including {@link #BASE_USAGE}. Kept so
     * that reallocation creates an equivalent buffer.
     */
    public int usage = BASE_USAGE;

    /**
     * Create a VMA buffer that is host visible, host coherent, and persistently mapped.
     *
     * @param bufferSize The size in bytes, must be positive.
     * @param usage The VkBufferUsageFlags for the buffer.
     * @param state The Vulkan state.
     * @param allocationInfo Where to store the allocation info.
     * @param stack The stack to allocate temporary structs on.
     * @return The buffer and allocation handles.
     */
    private static RetiredBuffer createMapped(
            long bufferSize,
            int usage,
            @NonNull VulkanState state,
            @NonNull VmaAllocationInfo allocationInfo,
            @NonNull MemoryStack stack) {
        VkBufferCreateInfo bufferCreateInfo =
                VkBufferCreateInfo.calloc(stack).sType$Default().size(bufferSize).usage(usage);
        /*
         * NOTE(ches) No HOST_ACCESS_ALLOW_TRANSFER_INSTEAD, so VMA must give us host visible memory,
         * and pMappedData is always valid. Requiring coherent memory means we never need to flush.
         */
        VmaAllocationCreateInfo bufferAllocationCreateInfo =
                VmaAllocationCreateInfo.calloc(stack)
                        .flags(
                                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                        | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                        .usage(VMA_MEMORY_USAGE_AUTO)
                        .requiredFlags(
                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                        | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        LongBuffer longOutput = stack.callocLong(1);
        PointerBuffer pointerOutput = stack.callocPointer(1);

        checkError(
                vmaCreateBuffer(
                        state.vmaAllocator,
                        bufferCreateInfo,
                        bufferAllocationCreateInfo,
                        longOutput,
                        pointerOutput,
                        allocationInfo));
        return new RetiredBuffer(longOutput.get(0), pointerOutput.get(0));
    }

    /**
     * Look up the device address for a buffer.
     *
     * @param buffer The VkBuffer handle.
     * @param state The Vulkan state.
     * @param stack The stack to allocate temporary structs on.
     * @return The device address.
     */
    private static long getDeviceAddress(
            long buffer, @NonNull VulkanState state, @NonNull MemoryStack stack) {
        VkBufferDeviceAddressInfo bufferDeviceAddressInfo =
                VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(buffer);
        return vkGetBufferDeviceAddress(state.device.logical, bufferDeviceAddressInfo);
    }

    /**
     * Replace a buffer with a new one of a different size, discarding the old contents.
     *
     * @param buffer The buffer to reallocate.
     * @param bufferSize The new size. If 0 or negative, we just free and don't recreate.
     * @param state The Vulkan state.
     * @see #reallocate(SharedBuffer, long, VulkanState, boolean)
     */
    public static void reallocate(
            @NonNull SharedBuffer buffer, long bufferSize, @NonNull VulkanState state) {
        reallocate(buffer, bufferSize, state, false);
    }

    /**
     * Replace a buffer with a new one of a different size. If the new buffer is smaller, and we are
     * keeping contents, then only the contents that fit are kept. The old buffer is retired rather
     * than freed immediately, since a frame in flight may still be reading it.
     *
     * @param buffer The buffer to reallocate.
     * @param bufferSize The new size. If 0 or negative, we just free and don't recreate.
     * @param state The Vulkan state.
     * @param keepContents Whether the old buffer contents should be copied over to the new buffer.
     * @see #reallocate(SharedBuffer, long, VulkanState)
     */
    public static void reallocate(
            @NonNull SharedBuffer buffer,
            long bufferSize,
            @NonNull VulkanState state,
            boolean keepContents) {
        final RetiredBuffer old =
                buffer.buffer == VK_NULL_HANDLE
                        ? null
                        : new RetiredBuffer(buffer.buffer, buffer.allocation);
        final long oldMappedData = buffer.allocationInfo.pMappedData();
        final long oldSize = buffer.allocationInfo.size();

        if (bufferSize <= 0) {
            buffer.allocationInfo.clear();
            buffer.buffer = VK_NULL_HANDLE;
            buffer.allocation = VK_NULL_HANDLE;
            buffer.deviceAddress = VK_NULL_HANDLE;
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VmaAllocationInfo newAllocationInfo = VmaAllocationInfo.create();
                RetiredBuffer created =
                        createMapped(bufferSize, buffer.usage, state, newAllocationInfo, stack);

                if (keepContents && old != null) {
                    MemoryUtil.memCopy(
                            oldMappedData,
                            newAllocationInfo.pMappedData(),
                            Math.min(oldSize, bufferSize));
                }

                buffer.allocationInfo = newAllocationInfo;
                buffer.buffer = created.buffer();
                buffer.allocation = created.allocation();
                buffer.deviceAddress = getDeviceAddress(buffer.buffer, state, stack);
            }
        }

        if (old != null) {
            state.retireBuffer(old);
        }
        buffer.updated = true;
    }

    /**
     * Allocate a new shared buffer. If the size is zero or negative we don't actually set up the
     * buffer, but the usage is remembered for when it is reallocated later.
     *
     * @param bufferSize The size of the buffer in bytes.
     * @param state The Vulkan state.
     * @param usage The VkBufferUsageFlags for the buffer. {@link #BASE_USAGE} is always added.
     * @return The new shared buffer.
     */
    public static SharedBuffer allocate(long bufferSize, @NonNull VulkanState state, int usage) {
        SharedBuffer result = new SharedBuffer();
        result.usage = usage | BASE_USAGE;

        if (bufferSize <= 0) {
            return result;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            RetiredBuffer created =
                    createMapped(bufferSize, result.usage, state, result.allocationInfo, stack);
            result.buffer = created.buffer();
            result.allocation = created.allocation();
            result.deviceAddress = getDeviceAddress(result.buffer, state, stack);
            return result;
        }
    }

    /**
     * Rounds up bytes to a multiple of 16 bytes, to match std140/std430 padding rules.
     *
     * @param bytes The size we want to align.
     * @return The next largest multiple of 16 bytes.
     */
    public static long align(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return (bytes + 16 - 1) & -16;
    }

    /**
     * Round up bytes to a multiple of alignment bytes, where alignment gets padded out to 16 bytes.
     * This is for things like buffers of structs that we expect to automatically get padded out to
     * 16 bytes, according to std140/std430 padding rules.
     *
     * <p>For example, if we provide bytes=30 and alignment=13, alignment is padded out to 16 and
     * thus the result will be 32.
     *
     * @param bytes The size we want to align.
     * @param alignment The alignment, which will itself be aligned up to a multiple of 16 bytes.
     * @return The next largest multiple of align(alignment) bytes.
     * @see #alignWithoutPadding(long, long)
     */
    public static long align(long bytes, long alignment) {
        return alignWithoutPadding(bytes, align(alignment));
    }

    /**
     * Rounds up bytes to a multiple of alignment bytes, exactly. This is for buffers with no
     * padding which we still want to be a multiple of some number of bytes.
     *
     * @param bytes The size we want to align.
     * @param alignment The alignment in bytes.
     * @return The next largest multiple of alignment bytes.
     * @see #align(long, long)
     */
    public static long alignWithoutPadding(long bytes, long alignment) {
        if (bytes <= 0 || alignment <= 0) {
            return 0;
        }
        return ((bytes + alignment - 1) / alignment) * alignment;
    }

    /**
     * Free up a buffer and zero out the fields. If (and only if) the buffer field is
     * VK_NULL_HANDLE, the buffer is considered already freed.
     *
     * @param buffer The buffer to free.
     * @param state The Vulkan state.
     */
    public static void free(@NonNull SharedBuffer buffer, @NonNull VulkanState state) {
        if (buffer.buffer == VK_NULL_HANDLE) {
            return;
        }
        vmaDestroyBuffer(state.vmaAllocator, buffer.buffer, buffer.allocation);
        buffer.allocationInfo.clear();
        buffer.buffer = VK_NULL_HANDLE;
        buffer.allocation = VK_NULL_HANDLE;
        buffer.deviceAddress = VK_NULL_HANDLE;
    }

    /**
     * Make sure that the buffer can fit the specified number of bytes, resizing it to be larger if
     * it can't. Old buffer contents are discarded if resized.
     *
     * @param bytes The number of bytes we need to store.
     * @param state The Vulkan state.
     * @see #ensureFits(long, VulkanState, boolean)
     */
    public void ensureFits(long bytes, @NonNull VulkanState state) {
        ensureFits(bytes, state, false);
    }

    /**
     * Make sure that the buffer can fit the specified number of bytes, resizing it to be larger if
     * it can't.
     *
     * @param bytes The number of bytes we need to store.
     * @param state The Vulkan state.
     * @param keepContents Whether the old buffer contents should be copied over to the new buffer.
     * @see #ensureFits(long, VulkanState)
     */
    public void ensureFits(long bytes, @NonNull VulkanState state, boolean keepContents) {
        if (bytes > allocationInfo.size()) {
            SharedBuffer.reallocate(this, bytes, state, keepContents);
        }
    }
}
