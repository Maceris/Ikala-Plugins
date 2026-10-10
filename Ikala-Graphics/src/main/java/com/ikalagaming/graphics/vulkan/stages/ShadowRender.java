package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * Renders the scene depth from the directional light into each cascade shadow map. The shadow maps
 * are left in the read only layout for the light stage.
 */
@Slf4j
public class ShadowRender implements RenderStage {

    /**
     * How far every surface is pushed from the light in the shadow maps, in units of the smallest
     * depth step at its depth. Tiny with a 32-bit float depth buffer; the slope term does the work.
     */
    private static final float DEPTH_BIAS_CONSTANT = 1;

    /**
     * How far surfaces are pushed from the light per unit of their depth slope across a texel, so a
     * surface nearly edge-on to the sun, which spans a wide depth range within one texel, doesn't
     * shadow itself. The light stage also offsets along the normal, see lights.frag.
     */
    private static final float DEPTH_BIAS_SLOPE = 1.5f;

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The shader for baked sections, in the baked vertex format. */
    @NonNull private final ShaderVulkan bakedShader;

    /** VkPipeline for baked sections, will be VK_NULL_HANDLE if not set up. */
    private long bakedPipeline;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** The VkDescriptorSet for each frame in flight, VK_NULL_HANDLE if not set up. */
    private final long[] descriptorSets;

