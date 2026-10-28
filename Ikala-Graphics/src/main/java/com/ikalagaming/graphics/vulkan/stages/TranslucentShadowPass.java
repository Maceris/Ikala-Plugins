package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Draws see-through surfaces into each sun cascade's translucent maps, every frame, for shadows
 * through glass, see {@code translucent_shadow.glsl}: the translucent meshes culling listed for any
 * cascade, standard and baked, blended by adding into the moments of their depths and their
 * absorbance. Drawn whole each time with the cascade's matrix, off its edges clipped away; the maps
 * are left read only for the light stage. Part of the shadow stage, with its pipeline layout,
 * descriptor sets and push constants.
 */
final class TranslucentShadowPass {

    /** Where the texture coordinates are in a standard vertex: after its position and normal. */
    private static final int STANDARD_UV_OFFSET = 4 * 3 * Float.BYTES;

    /** The shader for standard meshes. */
    @NonNull private final ShaderVulkan standardShader;

    /** The shader for baked sections' translucent meshes. */
    @NonNull private final ShaderVulkan bakedShader;

    /** The pipelines, VK_NULL_HANDLE until set up. */
    private long standardPipeline = VK_NULL_HANDLE;

    private long bakedPipeline = VK_NULL_HANDLE;

    /**
     * Set up the pass.
     *
     * @param standardShader The shader for standard meshes.
     * @param bakedShader The shader for baked sections' translucent meshes.
     */
    TranslucentShadowPass(@NonNull ShaderVulkan standardShader, @NonNull ShaderVulkan bakedShader) {
        this.standardShader = standardShader;
        this.bakedShader = bakedShader;
    }

    /**
     * Create the pipelines.
     *
     * @param state The Vulkan state.
     * @param layout The shadow stage's pipeline layout.
     */
    void initialize(@NonNull VulkanState state, long layout) {
        standardPipeline = createPipeline(state, standardShader, layout, false);
        bakedPipeline = createPipeline(state, bakedShader, layout, true);
    }

