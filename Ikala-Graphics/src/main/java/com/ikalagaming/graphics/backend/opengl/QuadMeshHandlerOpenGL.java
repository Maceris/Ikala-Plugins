package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.backend.base.QuadMeshHandler;
import com.ikalagaming.graphics.graph.QuadMesh;

import lombok.NonNull;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class QuadMeshHandlerOpenGL implements QuadMeshHandler {

    @Override
    public void initialize(@NonNull QuadMesh mesh) {
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

            int vaoID = glGenVertexArrays();
            mesh.setVaoID(vaoID);
            glBindVertexArray(vaoID);

            // Positions VBO
            int vboId = glGenBuffers();
            mesh.getVboIDList().add(vboId);
            FloatBuffer positionsBuffer = stack.callocFloat(positions.length);
            positionsBuffer.put(0, positions);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            // Texture coordinates VBO
            vboId = glGenBuffers();
            mesh.getVboIDList().add(vboId);
            FloatBuffer textureCoordinatesBuffer =
                    MemoryUtil.memAllocFloat(textureCoordinates.length);
            textureCoordinatesBuffer.put(0, textureCoordinates);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, textureCoordinatesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

            // Index VBO
            vboId = glGenBuffers();
            mesh.getVboIDList().add(vboId);
            IntBuffer indicesBuffer = stack.callocInt(indices.length);
            indicesBuffer.put(0, indices);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
    }

    @Override
    public void cleanup(@NonNull QuadMesh mesh) {
        mesh.getVboIDList().forEach(GL15::glDeleteBuffers);
        mesh.getVboIDList().clear();
        glDeleteVertexArrays(mesh.getVaoID());
        mesh.setVaoID(0);
    }
}
