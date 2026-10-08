package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.*;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Runs the animation compute shader, which applies each pose's animation frame to the mesh vertices
 * and writes the results to the mesh's animation target buffer. Entities on the same frame of the
 * same animation share a pose, so each pose is only skinned once. The scene and shadow stages draw
 * animated models from those buffers.
 */
@Slf4j
public class AnimationRender implements RenderStage {

    /** Floats per vertex in the mesh and animation target buffers. */
    private static final int VERTEX_SIZE_IN_FLOATS = 14;

    /**
     * Make sure each mesh's animation target buffer can hold a copy of the vertices for every pose,
     * growing the capacity by doubling like the OpenGL backend.
     *
     * @param state The Vulkan state.
     * @param model The model.
     * @param poseCount The number of distinct poses the model's entities are in.
     */
    private static void updateInstancedStorage(
            @NonNull VulkanState state, @NonNull Model model, int poseCount) {
        int poseCap = model.getMaxAnimatedBufferCapacity();
        if (poseCount <= poseCap) {
            return;
        }
        if (poseCap < 4) {
            poseCap = 4;
        }
        while (poseCount >= poseCap) {
            poseCap *= 2;
        }
        model.setMaxAnimatedBufferCapacity(poseCap);

        // The old buffers are freed once frames in flight are done with them
        for (MeshData meshData : model.getMeshDataList()) {
            var targetBuffer = (SharedBuffer) meshData.getAnimationTargetBuffer();
            targetBuffer.ensureFits(
                    (long) poseCap
                            * meshData.getVertexCount()
                            * VERTEX_SIZE_IN_FLOATS
                            * Float.BYTES,
                    state);
        }
    }

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the animation render stage.
     *
     * @param shader The shader to use for rendering.
     */
    public AnimationRender(final @NonNull ShaderVulkan shader) {
        this.shader = shader;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull State state) {
        log.debug("Initializing animation render");
        VulkanState vulkanState = (VulkanState) state;
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
    }

    @Override
    public void cleanup(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        // The model matrix stage already grouped the entities into poses and wrote the offsets
        boolean anyAnimated = false;
        for (var entry : frameData.modelDrawInfo.entrySet()) {
            if (entry.getKey().isAnimated()) {
                updateInstancedStorage(vulkanState, entry.getKey(), entry.getValue().poseCount());
                anyAnimated = true;
            }
        }
        if (!anyAnimated) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // An earlier frame may still be drawing from the animation targets
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_VERTEX_ATTRIBUTE_INPUT_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);

            ByteBuffer pushConstants = stack.calloc(ShaderBindings.Animation.PUSH_CONSTANTS_SIZE);
            pushConstants.putLong(
                    ShaderBindings.Animation.PUSH_CONSTANT_ANIMATION_OFFSETS_OFFSET,
                    frameData.animationOffsets.deviceAddress);

            for (var entry : frameData.modelDrawInfo.entrySet()) {
                final Model model = entry.getKey();
                if (!model.isAnimated()) {
                    continue;
                }
                final PerFrameData.ModelDrawInfo info = entry.getValue();
                pushConstants.putLong(
                        ShaderBindings.Animation.PUSH_CONSTANT_ANIMATION_DATA_OFFSET,
                        ((SharedBuffer) model.getAnimationBuffer()).deviceAddress);
                pushConstants.putInt(
                        ShaderBindings.Animation.PUSH_CONSTANT_FIRST_POSE_OFFSET, info.firstPose());

                for (MeshData meshData : model.getMeshDataList()) {
                    final int vertexCount = meshData.getVertexCount();
                    pushConstants.putLong(
                            ShaderBindings.Animation.PUSH_CONSTANT_MODEL_DATA_OFFSET,
                            ((SharedBuffer) meshData.getVertexBuffer()).deviceAddress);
                    pushConstants.putLong(
                            ShaderBindings.Animation.PUSH_CONSTANT_BONE_WEIGHTS_OFFSET,
                            ((SharedBuffer) meshData.getBoneWeightBuffer()).deviceAddress);
                    pushConstants.putLong(
                            ShaderBindings.Animation.PUSH_CONSTANT_ANIMATION_TARGET_OFFSET,
                            ((SharedBuffer) meshData.getAnimationTargetBuffer()).deviceAddress);
                    pushConstants.putInt(
                            ShaderBindings.Animation.PUSH_CONSTANT_VERTEX_COUNT_OFFSET,
                            vertexCount);
                    vkCmdPushConstants(
                            commandBuffer,
                            pipelineLayout,
                            VK_SHADER_STAGE_COMPUTE_BIT,
                            0,
                            pushConstants);

                    final int workgroups =
                            (vertexCount + ShaderBindings.Animation.WORKGROUP_SIZE - 1)
                                    / ShaderBindings.Animation.WORKGROUP_SIZE;
                    vkCmdDispatch(commandBuffer, workgroups, info.poseCount(), 1);
                }
            }

            // The scene and shadow stages read the results as vertices
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_VERTEX_ATTRIBUTE_INPUT_BIT,
                    VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT,
                    stack);
        }
    }

    /**
     * Record a global memory barrier.
     *
     * @param commandBuffer The command buffer to record into.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param stack The stack to allocate on.
     */
    private static void memoryBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            @NonNull MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Animation.PUSH_CONSTANTS_SIZE);

            // No descriptor sets, the buffers are all passed by device address
            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkComputePipelineCreateInfo.Buffer pipelineCreateInfos =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineCreateInfos
                    .get(0)
                    .sType$Default()
                    .stage(shader.shaderStages.get(0))
                    .layout(pipelineLayout);

            checkError(
                    vkCreateComputePipelines(
                            state.device.logical,
                            VK_NULL_HANDLE,
                            pipelineCreateInfos,
                            null,
                            longOutput));
            pipeline = longOutput.get(0);
        }
    }
}
