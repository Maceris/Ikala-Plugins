package com.ikalagaming.graphics.backend.vulkan;

import lombok.Getter;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A skybox model. */
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

    private final int vao;
    private final int[] vbos;

    /**
     * The number of vertices to draw (i.e. number of indices), since we reuse a couple of the
     * vertices.
     */
    public static final int VERTEX_COUNT = INDICES.length;

    /**
     * The number of VBOs. These are for position, texture coordinates, and indices respectively.
     */
    public static final int VBO_COUNT = 3;

    public SkyboxModel() {
        vbos = new int[VBO_COUNT];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vao = 0;

            // Positions VBO

            final int vboPositions = vbos[0];
            final int vboTextureCoordinates = vbos[1];
            final int vboIndices = vbos[2];

            FloatBuffer positionsBuffer = stack.callocFloat(POSITIONS.length);
            positionsBuffer.put(0, POSITIONS);
            // TODO(ches) set up buffer

            FloatBuffer textureCoordinatesBuffer = stack.callocFloat(TEXTURE_COORDINATES.length);
            textureCoordinatesBuffer.put(0, TEXTURE_COORDINATES);
            // TODO(ches) set up buffer

            IntBuffer indicesBuffer = stack.callocInt(INDICES.length);
            indicesBuffer.put(0, INDICES);
            // TODO(ches) set up buffer

        }
    }

    /** Clean up the model buffers. */
    public void cleanup() {
        // TODO(ches) clean up
    }
}
