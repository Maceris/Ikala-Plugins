package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import org.lwjgl.opengl.GL15;
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

            vaoID = glGenVertexArrays();
            glBindVertexArray(vaoID);

            // Positions VBO
            int positionsVBO = glGenBuffers();
            FloatBuffer positionsBuffer = stack.callocFloat(positions.length);
            positionsBuffer.put(0, positions);
            glBindBuffer(GL_ARRAY_BUFFER, positionsVBO);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            // Texture coordinates VBO
            int textureCoordinatesVBO = glGenBuffers();
            FloatBuffer textureCoordinatesBuffer =
                    MemoryUtil.memAllocFloat(textureCoordinates.length);
            textureCoordinatesBuffer.put(0, textureCoordinates);
            glBindBuffer(GL_ARRAY_BUFFER, textureCoordinatesVBO);
            glBufferData(GL_ARRAY_BUFFER, textureCoordinatesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

            // Index VBO
            int indexVBO = glGenBuffers();
            IntBuffer indicesBuffer = stack.callocInt(indices.length);
            indicesBuffer.put(0, indices);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexVBO);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            vboIDs = new int[] {positionsVBO, textureCoordinatesVBO, indexVBO};
        }
        return new QuadMesh(vaoID, vboIDs);
    }

    public void cleanup() {
        Arrays.stream(vboIDs).forEach(GL15::glDeleteBuffers);
        glDeleteVertexArrays(vao);
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
