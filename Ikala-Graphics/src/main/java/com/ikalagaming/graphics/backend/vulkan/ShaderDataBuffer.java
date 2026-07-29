package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

import org.lwjgl.util.vma.VmaAllocationInfo;

/** Data buffers for a frame, only the data that the CPU cares about. */
public class ShaderDataBuffer {
    /** VMA handle for the allocation. 0 if unused. */
    public long allocation = VK_NULL_HANDLE;

    /** VMA allocation info. */
    public VmaAllocationInfo allocationInfo = VmaAllocationInfo.create();

    /** The buffer handle on the CPU side. */
    public long buffer = VK_NULL_HANDLE;

    /** The device address on the GPU side. */
    public long deviceAddress = VK_NULL_HANDLE;
}
