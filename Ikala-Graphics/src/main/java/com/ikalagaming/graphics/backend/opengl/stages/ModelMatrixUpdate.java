package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.PipelineManager;
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

    @Override
    public void render(Scene scene) {
        scene.getModelMap().values().forEach(this::updateModelBuffer);
    }

    /** Update the model matrices for a model. */
    private void updateModelBuffer(@NonNull Model model) {
        List<Entity> entities = model.getEntitiesList();

        if (entities.isEmpty()) {
            return;
        }

        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(entities.size() * PipelineManager.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Entity entity : entities) {
            entity.getModelMatrix()
                    .get(entityIndex * PipelineManager.MODEL_MATRIX_SIZE, modelMatrices);
            entityIndex++;
        }
        modelMatrices.flip();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, (int) model.getModelMatricesBuffer().id());
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);
    }
}
