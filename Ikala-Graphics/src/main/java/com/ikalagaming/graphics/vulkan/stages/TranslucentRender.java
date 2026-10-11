package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Draws translucent meshes, the ones culling put in {@link InstanceDrawUpdate#LIST_TRANSLUCENT}, in
 * any order into the order independent transparency targets: each surface lit in {@code
 * translucent.frag} the same way the light stage lights the g-buffer, then weighted and added up.
 * {@link OitResolve} composites the result over the scene color.
 *
 * <p>The surfaces are depth tested against the opaque scene without writing depth, and drawn from
 * both sides. The stage reads the light stage's descriptor set (set 0), the bindless textures (set
 * 1), the scene stage's descriptor set (set 2), all written earlier in the frame, and the
 * transparency volume (set 3), which {@link OitSplat} and {@link OitIntegrate} filled this frame
 * when it weights by the volume.
 */
@Slf4j
public class TranslucentRender implements RenderStage {

    /** The kinds of mesh that can be translucent, each with its own pipeline. */
    static final MeshKind[] KINDS = {MeshKind.STANDARD, MeshKind.BAKED_TRANSLUCENT};

    /** The shaders for meshes in the full vertex format. */
    @NonNull private final ShaderVulkan shader;

    /** The shaders for baked sections. */
    @NonNull private final ShaderVulkan bakedShader;

    /** The scene stage, whose descriptor sets hold the matrices and visible lists. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, whose descriptor sets hold the lights, clusters and shadows. */
    @NonNull private final LightRender lightRender;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline for the full vertex format, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkPipeline for baked sections, will be VK_NULL_HANDLE if not set up. */
    private long bakedPipeline;

