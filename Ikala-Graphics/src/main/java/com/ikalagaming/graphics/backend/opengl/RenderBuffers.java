/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Buffers for indirect drawing of models. */
public class RenderBuffers {

    /**
     * Data used for drawing animated meshes.
     *
     * @param entity The entity associated with the model.
     * @param bindingPoseOffset The offset to the binding pose within the data.
     * @param weightsOffset The offset to the weight within the data.
     */
    public record AnimMeshDrawData(Entity entity, int bindingPoseOffset, int weightsOffset) {}

    /**
     * Data used for drawing meshes.
     *
     * @param sizeInBytes The size of the mesh in bytes.
     * @param materialIndex The material index that the mesh is associated with.
     * @param offset The offset in rows.
     * @param vertices The number of indices.
     * @param animMeshDrawData The animation mesh draw data.
     */
    public record MeshDrawData(
            int sizeInBytes,
            int materialIndex,
            int offset,
            int vertices,
            AnimMeshDrawData animMeshDrawData) {
        /**
         * Set up mesh draw data for a mesh without animation draw data.
         *
         * @param sizeInBytes The size of the mesh in bytes.
         * @param materialIndex The material index that the mesh is associated with.
         * @param offset The offset in rows, measured in vertices.
         * @param vertices The number of indices.
         */
        public MeshDrawData(int sizeInBytes, int materialIndex, int offset, int vertices) {
            this(sizeInBytes, materialIndex, offset, vertices, null);
        }
    }

    /**
     * The Vertex Array Object ID, for animated models.
     *
     * @return The VAO ID of the animated model data.
     */
    @Getter private int animVaoID;

    /**
     * The Vertex Buffer Object ID for binding poses, for animated models.
     *
     * @return The binding poses buffer.
     */
    @Getter private int bindingPosesBuffer;

    /**
     * The Vertex Buffer Object ID for bone indices weights, for animated models.
     *
     * @return The bone indices weights buffer.
     */
    @Getter private int bonesIndicesWeightsBuffer;

    /**
     * The Vertex Buffer Object ID for bone matrices, for animated models.
     *
     * @return The bone matrices buffer.
     */
    @Getter private int bonesMatricesBuffer;

    /**
     * The Vertex Buffer Object ID for transformed animation vertices after processing, for animated
     * models.
     *
     * @return The processed animation data.
     */
    @Getter private int destinationAnimationBuffer;

    /**
     * The Vertex Array Object ID, for static models.
     *
     * @return The VAO ID of the static model data.
     */
    @Getter private int staticVaoID;

    /** The list of Vertex Buffer Objects that this class set up. */
    private final List<Integer> vboIDList;

    /** Set up a new render buffer. */
    public RenderBuffers() {
        vboIDList = new ArrayList<>();
    }

    /** Clean up all the data. */
    public void cleanup() {
        vboIDList.forEach(GL15::glDeleteBuffers);
        vboIDList.clear();
        glDeleteVertexArrays(staticVaoID);
        glDeleteVertexArrays(animVaoID);
    }