    /**
     * Destroy the pipelines.
     *
     * @param state The Vulkan state.
     */
    void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, standardPipeline, null);
        vkDestroyPipeline(state.device.logical, bakedPipeline, null);
        standardPipeline = VK_NULL_HANDLE;
        bakedPipeline = VK_NULL_HANDLE;
    }

    /**
     * Draw one cascade's translucent maps.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param cascade Which cascade.
     * @param layout The shadow stage's pipeline layout.
     * @param descriptorSet The shadow stage's descriptor set for this frame.
     * @param pushConstants The push constants, with the cascade's matrix filled out.
     * @param stack The stack to allocate on.
     */
    void render(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            int cascade,
            long layout,
            long descriptorSet,
            @NonNull ByteBuffer pushConstants,
            @NonNull MemoryStack stack) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final TextureInfoVulkan moments = frameData.cascadeTranslucentMoments[cascade];
        final TextureInfoVulkan absorbance = frameData.cascadeTranslucentAbsorbance[cascade];
        final int size = CascadeShadowSplit.TRANSLUCENT_MAP_SIZE;
        // After the light stage and the rest read them last frame; what they held is thrown away
        barrier(
                commandBuffer,
                new TextureInfoVulkan[] {moments, absorbance},
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                stack);

        VkRenderingAttachmentInfo.Buffer attachments = VkRenderingAttachmentInfo.calloc(2, stack);
        final TextureInfoVulkan[] targets = {moments, absorbance};
        for (int i = 0; i < targets.length; ++i) {
            attachments
                    .get(i)
                    .sType$Default()
                    .imageView(targets[i].view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            attachments.get(i).clearValue().color().float32(0, 0).float32(1, 0).float32(2, 0);
            attachments.get(i).clearValue().color().float32(3, 0);
        }
        vkCmdBeginRendering(
                commandBuffer,
                VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .renderArea(area -> area.extent().set(size, size))
                        .layerCount(1)
                        .pColorAttachments(attachments));

        if (frameData.meshSlotCount + frameData.bakedSlotCount > 0) {
            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            viewports.get(0).width(size).height(size).minDepth(0).maxDepth(1);
            vkCmdSetViewport(commandBuffer, 0, viewports);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            scissors.get(0).extent().set(size, size);
            vkCmdSetScissor(commandBuffer, 0, scissors);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    layout,
                    0,
                    stack.longs(descriptorSet, state.bindlessTextures.getDescriptorSet()),
                    null);
            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);
            final LongBuffer vertexBuffers = stack.callocLong(1);
            final LongBuffer vertexOffsets = stack.callocLong(1);
            for (MeshKind kind : new MeshKind[] {MeshKind.STANDARD, MeshKind.BAKED_TRANSLUCENT}) {
                final int slots =
                        InstanceDrawUpdate.slotsOf(
                                kind, frameData.meshSlotCount, frameData.bakedSlotCount);
                if (slots == 0) {
                    continue;
                }
                vkCmdBindPipeline(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        kind == MeshKind.STANDARD ? standardPipeline : bakedPipeline);
                final GeometryArena arena = state.arenaFor(kind);
                vkCmdBindIndexBuffer(
                        commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
                vertexBuffers.put(0, arena.getVertices().buffer);
                vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
                vkCmdDrawIndexedIndirect(
                        commandBuffer,
                        frameData.sceneDrawCommands.buffer,
                        (long)
                                        InstanceDrawUpdate.commandIndex(
                                                kind,
                                                InstanceDrawUpdate.LIST_TRANSLUCENT_SHADOW,
                                                0,
                                                frameData.meshSlotCount,
                                                frameData.bakedSlotCount)
                                * InstanceDrawUpdate.DRAW_COMMAND_SIZE,
                        slots,
                        InstanceDrawUpdate.DRAW_COMMAND_SIZE);
            }
        }
        vkCmdEndRendering(commandBuffer);

        // For the light stage, the transparency, fog and particles to read
        barrier(
                commandBuffer,
                targets,
                VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                stack);
    }

    /**
     * A layout transition of the maps.
     *
     * @param commandBuffer The command buffer.
     * @param maps The maps.
     * @param srcStage The source stages.
     * @param srcAccess The source accesses.
     * @param dstStage The destination stages.
     * @param dstAccess The destination accesses.
     * @param oldLayout The layout to leave.
     * @param newLayout The layout to take.
     * @param stack The stack to allocate on.
     */
    private static void barrier(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan[] maps,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            int oldLayout,
            int newLayout,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(maps.length, stack);
        for (int i = 0; i < maps.length; ++i) {
            SceneRender.imageBarrier(
                    barriers.get(i),
                    maps[i].texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
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

    /**
     * Create a pipeline: the position, texture coordinates and, for baked meshes, the material of
     * each vertex; both sides of every surface, as the transparent stage draws them; no depth; both
     * maps blended by adding.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param layout The shadow stage's pipeline layout.
     * @param baked Whether it reads baked vertices.
     * @return The VkPipeline.
     */
    private static long createPipeline(
            @NonNull VulkanState state, @NonNull ShaderVulkan shader, long layout, boolean baked) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(baked ? 3 : 2, stack);
            attributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(baked ? VK_FORMAT_R16G16B16A16_SINT : VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(baked ? BakedVertex.POSITION_OFFSET : 0);
            attributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(baked ? VK_FORMAT_R16G16_SFLOAT : VK_FORMAT_R32G32_SFLOAT)
                    .offset(baked ? BakedVertex.UV_OFFSET : STANDARD_UV_OFFSET);
            if (baked) {
                attributes
                        .get(2)
                        .binding(0)
                        .location(2)
                        .format(VK_FORMAT_R16G16_UINT)
                        .offset(BakedVertex.MATERIAL_OFFSET);
            }
            VkVertexInputBindingDescription.Buffer bindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            bindings.get(0)
                    .binding(0)
                    .stride(baked ? BakedVertex.SIZE : MeshData.VERTEX_SIZE_IN_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineColorBlendAttachmentState.Buffer blends =
                    VkPipelineColorBlendAttachmentState.calloc(2, stack);
            for (int i = 0; i < 2; ++i) {
                blends.get(i)
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

            VkGraphicsPipelineCreateInfo.Buffer info =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0)
                    .sType$Default()
                    .pNext(
                            VkPipelineRenderingCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .colorAttachmentCount(2)
                                    .pColorAttachmentFormats(
                                            stack.ints(
                                                    PipelineManagerVulkan
                                                            .TRANSLUCENT_MOMENTS_FORMAT,
                                                    PipelineManagerVulkan
                                                            .TRANSLUCENT_ABSORBANCE_FORMAT)))
                    .pStages(shader.shaderStages)
                    .pVertexInputState(
                            VkPipelineVertexInputStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pVertexBindingDescriptions(bindings)
                                    .pVertexAttributeDescriptions(attributes))
                    .pInputAssemblyState(
                            VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST))
                    .pViewportState(
                            VkPipelineViewportStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .viewportCount(1)
                                    .scissorCount(1))
                    .pRasterizationState(
                            VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .polygonMode(VK_POLYGON_MODE_FILL)
                                    .cullMode(VK_CULL_MODE_NONE)
                                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                                    .lineWidth(1.0f))
                    .pMultisampleState(
                            VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT))
                    .pDepthStencilState(
                            VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default())
                    .pColorBlendState(
                            VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pAttachments(blends))
                    .pDynamicState(
                            VkPipelineDynamicStateCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pDynamicStates(
                                            stack.ints(
                                                    VK_DYNAMIC_STATE_VIEWPORT,
                                                    VK_DYNAMIC_STATE_SCISSOR)))
                    .layout(layout)
                    .renderPass(VK_NULL_HANDLE);
            LongBuffer pipeline = stack.callocLong(1);
            checkError(
                    vkCreateGraphicsPipelines(
                            state.device.logical, VK_NULL_HANDLE, info, null, pipeline));
            return pipeline.get(0);
        }
    }
}
