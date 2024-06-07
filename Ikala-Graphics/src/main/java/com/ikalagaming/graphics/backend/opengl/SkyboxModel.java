package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A skybox model. Should be constructed with the {@link #create()} method instead of a constructor,
 * as buffers get set up there. Will be replaced with a more generic setup.
 *
 * @param vao The VAO index.
 * @param vbos The {@value #VBO_COUNT} VBOs, which represent position, texture coordinates, and
 *     indices respectively.
 */
@Deprecated
public record SkyboxModel(int vao, int[] vbos) {

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

    /**
     * The number of VBOs. These are for position, texture coordinates, and indices respectively.
     */
    public static final int VBO_COUNT = 3;

    /**
     * Create a new skybox model. This should be called instead of the constructor.
     *
     * @return The newly set up skybox model.
     */
    public static SkyboxModel create() {
        int vaoID;
        int[] vboIDList = new int[VBO_COUNT];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vaoID = glGenVertexArrays();
            glBindVertexArray(vaoID);

            // Positions VBO
            glGenBuffers(vboIDList);

            final int vboPositions = vboIDList[0];
            final int vboTextureCoordinates = vboIDList[1];
            final int vboIndices = vboIDList[2];

            FloatBuffer positionsBuffer = stack.callocFloat(POSITIONS.length);
            positionsBuffer.put(0, POSITIONS);
            glBindBuffer(GL_ARRAY_BUFFER, vboPositions);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            FloatBuffer textureCoordinatesBuffer = stack.callocFloat(TEXTURE_COORDINATES.length);
            textureCoordinatesBuffer.put(0, TEXTURE_COORDINATES);
            glBindBuffer(GL_ARRAY_BUFFER, vboTextureCoordinates);
            glBufferData(GL_ARRAY_BUFFER, textureCoordinatesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

            IntBuffer indicesBuffer = stack.callocInt(INDICES.length);
            indicesBuffer.put(0, INDICES);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndices);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
        return new SkyboxModel(vaoID, vboIDList);
    }

    /** Clean up the model buffers. */
    public void cleanup() {
        glDeleteBuffers(vbos);
        glDeleteVertexArrays(vao);
    }
}
