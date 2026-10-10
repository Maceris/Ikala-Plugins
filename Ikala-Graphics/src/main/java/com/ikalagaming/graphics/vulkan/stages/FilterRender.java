package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Runs a post-processing filter over the lit scene, from the pre-filter image to the final image.
 * Unlike OpenGL, filters can also read the g-buffer through the bindless texture array, along with
 * the camera matrices, which helps with screen space effects. See the default filter shader for the
 * inputs. There is a pipeline for each {@link FilterView}, so the debug views can be swapped in
 * without rebuilding anything.
 */
@Slf4j
public class FilterRender implements RenderStage {

    /** The shader for each view. */
    @NonNull private final Map<FilterView, ShaderVulkan> shaders;

    /** What to show, picks the pipeline we render with. */
    @NonNull @Getter @Setter private FilterView view;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer for each view, by ordinal. VK_NULL_HANDLE if not set up. */
    private final long[] pipelines;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** The VkDescriptorSet for each frame in flight, VK_NULL_HANDLE if not set up. */
    private final long[] descriptorSets;

    /**
     * The VkImageView of the screen texture each frame's descriptor set points at, so we can
     * rewrite them when the images are recreated after a resize.
     */
    private final long[] writtenViews;

    /**
     * Set up the filter render stage.
     *
     * @param shaders The shader for each view, which must include every view.
     * @param quadMesh The mesh to render onto.
     */
    public FilterRender(
            final @NonNull Map<FilterView, ShaderVulkan> shaders,
            final @NonNull QuadMesh quadMesh) {
        for (FilterView filterView : FilterView.values()) {
            if (!shaders.containsKey(filterView)) {
                throw new IllegalArgumentException("Missing filter shader for " + filterView);
            }
        }
        this.shaders = new EnumMap<>(shaders);
        this.view = FilterView.DEFAULT;
        this.quadMesh = quadMesh;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipelines = new long[FilterView.values().length];
        Arrays.fill(pipelines, VK_NULL_HANDLE);
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenViews = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing filter render");
        createPipelineLayout(vulkanState);
        for (FilterView filterView : FilterView.values()) {
            pipelines[filterView.ordinal()] = createPipeline(vulkanState, shaders.get(filterView));
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        Arrays.fill(writtenViews, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        for (int i = 0; i < pipelines.length; i++) {
            vkDestroyPipeline(vulkanState.device.logical, pipelines[i], null);
            pipelines[i] = VK_NULL_HANDLE;
        }
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
        final TextureInfoVulkan source = frameData.preFilterTexture;
        final TextureInfoVulkan target = frameData.finalTexture;
        final boolean hasScene = RenderConfig.hasSceneStage(renderConfig);
        final boolean sourceRendered = hasScene || RenderConfig.hasSkyboxStage(renderConfig);

        final int width = Math.min(window.getWidth(), vulkanState.realSize.width());
        final int height = Math.min(window.getHeight(), vulkanState.realSize.height());

        updateUniforms(scene, vulkanState, frameData, width, height, hasScene);
        updateScreenTexture(vulkanState, source);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!sourceRendered) {
                // Like OpenGL, the filter sees a cleared image if nothing rendered to it
                clearSource(commandBuffer, source, stack);
            }

            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
            if (sourceRendered) {
                SceneRender.imageBarrier(
                        barriers.get(0),
                        source.texture,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            }
            // Last used by the blit to the swapchain, a frame or more ago
            SceneRender.imageBarrier(
                    barriers.get(sourceRendered ? 1 : 0),
                    target.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            barriers.limit(sourceRendered ? 2 : 1);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            // Cleared to transparent black and alpha blended, like OpenGL
            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(target.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            if (width > 0 && height > 0) {
                vkCmdBindPipeline(
                        commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelines[view.ordinal()]);

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
                        stack.longs(
                                descriptorSets[vulkanState.frameIndex],
                                vulkanState.bindlessTextures.getDescriptorSet()),
                        null);
                vkCmdBindVertexBuffers(
                        commandBuffer,
                        0,
                        stack.longs(quadMesh.vertexBuffer().buffer),
                        stack.longs(0));
                vkCmdBindIndexBuffer(
                        commandBuffer, quadMesh.indexBuffer().buffer, 0, VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexed(commandBuffer, QuadMesh.INDEX_COUNT, 1, 0, 0, 0);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Clear the source image and leave it ready to sample, for when nothing rendered to it.
     *
     * @param commandBuffer The command buffer to record into.
     * @param source The pre-filter image.
     * @param stack The stack to allocate on.
     */
    private static void clearSource(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan source,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier);
        SceneRender.imageBarrier(
                barrier.get(0),
                source.texture,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);

        VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
        range.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdClearColorImage(
                commandBuffer,
                source.texture,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VkClearColorValue.calloc(stack),
                range);

        SceneRender.imageBarrier(
                barrier.get(0),
                source.texture,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_CLEAR_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Write the uniforms for the current frame.
     *
     * @param scene The scene.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     * @param width The width of the screen in pixels.
     * @param height The height of the screen in pixels.
     * @param hasScene Whether the scene stage drew the g-buffer this frame.
     */
    private static void updateUniforms(
            @NonNull Scene scene,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            int width,
            int height,
            boolean hasScene) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.filterUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.Filter.UNIFORMS_BUFFER_SIZE);

        scene.getProjection()
                .getProjectionMatrix()
                .get(ShaderBindings.Filter.PROJECTION_MATRIX_OFFSET, uniformData);
        scene.getProjection()
                .getInverseProjectionMatrix()
                .get(ShaderBindings.Filter.INVERSE_PROJECTION_MATRIX_OFFSET, uniformData);
        scene.getCamera()
                .getViewMatrix()
                .get(ShaderBindings.Filter.VIEW_MATRIX_OFFSET, uniformData);
        scene.getCamera()
                .getInvViewMatrix()
                .get(ShaderBindings.Filter.INVERSE_VIEW_MATRIX_OFFSET, uniformData);

        uniformData.putFloat(ShaderBindings.Filter.SCREEN_SIZE_OFFSET, width);
        uniformData.putFloat(ShaderBindings.Filter.SCREEN_SIZE_OFFSET + Float.BYTES, height);
        uniformData.putFloat(
                ShaderBindings.Filter.UV_SCALE_OFFSET, (float) width / state.realSize.width());
        uniformData.putFloat(
                ShaderBindings.Filter.UV_SCALE_OFFSET + Float.BYTES,
                (float) height / state.realSize.height());

        uniformData.putInt(ShaderBindings.Filter.HAS_GBUFFER_OFFSET, hasScene ? 1 : 0);
        TextureInfoVulkan[] gBuffer = frameData.gBuffer.textures();
        for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
            uniformData.putInt(
                    ShaderBindings.Filter.BASE_COLOR_INDEX_OFFSET + i * Integer.BYTES,
                    gBuffer[i].bindlessIndex);
        }
        uniformData.putInt(
                ShaderBindings.Filter.DEPTH_INDEX_OFFSET, frameData.gBuffer.depth().bindlessIndex);
    }

    /**
     * Point this frame's descriptor set at the screen texture, if it changed since we last wrote
     * it. This frame's set is not in use by the GPU, so it can be updated directly.
     *
     * @param state The Vulkan state.
     * @param source The pre-filter image.
     */
    private void updateScreenTexture(
            @NonNull VulkanState state, @NonNull TextureInfoVulkan source) {
        if (writtenViews[state.frameIndex] == source.view) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo
                    .get(0)
                    .imageLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL)
                    .imageView(source.view)
                    .sampler(source.sampler);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(descriptorSets[state.frameIndex])
                    .dstBinding(ShaderBindings.Filter.SCREEN_TEXTURE)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
        writtenViews[state.frameIndex] = source.view;
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Filter.SCREEN_TEXTURE)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(1)
                    .binding(ShaderBindings.Filter.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

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
                            .pSetLayouts(
                                    stack.longs(
                                            descriptorSetLayout,
                                            state.bindlessTextures.getDescriptorSetLayout()));
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
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

            // The uniform buffers never change, so write them once. The screen texture is
            // written when rendering, since it changes when the window grows.
            VkWriteDescriptorSet.Buffer writes =
                    VkWriteDescriptorSet.calloc(GraphicsManager.MAX_FRAMES_IN_FLIGHT, stack);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfo
                        .get(0)
                        .buffer(state.perFrameData[i].filterUniforms.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writes.get(i)
                        .sType$Default()
                        .dstSet(descriptorSets[i])
                        .dstBinding(ShaderBindings.Filter.UNIFORMS_BINDING)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .pBufferInfo(bufferInfo);
            }
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
    }

    /**
     * Create a pipeline that runs the given filter shader.
     *
     * @param state The Vulkan state.
     * @param shader The filter shader.
     * @return The VkPipeline.
     */
    private long createPipeline(@NonNull VulkanState state, @NonNull ShaderVulkan shader) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Matches the QuadMesh layout
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            // Texture coordinates
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(3 * Float.BYTES);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride((3 + 2) * Float.BYTES)
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

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .pColorAttachmentFormats(
                                    stack.ints(PipelineManagerVulkan.SCREEN_FORMAT));

            // OpenGL alpha blends onto the cleared back buffer, for the alpha channel too
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
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
