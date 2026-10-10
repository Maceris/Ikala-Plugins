package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.InstanceRegistry;
import com.ikalagaming.graphics.vulkan.MeshRegistry;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lists the instances to draw this frame and writes their draw commands. Each model's instances are
 * listed back to back by their instance table slot, which the instance transform stage turns into
 * model matrices on the GPU; the material overrides, animation poses and indirect draw commands go
 * into this frame's buffers in the same order.
 *
 * <p>The transforms themselves stay on the GPU, in the persistent instance table, so this costs a
 * few integers per instance and no matrix math. Culling on the GPU replaces the list later.
 */
@Slf4j
public class InstanceDrawUpdate implements RenderStage {

    /** The size of a VkDrawIndexedIndirectCommand in bytes. */
    public static final int DRAW_COMMAND_SIZE = 5 * Integer.BYTES;

    /** One model's instances this frame, collected while the instance registry is locked. */
    private static final class ModelInstances {
        /** The slot of each instance. */
        private int[] slots = new int[16];

        /** The material index override of each mesh of each instance, 0 for none. */
        private int[] overrides = new int[16];

        /** The animation matrix offset of each instance, or -1 for the bind pose. */
        private int[] animationOffsets = new int[16];

        /** How many instances there are. */
        private int count;

        /** How many meshes the model has. */
        private final int meshCount;

        /**
         * Start collecting a model's instances.
         *
         * @param meshCount How many meshes the model has.
         */
        ModelInstances(int meshCount) {
            this.meshCount = meshCount;
            overrides = new int[16 * Math.max(1, meshCount)];
        }

        /**
         * Add an instance.
         *
         * @param slot Its slot.
         * @param materials Its material overrides.
         * @param animationOffset Its animation matrix offset, or -1.
         * @param materialCache Looks up material indices.
         */
        void add(int slot, Material[] materials, int animationOffset, MaterialCache materialCache) {
            if (count == slots.length) {
                slots = Arrays.copyOf(slots, count * 2);
                animationOffsets = Arrays.copyOf(animationOffsets, count * 2);
                overrides = Arrays.copyOf(overrides, count * 2 * Math.max(1, meshCount));
            }
            slots[count] = slot;
            animationOffsets[count] = animationOffset;
            for (int mesh = 0; mesh < meshCount; ++mesh) {
                Material material = mesh < materials.length ? materials[mesh] : null;
                // Index 0 is the default material, which the shader treats as no override
                overrides[count * meshCount + mesh] =
                        material == null ? 0 : materialCache.getMaterialIndex(material);
            }
            count += 1;
        }
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        frameData.modelDrawInfo.clear();
        frameData.instanceCount = 0;

        // Collect each model's instances first, so the buffers only need resizing once
        final InstanceRegistry instances = vulkanState.instances.getRegistry();
        final MaterialCache materialCache = scene.getMaterialCache();
        final Map<Model, ModelInstances> collected = new LinkedHashMap<>();
        for (Model model : scene.getModelMap().values()) {
            final ModelInstances modelInstances =
                    new ModelInstances(model.getMeshDataList().size());
            instances.visit(
                    model,
                    (slot, materials, animation) ->
                            modelInstances.add(
                                    slot,
                                    materials,
                                    model.isAnimated()
                                            ? Model.getAnimationMatrixOffset(animation)
                                            : -1,
                                    materialCache));
            if (modelInstances.count > 0) {
                collected.put(model, modelInstances);
            }
        }

        // Work out where everything goes
        int matrixCount = 0;
        int overrideCount = 0;
        int commandCount = 0;
        int poseCount = 0;
        Map<Model, Poses> modelPoses = new LinkedHashMap<>();
        for (var entry : collected.entrySet()) {
            final Model model = entry.getKey();
            final ModelInstances modelInstances = entry.getValue();
            final int count = modelInstances.count;
            int modelPoseCount = 0;
            if (model.isAnimated()) {
                Poses poses = findPoses(modelInstances);
                modelPoses.put(model, poses);
                modelPoseCount = poses.matrixOffsets().length;
            }
            final int meshCount = model.getMeshDataList().size();
            final int commandsPerMesh = model.isAnimated() ? count : 1;
            frameData.modelDrawInfo.put(
                    model,
                    new PerFrameData.ModelDrawInfo(
                            matrixCount,
                            overrideCount,
                            commandCount,
                            commandsPerMesh,
                            poseCount,
                            modelPoseCount));
            matrixCount += count;
            overrideCount += count * meshCount;
            commandCount += commandsPerMesh * meshCount;
            poseCount += modelPoseCount;
        }

