package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VolumeBricks;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Writes the depth of the first solid face the frame's volume bricks show at each pixel into the
 * scene's depth, depth tested against the opaque scene, see {@code volume_depth.frag}: TAA then
 * follows the blocks the bricks stand in for, and translucent surfaces behind them are hidden. Also
 * fills each pixel's nearest solid face's distance, which {@link VolumeSplat} and {@link
 * VolumeColor} stop at, so a brick behind another's solid face adds nothing.
 *
 * <p>Runs before the transparency stages. Its sets are the light stage's (set 0), the bindless
 * textures (set 1), the scene stage's (set 2) and the bricks (set 3).
 */
@Slf4j
public class VolumeDepth implements RenderStage {

    /** Where the hit buffer's address is in the push constants, after the draw list's. */
    private static final int HITS_OFFSET = Long.BYTES;

    /** Where the hit buffer's width is in the push constants. */
    private static final int HIT_WIDTH_OFFSET = HITS_OFFSET + Long.BYTES;

    /** Where the frame number is in the push constants. */
    private static final int FRAME_OFFSET = HIT_WIDTH_OFFSET + Integer.BYTES;

    /** Where the hand over's start and length are in the push constants. */
    private static final int FADE_OFFSET = FRAME_OFFSET + Integer.BYTES;

    /** Where the drawn area's height is in the push constants. */
    private static final int SCREEN_HEIGHT_OFFSET = FADE_OFFSET + 2 * Float.BYTES;

    /** The size of the push constants. */
    private static final int CONSTANTS_SIZE = SCREEN_HEIGHT_OFFSET + Float.BYTES;

    /** What the hits are cleared to: the largest float's bits, for no solid face. */
    private static final int NO_HIT = Float.floatToRawIntBits(Float.MAX_VALUE);

    /** The shaders. */
    @NonNull private final ShaderVulkan shader;

    /** The scene stage, whose descriptor sets hold the matrices. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, whose descriptor sets hold the view. */
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
    public VolumeDepth(
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
        log.debug("Initializing volume depth");
        pipelineLayout =
                VolumeColor.createLayout(
                        state,
                        CONSTANTS_SIZE,
                        lightRender.getDescriptorSetLayout(),
                        state.bindlessTextures.getDescriptorSetLayout(),
                        sceneRender.getDescriptorSetLayout(),
                        state.volumeBricks.getSetLayout());
        pipeline = createPipeline(state);
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
        final VolumeBricks bricks = state.volumeBricks;
        final int count = bricks.drawCount(state.frameIndex);
        final int width = Math.min(sceneTarget.width(), state.realSize.width());
        final int height = Math.min(sceneTarget.height(), state.realSize.height());
        if (scene == null || count == 0 || width <= 0 || height <= 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final SharedBuffer hits = bricks.hits(state.frameIndex, state, (long) width * height);
        final long depthImage = frameData.gBuffer.depth().texture;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // No solid face anywhere yet
            VkMemoryBarrier2.Buffer memory = VkMemoryBarrier2.calloc(1, stack);
            memory.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                    .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(memory));
            vkCmdFillBuffer(
                    commandBuffer, hits.buffer, 0, (long) width * height * Integer.BYTES, NO_HIT);
            memory.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(
                            VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
            // The depth, read only since the light stage, is written again
            VkImageMemoryBarrier2.Buffer image = VkImageMemoryBarrier2.calloc(1, stack);
            SceneRender.imageBarrier(
                    image.get(0),
                    depthImage,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack)
                            .sType$Default()
                            .pMemoryBarriers(memory)
                            .pImageMemoryBarriers(image));

            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(frameData.gBuffer.depth().view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment);
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
                            bricks.getSet()),
                    null);
            final ByteBuffer constants = stack.calloc(CONSTANTS_SIZE);
            constants.putLong(0, bricks.drawList(state.frameIndex));
            constants.putLong(HITS_OFFSET, hits.deviceAddress);
            constants.putInt(HIT_WIDTH_OFFSET, width);
            constants.putInt(FRAME_OFFSET, (int) state.frameNumber);
            constants.putFloat(
                    FADE_OFFSET,
                    VolumeMath.fadeStart(GraphicsManager.getSettings().volumeDistance));
            constants.putFloat(FADE_OFFSET + Float.BYTES, (float) VolumeMath.FADE_LENGTH);
            constants.putFloat(SCREEN_HEIGHT_OFFSET, height);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                    0,
                    constants);
            vkCmdDraw(commandBuffer, VolumeColor.BOX_VERTICES, count, 0, 0);
            vkCmdEndRendering(commandBuffer);

            // Read only again for the stages after, and the hits for the splat and color passes
            memory.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
            SceneRender.imageBarrier(
                    image.get(0),
                    depthImage,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack)
                            .sType$Default()
                            .pMemoryBarriers(memory)
                            .pImageMemoryBarriers(image));
        }
    }

    /**
     * Create the pipeline: bricks' boxes from the front, no vertex input, depth tested and written
     * with the depth the shader finds, no color.
     *
     * @param state The Vulkan state.
     * @return The VkPipeline.
     */
    private long createPipeline(@NonNull VulkanState state) {
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
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS);
            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();
            // Only the front of each box, so each pixel walks it once, and the depth test can drop
            // boxes hidden before the shader runs: bricks are never around the camera
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_BACK_BIT)
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
