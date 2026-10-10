package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.DepthPyramid;
import com.ikalagaming.graphics.vulkan.DepthPyramidMath;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
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
 * Builds the depth pyramid from what the early scene draws left in the depth buffer, one level at a
 * time, for the late culling pass to test against. Skipped on frames that keep the pyramid they
 * have, like while the observer is frozen, see {@link PerFrameData#buildPyramid}.
 */
@Slf4j
public class DepthPyramidBuild implements RenderStage {

    /** The shader to run. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The depth pyramid compute shader.
     */
    public DepthPyramidBuild(@NonNull ShaderVulkan shader) {
        this.shader = shader;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the depth pyramid build");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.DepthPyramid.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            state.depthPyramid.getBuildSetLayout(),
                                            state.bindlessTextures.getDescriptorSetLayout()))
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
        if (!frameData.buildPyramid) {
            return;
        }
        final DepthPyramid pyramid = state.depthPyramid;
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final int width = pyramid.getDepthWidth();
        final int height = pyramid.getDepthHeight();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // An earlier frame's culling may still be reading it
            computeBarrier(
                    commandBuffer,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    stack);

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    1,
                    stack.longs(state.bindlessTextures.getDescriptorSet()),
                    null);
            ByteBuffer constants = stack.calloc(ShaderBindings.DepthPyramid.PUSH_CONSTANTS_SIZE);
            constants.putInt(
                    ShaderBindings.DepthPyramid.PUSH_CONSTANT_DEPTH_INDEX_OFFSET,
                    frameData.gBuffer.depth().bindlessIndex);

            final int levels = pyramid.getLevels();
            for (int level = 0; level < levels; ++level) {
                final int sourceWidth =
                        level == 0 ? width : DepthPyramidMath.levelSize(width, level - 1);
                final int sourceHeight =
                        level == 0 ? height : DepthPyramidMath.levelSize(height, level - 1);
                final int levelWidth = DepthPyramidMath.levelSize(width, level);
                final int levelHeight = DepthPyramidMath.levelSize(height, level);
                constants.putInt(
                        ShaderBindings.DepthPyramid.PUSH_CONSTANT_SOURCE_SIZE_OFFSET, sourceWidth);
                constants.putInt(
                        ShaderBindings.DepthPyramid.PUSH_CONSTANT_SOURCE_SIZE_OFFSET
                                + Integer.BYTES,
                        sourceHeight);
                constants.putInt(
                        ShaderBindings.DepthPyramid.PUSH_CONSTANT_DESTINATION_SIZE_OFFSET,
                        levelWidth);
                constants.putInt(
                        ShaderBindings.DepthPyramid.PUSH_CONSTANT_DESTINATION_SIZE_OFFSET
                                + Integer.BYTES,
                        levelHeight);
                constants.putInt(ShaderBindings.DepthPyramid.PUSH_CONSTANT_LEVEL_OFFSET, level);
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipelineLayout,
                        0,
                        stack.longs(pyramid.getBuildSet(level)),
                        null);
                final int groupSize = ShaderBindings.DepthPyramid.WORKGROUP_SIZE;
                vkCmdDispatch(
                        commandBuffer,
                        (levelWidth + groupSize - 1) / groupSize,
                        (levelHeight + groupSize - 1) / groupSize,
                        1);
                // The next level reads this one, and after the last, culling reads them all
                computeBarrier(
                        commandBuffer,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                        VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                        stack);
            }
        }
    }

    /**
     * Order compute work against earlier compute work.
     *
     * @param commandBuffer The command buffer.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param stack The stack to allocate on.
     */
    private static void computeBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcAccess,
            long dstAccess,
            @NonNull MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                .srcAccessMask(srcAccess)
                .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                .dstAccessMask(dstAccess);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }
}
