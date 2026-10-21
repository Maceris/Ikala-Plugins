package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.Environment;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
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
 * Works out the light the sky gives, see {@link Environment}, when the sky changed: captures the
 * skybox into the source cube, fills in its levels, prefilters each level of the environment cube
 * and takes the harmonics. Costs nothing on frames the sky stays the same.
 */
@Slf4j
public class EnvironmentUpdate implements RenderStage {

    /** The shader to run. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The environment compute shader.
     */
    public EnvironmentUpdate(@NonNull ShaderVulkan shader) {
        this.shader = shader;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the environment update");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Environment.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            state.environment.getSetLayout(),
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
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            int renderConfig) {
        if (scene == null || state.environment == null) {
            return;
        }
        final TextureHandle texture = scene.getSkyboxTexture();
        // Not until it is uploaded, or the default texture would be captured
        final boolean textured =
                state.textureRegistry.isValid(texture) && state.textureRegistry.isResident(texture);
        final int skyTexture = textured ? state.textureRegistry.slotOrDefault(texture) : -1;
        if (!state.environment.takeChange(skyTexture, scene.getSkyboxDiffuse())) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Earlier frames' lighting may still be reading the cubes
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    0,
                    stack.longs(
                            state.environment.getSet(), state.bindlessTextures.getDescriptorSet()),
                    null);
            ByteBuffer constants = stack.calloc(ShaderBindings.Environment.PUSH_CONSTANTS_SIZE);
            scene.getSkyboxDiffuse().get(ShaderBindings.Environment.SKY_COLOR_OFFSET, constants);
            constants.putInt(ShaderBindings.Environment.SKY_TEXTURE_OFFSET, skyTexture);

            // The sky into the first level, then each level from the one before
            for (int level = 0; level < Environment.SOURCE_LEVELS; ++level) {
                dispatch(
                        commandBuffer,
                        constants,
                        level == 0
                                ? ShaderBindings.Environment.MODE_CAPTURE
                                : ShaderBindings.Environment.MODE_DOWNSAMPLE,
                        level,
                        0,
                        stack);
                barrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                        stack);
            }

            // Every level of the environment and the harmonics read only the finished source
            for (int level = 0; level < Environment.ENVIRONMENT_LEVELS; ++level) {
                dispatch(
                        commandBuffer,
                        constants,
                        ShaderBindings.Environment.MODE_PREFILTER,
                        level,
                        (float) level / (Environment.ENVIRONMENT_LEVELS - 1),
                        stack);
            }
            constants.putInt(
                    ShaderBindings.Environment.MODE_OFFSET,
                    ShaderBindings.Environment.MODE_HARMONICS);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            vkCmdDispatch(commandBuffer, 1, 1, 1);

            // The lighting reads them from here on
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    stack);
        }
    }

    /**
     * Run one pass over one level of a cube, each workgroup a block of one face.
     *
     * @param commandBuffer The command buffer.
     * @param constants The push constants, with the sky filled in.
     * @param mode Which pass.
     * @param level The level written.
     * @param roughness The roughness of that level, for prefiltering.
     * @param stack The stack to allocate on.
     */
    private void dispatch(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull ByteBuffer constants,
            int mode,
            int level,
            float roughness,
            @NonNull MemoryStack stack) {
        constants.putInt(ShaderBindings.Environment.MODE_OFFSET, mode);
        constants.putInt(ShaderBindings.Environment.LEVEL_OFFSET, level);
        constants.putFloat(ShaderBindings.Environment.ROUGHNESS_OFFSET, roughness);
        vkCmdPushConstants(
                commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        final int size = Math.max(Environment.SIZE >> level, 1);
        final int groups =
                (size + ShaderBindings.Environment.WORKGROUP_SIZE - 1)
                        / ShaderBindings.Environment.WORKGROUP_SIZE;
        vkCmdDispatch(commandBuffer, groups, groups, Environment.FACES);
    }

    /**
     * Order work against earlier work.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param stack The stack to allocate on.
     */
    private static void barrier(
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
}