    /** Used to define the vertex attribute arrays. */
    private void defineVertexAttributes() {
        final int stride = (3 * 4) * 4 + 2 * 4;
        int pointer = 0;
        // Positions
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, pointer);
        pointer += 3 * 4;
        // Normals
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, pointer);
        pointer += 3 * 4;
        // Tangents
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, pointer);
        pointer += 3 * 4;
        // Bitangents
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, pointer);
        pointer += 3 * 4;
        // Texture coordinates
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, pointer);
    }

    /**
     * Load models with animation information for a scene. Make sure to clear out the existing
     * buffer data before doing this a second time.
     *
     * @param scene The scene to load models for.
     */
    public void loadAnimatedModels(@NonNull Scene scene) {
        List<Model> modelList =
                scene.getModelMap().values().stream().filter(Model::isAnimated).toList();
        loadBindingPoses(modelList);
        loadBonesMatricesBuffer(modelList);
        loadBonesIndicesWeights(modelList);

        animVaoID = glGenVertexArrays();
        glBindVertexArray(animVaoID);
        int positionsSize = 0;
        int normalsSize = 0;
        int textureCoordinatesSize = 0;
        int indicesSize = 0;
        int offset = 0;
        int chunkBindingPoseOffset = 0;
        int bindingPoseOffset = 0;
        int chunkWeightsOffset = 0;
        int weightsOffset = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            List<RenderBuffers.MeshDrawData> meshDrawDataList = model.getMeshDrawDataList();
            meshDrawDataList.clear();
            for (Entity entity : entities) {
                bindingPoseOffset = chunkBindingPoseOffset;
                weightsOffset = chunkWeightsOffset;
                for (MeshData meshData : model.getMeshDataList()) {
                    positionsSize += meshData.getPositions().length;
                    normalsSize += meshData.getNormals().length;
                    textureCoordinatesSize += meshData.getTextureCoordinates().length;
                    indicesSize += meshData.getIndices().length;

                    int meshSizeInBytes =
                            (meshData.getPositions().length
                                            + meshData.getNormals().length * 3
                                            + meshData.getTextureCoordinates().length)
                                    * 4;
                    meshDrawDataList.add(
                            new MeshDrawData(
                                    meshSizeInBytes,
                                    meshData.getMaterialIndex(),
                                    offset,
                                    meshData.getIndices().length,
                                    new AnimMeshDrawData(
                                            entity, bindingPoseOffset, weightsOffset)));
                    bindingPoseOffset += meshSizeInBytes / 4;
                    int groupSize = (int) Math.ceil((float) meshSizeInBytes / (14 * 4));
                    weightsOffset += groupSize * 2 * 4;
                    offset = positionsSize / 3;
                }
            }
            chunkBindingPoseOffset += bindingPoseOffset;
            chunkWeightsOffset += weightsOffset;
        }

        destinationAnimationBuffer = glGenBuffers();
        vboIDList.add(destinationAnimationBuffer);
        FloatBuffer meshesBuffer =
                MemoryUtil.memAllocFloat(positionsSize + normalsSize * 3 + textureCoordinatesSize);
        for (Model model : modelList) {
            model.getEntitiesList()
                    .forEach(
                            e -> {
                                for (MeshData meshData : model.getMeshDataList()) {
                                    populateMeshBuffer(meshesBuffer, meshData);
                                }
                            });
        }
        meshesBuffer.flip();
        glBindBuffer(GL_ARRAY_BUFFER, destinationAnimationBuffer);
        glBufferData(GL_ARRAY_BUFFER, meshesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(meshesBuffer);

        defineVertexAttributes();

        // Index VBO
        int vboId = glGenBuffers();
        vboIDList.add(vboId);
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indicesSize);
        for (Model model : modelList) {
            model.getEntitiesList()
                    .forEach(
                            e -> {
                                for (MeshData meshData : model.getMeshDataList()) {
                                    indicesBuffer.put(meshData.getIndices());
                                }
                            });
        }
        indicesBuffer.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Store binding pose information for all the meshes that are associated with the animated
     * models.
     *
     * @param modelList The models to load binding pose information for.
     */
    private void loadBindingPoses(@NonNull List<Model> modelList) {
        int meshSize = 0;
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                meshSize +=
                        meshData.getPositions().length
                                // normal, tangent, bitangent
                                + meshData.getNormals().length * 3
                                + meshData.getTextureCoordinates().length;
            }
        }

        bindingPosesBuffer = glGenBuffers();
        vboIDList.add(bindingPosesBuffer);
        FloatBuffer meshesBuffer = MemoryUtil.memAllocFloat(meshSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                populateMeshBuffer(meshesBuffer, meshData);
            }
        }
        meshesBuffer.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bindingPosesBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, meshesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(meshesBuffer);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Store all the bone index and weight information for the animated models.
     *
     * @param modelList The list of models to load bone index and weight information for.
     */
    private void loadBonesIndicesWeights(@NonNull List<Model> modelList) {
        int bufferSize = 0;
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                bufferSize +=
                        meshData.getBoneIndices().length * ModelLoader.MAX_WEIGHTS
                                + meshData.getWeights().length * ModelLoader.MAX_WEIGHTS;
            }
        }
        ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                int[] bonesIndices = meshData.getBoneIndices();
                float[] weights = meshData.getWeights();
                int rows = bonesIndices.length / ModelLoader.MAX_WEIGHTS;
                for (int row = 0; row < rows; row++) {
                    int startPos = row * ModelLoader.MAX_WEIGHTS;
                    for (int i = 0; i < ModelLoader.MAX_WEIGHTS; ++i) {
                        dataBuffer.putFloat(weights[startPos + i]);
                    }
                    for (int i = 0; i < ModelLoader.MAX_WEIGHTS; ++i) {
                        dataBuffer.putFloat(bonesIndices[startPos + i]);
                    }
                }
            }
        }
        dataBuffer.flip();

        bonesIndicesWeightsBuffer = glGenBuffers();
        vboIDList.add(bonesIndicesWeightsBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bonesIndicesWeightsBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, dataBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(dataBuffer);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Store the bone matrices for all animations of the animated models.
     *
     * @param modelList The list of models to store bone matrices information for.
     */
    private void loadBonesMatricesBuffer(@NonNull List<Model> modelList) {
        int bufferSize = 0;
        final int matrixSize = 4 * 4 * 4;
        for (Model model : modelList) {
            List<Model.Animation> animationsList = model.getAnimationList();
            for (Model.Animation animation : animationsList) {
                List<Model.AnimatedFrame> frameList = animation.frames();
                for (Model.AnimatedFrame frame : frameList) {
                    Matrix4f[] matrices = frame.getBonesMatrices();
                    bufferSize += matrices.length * matrixSize;
                }
            }
        }

        ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);

        for (Model model : modelList) {
            List<Model.Animation> animationsList = model.getAnimationList();
            for (Model.Animation animation : animationsList) {
                List<Model.AnimatedFrame> frameList = animation.frames();
                for (Model.AnimatedFrame frame : frameList) {
                    frame.setOffset(dataBuffer.position() / matrixSize);
                    Matrix4f[] matrices = frame.getBonesMatrices();
                    for (Matrix4f matrix : matrices) {
                        matrix.get(dataBuffer);
                        dataBuffer.position(dataBuffer.position() + matrixSize);
                    }
                }
            }
        }
        dataBuffer.flip();

        bonesMatricesBuffer = glGenBuffers();
        vboIDList.add(bonesMatricesBuffer);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bonesMatricesBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, dataBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(dataBuffer);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Load models with no animation information for a scene. Make sure to clear out the existing
     * buffer data before doing this a second time.
     *
     * @param scene The scene to load models for.
     */
    public void loadStaticModels(@NonNull Scene scene) {
        List<Model> modelList =
                scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();
        staticVaoID = glGenVertexArrays();
        glBindVertexArray(staticVaoID);
        int positionsSize = 0;
        int normalsSize = 0;
        int textureCoordinatesSize = 0;
        int indicesSize = 0;
        int offset = 0;
        for (Model model : modelList) {
            List<RenderBuffers.MeshDrawData> meshDrawDataList = model.getMeshDrawDataList();
            meshDrawDataList.clear();
            for (MeshData meshData : model.getMeshDataList()) {
                positionsSize += meshData.getPositions().length;
                normalsSize += meshData.getNormals().length;
                textureCoordinatesSize += meshData.getTextureCoordinates().length;
                indicesSize += meshData.getIndices().length;

                int meshSizeInBytes = meshData.getPositions().length * 14 * 4;
                meshDrawDataList.add(
                        new MeshDrawData(
                                meshSizeInBytes,
                                meshData.getMaterialIndex(),
                                offset,
                                meshData.getIndices().length));
                offset = positionsSize / 3;
            }
        }

        int vboID = glGenBuffers();
        vboIDList.add(vboID);
        FloatBuffer meshesBuffer =
                MemoryUtil.memAllocFloat(positionsSize + normalsSize * 3 + textureCoordinatesSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                populateMeshBuffer(meshesBuffer, meshData);
            }
        }
        meshesBuffer.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboID);
        glBufferData(GL_ARRAY_BUFFER, meshesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(meshesBuffer);

        defineVertexAttributes();

        // Index VBO
        vboID = glGenBuffers();
        vboIDList.add(vboID);
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indicesSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                indicesBuffer.put(meshData.getIndices());
            }
        }
        indicesBuffer.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboID);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Populate the meshes buffer from mesh data.
     *
     * @param meshesBuffer The buffer to populate.
     * @param meshData The data to fill the buffer with.
     */
    private void populateMeshBuffer(@NonNull FloatBuffer meshesBuffer, @NonNull MeshData meshData) {
        float[] positions = meshData.getPositions();
        float[] normals = meshData.getNormals();
        float[] tangents = meshData.getTangents();
        float[] bitangents = meshData.getBitangents();
        float[] textureCoordinates = meshData.getTextureCoordinates();

        int rows = positions.length / 3;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            int startTextureCoordinate = row * 2;
            meshesBuffer.put(positions[startPos]);
            meshesBuffer.put(positions[startPos + 1]);
            meshesBuffer.put(positions[startPos + 2]);
            meshesBuffer.put(normals[startPos]);
            meshesBuffer.put(normals[startPos + 1]);
            meshesBuffer.put(normals[startPos + 2]);
            meshesBuffer.put(tangents[startPos]);
            meshesBuffer.put(tangents[startPos + 1]);
            meshesBuffer.put(tangents[startPos + 2]);
            meshesBuffer.put(bitangents[startPos]);
            meshesBuffer.put(bitangents[startPos + 1]);
            meshesBuffer.put(bitangents[startPos + 2]);
            meshesBuffer.put(textureCoordinates[startTextureCoordinate]);
            meshesBuffer.put(textureCoordinates[startTextureCoordinate + 1]);
        }
    }
}
