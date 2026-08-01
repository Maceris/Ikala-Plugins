package com.ikalagaming.graphics.backend.vulkan.stages;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

/** Used to update the model matrices buffers for the scene. */
@Slf4j
@Setter
@AllArgsConstructor
public class ModelMatrixUpdate implements RenderStage {

    @Override
    public void render(Scene scene) {
        scene.getModelMap().values().forEach(this::updateModelBuffer);
        // TODO(ches) barrier
    }

    /** Update the model matrices for a model. */
    private void updateModelBuffer(@NonNull Model model) {
        List<Entity> entities = model.getEntitiesList();

        if (entities.isEmpty()) {
            model.setEntitiesLastFrame(0);
            return;
        }

        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(entities.size() * PipelineManagerVulkan.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Entity entity : entities) {
            entity.getModelMatrix()
                    .get(entityIndex * PipelineManagerVulkan.MODEL_MATRIX_SIZE, modelMatrices);
            entityIndex++;
        }

        BufferUtil.INSTANCE.bindBuffer(model.getModelMatricesBuffer());
        // TODO(ches) figure out right buffer usage or redesign BufferUtil API
        BufferUtil.INSTANCE.bufferData(
                model.getModelMatricesBuffer(), modelMatrices, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        MemoryUtil.memFree(modelMatrices);
        BufferUtil.INSTANCE.unbindBuffer(model.getModelMatricesBuffer());

        if (model.getEntitiesLastFrame() != entities.size()) {
            updateCommandBuffers(model);
            model.setEntitiesLastFrame(entities.size());
            model.setMaterialOverridesDirty(true);
        }
    }

    private static void updateCommandBuffers(Model model) {
        List<Entity> entities = model.getEntitiesList();

        final int COMMAND_SIZE = 5 * 4;
        final int NUMBER_OF_COMMANDS = model.isAnimated() ? entities.size() : 1;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(NUMBER_OF_COMMANDS * COMMAND_SIZE);

        for (MeshData mesh : model.getMeshDataList()) {
            commandBuffer.clear();

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

            BufferUtil.INSTANCE.bindBuffer(mesh.getDrawIndirectBuffer());
            // TODO(ches) figure out right buffer usage or redesign BufferUtil API
            BufferUtil.INSTANCE.bufferData(
                    mesh.getDrawIndirectBuffer(),
                    commandBuffer,
                    VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            BufferUtil.INSTANCE.unbindBuffer(mesh.getDrawIndirectBuffer());
        }

        MemoryUtil.memFree(commandBuffer);
    }
}
