package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress;
import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

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

/** Buffer that is shared between the CPU and the GPU. */
@Slf4j
public class SharedBuffer implements Buffer {
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
     * Free and recreate a buffer with a new size, discarding the old contents.
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
     * Free and recreate a buffer with a new size. If the new buffer is smaller, and we are keeping
     * contents, then only the contents that fit are kept.
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
        if (bufferSize > 65_535) {
            log.warn(
                    "Allocating a buffer of size {}, which is too big to update in a command buffer",
                    bufferSize);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaAllocationInfo newAllocationInfo;
            if (!keepContents || bufferSize <= 0) {
                free(buffer, state);
                // we can just overwrite the allocation info in this case
                newAllocationInfo = buffer.allocationInfo;
            } else {
                newAllocationInfo = VmaAllocationInfo.create();
            }

            VkBufferCreateInfo bufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(bufferSize)
                            .usage(VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
            VmaAllocationCreateInfo bufferAllocationCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                            | VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);

            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);

            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            bufferCreateInfo,
                            bufferAllocationCreateInfo,
                            longOutput,
                            pointerOutput,
                            newAllocationInfo));

            final long newBuffer = longOutput.get(0);
            final long newAllocation = pointerOutput.get(0);

            if (keepContents && bufferSize > 0) {
                final long minSize = Math.min(buffer.allocationInfo.size(), bufferSize);
                MemoryUtil.memCopy(buffer.buffer, newBuffer, minSize);
                free(buffer, state);
                // We didn't overwrite the allocation info if copying, so replace the old one
                buffer.allocationInfo = newAllocationInfo;
            }
            buffer.buffer = newBuffer;
            buffer.allocation = newAllocation;

            VkBufferDeviceAddressInfo bufferDeviceAddressInfo =
                    VkBufferDeviceAddressInfo.calloc(stack).sType$Default();
            bufferDeviceAddressInfo.buffer(buffer.buffer);
            buffer.deviceAddress =
                    vkGetBufferDeviceAddress(state.device.logical, bufferDeviceAddressInfo);
            buffer.updated = true;
        }
    }

    /**
     * Allocate a new shared buffer. If the size is zero or negative we don't actually set up the
     * buffer.
     *
     * @param bufferSize The size of the buffer in bytes.
     * @param state The Vulkan state.
     * @return The new shared buffer.
     */
    public static SharedBuffer allocate(long bufferSize, @NonNull VulkanState state, int usage) {
        if (bufferSize <= 0) {
            return new SharedBuffer();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack).sType$Default().size(bufferSize).usage(usage);
            VmaAllocationCreateInfo bufferAllocationCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                            | VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);

            SharedBuffer result = new SharedBuffer();

            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);

            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            bufferCreateInfo,
                            bufferAllocationCreateInfo,
                            longOutput,
                            pointerOutput,
                            result.allocationInfo));
            result.buffer = longOutput.get(0);
            result.allocation = pointerOutput.get(0);

            VkBufferDeviceAddressInfo bufferDeviceAddressInfo =
                    VkBufferDeviceAddressInfo.calloc(stack).sType$Default();
            bufferDeviceAddressInfo.buffer(result.buffer);
            result.deviceAddress =
                    vkGetBufferDeviceAddress(state.device.logical, bufferDeviceAddressInfo);
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
        if (bytes < allocationInfo.size()) {
            SharedBuffer.reallocate(this, bytes, state, keepContents);
        }
    }
}
