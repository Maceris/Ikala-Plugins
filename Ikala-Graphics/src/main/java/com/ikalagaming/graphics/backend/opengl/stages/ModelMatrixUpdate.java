package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.PipelineManager;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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

        // TODO(ches) cache these until the number of entities changes
        final int COMMAND_SIZE = 5 * 4;
        final int NUMBER_OF_COMMANDS = model.isAnimated() ? entities.size() : 1;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(NUMBER_OF_COMMANDS * COMMAND_SIZE);

        for (MeshData mesh : model.getMeshDataList()) {
            commandBuffer.clear();

            BufferUtil.getInstance().bindBuffer(mesh.getDrawIndirectBuffer());

            final int count = mesh.getIndices().length;
            final int instanceCount = model.isAnimated() ? 1 : entities.size();
            final int firstIndex = 0;

            if (model.isAnimated()) {
                int baseVertex = 0;
                int baseInstance = 0;

                for (int i = 0; i < entities.size(); ++i) {
                    commandBuffer.putInt(count);
                    commandBuffer.putInt(instanceCount);
                    commandBuffer.putInt(firstIndex);
                    commandBuffer.putInt(baseVertex);
                    commandBuffer.putInt(baseInstance);

                    baseVertex += mesh.getVertexCount();
                    baseInstance += 1;
                }
            } else {
                int baseVertex = 0;
                int baseInstance = 0;
                commandBuffer.putInt(count);
                commandBuffer.putInt(instanceCount);
                commandBuffer.putInt(firstIndex);
                commandBuffer.putInt(baseVertex);
                commandBuffer.putInt(baseInstance);
            }

            commandBuffer.flip();
            glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_STATIC_DRAW);
            BufferUtil.getInstance().unbindBuffer(mesh.getDrawIndirectBuffer());
        }

        MemoryUtil.memFree(commandBuffer);
    }
}
