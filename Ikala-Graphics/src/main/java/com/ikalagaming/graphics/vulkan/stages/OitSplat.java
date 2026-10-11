package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.OitVolume;
import com.ikalagaming.graphics.vulkan.PerFrameData;
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
 * Draws the translucent list again at one pixel per tile of the transparency volume, adding each
 * surface's extinction into the volume's buffer, see {@code oit_splat.frag} and {@link
 * VoxelOitMath}. There are no attachments and no depth test: only what is in front of a translucent
 * surface weighs on it, and that is never hidden behind the opaque scene.
 *
 * <p>The first of the transparency stages, so it decides for the frame whether they weight by the
 * volume, see {@link PerFrameData#voxelTransparency}. When they don't, it draws nothing.
 *
 * <p>It uses the same sets as {@link TranslucentRender}: the light stage's (set 0, for the
 * materials), the bindless textures (set 1) and the scene stage's (set 2).
 */
@Slf4j
public class OitSplat implements RenderStage {

    /** The shaders for meshes in the full vertex format. */
    @NonNull private final ShaderVulkan shader;

    /** The shaders for baked sections. */
    @NonNull private final ShaderVulkan bakedShader;

    /** The scene stage, whose descriptor sets hold the matrices and visible lists. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, whose descriptor sets hold the materials. */
    @NonNull private final LightRender lightRender;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline for the full vertex format, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkPipeline for baked sections, will be VK_NULL_HANDLE if not set up. */
    private long bakedPipeline;

    /**
     * Set up the stage.
     *
     * @param shader The shaders for meshes in the full vertex format.
     * @param bakedShader The shaders for baked sections.
     * @param sceneRender The scene stage, whose descriptor sets this reads.
     * @param lightRender The light stage, whose descriptor sets this reads.
     */
    public OitSplat(
            @NonNull ShaderVulkan shader,
            @NonNull ShaderVulkan bakedShader,
            @NonNull SceneRender sceneRender,
            @NonNull LightRender lightRender) {
        this.shader = shader;
        this.bakedShader = bakedShader;
        this.sceneRender = sceneRender;
        this.lightRender = lightRender;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
        bakedPipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the transparency splat");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.OitVolume.SPLAT_PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            lightRender.getDescriptorSetLayout(),
                                            state.bindlessTextures.getDescriptorSetLayout(),
                                            sceneRender.getDescriptorSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
        pipeline = TranslucentRender.createPipeline(state, shader, pipelineLayout, false, false);
        bakedPipeline =
                TranslucentRender.createPipeline(state, bakedShader, pipelineLayout, true, false);
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        frameData.voxelTransparency =
                scene != null && scene.getDebugVisualizers().isVoxelTransparency();
        final int width = Math.min(window.getWidth(), state.realSize.width());
        final int height = Math.min(window.getHeight(), state.realSize.height());
        if (!frameData.voxelTransparency || width <= 0 || height <= 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final OitVolume volume = state.oitVolume;
        final int tilesX = VoxelOitMath.tiles(width);
        final int tilesY = VoxelOitMath.tiles(height);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The last integration read and cleared the buffer
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(
                            VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));

            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(tilesX, tilesY))
                            .layerCount(1);
            vkCmdBeginRendering(commandBuffer, renderingInfo);
            TranslucentRender.setViewport(commandBuffer, tilesX, tilesY, stack);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(
                            lightRender.getDescriptorSet(state.frameIndex),
                            state.bindlessTextures.getDescriptorSet(),
                            sceneRender.getDescriptorSet(state.frameIndex)),
                    null);
            ByteBuffer constants = stack.calloc(ShaderBindings.OitVolume.SPLAT_PUSH_CONSTANTS_SIZE);
            constants.putLong(
                    ShaderBindings.OitVolume.PUSH_CONSTANT_EXTINCTION_OFFSET,
                    volume.getExtinction().deviceAddress);
            constants.putInt(ShaderBindings.OitVolume.PUSH_CONSTANT_TILES_OFFSET, tilesX);
            constants.putInt(
                    ShaderBindings.OitVolume.PUSH_CONSTANT_TILES_OFFSET + Integer.BYTES, tilesY);
            constants.putFloat(
                    ShaderBindings.OitVolume.PUSH_CONSTANT_LOG_SCALE_OFFSET,
                    VoxelOitMath.logScale(scene.getViewDistance()));
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
            TranslucentRender.drawList(
                    commandBuffer, state, frameData, pipeline, bakedPipeline, stack);
            vkCmdEndRendering(commandBuffer);
        }
    }
}
