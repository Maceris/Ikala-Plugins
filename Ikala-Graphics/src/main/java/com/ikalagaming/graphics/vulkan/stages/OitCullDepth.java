package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FullScreenPass;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.QuadMesh;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Writes the depth {@link TranslucentRender} tests against when weighting by the voxel-based
 * transparency volume, see {@code oit_cull_depth.frag}: the opaque scene's depth, or nearer where
 * {@link OitIntegrate} found no light gets back from, Drobot's zero transmittance depth. Layers
 * past it would discard themselves anyway; this way the depth test drops them before they are
 * shaded. Does nothing on frames that don't weight by the volume.
 *
 * <p>The target, {@link PerFrameData#oitCullDepth}, is left as a depth attachment for the
 * transparent stage.
 */
@Slf4j
public class OitCullDepth implements RenderStage {

    /** The cull depth shaders. */
    @NonNull private final ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The cull depth shaders.
     * @param quadMesh The mesh to draw over the screen.
     */
    public OitCullDepth(@NonNull ShaderVulkan shader, @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        this.quadMesh = quadMesh;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the transparency cull depth");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.OitVolume.CULL_PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(state.bindlessTextures.getDescriptorSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
        pipeline = FullScreenPass.createDepthPipeline(state, shader, pipelineLayout);
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
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final int width = Math.min(sceneTarget.width(), state.realSize.width());
        final int height = Math.min(sceneTarget.height(), state.realSize.height());
        if (!frameData.voxelTransparency || scene == null || width <= 0 || height <= 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final long image = frameData.oitCullDepth.texture;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Last tested against by the transparent stage, earlier this frame or a frame ago;
            // every pixel is written again
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
            SceneRender.imageBarrier(
                    barriers.get(0),
                    image,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(frameData.oitCullDepth.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(state.bindlessTextures.getDescriptorSet()),
                    null);
            final Matrix4f projection = scene.getProjection().getJitteredProjectionMatrix();
            ByteBuffer constants = stack.calloc(ShaderBindings.OitVolume.CULL_PUSH_CONSTANTS_SIZE);
            constants.putLong(
                    ShaderBindings.OitVolume.CULL_ZERO_SLICES_OFFSET,
                    state.oitVolume.getZeroSlices().deviceAddress);
            constants.putInt(ShaderBindings.OitVolume.CULL_TILES_OFFSET, VoxelOitMath.tiles(width));
            constants.putInt(
                    ShaderBindings.OitVolume.CULL_TILES_OFFSET + Integer.BYTES,
                    VoxelOitMath.tiles(height));
            constants.putFloat(
                    ShaderBindings.OitVolume.CULL_LOG_SCALE_OFFSET,
                    VoxelOitMath.logScale(scene.getViewDistance()));
            constants.putInt(
                    ShaderBindings.OitVolume.CULL_DEPTH_INDEX_OFFSET,
                    frameData.gBuffer.depth().bindlessIndex);
            constants.putFloat(ShaderBindings.OitVolume.CULL_PROJECTION_Z_OFFSET, projection.m22());
            constants.putFloat(
                    ShaderBindings.OitVolume.CULL_PROJECTION_Z_OFFSET + Float.BYTES,
                    projection.m32());
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
            FullScreenPass.draw(commandBuffer, quadMesh, width, height, stack);
            vkCmdEndRendering(commandBuffer);

            // The transparent stage tests against it
            SceneRender.imageBarrier(
                    barriers.get(0),
                    image,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
        }
    }
}
