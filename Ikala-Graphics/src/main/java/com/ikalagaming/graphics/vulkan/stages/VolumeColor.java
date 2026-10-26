package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VolumeBricks;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Ray marches the frame's volume bricks into the transparency targets after the translucent
 * surfaces, see {@code volume_color.frag}: each brick's box from the back, so a pixel marches it
 * once, adding what it scatters toward the camera weighted the same way as the surfaces, so {@link
 * OitResolve} composites both together.
 *
 * <p>Its sets are the light stage's (set 0), the bindless textures (set 1), the scene stage's (set
 * 2), the transparency volume (set 3) and the bricks (set 4).
 */
@Slf4j
public class VolumeColor implements RenderStage {

    /** Vertices of a brick's box: 12 triangles. */
    static final int BOX_VERTICES = 36;

    /** Where the screen size is in the push constants, after the draw list's address. */
    private static final int SCREEN_SIZE_OFFSET = Long.BYTES;

    /** Where the log scale is in the push constants. */
    private static final int LOG_SCALE_OFFSET = SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

    /** Where the voxel weights flag is in the push constants. */
    private static final int VOXEL_WEIGHTS_OFFSET = LOG_SCALE_OFFSET + Float.BYTES;

    /** Where the tiles are in the push constants. */
    private static final int TILES_OFFSET = VOXEL_WEIGHTS_OFFSET + Integer.BYTES;

    /** Where the frame number is in the push constants. */
    private static final int FRAME_OFFSET = TILES_OFFSET + 2 * Float.BYTES;

    /** Where the hand over's start and length are in the push constants. */
    private static final int FADE_OFFSET = FRAME_OFFSET + Integer.BYTES;

    /** Where the nearest solid faces' address is in the push constants, aligned for it. */
    private static final int HITS_OFFSET =
            (FADE_OFFSET + 2 * Float.BYTES + Long.BYTES - 1) / Long.BYTES * Long.BYTES;

    /** Where the debug flag that colors bricks by source is in the push constants. */
    private static final int DEBUG_OFFSET = HITS_OFFSET + Long.BYTES;

    /** The size of the push constants. */
    private static final int CONSTANTS_SIZE = DEBUG_OFFSET + Integer.BYTES;

    /** The shaders. */
    @NonNull private final ShaderVulkan shader;

    /** The scene stage, whose descriptor sets hold the matrices. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, whose descriptor sets hold the lights and shadows. */
    @NonNull private final LightRender lightRender;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The vertex and fragment shaders.
     * @param sceneRender The scene stage, whose descriptor sets this reads.
     * @param lightRender The light stage, whose descriptor sets this reads.
     */
    public VolumeColor(
            @NonNull ShaderVulkan shader,
            @NonNull SceneRender sceneRender,
            @NonNull LightRender lightRender) {
        this.shader = shader;
        this.sceneRender = sceneRender;
        this.lightRender = lightRender;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing volume color");
        pipelineLayout =
                createLayout(
                        state,
                        CONSTANTS_SIZE,
                        lightRender.getDescriptorSetLayout(),
                        state.bindlessTextures.getDescriptorSetLayout(),
                        sceneRender.getDescriptorSetLayout(),
                        state.oitVolume.getReadSetLayout(),
                        state.volumeBricks.getSetLayout(),
                        state.environment.getSetLayout(),
                        state.fogVolume.getReadSetLayout());
        pipeline = createPipeline(state, shader, pipelineLayout, true);
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
            RenderConfig renderConfig) {
        final VolumeBricks bricks = state.volumeBricks;
        final int count = bricks.drawCount(state.frameIndex);
        final int width = Math.min(sceneTarget.width(), state.realSize.width());
        final int height = Math.min(sceneTarget.height(), state.realSize.height());
        if (scene == null || count == 0 || width <= 0 || height <= 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The translucent surfaces were just added
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(
                            VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT
                                    | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(2, stack);
            for (int i = 0; i < 2; ++i) {
                colorAttachments
                        .get(i)
                        .sType$Default()
                        .imageView(i == 0 ? frameData.oitAccum.view : frameData.oitExtinction.view)
                        .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            }
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments);
            vkCmdBeginRendering(commandBuffer, renderingInfo);
            TranslucentRender.setViewport(commandBuffer, width, height, stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(
                            lightRender.getDescriptorSet(state.frameIndex),
                            state.bindlessTextures.getDescriptorSet(),
                            sceneRender.getDescriptorSet(state.frameIndex),
                            state.oitVolume.getReadSet(),
                            bricks.getSet(),
                            state.environment.getSet(),
                            state.fogVolume.getReadSet()),
                    null);
            final ByteBuffer constants = stack.calloc(CONSTANTS_SIZE);
            constants.putLong(0, bricks.drawList(state.frameIndex));
            constants.putFloat(SCREEN_SIZE_OFFSET, width);
            constants.putFloat(SCREEN_SIZE_OFFSET + Float.BYTES, height);
            constants.putFloat(LOG_SCALE_OFFSET, VoxelOitMath.logScale(scene.getViewDistance()));
            constants.putInt(VOXEL_WEIGHTS_OFFSET, frameData.voxelTransparency ? 1 : 0);
            constants.putFloat(TILES_OFFSET, VoxelOitMath.tiles(width));
            constants.putFloat(TILES_OFFSET + Float.BYTES, VoxelOitMath.tiles(height));
            constants.putInt(FRAME_OFFSET, (int) state.frameNumber);
            constants.putFloat(
                    FADE_OFFSET,
                    VolumeMath.fadeStart(GraphicsManager.getSettings().volumeDistance));
            constants.putFloat(FADE_OFFSET + Float.BYTES, (float) VolumeMath.FADE_LENGTH);
            constants.putLong(
                    HITS_OFFSET,
                    bricks.hits(state.frameIndex, state, (long) width * height).deviceAddress);
            constants.putInt(DEBUG_OFFSET, scene.getDebugVisualizers().isVolumeSources() ? 1 : 0);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                    0,
                    constants);
            vkCmdDraw(commandBuffer, BOX_VERTICES, count, 0, 0);
            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * A pipeline layout for drawing bricks, its push constants seen by both shaders.
     *
     * @param state The Vulkan state.
     * @param constantsSize The size of the push constants.
     * @param setLayouts The sets' layouts, in order.
     * @return The VkPipelineLayout.
     */
    static long createLayout(@NonNull VulkanState state, int constantsSize, long... setLayouts) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(constantsSize);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(stack.longs(setLayouts))
                                    .pPushConstantRanges(ranges),
                            null,
                            longOutput));
            return longOutput.get(0);
        }
    }

    /**
     * Create a pipeline that draws bricks' boxes from the back, with no vertex input and no depth
     * test: the march stops at the opaque scene itself.
     *
     * @param state The Vulkan state.
     * @param shader The shaders.
     * @param pipelineLayout The VkPipelineLayout.
     * @param accumulate True to add up into the transparency targets, false to draw with no
     *     attachments at all, like the splat into the volume.
     * @return The VkPipeline.
     */
    static long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            long pipelineLayout,
            boolean accumulate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
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
                                    stack.ints(
                                            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(false)
                            .depthWriteEnable(false);
            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack).sType$Default();
            if (accumulate) {
                renderingCreateInfo.pColorAttachmentFormats(
                        stack.ints(
                                PipelineManagerVulkan.OIT_ACCUM_FORMAT,
                                PipelineManagerVulkan.OIT_EXTINCTION_FORMAT));
            }
            // Every brick adds on, so the order doesn't matter
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
            // Only the back of each box, wound like the scene's, so each pixel marches it once
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_FRONT_BIT)
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
