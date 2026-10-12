package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.TaaMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FullScreenPass;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.QuadMesh;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Temporal anti-aliasing: blends each frame, whose pixels the projection's jitter samples a little
 * apart (see {@link TaaMath}), into the history of the frames before, in linear light before tone
 * mapping. The result goes to this frame's {@link PerFrameData#taaColor}, which the tone map stage
 * reads and the next frame takes as history; see {@code taa.frag} for how.
 *
 * <p>History starts over when there is none to read: the first frame, after the screen is resized,
 * after anti-aliasing is turned back on, when the camera jumps, or when the history view is turned
 * on or off.
 */
@Slf4j
public class Taa implements RenderStage {

    /** How far the camera may move in a frame, in meters, before the history starts over. */
    private static final double MAX_CAMERA_MOVE = 2;

    /** The anti-aliasing shaders. */
    @NonNull private final ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** Last frame's projection, without jitter. */
    private final Matrix4f previousProjection = new Matrix4f();

    /** Last frame's view matrix. */
    private final Matrix4f previousView = new Matrix4f();

    /** Where the camera was last frame. */
    private final Vector3d previousCamera = new Vector3d();

    /** Whether last frame wrote a history this one can read. */
    private boolean historyValid;

    /** The size drawn last frame, in pixels. */
    private int previousWidth;

    private int previousHeight;

    /** Whether the history view was on last frame. */
    private boolean viewShownBefore;

    /**
     * Set up the stage.
     *
     * @param shader The anti-aliasing shaders.
     * @param quadMesh The mesh to draw over the screen.
     */
    public Taa(@NonNull ShaderVulkan shader, @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        this.quadMesh = quadMesh;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing temporal anti-aliasing");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.Taa.PUSH_CONSTANTS_SIZE);
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
                        state,
                        shader,
                        pipelineLayout,
                        PipelineManagerVulkan.SCENE_COLOR_FORMAT,
                        null);
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
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        if (!GraphicsManager.getSettings().taa) {
            frameData.taaApplied = false;
            historyValid = false;
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData previousData =
                state.perFrameData[
                        (state.frameIndex + GraphicsManager.MAX_FRAMES_IN_FLIGHT - 1)
                                % GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        final int width = Math.min(window.getWidth(), state.realSize.width());
        final int height = Math.min(window.getHeight(), state.realSize.height());

        final Matrix4f projection = scene.getProjection().getProjectionMatrix();
        final Matrix4f view = scene.getCamera().getViewMatrix();
        final Vector3d camera = new Vector3d(scene.getCamera().getPosition());
        final Vector3d cameraMove = new Vector3d(camera).sub(previousCamera);
        final boolean showView = scene.getDebugVisualizers().isTaaView();
        final boolean readHistory =
                historyValid
                        && width == previousWidth
                        && height == previousHeight
                        && showView == viewShownBefore
                        && cameraMove.length() <= MAX_CAMERA_MOVE;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
            // The light, skybox and transparency stages drew the scene color
            SceneRender.imageBarrier(
                    barriers.get(0),
                    frameData.sceneColor.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            // Last read as history by the frame before, and by the tone map stage before that
            SceneRender.imageBarrier(
                    barriers.get(1),
                    frameData.taaColor.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(frameData.taaColor.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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
                ByteBuffer constants = stack.calloc(ShaderBindings.Taa.PUSH_CONSTANTS_SIZE);
                new Matrix4f(
                                TaaMath.reprojection(
                                        projection,
                                        view,
                                        previousProjection,
                                        previousView,
                                        cameraMove))
                        .get(ShaderBindings.Taa.PUSH_CONSTANT_REPROJECTION_OFFSET, constants);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_COLOR_INDEX_OFFSET,
                        frameData.sceneColor.bindlessIndex);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_HISTORY_INDEX_OFFSET,
                        previousData.taaColor.bindlessIndex);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_DEPTH_INDEX_OFFSET,
                        frameData.gBuffer.depth().bindlessIndex);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_PREVIOUS_DEPTH_INDEX_OFFSET,
                        previousData.gBuffer.depth().bindlessIndex);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET,
                        frameData.oitExtinction.bindlessIndex);
                constants.putInt(
                        ShaderBindings.Taa.PUSH_CONSTANT_FLAGS_OFFSET,
                        (readHistory ? ShaderBindings.Taa.FLAG_HISTORY : 0)
                                | (showView ? ShaderBindings.Taa.FLAG_VIEW : 0));
                constants.putFloat(ShaderBindings.Taa.PUSH_CONSTANT_SCREEN_SIZE_OFFSET, width);
                constants.putFloat(
                        ShaderBindings.Taa.PUSH_CONSTANT_SCREEN_SIZE_OFFSET + Float.BYTES, height);
                constants.putFloat(
                        ShaderBindings.Taa.PUSH_CONSTANT_EXPOSURE_OFFSET, scene.getExposure());
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
                FullScreenPass.draw(commandBuffer, quadMesh, width, height, stack);
            }
            vkCmdEndRendering(commandBuffer);
        }

        frameData.taaApplied = true;
        historyValid = width > 0 && height > 0;
        previousWidth = width;
        previousHeight = height;
        viewShownBefore = showView;
        previousProjection.set(projection);
        previousView.set(view);
        previousCamera.set(camera);
    }
}
