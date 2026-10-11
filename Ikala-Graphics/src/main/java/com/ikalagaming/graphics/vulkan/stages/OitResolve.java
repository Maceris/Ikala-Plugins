package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FullScreenPass;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.QuadMesh;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Composites what {@link TranslucentRender} added up over the scene color, see {@code
 * oit_resolve.frag} and {@link com.ikalagaming.graphics.vulkan.OitMath}. Leaves the scene color as
 * a color attachment. For debugging it can show a slice of the transparency volume instead, see
 * {@link com.ikalagaming.graphics.scene.debug.DebugVisualizers#getTransmittanceSlice()}.
 */
@Slf4j
public class OitResolve implements RenderStage {

    /** The resolve shaders. */
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
     * @param shader The resolve shaders.
     * @param quadMesh The mesh to draw over the screen.
     */
    public OitResolve(@NonNull ShaderVulkan shader, @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        this.quadMesh = quadMesh;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the transparency resolve");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.OitResolve.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            state.bindlessTextures.getDescriptorSetLayout(),
                                            state.oitVolume.getReadSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
        // The layers plus the background, as much of it as shows through: the shader's alpha
        pipeline =
                FullScreenPass.createPipeline(
                        state,
                        shader,
                        pipelineLayout,
                        PipelineManagerVulkan.SCENE_COLOR_FORMAT,
                        new FullScreenPass.Blend(VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_SRC_ALPHA));
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
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final int width = Math.min(window.getWidth(), state.realSize.width());
        final int height = Math.min(window.getHeight(), state.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(3, stack);
            // The transparent stage drew these
            for (int i = 0; i < 2; ++i) {
                SceneRender.imageBarrier(
                        barriers.get(i),
                        i == 0 ? frameData.oitAccum.texture : frameData.oitExtinction.texture,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            }
            // The light and skybox stages drew the scene color, which this blends onto
            SceneRender.imageBarrier(
                    barriers.get(2),
                    frameData.sceneColor.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(frameData.sceneColor.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
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
                        stack.longs(
                                state.bindlessTextures.getDescriptorSet(),
                                state.oitVolume.getReadSet()),
                        null);
                ByteBuffer constants = stack.calloc(ShaderBindings.OitResolve.PUSH_CONSTANTS_SIZE);
                constants.putInt(
                        ShaderBindings.OitResolve.PUSH_CONSTANT_ACCUM_INDEX_OFFSET,
                        frameData.oitAccum.bindlessIndex);
                constants.putInt(
                        ShaderBindings.OitResolve.PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET,
                        frameData.oitExtinction.bindlessIndex);
                // Only filled when the transparent stage weights by the volume
                final int slice =
                        scene != null && frameData.voxelTransparency
                                ? scene.getDebugVisualizers().getTransmittanceSlice()
                                : -1;
                constants.putInt(
                        ShaderBindings.OitResolve.PUSH_CONSTANT_DEBUG_SLICE_OFFSET,
                        Math.min(slice, VoxelOitMath.SLICES - 1));
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
                FullScreenPass.draw(commandBuffer, quadMesh, width, height, stack);
            }
            vkCmdEndRendering(commandBuffer);
        }
    }
}