    /**
     * Set up the stage.
     *
     * @param shader The shaders for meshes in the full vertex format.
     * @param bakedShader The shaders for baked sections.
     * @param sceneRender The scene stage, whose descriptor sets this reads.
     * @param lightRender The light stage, whose descriptor sets this reads.
     */
    public TranslucentRender(
            @NonNull ShaderVulkan shader,
            @NonNull ShaderVulkan bakedShader,
            @NonNull SceneRender sceneRender,
            @NonNull LightRender lightRender) {
        this.shader = shader;
        this.bakedShader = bakedShader;
        this.sceneRender = sceneRender;
        this.lightRender = lightRender;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
        bakedPipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing translucent render");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.Translucent.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            lightRender.getDescriptorSetLayout(),
                                            state.bindlessTextures.getDescriptorSetLayout(),
                                            sceneRender.getDescriptorSetLayout(),
                                            state.oitVolume.getReadSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
        pipeline = createPipeline(state, shader, pipelineLayout, false, true);
        bakedPipeline = createPipeline(state, bakedShader, pipelineLayout, true, true);
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final int width = Math.min(window.getWidth(), state.realSize.width());
        final int height = Math.min(window.getHeight(), state.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Last read by the resolve, a frame or more ago
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
            for (int i = 0; i < 2; ++i) {
                SceneRender.imageBarrier(
                        barriers.get(i),
                        i == 0 ? frameData.oitAccum.texture : frameData.oitExtinction.texture,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_NONE,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT
                                | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_IMAGE_LAYOUT_UNDEFINED,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            // Cleared to nothing in front of the scene
            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(2, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(frameData.oitAccum.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachments
                    .get(1)
                    .sType$Default()
                    .imageView(frameData.oitExtinction.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            // The opaque scene's depth, read only, as the light and skybox stages left it
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(frameData.gBuffer.depth().view)
                            .imageLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_NONE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);
            if (width > 0 && height > 0) {
                setViewport(commandBuffer, width, height, stack);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(
                                lightRender.getDescriptorSet(state.frameIndex),
                                state.bindlessTextures.getDescriptorSet(),
                                sceneRender.getDescriptorSet(state.frameIndex),
                                state.oitVolume.getReadSet()),
                        null);
                ByteBuffer constants = stack.calloc(ShaderBindings.Translucent.PUSH_CONSTANTS_SIZE);
                constants.putFloat(
                        ShaderBindings.Translucent.PUSH_CONSTANT_SCREEN_SIZE_OFFSET, width);
                constants.putFloat(
                        ShaderBindings.Translucent.PUSH_CONSTANT_SCREEN_SIZE_OFFSET + Float.BYTES,
                        height);
                constants.putFloat(
                        ShaderBindings.Translucent.PUSH_CONSTANT_LOG_SCALE_OFFSET,
                        VoxelOitMath.logScale(scene.getViewDistance()));
                constants.putInt(
                        ShaderBindings.Translucent.PUSH_CONSTANT_VOXEL_WEIGHTS_OFFSET,
                        frameData.voxelTransparency ? 1 : 0);
                constants.putFloat(
                        ShaderBindings.Translucent.PUSH_CONSTANT_TILES_OFFSET,
                        VoxelOitMath.tiles(width));
                constants.putFloat(
                        ShaderBindings.Translucent.PUSH_CONSTANT_TILES_OFFSET + Float.BYTES,
                        VoxelOitMath.tiles(height));
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
                drawList(commandBuffer, state, frameData, pipeline, bakedPipeline, stack);
            }
            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Set the viewport and scissor to an area, flipping y with a negative height, as the scene
     * stage does.
     *
     * @param commandBuffer The command buffer.
     * @param width The width drawn, in pixels.
     * @param height The height drawn, in pixels.
     * @param stack The stack to allocate on.
     */
    static void setViewport(
            @NonNull VkCommandBuffer commandBuffer,
            int width,
            int height,
            @NonNull MemoryStack stack) {
        VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
        viewports.get(0).x(0).y(height).width(width).height(-height).minDepth(0).maxDepth(1);
        vkCmdSetViewport(commandBuffer, 0, viewports);
        VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
        scissors.get(0).extent().set(width, height);
        vkCmdSetScissor(commandBuffer, 0, scissors);
    }

    /**
     * Draw the translucent list, kind by kind, with the descriptor sets and push constants already
     * bound.
     *
     * @param commandBuffer The command buffer, inside the rendering.
     * @param state The Vulkan state.
     * @param frameData This frame's data.
     * @param pipeline The pipeline for meshes in the full vertex format.
     * @param bakedPipeline The pipeline for baked sections.
     * @param stack The stack to allocate on.
     */
    static void drawList(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            long pipeline,
            long bakedPipeline,
            @NonNull MemoryStack stack) {
        LongBuffer vertexBuffers = stack.callocLong(1);
        LongBuffer vertexOffsets = stack.callocLong(1);
        for (MeshKind kind : KINDS) {
            final int slots =
                    InstanceDrawUpdate.slotsOf(
                            kind, frameData.meshSlotCount, frameData.bakedSlotCount);
            if (slots == 0) {
                continue;
            }
            vkCmdBindPipeline(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    kind.isBaked() ? bakedPipeline : pipeline);
            final GeometryArena arena = state.arenaFor(kind);
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vertexBuffers.put(0, arena.getVertices().buffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
            vkCmdDrawIndexedIndirect(
                    commandBuffer,
                    frameData.sceneDrawCommands.buffer,
                    (long)
                                    InstanceDrawUpdate.commandIndex(
                                            kind,
                                            InstanceDrawUpdate.LIST_TRANSLUCENT,
                                            0,
                                            frameData.meshSlotCount,
                                            frameData.bakedSlotCount)
                            * InstanceDrawUpdate.DRAW_COMMAND_SIZE,
                    slots,
                    InstanceDrawUpdate.DRAW_COMMAND_SIZE);
        }
    }

    /**
     * Create a pipeline that draws the translucent list.
     *
     * @param state The Vulkan state.
     * @param shader The shaders.
     * @param pipelineLayout The VkPipelineLayout.
     * @param baked Whether it reads the baked vertex format.
     * @param accumulate True to add up into the transparency targets, depth tested against the
     *     opaque scene, false to draw with no attachments at all, like the splat into the volume.
     * @return The VkPipeline.
     */
    static long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            long pipelineLayout,
            boolean baked,
            boolean accumulate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(baked ? BakedVertex.SIZE : MeshData.VERTEX_SIZE_IN_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkPipelineVertexInputStateCreateInfo vertexInputState =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(vertexBindings)
                            .pVertexAttributeDescriptions(
                                    SceneRender.vertexAttributes(baked, stack));
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
                                    stack.ints(
                                            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            // In front of the opaque scene only, without hiding each other
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

            // Every layer adds on, so the order doesn't matter
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

            // Both sides of a pane show
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
                    .stageCount(shader.shaderModules.length)
                    .pStages(shader.shaderStages)
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
}
