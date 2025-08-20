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
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
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
            AnimMeshDrawData animMeshDrawData) {}

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

        int indicesSize = 0;
        int verticesSize = 0;
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                verticesSize += meshData.getVertexData().length;
                indicesSize += meshData.getIndices().length;
            }
        }

        destinationAnimationBuffer = glGenBuffers();
        vboIDList.add(destinationAnimationBuffer);
        FloatBuffer meshesBuffer = FloatBuffer.allocate(verticesSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                meshesBuffer.put(meshData.getVertexData());
            }
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
            model.getEntitiesList().stream()
                    .flatMap(ignored -> model.getMeshDataList().stream())
                    .map(MeshData::getIndices)
                    .forEach(indicesBuffer::put);
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
                meshSize += meshData.getVertexData().length;
            }
        }

        bindingPosesBuffer = glGenBuffers();
        vboIDList.add(bindingPosesBuffer);
        FloatBuffer meshesBuffer = FloatBuffer.allocate(meshSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                meshesBuffer.put(meshData.getVertexData());
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
                bufferSize += meshData.getBoneCount() * MeshData.MAX_WEIGHTS * 2;
            }
        }
        ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                dataBuffer.put(meshData.getBoneWeightData());
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
                bufferSize += animation.frameCount() * animation.boneCount() * matrixSize;
            }
        }

        ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);

        for (Model model : modelList) {
            List<Model.Animation> animationsList = model.getAnimationList();
            for (Model.Animation animation : animationsList) {
                dataBuffer.put(animation.frameData());
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
        // TODO(ches) we need to load these separately, not in one big buffer
        List<Model> modelList =
                scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();
        staticVaoID = glGenVertexArrays();
        glBindVertexArray(staticVaoID);

        int indicesSize = 0;
        int verticesSize = 0;
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                verticesSize += meshData.getVertexData().length;
                indicesSize += meshData.getIndices().length;
            }
        }

        int vboID = glGenBuffers();
        vboIDList.add(vboID);

        FloatBuffer meshesBuffer = FloatBuffer.allocate(verticesSize);
        for (Model model : modelList) {
            for (MeshData meshData : model.getMeshDataList()) {
                meshesBuffer.put(meshData.getVertexData());
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
}
