package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.PerFrameData;
import com.ikalagaming.graphics.backend.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs the model matrices, material overrides, animation poses, and indirect draw commands for
 * every model in the scene into this frame's buffers. Unlike OpenGL, we can't overwrite a model's
 * buffers while an earlier frame may still be drawing with them, so everything is rebuilt each
 * frame instead.
 */
@Slf4j
public class ModelMatrixUpdate implements RenderStage {

    /** The size of a VkDrawIndexedIndirectCommand in bytes. */
    public static final int DRAW_COMMAND_SIZE = 5 * Integer.BYTES;

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        VulkanState vulkanState = (VulkanState) state;
        PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        frameData.modelDrawInfo.clear();

        // Work out where everything goes first, so the buffers only need resizing once
        int matrixCount = 0;
        int overrideCount = 0;
        int commandCount = 0;
        int poseCount = 0;
        Map<Model, Poses> modelPoses = new HashMap<>();
        for (Model model : scene.getModelMap().values()) {
            final int entityCount = model.getEntitiesList().size();
            if (entityCount == 0) {
                model.setEntitiesLastFrame(0);
                continue;
            }
            model.setEntitiesLastFrame(entityCount);

            int modelPoseCount = 0;
            if (model.isAnimated()) {
                Poses poses = findPoses(model);
                modelPoses.put(model, poses);
                modelPoseCount = poses.matrixOffsets().length;
            }

            final int meshCount = model.getMeshDataList().size();
            final int commandsPerMesh = model.isAnimated() ? entityCount : 1;
            frameData.modelDrawInfo.put(
                    model,
                    new PerFrameData.ModelDrawInfo(
                            matrixCount,
                            overrideCount,
                            commandCount,
                            commandsPerMesh,
                            poseCount,
                            modelPoseCount));
            matrixCount += entityCount;
            overrideCount += entityCount * meshCount;
            commandCount += commandsPerMesh * meshCount;
            poseCount += modelPoseCount;
        }

        frameData.sceneModelMatrices.ensureCapacity(
                (long) matrixCount * PipelineManagerVulkan.MODEL_MATRIX_SIZE * Float.BYTES,
                vulkanState);
        frameData.sceneMaterialOverrides.ensureCapacity(
                (long) overrideCount * Integer.BYTES, vulkanState);
        frameData.sceneDrawCommands.ensureCapacity(
                (long) commandCount * DRAW_COMMAND_SIZE, vulkanState);
        frameData.animationOffsets.ensureCapacity((long) poseCount * Integer.BYTES, vulkanState);

        if (frameData.modelDrawInfo.isEmpty()) {
            return;
        }

        // The buffers are host coherent and the GPU is done with this frame's copies
        FloatBuffer matrices =
                MemoryUtil.memFloatBuffer(
                        frameData.sceneModelMatrices.allocationInfo.pMappedData(),
                        matrixCount * PipelineManagerVulkan.MODEL_MATRIX_SIZE);
        IntBuffer overrides =
                MemoryUtil.memIntBuffer(
                        frameData.sceneMaterialOverrides.allocationInfo.pMappedData(),
                        overrideCount);
        ByteBuffer commands =
                MemoryUtil.memByteBuffer(
                        frameData.sceneDrawCommands.allocationInfo.pMappedData(),
                        commandCount * DRAW_COMMAND_SIZE);

        IntBuffer animationOffsets =
                MemoryUtil.memIntBuffer(
                        frameData.animationOffsets.allocationInfo.pMappedData(), poseCount);

