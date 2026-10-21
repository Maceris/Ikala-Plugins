package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FullScreenPass;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.QuadMesh;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Turns the HDR scene color, in linear light, into the image shown: exposure, tone mapping and the
 * sRGB encode, see {@code tonemap.frag} and {@link com.ikalagaming.graphics.vulkan.ToneMapMath}.
 * Renders into the pre-filter image if there is a filter stage, otherwise the final image, and
 * leaves it as a color attachment for the stages after.
 *
 * <p>With {@link Scene#isAutoExposure()}, the scene color is metered first, see {@link
 * AutoExposure}, and the scene's exposure compensates the one it adapts to.
 */
@Slf4j
public class ToneMap implements RenderStage {

    /** The tone map shaders. */
    @NonNull private final ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /**
     * The automatic exposure. -- GETTER -- The automatic exposure, for the debug window.
     *
     * @return The automatic exposure.
     */
    @Getter @NonNull private final AutoExposure autoExposure;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The tone map shaders.
     * @param exposureShader The automatic exposure compute shader.
     * @param quadMesh The mesh to draw over the screen.
     */
    public ToneMap(
            @NonNull ShaderVulkan shader,
            @NonNull ShaderVulkan exposureShader,
            @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        autoExposure = new AutoExposure(exposureShader);
        this.quadMesh = quadMesh;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing tone mapping");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.ToneMap.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(state.bindlessTextures.getDescriptorSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
        pipeline =
                FullScreenPass.createPipeline(
                        state, shader, pipelineLayout, PipelineManagerVulkan.SCREEN_FORMAT, null);
        autoExposure.initialize(state);
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        autoExposure.cleanup(state);
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            int renderConfig) {
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        // The anti-aliased color if that stage ran, which leaves the scene color ready to read
        final TextureInfoVulkan source =
                frameData.taaApplied ? frameData.taaColor : frameData.sceneColor;
        frameData.taaApplied = false;
        final TextureInfoVulkan target =
                RenderConfig.hasFilterStage(renderConfig)
                        ? frameData.preFilterTexture
                        : frameData.finalTexture;
        final int width = Math.min(sceneTarget.width(), state.realSize.width());
        final int height = Math.min(sceneTarget.height(), state.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
            // The light, skybox and transparency stages drew the scene color
            SceneRender.imageBarrier(
                    barriers.get(0),
                    source.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            // Last used by the blit to the swapchain or by the filter, a frame or more ago
            SceneRender.imageBarrier(
                    barriers.get(1),
                    target.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            // Meter what is about to be shown, outside the render pass
            final boolean auto = scene != null && scene.isAutoExposure();
            if (auto && width > 0 && height > 0) {
                autoExposure.record(commandBuffer, state, source, width, height);
            } else if (!auto) {
                autoExposure.reset();
            }

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
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(state.bindlessTextures.getDescriptorSet()),
                        null);
                ByteBuffer constants = stack.calloc(ShaderBindings.ToneMap.PUSH_CONSTANTS_SIZE);
                constants.putInt(
                        ShaderBindings.ToneMap.PUSH_CONSTANT_SCENE_COLOR_INDEX_OFFSET,
                        source.bindlessIndex);
                constants.putFloat(
                        ShaderBindings.ToneMap.PUSH_CONSTANT_EXPOSURE_OFFSET, scene.getExposure());
                constants.putLong(
                        ShaderBindings.ToneMap.PUSH_CONSTANT_EXPOSURE_STATE_OFFSET,
                        autoExposure.getStateAddress());
                constants.putInt(
                        ShaderBindings.ToneMap.PUSH_CONSTANT_AUTO_EXPOSURE_OFFSET, auto ? 1 : 0);
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
                FullScreenPass.draw(commandBuffer, quadMesh, width, height, stack);
            }
            vkCmdEndRendering(commandBuffer);
        }
    }
}
