package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.MeshRegistry;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
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
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
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
        final MeshRegistry meshes = vulkanState.geometry.getRegistry();
        final Vector3dc origin = scene.getCamera().getPosition();
        final Matrix4f scratch = new Matrix4f();
        frameData.modelDrawInfo.forEach(
                (model, info) -> {
                    writeModelMatrices(model, info, origin, scratch, matrices);
                    writeMaterialOverrides(model, info, overrides, materialCache);
                    Poses poses = modelPoses.get(model);
                    if (poses != null) {
                        animationOffsets.put(info.firstPose(), poses.matrixOffsets());
                    }
                    writeDrawCommands(model, info, poses, meshes, commands);
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
     * Write the model matrices for every entity of a model, in render space.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param origin The world position of the render space origin, the camera position.
     * @param scratch A matrix to do math in, so we don't allocate one per entity.
     * @param matrices The mapped model matrix buffer.
     */
    private static void writeModelMatrices(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            @NonNull Vector3dc origin,
            @NonNull Matrix4f scratch,
            @NonNull FloatBuffer matrices) {
        int matrixIndex = info.firstMatrix();
        for (Entity entity : model.getEntitiesList()) {
            entity.getRenderMatrix(origin, scratch)
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
     * Write the indirect draw commands for each mesh of a model. Indices come from the shared index
     * buffer. Animated models draw each entity separately from the animation output, which has a
     * copy of the vertices for each pose back to back. Everything else is one instanced draw from
     * the shared vertex buffer. Meshes that aren't resident yet get commands that draw nothing, so
     * every mesh keeps its place.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param poses The pose of each entity, null if the model isn't animated.
     * @param meshes Where each mesh is in the shared buffers.
     * @param commands The mapped draw command buffer.
     */
    private static void writeDrawCommands(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            Poses poses,
            @NonNull MeshRegistry meshes,
            @NonNull ByteBuffer commands) {
        List<Entity> entities = model.getEntitiesList();

        int position = info.firstCommand() * DRAW_COMMAND_SIZE;
        for (MeshData mesh : model.getMeshDataList()) {
            final MeshRegistry.Location location = meshes.locate(mesh.getMesh());
            final int indexCount = location == null ? 0 : mesh.getIndices().length;
            final int firstIndex = location == null ? 0 : location.firstIndex();

            if (poses != null) {
                for (int i = 0; i < entities.size(); ++i) {
                    // The vertices of this entity's pose, with this entity's model matrix
                    final int vertexOffset = poses.entityPoses()[i] * mesh.getVertexCount();
                    writeCommand(
                            commands,
                            position,
                            indexCount,
                            location == null ? 0 : 1,
                            firstIndex,
                            vertexOffset,
                            i);
                    position += DRAW_COMMAND_SIZE;
                }
            } else {
                writeCommand(
                        commands,
                        position,
                        indexCount,
                        location == null ? 0 : entities.size(),
                        firstIndex,
                        location == null ? 0 : location.vertexOffset(),
                        0);
                position += DRAW_COMMAND_SIZE;
            }
        }
    }

    /**
     * Write one VkDrawIndexedIndirectCommand.
     *
     * @param commands The mapped draw command buffer.
     * @param position Where the command goes, in bytes.
     * @param indexCount The number of indices to draw.
     * @param instanceCount The number of instances.
     * @param firstIndex The first index in the index buffer.
     * @param vertexOffset What is added to each index to find its vertex.
     * @param firstInstance The first instance.
     */
    private static void writeCommand(
            @NonNull ByteBuffer commands,
            int position,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int vertexOffset,
            int firstInstance) {
        commands.putInt(position, indexCount);
        commands.putInt(position + Integer.BYTES, instanceCount);
        commands.putInt(position + 2 * Integer.BYTES, firstIndex);
        commands.putInt(position + 3 * Integer.BYTES, vertexOffset);
        commands.putInt(position + 4 * Integer.BYTES, firstInstance);
    }
}
