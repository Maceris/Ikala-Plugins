package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.ParticleMath;
import com.ikalagaming.graphics.vulkan.ParticlePool;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Draws the particles {@link ParticleUpdate} simulated as camera-facing billboards, one instance
 * per alive particle, drawn indirectly with the count the simulation left. Each transparency pass
 * owns one: {@link TranslucentRender} draws them into its weighted color targets with {@code
 * particle.frag}, and {@link OitSplat} adds their extinction into the transparency volume with
 * {@code particle_splat.frag}, so particles and translucent surfaces weight each other in any
 * order. The pass's own descriptor sets stay bound in the same order; the particles' push
 * constants, which both shader stages read, take the place of the pass's.
 */
public class ParticleDraw {

    /** How far in front of the opaque scene particles start fading, per meter of their size. */
    public static final float SOFT_SCALE = 0.5f;

    /** The billboards' shaders. */
    @NonNull private final ShaderVulkan shader;

    /** True to draw into the color targets, false to splat into the volume with no targets. */
    private final boolean accumulate;

    /** VkPipelineLayout, VK_NULL_HANDLE until set up. */
    private long pipelineLayout = VK_NULL_HANDLE;

    /** VkPipeline, VK_NULL_HANDLE until set up. */
    private long pipeline = VK_NULL_HANDLE;

    /**
     * Set up the drawing for one pass.
     *
     * @param shader The billboards' vertex and fragment shaders.
     * @param accumulate True for the color pass, false for the splat.
     */
    public ParticleDraw(@NonNull ShaderVulkan shader, boolean accumulate) {
        this.shader = shader;
        this.accumulate = accumulate;
    }

