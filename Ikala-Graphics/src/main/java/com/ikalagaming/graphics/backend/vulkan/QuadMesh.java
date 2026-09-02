package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/** Defines a quad that is used to render in the lighting pass. */
@Slf4j
public record QuadMesh(@NonNull SharedBuffer vertexBuffer, SharedBuffer indexBuffer) {
    /** The number of vertices in the mesh. */
    public static final int INDEX_COUNT = 6;

    public static QuadMesh getInstance(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float[] positions = {
                -1.0f, +1.0f, 0.0f, // Position 0
                +1.0f, +1.0f, 0.0f, // Position 1
                -1.0f, -1.0f, 0.0f, // Position 2
                +1.0f, -1.0f, 0.0f, // Position 3
            };
            float[] textureCoordinates = {
                0.0f, 1.0f, // Position 0
                1.0f, 1.0f, // Position 1
                0.0f, 0.0f, // Position 2
                1.0f, 0.0f, // Position 3
            };
            int[] indices = {0, 2, 1, 1, 2, 3};

            final int vertexBufferSize =
                    (positions.length + textureCoordinates.length) * Float.BYTES;
            final int indexBufferSize = indices.length * Integer.BYTES;

            PointerBuffer allocation = MemoryUtil.memAllocPointer(1);
            LongBuffer bufferAddress = MemoryUtil.memAllocLong(1);
            SharedBuffer vertexBuffer = new SharedBuffer();
            SharedBuffer indexBuffer = new SharedBuffer();

            VkBufferCreateInfo vertexBufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(vertexBufferSize)
                            .usage(
                                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
                                            | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            VmaAllocationCreateInfo bufferAllocCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                            | VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);
            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            vertexBufferCreateInfo,
                            bufferAllocCreateInfo,
                            bufferAddress,
                            allocation,
                            vertexBuffer.allocationInfo));
            vertexBuffer.buffer = bufferAddress.get(0);
            vertexBuffer.allocation = allocation.get(0);

            VkBufferCreateInfo indexBufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(indexBufferSize)
                            .usage(
                                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
                                            | VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            indexBufferCreateInfo,
                            bufferAllocCreateInfo,
                            bufferAddress,
                            allocation,
                            indexBuffer.allocationInfo));
            indexBuffer.buffer = bufferAddress.get(0);
            indexBuffer.allocation = allocation.get(0);
            MemoryUtil.memFree(bufferAddress);
            MemoryUtil.memFree(allocation);

            FloatBuffer vertexStaging =
                    MemoryUtil.memAllocFloat(positions.length + textureCoordinates.length);
            for (int i = 0; i < 4; i++) {
                vertexStaging.put(positions[i * 3]);
                vertexStaging.put(positions[i * 3 + 1]);
                vertexStaging.put(positions[i * 3 + 2]);
                vertexStaging.put(textureCoordinates[i * 2]);
                vertexStaging.put(textureCoordinates[i * 2 + 1]);
            }
            vertexStaging.flip();
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(vertexStaging),
                    vertexBuffer.allocationInfo.pMappedData(),
                    vertexBufferSize);
            MemoryUtil.memFree(vertexStaging);

            IntBuffer indexStaging = MemoryUtil.memAllocInt(indices.length);
            for (int i : indices) {
                indexStaging.put(i);
            }
            indexStaging.flip();
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(indexStaging),
                    indexBuffer.allocationInfo.pMappedData(),
                    indexBufferSize);
            MemoryUtil.memFree(indexStaging);

            return new QuadMesh(vertexBuffer, indexBuffer);
        }
    }

    public void cleanup(@NonNull VulkanState state) {
        vmaDestroyBuffer(state.vmaAllocator, indexBuffer.buffer, indexBuffer.allocation);
        vmaDestroyBuffer(state.vmaAllocator, vertexBuffer.buffer, vertexBuffer.allocation);
    }
}
