package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.QuadMesh;
import com.ikalagaming.graphics.backend.vulkan.ShaderBindings;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/** Handles post-processing filters. */
@Slf4j
public class FilterRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The source texture for the filter. */
    @Setter @NonNull private Framebuffer sceneTexture;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    // TODO(ches) we can reuse this pipeline layout for all the filters
    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    private LongBuffer texture;

    /**
     * Set up the skybox render stage.
     *
     * @param shader The shader to use for rendering.
     * @param sceneTexture The destination framebuffer to render to.
     */
    public FilterRender(
            final @NonNull ShaderVulkan shader,
            @NonNull final Framebuffer sceneTexture,
            @NonNull final QuadMesh quadMesh) {
        this.shader = shader;
        this.sceneTexture = sceneTexture;
        texture = LongBuffer.allocate(1);
        this.quadMesh = quadMesh;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();

        // TODO(ches) remove this when we don't have the nothingburger state?
        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];

        uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            viewports
                    .get(0)
                    .width(window.getWidth())
                    .height(window.getHeight())
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            vkCmdSetViewport(commandBuffer, 0, viewports);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            scissors.get(0)
                    .extent(
                            VkExtent2D.calloc(stack)
                                    .width(window.getWidth())
                                    .height(window.getHeight()));
            vkCmdSetScissor(commandBuffer, 0, scissors);

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    texture,
                    null);
            LongBuffer vertices = stack.longs(quadMesh.vertexBuffer().buffer);
            LongBuffer offsets = stack.longs(0);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertices, offsets);
            vkCmdBindIndexBuffer(
                    commandBuffer, quadMesh.indexBuffer().buffer, 0, VK_INDEX_TYPE_UINT32);

            vkCmdDrawIndexed(commandBuffer, QuadMesh.INDEX_COUNT, 1, 0, 0, 0);
        }
        // TODO(ches) render

        shader.unbind();
    }

    @Override
    public void initialize(@NonNull State state) {
        log.debug("Initializing filter render");
        VulkanState vulkanState = (VulkanState) state;
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
        texture.put(0, sceneTexture.textures()[0]);
    }

    @Override
    public void cleanup(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .size(Long.BYTES);

            IntBuffer descriptorVariableFlags = stack.ints(0);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(1)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Filter.SCREEN_TEXTURE)
                    .binding(ShaderBindings.Filter.SCREEN_TEXTURE)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(descriptorSetBindingFlags)
                            .pBindings(descriptorSetLayoutBindings)
                            .flags(0);

            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            LongBuffer descriptorSetLayoutAddress = stack.longs(descriptorSetLayout);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutCreateInfo
                    .sType$Default()
                    .setLayoutCount(1)
                    .pSetLayouts(descriptorSetLayoutAddress)
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

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);

            int offset = 0;
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(offset);
            offset += 3 * Float.BYTES;

            // Texture coordinates
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.get(0).binding(0).stride(offset).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

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

            IntBuffer dynamicStates =
                    stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(dynamicStates);

            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(false)
                            .depthWriteEnable(false)
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            IntBuffer imageFormat = stack.ints(VK_FORMAT_R8G8B8A8_SRGB);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(imageFormat)
                            .depthAttachmentFormat(VK_FORMAT_UNDEFINED);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);

            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .attachmentCount(1)
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
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

            pipeline = longOutput.get(0);
        }
    }
}
