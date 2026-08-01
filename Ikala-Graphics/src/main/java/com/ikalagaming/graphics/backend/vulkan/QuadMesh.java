package com.ikalagaming.graphics.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Defines a quad that is used to render in the lighting pass. */
public record QuadMesh(int vao, int[] vboIDs) {
    /** The number of vertices in the mesh. */
    public static final int VERTEX_COUNT = 6;

    public static QuadMesh getInstance() {
        int vaoID;
        int[] vboIDs;
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

            vaoID = 0;

            // Positions VBO
            int positionsVBO = 0;
            FloatBuffer positionsBuffer = stack.callocFloat(positions.length);
            positionsBuffer.put(0, positions);
            // TODO(ches) update buffer

            // Texture coordinates VBO
            int textureCoordinatesVBO = 0;
            FloatBuffer textureCoordinatesBuffer =
                    MemoryUtil.memAllocFloat(textureCoordinates.length);
            textureCoordinatesBuffer.put(0, textureCoordinates);
            // TODO(ches) update buffer

            // Index VBO
            int indexVBO = 0;
            IntBuffer indicesBuffer = stack.callocInt(indices.length);
            indicesBuffer.put(0, indices);
            // TODO(ches) update buffer

            vboIDs = new int[] {positionsVBO, textureCoordinatesVBO, indexVBO};
        }
        return new QuadMesh(vaoID, vboIDs);
    }

    public void cleanup() {
        // TODO(ches) clean up
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QuadMesh other)) {
            return false;
        }
        if (vao != other.vao) {
            return false;
        }
        return Arrays.equals(vboIDs, other.vboIDs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vao, Arrays.hashCode(vboIDs));
    }

    @Override
    public String toString() {
        return String.format("[vao=%d, vboIDs=%s]", vao, Arrays.toString(vboIDs));
    }
}
