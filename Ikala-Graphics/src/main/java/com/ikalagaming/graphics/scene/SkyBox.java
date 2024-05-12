/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A sky box that provides a backdrop for the environment. */
@Getter
public class SkyBox {
    /**
     * The number of VBO's that we use. These represent: 1) positions 2) texture coordinates 3)
     * indices
     */
    private static final int VBO_COUNT = 3;

    /**
     * The index of the material used to render the skybox.
     *
     * @return The material.
     */
    private int materialIndex;

    /**
     * The skybox entity.
     *
     * @return The entity.
     */
    private Entity skyBoxEntity;

    /**
     * The model for the skybox.
     *
     * @return The model.
     */
    private Model skyBoxModel;

    /**
     * The ID of the OpenGL vertex array object.
     *
     * @return The VAO ID.
     */
    @Getter private int vaoID;

    /** A list of vertex buffer objects for this mesh. */
    private int[] vboIDList;

    /**
     * The number of vertices in the mesh.
     *
     * @return The number of vertices.
     */
    @Getter private int vertexCount;

    /**
     * Create a new skybox.
     *
     * @param skyBoxModelPath The path to the model from the resource directory.
     * @param materialCache The material cache to use.
     */
    public SkyBox(@NonNull String skyBoxModelPath, @NonNull MaterialCache materialCache) {
        skyBoxModel =
                ModelLoader.loadModel(
                        new ModelLoader.ModelLoadRequest(
                                "skybox-model",
                                GraphicsPlugin.PLUGIN_NAME,
                                skyBoxModelPath,
                                materialCache,
                                false));
        MeshData meshData = skyBoxModel.getMeshDataList().get(0);
        materialIndex = meshData.getMaterialIndex();
        setupBuffers(meshData);
        skyBoxModel.getMeshDataList().clear();
        skyBoxEntity = new Entity("skyBoxEntity-entity", skyBoxModel.getId());
    }

    /** Clean up the mesh. */
    public void cleanup() {
        glDeleteBuffers(vboIDList);
        glDeleteVertexArrays(vaoID);
    }

    /**
     * Load the mesh data into VBOs.
     *
     * @param meshData The data to load.
     */
    private void setupBuffers(@NonNull MeshData meshData) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vertexCount = meshData.getIndices().length;
            vboIDList = new int[VBO_COUNT];

            vaoID = glGenVertexArrays();
            glBindVertexArray(vaoID);

            // Positions VBO
            glGenBuffers(vboIDList);

            final int vboPositions = vboIDList[0];
            final int vboTextureCoordinates = vboIDList[1];
            final int vboIndices = vboIDList[2];

            FloatBuffer positionsBuffer = stack.callocFloat(meshData.getPositions().length);
            positionsBuffer.put(0, meshData.getPositions());
            glBindBuffer(GL_ARRAY_BUFFER, vboPositions);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            // Texture coordinates VBO
            FloatBuffer textureCoordinatesBuffer =
                    stack.callocFloat(meshData.getTextureCoordinates().length);
            textureCoordinatesBuffer.put(0, meshData.getTextureCoordinates());
            glBindBuffer(GL_ARRAY_BUFFER, vboTextureCoordinates);
            glBufferData(GL_ARRAY_BUFFER, textureCoordinatesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

            IntBuffer indicesBuffer = stack.callocInt(meshData.getIndices().length);
            indicesBuffer.put(0, meshData.getIndices());
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndices);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
    }
}
