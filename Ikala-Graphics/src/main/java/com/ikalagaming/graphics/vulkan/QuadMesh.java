package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Defines a quad that is used to render in the lighting pass. */
@Slf4j
public record QuadMesh(@NonNull SharedBuffer vertexBuffer, SharedBuffer indexBuffer) {
    /** The number of vertices in the mesh. */
    public static final int INDEX_COUNT = 6;

    public static QuadMesh getInstance(@NonNull VulkanState state) {
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

        final int vertexBufferSize = (positions.length + textureCoordinates.length) * Float.BYTES;
        final int indexBufferSize = indices.length * Integer.BYTES;

        SharedBuffer vertexBuffer =
                SharedBuffer.allocate(vertexBufferSize, state, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        SharedBuffer indexBuffer =
                SharedBuffer.allocate(indexBufferSize, state, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);

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

    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(indexBuffer, state);
        SharedBuffer.free(vertexBuffer, state);
    }
}