    /**
     * The VkBuffer the model matrix binding of each frame's descriptor set points at, so we know
     * when to rewrite them.
     */
    private final long[][] writtenBuffers;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param bakedShader The shader for baked sections.
     */
    public ShadowRender(
            final @NonNull ShaderVulkan shader, final @NonNull ShaderVulkan bakedShader) {
        this.shader = shader;
        this.bakedShader = bakedShader;
        this.bakedPipeline = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][1];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing shadow render");
        createPipelineLayout(vulkanState);
        pipeline = createPipeline(vulkanState, shader, false);
        bakedPipeline = createPipeline(vulkanState, bakedShader, true);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        // Worked out by the instance draw update stage, since culling needs them first
        CascadeShadowSplit[] cascadeShadowSplits = frameData.cascadeShadowSplits;

        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {frameData.sceneModelMatrices},
                new int[] {ShaderBindings.Shadow.MODEL_MATRICES_BINDING},
                writtenBuffers[vulkanState.frameIndex]);

        final TextureInfoVulkan[] shadowMaps = frameData.cascadeShadows;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The light stage of an earlier frame may still be reading them
            transitionShadowMaps(
                    commandBuffer,
                    shadowMaps,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    stack);

            ByteBuffer pushConstants = stack.calloc(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);
            pushConstants.putLong(
                    ShaderBindings.Shadow.PUSH_CONSTANT_VISIBLE_OFFSET,
                    frameData.visibleInstances.deviceAddress);
            for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
                cascadeShadowSplits[i]
                        .getProjViewMatrix()
                        .get(
                                ShaderBindings.Shadow.PUSH_CONSTANT_PROJECTION_VIEW_MATRIX_OFFSET,
                                pushConstants);
                renderCascade(
                        commandBuffer,
                        vulkanState,
                        shadowMaps[i],
                        InstanceDrawUpdate.LIST_FIRST_CASCADE + i,
                        pushConstants);
            }

            transitionShadowMaps(
                    commandBuffer,
                    shadowMaps,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                    stack);
        }
    }

    /**
     * Draw the scene depth into one cascade's shadow map.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param shadowMap The shadow map to render into, as a depth attachment.
     * @param list Which visible list the cascade is, to find its draw commands.
     * @param pushConstants The push constants, with the cascade's matrix already filled out.
     */
    private void renderCascade(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull TextureInfoVulkan shadowMap,
            int list,
            @NonNull ByteBuffer pushConstants) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final int width = CascadeShadowSplit.SHADOW_MAP_WIDTH;
        final int height = CascadeShadowSplit.SHADOW_MAP_HEIGHT;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(shadowMap.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depthAttachment.clearValue().depthStencil().depth(1.0f);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            if (frameData.meshSlotCount + frameData.bakedSlotCount > 0
                    || !frameData.modelDrawInfo.isEmpty()) {
                // Not flipped, so the light stage can use the shadow map coordinates as is
                VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
                viewports.get(0).width(width).height(height).minDepth(0).maxDepth(1);
                vkCmdSetViewport(commandBuffer, 0, viewports);
                VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
                scissors.get(0).extent().set(width, height);
                vkCmdSetScissor(commandBuffer, 0, scissors);

                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(descriptorSets[state.frameIndex]),
                        null);

                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT,
                        0,
                        pushConstants);
                LongBuffer vertexBuffers = stack.callocLong(1);
                LongBuffer vertexOffsets = stack.callocLong(1);

                // Everything culling handled for this cascade, each kind from its own buffers,
                // one command per mesh slot. Standard last, which the listed models need.
                for (int k = MeshKind.ALL.length - 1; k >= 0; --k) {
                    final MeshKind kind = MeshKind.ALL[k];
                    final int slots =
                            InstanceDrawUpdate.slotsOf(
                                    kind, frameData.meshSlotCount, frameData.bakedSlotCount);
                    if (slots == 0 && kind != MeshKind.STANDARD) {
                        continue;
                    }
                    vkCmdBindPipeline(
                            commandBuffer,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            kind.isBaked() ? bakedPipeline : pipeline);
                    final GeometryArena arena = state.arenaFor(kind);
                    vkCmdBindIndexBuffer(
                            commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
                    vertexBuffers.put(0, arena.getVertices().buffer);
                    vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
                    if (slots == 0) {
                        continue;
                    }
                    vkCmdDrawIndexedIndirect(
                            commandBuffer,
                            frameData.sceneDrawCommands.buffer,
                            (long)
                                            InstanceDrawUpdate.commandIndex(
                                                    kind,
                                                    list,
                                                    0,
                                                    frameData.meshSlotCount,
                                                    frameData.bakedSlotCount)
                                    * InstanceDrawUpdate.DRAW_COMMAND_SIZE,
                            slots,
                            InstanceDrawUpdate.DRAW_COMMAND_SIZE);
                }
                SceneRender.drawListedModels(
                        commandBuffer, state, frameData, vertexBuffers, vertexOffsets);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Record a barrier for all the shadow maps.
     *
     * @param commandBuffer The command buffer to record into.
     * @param shadowMaps The shadow maps.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param oldLayout The layout to transition from.
     * @param newLayout The layout to transition to.
     * @param stack The stack to allocate on.
     */
    private static void transitionShadowMaps(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan[] shadowMaps,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            int oldLayout,
            int newLayout,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers =
                VkImageMemoryBarrier2.calloc(shadowMaps.length, stack);
        for (int i = 0; i < shadowMaps.length; i++) {
            SceneRender.imageBarrier(
                    barriers.get(i),
                    shadowMaps[i].texture,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    srcStage,
                    srcAccess,
                    dstStage,
                    dstAccess,
                    oldLayout,
                    newLayout);
        }
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Shadow.MODEL_MATRICES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            // Each frame has its own set, only updated once the GPU is done with that frame
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindings(bindings);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(stack.longs(descriptorSetLayout))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT)
                            .pPoolSizes(poolSizes);
            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical, descriptorPoolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            LongBuffer setLayouts = stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                setLayouts.put(i, descriptorSetLayout);
            }
            VkDescriptorSetAllocateInfo descriptorSetAlloc =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(setLayouts);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, descriptorSets));
        }
    }

    /**
     * Create a shadow pipeline.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param baked Whether it reads the baked vertex format.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state, @NonNull ShaderVulkan shader, boolean baked) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Only the position is needed, the rest of the vertex is skipped over
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(1, stack);
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(baked ? VK_FORMAT_R16G16B16A16_SINT : VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(baked ? BakedVertex.POSITION_OFFSET : 0);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(baked ? BakedVertex.SIZE : MeshData.VERTEX_SIZE_IN_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineVertexInputStateCreateInfo vertexInputStateCreateInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(vertexBindings)
                            .pVertexAttributeDescriptions(vertexAttributes);

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

            // OpenGL's default depth function
            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);

            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();

            // Back face culling like OpenGL. The viewport isn't flipped here, which flips the
            // winding, so counter-clockwise faces in OpenGL are clockwise here. Depth is pushed
            // away from the light, more on slopes, so lit surfaces don't shadow themselves.
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_BACK_BIT)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .depthBiasEnable(true)
                            .depthBiasConstantFactor(DEPTH_BIAS_CONSTANT)
                            .depthBiasSlopeFactor(DEPTH_BIAS_SLOPE)
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
                    .pVertexInputState(vertexInputStateCreateInfo)
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
