package com.ikalagaming.graphics.vulkan.stages;

import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VolumeBricks;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

/**
 * Adds the frame's volume bricks' extinction into the transparency volume, after {@link OitSplat}
 * and before {@link OitIntegrate}, at one pixel per tile, see {@code volume_splat.frag}. Draws
 * nothing when the transparency stages don't weight by the volume this frame.
 *
 * <p>Its sets are the light stage's (set 0), the bindless textures (set 1), the scene stage's (set
 * 2) and the bricks (set 3).
 */
@Slf4j
public class VolumeSplat implements RenderStage {

    /** Where the extinction buffer's address is in the push constants, after the draw list's. */
    private static final int EXTINCTION_OFFSET = Long.BYTES;

    /** Where the tiles are in the push constants. */
    private static final int TILES_OFFSET = EXTINCTION_OFFSET + Long.BYTES;

    /** Where the screen size is in the push constants. */
    private static final int SCREEN_SIZE_OFFSET = TILES_OFFSET + 2 * Integer.BYTES;

    /** Where the log scale is in the push constants. */
    private static final int LOG_SCALE_OFFSET = SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

    /** Where the frame number is in the push constants. */
    private static final int FRAME_OFFSET = LOG_SCALE_OFFSET + Float.BYTES;

    /** Where the hand over's start and length are in the push constants. */
    private static final int FADE_OFFSET = FRAME_OFFSET + Integer.BYTES;

    /** Where the nearest solid faces' address is in the push constants, aligned for it. */
    private static final int HITS_OFFSET =
            (FADE_OFFSET + 2 * Float.BYTES + Long.BYTES - 1) / Long.BYTES * Long.BYTES;

    /** The size of the push constants. */
    private static final int CONSTANTS_SIZE = HITS_OFFSET + Long.BYTES;

    /** The shaders. */
    @NonNull private final ShaderVulkan shader;

    /** The scene stage, whose descriptor sets hold the matrices. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, whose descriptor sets hold the depth's index. */
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
    public VolumeSplat(
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
        log.debug("Initializing volume splat");
        pipelineLayout =
                VolumeColor.createLayout(
                        state,
                        CONSTANTS_SIZE,
                        lightRender.getDescriptorSetLayout(),
                        state.bindlessTextures.getDescriptorSetLayout(),
                        sceneRender.getDescriptorSetLayout(),
                        state.volumeBricks.getSetLayout());
        pipeline = VolumeColor.createPipeline(state, shader, pipelineLayout, false);
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
        final VolumeBricks bricks = state.volumeBricks;
        final int count = bricks.drawCount(state.frameIndex);
        final int width = Math.min(window.getWidth(), state.realSize.width());
        final int height = Math.min(window.getHeight(), state.realSize.height());
        if (scene == null
                || !frameData.voxelTransparency
                || count == 0
                || width <= 0
                || height <= 0) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final int tilesX = VoxelOitMath.tiles(width);
        final int tilesY = VoxelOitMath.tiles(height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The surfaces' splat adds into the same buffer
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
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
            constants.putLong(EXTINCTION_OFFSET, state.oitVolume.getExtinction().deviceAddress);
            constants.putInt(TILES_OFFSET, tilesX);
            constants.putInt(TILES_OFFSET + Integer.BYTES, tilesY);
            constants.putFloat(SCREEN_SIZE_OFFSET, width);
            constants.putFloat(SCREEN_SIZE_OFFSET + Float.BYTES, height);
            constants.putFloat(LOG_SCALE_OFFSET, VoxelOitMath.logScale(scene.getViewDistance()));
            constants.putInt(FRAME_OFFSET, (int) state.frameNumber);
            constants.putFloat(
                    FADE_OFFSET,
                    VolumeMath.fadeStart(GraphicsManager.getSettings().volumeDistance));
            constants.putFloat(FADE_OFFSET + Float.BYTES, (float) VolumeMath.FADE_LENGTH);
            constants.putLong(
                    HITS_OFFSET,
                    bricks.hits(state.frameIndex, state, (long) width * height).deviceAddress);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                    0,
                    constants);
            vkCmdDraw(commandBuffer, VolumeColor.BOX_VERTICES, count, 0, 0);
            vkCmdEndRendering(commandBuffer);
        }
    }
}
