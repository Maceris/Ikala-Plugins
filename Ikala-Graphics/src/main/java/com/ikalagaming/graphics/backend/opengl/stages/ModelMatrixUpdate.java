package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.backend.opengl.CommandBuffer;
import com.ikalagaming.graphics.backend.opengl.PipelineManager;
import com.ikalagaming.graphics.frontend.RenderStage;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

/** Used to update the model matrices buffers for the scene. */
@Setter
@AllArgsConstructor
public class ModelMatrixUpdate implements RenderStage {

    /** The scene object rendering command buffers. */
    @NonNull private CommandBuffer commandBuffers;

    @Override
    public void render(Scene scene) {
        List<Model> animatedList =
                scene.getModelMap().values().stream().filter(Model::isAnimated).toList();
        int animatedBuffer = commandBuffers.getAnimatedModelMatricesBuffer();
        updateModelBuffer(animatedList, animatedBuffer);

        List<Model> staticList =
                scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();
        int staticBuffer = commandBuffers.getStaticModelMatricesBuffer();
        updateModelBuffer(staticList, staticBuffer);
    }

    /**
     * Take a list of models and upload the model matrices to the appropriate buffer.
     *
     * @param models The list of models.
     * @param bufferID The opengl ID of the buffer we want to buffer data to.
     */
    private void updateModelBuffer(List<Model> models, int bufferID) {
        int totalEntities = 0;
        for (Model model : models) {
            totalEntities += model.getEntitiesList().size();
        }

        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(totalEntities * PipelineManager.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : models) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * PipelineManager.MODEL_MATRIX_SIZE, modelMatrices);
                entityIndex++;
            }
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferID);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);
    }
}
