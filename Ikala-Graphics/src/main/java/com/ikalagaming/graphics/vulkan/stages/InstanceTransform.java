package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Turns this frame's instances into model matrices on the GPU, relative to the camera. The instance
 * draw update stage lists which instance slots are drawn this frame, model by model; this stage
 * reads each one from the persistent {@link InstanceTable} and writes its matrix at the same place
 * in this frame's matrix buffer, which the scene, shadow and debug stages read.
 */
@Slf4j
public class InstanceTransform implements RenderStage {

    /** The shader to run. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The instance transform compute shader.
     */
    public InstanceTransform(@NonNull ShaderVulkan shader) {
        this.shader = shader;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing instance transforms");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Instances.PUSH_CONSTANTS_SIZE);
            // No descriptor sets, the buffers are all passed by device address
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkComputePipelineCreateInfo.Buffer pipelineInfos =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfos
                    .get(0)
                    .sType$Default()
                    .stage(shader.shaderStages.get(0))
                    .layout(pipelineLayout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, pipelineInfos, null, longOutput));
            pipeline = longOutput.get(0);
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final int count = frameData.instanceCount;
        if (count == 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final Vector3dc camera = scene.getCamera().getPosition();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            ByteBuffer constants = stack.calloc(ShaderBindings.Instances.PUSH_CONSTANTS_SIZE);
            constants.putLong(
                    ShaderBindings.Instances.PUSH_CONSTANT_TABLE_OFFSET,
                    state.instances.getEntries().deviceAddress);
            constants.putLong(
                    ShaderBindings.Instances.PUSH_CONSTANT_LIST_OFFSET,
                    frameData.instanceList.deviceAddress);
            constants.putLong(
                    ShaderBindings.Instances.PUSH_CONSTANT_MATRICES_OFFSET,
                    frameData.sceneModelMatrices.deviceAddress);
            final int high = ShaderBindings.Instances.PUSH_CONSTANT_CAMERA_HIGH_OFFSET;
            constants.putFloat(high, InstanceTable.high(camera.x()));
            constants.putFloat(high + Float.BYTES, InstanceTable.high(camera.y()));
            constants.putFloat(high + 2 * Float.BYTES, InstanceTable.high(camera.z()));
            final int low = ShaderBindings.Instances.PUSH_CONSTANT_CAMERA_LOW_OFFSET;
            constants.putFloat(low, InstanceTable.low(camera.x()));
            constants.putFloat(low + Float.BYTES, InstanceTable.low(camera.y()));
            constants.putFloat(low + 2 * Float.BYTES, InstanceTable.low(camera.z()));
            constants.putInt(ShaderBindings.Instances.PUSH_CONSTANT_COUNT_OFFSET, count);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            final int groups =
                    (count + ShaderBindings.Instances.WORKGROUP_SIZE - 1)
                            / ShaderBindings.Instances.WORKGROUP_SIZE;
            vkCmdDispatch(commandBuffer, groups, 1, 1);

            // The scene, shadow and debug stages read the matrices
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
        }
    }
}
