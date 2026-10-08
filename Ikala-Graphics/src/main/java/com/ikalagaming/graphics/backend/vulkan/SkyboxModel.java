package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A skybox model, with positions and texture coordinates interleaved in one vertex buffer. */
@Getter
public class SkyboxModel {

    private static final float[] POSITIONS =
            new float[] {
                // Left Face
                -1, 1, 1, //
                -1, -1, 1, //
                -1, 1, -1, //
                -1, -1, -1, //
                // Front Face
                1, 1, -1, //
                1, -1, -1, //
                // Right Face
                1, 1, 1, //
                1, -1, 1, //
                // Back Face
                -1, 1, 1, //
                -1, -1, 1, //
                // Top Face
                -1, 1, 1, //
                1, 1, 1, //
                // Bottom Face
                -1, -1, 1, //
                1, -1, 1
            };

    private static final float[] TEXTURE_COORDINATES =
            new float[] {
                // Left Face
                0,
                1f / 3,
                0,
                2f / 3,
                0.25f,
                1f / 3,
                0.25f,
                2f / 3,
                // Front Face
                0.5f,
                1f / 3,
                0.5f,
                2f / 3,
                // Right Face
                0.75f,
                1f / 3,
                0.75f,
                2f / 3,
                // Back Face
                1,
                1f / 3,
                1,
                2f / 3,
                // Top Face
                0.25f,
                0,
                0.5f,
                0,
                // Bottom Face
                0.25f,
                1,
                0.5f,
                1
            };

    private static final int[] INDICES =
            new int[] {
                0, 1, 2, // Left Upper
                2, 1, 3, // Left Lower
                4, 2, 3, // Front Upper
                4, 3, 5, // Front Lower
                6, 4, 5, // Right Upper
                6, 5, 7, // Right Lower
                8, 6, 7, // Back Upper
                8, 7, 9, // Back Lower
                11, 10, 2, // Top Upper
                11, 2, 4, // Top Lower
                5, 3, 12, // Bottom Upper
                5, 12, 13 // Bottom Lower
            };

    /**
     * The number of vertices to draw (i.e. number of indices), since we reuse a couple of the
     * vertices.
     */
    public static final int VERTEX_COUNT = INDICES.length;

    /** The number of floats per vertex, a position followed by texture coordinates. */
    public static final int FLOATS_PER_VERTEX = 3 + 2;

    /** The interleaved positions and texture coordinates. */
    private final SharedBuffer vertexBuffer;

    /** The indices. */
    private final SharedBuffer indexBuffer;

    /**
     * Create the skybox buffers.
     *
     * @param state The Vulkan state.
     */
    public SkyboxModel(@NonNull VulkanState state) {
        final int vertexCount = POSITIONS.length / 3;
        vertexBuffer =
                SharedBuffer.allocate(
                        (long) vertexCount * FLOATS_PER_VERTEX * Float.BYTES,
                        state,
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        indexBuffer =
                SharedBuffer.allocate(
                        (long) INDICES.length * Integer.BYTES,
                        state,
                        VK_BUFFER_USAGE_INDEX_BUFFER_BIT);

        FloatBuffer vertices =
                MemoryUtil.memFloatBuffer(
                        vertexBuffer.allocationInfo.pMappedData(), vertexCount * FLOATS_PER_VERTEX);
        for (int i = 0; i < vertexCount; i++) {
            vertices.put(POSITIONS, i * 3, 3);
            vertices.put(TEXTURE_COORDINATES, i * 2, 2);
        }

        IntBuffer indices =
                MemoryUtil.memIntBuffer(indexBuffer.allocationInfo.pMappedData(), INDICES.length);
        indices.put(INDICES);
    }

    /**
     * Clean up the model buffers.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(vertexBuffer, state);
        SharedBuffer.free(indexBuffer, state);
    }
}