        final MaterialCache materialCache = scene.getMaterialCache();
        frameData.modelDrawInfo.forEach(
                (model, info) -> {
                    writeModelMatrices(model, info, matrices);
                    writeMaterialOverrides(model, info, overrides, materialCache);
                    Poses poses = modelPoses.get(model);
                    if (poses != null) {
                        animationOffsets.put(info.firstPose(), poses.matrixOffsets());
                    }
                    writeDrawCommands(model, info, poses, commands);
                });
    }

    /**
     * Which pose each entity of an animated model is in.
     *
     * @param matrixOffsets The animation matrix offset of each distinct pose, or -1 for the bind
     *     pose.
     * @param entityPoses The index into the poses for each entity.
     */
    private record Poses(int[] matrixOffsets, int[] entityPoses) {}

    /**
     * Group the entities of an animated model by pose. Entities on the same frame of the same
     * animation end up with identical vertices, so they share one copy, and every entity that isn't
     * animating shares the bind pose.
     *
     * @param model The animated model.
     * @return The poses.
     */
    private static Poses findPoses(@NonNull Model model) {
        List<Entity> entities = model.getEntitiesList();
        Map<Integer, Integer> poseByOffset = new LinkedHashMap<>();
        int[] entityPoses = new int[entities.size()];
        for (int i = 0; i < entities.size(); ++i) {
            final int offset = Model.getAnimationMatrixOffset(entities.get(i));
            entityPoses[i] = poseByOffset.computeIfAbsent(offset, ignored -> poseByOffset.size());
        }
        int[] matrixOffsets = new int[poseByOffset.size()];
        poseByOffset.forEach((offset, pose) -> matrixOffsets[pose] = offset);
        return new Poses(matrixOffsets, entityPoses);
    }

    /**
     * Write the model matrices for every entity of a model.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param matrices The mapped model matrix buffer.
     */
    private static void writeModelMatrices(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            @NonNull FloatBuffer matrices) {
        int matrixIndex = info.firstMatrix();
        for (Entity entity : model.getEntitiesList()) {
            entity.getModelMatrix()
                    .get(matrixIndex * PipelineManagerVulkan.MODEL_MATRIX_SIZE, matrices);
            matrixIndex += 1;
        }
    }

    /**
     * Write the material overrides for every entity of a model, one per mesh, in entity order.
     * Index 0 is the default material, which the shader treats as no override.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param overrides The mapped material override buffer.
     * @param materialCache The cache to look material indices up in.
     */
    private static void writeMaterialOverrides(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            @NonNull IntBuffer overrides,
            @NonNull MaterialCache materialCache) {
        int overrideIndex = info.firstOverride();
        for (Entity entity : model.getEntitiesList()) {
            for (Material material : entity.getMaterialOverrides()) {
                overrides.put(overrideIndex, materialCache.getMaterialIndex(material));
                overrideIndex += 1;
            }
        }
    }

    /**
     * Write the indirect draw commands for each mesh of a model. Animated models draw each entity
     * separately from the animation output, which has a copy of the vertices for each pose back to
     * back. Everything else is one instanced draw.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param poses The pose of each entity, null if the model isn't animated.
     * @param commands The mapped draw command buffer.
     */
    private static void writeDrawCommands(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            Poses poses,
            @NonNull ByteBuffer commands) {
        List<Entity> entities = model.getEntitiesList();
        final int firstIndex = 0;

        int position = info.firstCommand() * DRAW_COMMAND_SIZE;
        for (MeshData mesh : model.getMeshDataList()) {
            final int indexCount = mesh.getIndices().length;

            if (poses != null) {
                for (int i = 0; i < entities.size(); ++i) {
                    // The vertices of this entity's pose, with this entity's model matrix
                    final int vertexOffset = poses.entityPoses()[i] * mesh.getVertexCount();
                    commands.putInt(position, indexCount);
                    commands.putInt(position + Integer.BYTES, 1);
                    commands.putInt(position + 2 * Integer.BYTES, firstIndex);
                    commands.putInt(position + 3 * Integer.BYTES, vertexOffset);
                    commands.putInt(position + 4 * Integer.BYTES, i);
                    position += DRAW_COMMAND_SIZE;
                }
            } else {
                commands.putInt(position, indexCount);
                commands.putInt(position + Integer.BYTES, entities.size());
                commands.putInt(position + 2 * Integer.BYTES, firstIndex);
                commands.putInt(position + 3 * Integer.BYTES, 0);
                commands.putInt(position + 4 * Integer.BYTES, 0);
                position += DRAW_COMMAND_SIZE;
            }
        }
    }
}