        // Written on the GPU by the instance transform stage
        frameData.sceneModelMatrices.ensureCapacity(
                (long) matrixCount * PipelineManagerVulkan.MODEL_MATRIX_SIZE * Float.BYTES,
                vulkanState);
        frameData.instanceList.ensureCapacity((long) matrixCount * Integer.BYTES, vulkanState);
        frameData.sceneMaterialOverrides.ensureCapacity(
                (long) overrideCount * Integer.BYTES, vulkanState);
        frameData.sceneDrawCommands.ensureCapacity(
                (long) commandCount * DRAW_COMMAND_SIZE, vulkanState);
        frameData.animationOffsets.ensureCapacity((long) poseCount * Integer.BYTES, vulkanState);

        if (frameData.modelDrawInfo.isEmpty()) {
            return;
        }
        frameData.instanceCount = matrixCount;

        // The buffers are host coherent and the GPU is done with this frame's copies
        IntBuffer list =
                MemoryUtil.memIntBuffer(
                        frameData.instanceList.allocationInfo.pMappedData(), matrixCount);
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

        final MeshRegistry meshes = vulkanState.geometry.getRegistry();
        frameData.modelDrawInfo.forEach(
                (model, info) -> {
                    final ModelInstances modelInstances = collected.get(model);
                    list.put(info.firstMatrix(), modelInstances.slots, 0, modelInstances.count);
                    overrides.put(
                            info.firstOverride(),
                            modelInstances.overrides,
                            0,
                            modelInstances.count * modelInstances.meshCount);
                    Poses poses = modelPoses.get(model);
                    if (poses != null) {
                        animationOffsets.put(info.firstPose(), poses.matrixOffsets());
                    }
                    writeDrawCommands(model, info, modelInstances.count, poses, meshes, commands);
                });
    }

    /**
     * Which pose each instance of an animated model is in.
     *
     * @param matrixOffsets The animation matrix offset of each distinct pose, or -1 for the bind
     *     pose.
     * @param instancePoses The index into the poses for each instance.
     */
    private record Poses(int[] matrixOffsets, int[] instancePoses) {}

    /**
     * Group the instances of an animated model by pose. Instances on the same frame of the same
     * animation end up with identical vertices, so they share one copy, and every instance that
     * isn't animating shares the bind pose.
     *
     * @param modelInstances The animated model's instances.
     * @return The poses.
     */
    private static Poses findPoses(@NonNull ModelInstances modelInstances) {
        Map<Integer, Integer> poseByOffset = new LinkedHashMap<>();
        int[] instancePoses = new int[modelInstances.count];
        for (int i = 0; i < modelInstances.count; ++i) {
            final int offset = modelInstances.animationOffsets[i];
            instancePoses[i] = poseByOffset.computeIfAbsent(offset, ignored -> poseByOffset.size());
        }
        int[] matrixOffsets = new int[poseByOffset.size()];
        poseByOffset.forEach((offset, pose) -> matrixOffsets[pose] = offset);
        return new Poses(matrixOffsets, instancePoses);
    }

    /**
     * Write the indirect draw commands for each mesh of a model. Indices come from the shared index
     * buffer. Animated models draw each instance separately from the animation output, which has a
     * copy of the vertices for each pose back to back. Everything else is one instanced draw from
     * the shared vertex buffer. Meshes that aren't resident yet get commands that draw nothing, so
     * every mesh keeps its place.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param count How many instances the model has.
     * @param poses The pose of each instance, null if the model isn't animated.
     * @param meshes Where each mesh is in the shared buffers.
     * @param commands The mapped draw command buffer.
     */
    private static void writeDrawCommands(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            int count,
            Poses poses,
            @NonNull MeshRegistry meshes,
            @NonNull ByteBuffer commands) {
        int position = info.firstCommand() * DRAW_COMMAND_SIZE;
        for (MeshData mesh : model.getMeshDataList()) {
            final MeshRegistry.Location location = meshes.locate(mesh.getMesh());
            final int indexCount = location == null ? 0 : mesh.getIndices().length;
            final int firstIndex = location == null ? 0 : location.firstIndex();

            if (poses != null) {
                for (int i = 0; i < count; ++i) {
                    // The vertices of this instance's pose, with this instance's model matrix
                    final int vertexOffset = poses.instancePoses()[i] * mesh.getVertexCount();
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
                        location == null ? 0 : count,
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