    /**
     * Create the pipeline.
     *
     * @param state The Vulkan state.
     * @param setLayouts The pass's descriptor set layouts, in its order.
     */
    public void initialize(@NonNull VulkanState state, long @NonNull [] setLayouts) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.Particles.DRAW_CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(stack.longs(setLayouts))
                                    .pPushConstantRanges(ranges),
                            null,
                            longOutput));
            pipelineLayout = longOutput.get(0);
            pipeline = createPipeline(state, stack);
        }
    }

    /**
     * Destroy the pipeline.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipeline = VK_NULL_HANDLE;
        pipelineLayout = VK_NULL_HANDLE;
    }

    /**
     * Record drawing the particles, inside the pass's rendering with its viewport set.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param scene The scene, for the camera and view distance.
     * @param sets The pass's descriptor sets, in its order.
     * @param width The area drawn, in pixels.
     * @param height Its height.
     * @param voxelWeights Whether the color pass weights by the transparency volume.
     * @param stack The stack to allocate on.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull Scene scene,
            long @NonNull [] sets,
            int width,
            int height,
            boolean voxelWeights,
            @NonNull MemoryStack stack) {
        final ParticlePool pool = state.particlePool;
        if (pool == null) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout,
                0,
                stack.longs(sets),
                null);
        final Vector3d anchor = new Vector3d(pool.getAnchor()).sub(scene.getCamera().getPosition());
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.DRAW_CONSTANTS_SIZE);
        constants.putLong(
                ShaderBindings.Particles.DRAW_POOL_OFFSET, pool.getParticles().deviceAddress);
        constants.putLong(
                ShaderBindings.Particles.DRAW_POOL_OFFSET + Long.BYTES,
                pool.aliveListAddress(pool.getCurrent()));
        constants.putFloat(ShaderBindings.Particles.DRAW_ANCHOR_OFFSET, (float) anchor.x);
        constants.putFloat(
                ShaderBindings.Particles.DRAW_ANCHOR_OFFSET + Float.BYTES, (float) anchor.y);
        constants.putFloat(
                ShaderBindings.Particles.DRAW_ANCHOR_OFFSET + 2 * Float.BYTES, (float) anchor.z);
        constants.putFloat(ShaderBindings.Particles.DRAW_SOFT_SCALE_OFFSET, SOFT_SCALE);
        constants.putFloat(ShaderBindings.Particles.DRAW_SCREEN_SIZE_OFFSET, width);
        constants.putFloat(ShaderBindings.Particles.DRAW_SCREEN_SIZE_OFFSET + Float.BYTES, height);
        constants.putFloat(ShaderBindings.Particles.DRAW_TILES_OFFSET, VoxelOitMath.tiles(width));
        constants.putFloat(
                ShaderBindings.Particles.DRAW_TILES_OFFSET + Float.BYTES,
                VoxelOitMath.tiles(height));
        constants.putFloat(
                ShaderBindings.Particles.DRAW_LOG_SCALE_OFFSET,
                VoxelOitMath.logScale(scene.getViewDistance()));
        constants.putInt(ShaderBindings.Particles.DRAW_VOXEL_WEIGHTS_OFFSET, voxelWeights ? 1 : 0);
        constants.putFloat(
                ShaderBindings.Particles.DRAW_PIXEL_SCALE_OFFSET,
                accumulate ? 1 : VoxelOitMath.TILE_SIZE);
        constants.putLong(
                ShaderBindings.Particles.DRAW_SPLAT_OFFSET,
                state.oitVolume.getExtinction().deviceAddress);
        vkCmdPushConstants(
                commandBuffer,
                pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                0,
                constants);
        vkCmdDrawIndirect(
                commandBuffer,
                pool.getControl().buffer,
                ParticleMath.CONTROL_DRAW,
                1,
                ParticleMath.CONTROL_DRAW_STRIDE);
    }

    /**
     * Create the pipeline: billboards with no vertex input, both sides, depth tested against the
     * opaque scene without writing for the color pass, adding into both targets; or no targets and
     * no depth for the splat, as the translucent surfaces' own pipelines are.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     * @return The VkPipeline.
     */
    private long createPipeline(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        final int count = shader.shaderModules.length;
        VkPipelineShaderStageCreateInfo.Buffer stages =
                VkPipelineShaderStageCreateInfo.calloc(count, stack);
        for (int i = 0; i < count; ++i) {
            stages.get(i).set(shader.shaderStages.get(i));
        }
        VkPipelineVertexInputStateCreateInfo vertexInputState =
                VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
                VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        VkPipelineViewportStateCreateInfo viewportState =
                VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .viewportCount(1)
                        .scissorCount(1);
        VkPipelineDynamicStateCreateInfo dynamicState =
                VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(
                                stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
        VkPipelineDepthStencilStateCreateInfo depthStencilState =
                VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(accumulate)
                        .depthWriteEnable(false)
                        .depthCompareOp(VK_COMPARE_OP_LESS);
        VkPipelineRenderingCreateInfo renderingCreateInfo =
                VkPipelineRenderingCreateInfo.calloc(stack).sType$Default();
        if (accumulate) {
            renderingCreateInfo
                    .pColorAttachmentFormats(
                            stack.ints(
                                    PipelineManagerVulkan.OIT_ACCUM_FORMAT,
                                    PipelineManagerVulkan.OIT_EXTINCTION_FORMAT))
                    .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);
        }
        final int targets = accumulate ? 2 : 0;
        VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                VkPipelineColorBlendAttachmentState.calloc(targets, stack);
        for (int i = 0; i < targets; ++i) {
            blendAttachments
                    .get(i)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
        }
        VkPipelineColorBlendStateCreateInfo colorBlendState =
                VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pAttachments(blendAttachments);
        VkPipelineRasterizationStateCreateInfo rasterizationState =
                VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                        .lineWidth(1.0f);
        VkPipelineMultisampleStateCreateInfo multisampleState =
                VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfos =
                VkGraphicsPipelineCreateInfo.calloc(1, stack);
        pipelineCreateInfos
                .get(0)
                .sType$Default()
                .pNext(renderingCreateInfo)
                .stageCount(count)
                .pStages(stages)
                .pVertexInputState(vertexInputState)
                .pInputAssemblyState(inputAssemblyState)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizationState)
                .pMultisampleState(multisampleState)
                .pDepthStencilState(depthStencilState)
                .pColorBlendState(colorBlendState)
                .pDynamicState(dynamicState)
                .layout(pipelineLayout)
                .renderPass(VK_NULL_HANDLE);
        checkError(
                vkCreateGraphicsPipelines(
                        state.device.logical,
                        VK_NULL_HANDLE,
                        pipelineCreateInfos,
                        null,
                        longOutput));
        return longOutput.get(0);
    }
}
