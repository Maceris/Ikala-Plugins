package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import javax.annotation.Nullable;

/**
 * Pipelines and draws for stages that run a fragment shader over the whole screen with a {@link
 * QuadMesh}, like lighting, tone mapping and resolves.
 */
public final class FullScreenPass {

    /**
     * How a pass's output is blended onto what the target holds.
     *
     * @param source The VkBlendFactor for the shader's output.
     * @param destination The VkBlendFactor for what the target holds.
     */
    public record Blend(int source, int destination) {}

    /**
     * Create a pipeline that draws a quad mesh over the screen.
     *
     * @param state The Vulkan state.
     * @param shader The vertex and fragment shaders.
     * @param pipelineLayout The VkPipelineLayout.
     * @param colorFormat The VkFormat of the one color target.
     * @param blend How to blend the output onto the target, or null to replace it.
     * @return The VkPipeline.
     */
    public static long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            long pipelineLayout,
            int colorFormat,
            @Nullable Blend blend) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Matches the QuadMesh layout: positions, then texture coordinates
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
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
            VkPipelineVertexInputStateCreateInfo vertexInputState =
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
                            .pColorAttachmentFormats(stack.ints(colorFormat));

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .blendEnable(blend != null)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            if (blend != null) {
                blendAttachments
                        .get(0)
                        .srcColorBlendFactor(blend.source())
                        .dstColorBlendFactor(blend.destination())
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(blend.source())
                        .dstAlphaBlendFactor(blend.destination())
                        .alphaBlendOp(VK_BLEND_OP_ADD);
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

    /**
     * Set the viewport and scissor to an area from the top left, and draw the quad over it. The
     * pipeline and its descriptor sets must already be bound, inside a rendering block.
     *
     * @param commandBuffer The command buffer.
     * @param quadMesh The quad.
     * @param width The width of the area, in pixels.
     * @param height The height of the area, in pixels.
     * @param stack The stack to allocate on.
     */
    public static void draw(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull QuadMesh quadMesh,
            int width,
            int height,
            @NonNull MemoryStack stack) {
        VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
        viewports.get(0).width(width).height(height).minDepth(0).maxDepth(1);
        vkCmdSetViewport(commandBuffer, 0, viewports);
        VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
        scissors.get(0).extent().set(width, height);
        vkCmdSetScissor(commandBuffer, 0, scissors);
        vkCmdBindVertexBuffers(
                commandBuffer, 0, stack.longs(quadMesh.vertexBuffer().buffer), stack.longs(0));
        vkCmdBindIndexBuffer(commandBuffer, quadMesh.indexBuffer().buffer, 0, VK_INDEX_TYPE_UINT32);
        vkCmdDrawIndexed(commandBuffer, QuadMesh.INDEX_COUNT, 1, 0, 0, 0);
    }

    private FullScreenPass() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
