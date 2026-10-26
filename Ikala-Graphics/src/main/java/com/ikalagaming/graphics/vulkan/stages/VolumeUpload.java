package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.graph.CoarseBricks;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.VolumeMaterials;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.TextureRegistry;
import com.ikalagaming.graphics.vulkan.ToneMapMath;
import com.ikalagaming.graphics.vulkan.VolumeBricks;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the volume bricks up to date, see {@link VolumeBricks}: copies the next bricks the bake
 * workers voxelized into their slots, the texels into the atlas by compute, since it stays in the
 * general layout, and writes the frame's list of bricks to draw, for {@link VolumeSplat} and {@link
 * VolumeColor}.
 *
 * <p>Also publishes the materials' colors for the workers to voxelize with (see {@link
 * VolumeMaterials}), whenever the materials change.
 */
@Slf4j
public class VolumeUpload implements RenderStage {

    /** The most bricks copied in a frame. */
    private static final int MAX_BRICKS = 256;

    /** The size of the push constants: the upload buffer's address, then three counts. */
    private static final int CONSTANTS_SIZE = Long.BYTES + 3 * Integer.BYTES;

    /** Threads in a workgroup, see volume_upload.comp. */
    private static final int GROUP = 64;

    /** Workgroups a brick takes: a thread a voxel. */
    private static final int GROUPS_PER_BRICK =
            VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK / GROUP;

    /** The upload shader. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout of the upload pass. */
    private long layout;

    /** VkPipeline of the upload pass. */
    private long pipeline;

    /** The materials the published colors were taken from, to notice when they change. */
    private final List<Material> published = new ArrayList<>();

    /** Which snapshot of the materials was published last. */
    private int version;

    /**
     * Set up the stage.
     *
     * @param shader The upload compute shader.
     */
    public VolumeUpload(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing volume uploads");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(stack.longs(state.volumeBricks.getSetLayout()))
                                    .pPushConstantRanges(ranges),
                            null,
                            longOutput));
            layout = longOutput.get(0);
            VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
            infos.get(0).sType$Default().stage(shader.shaderStages.get(0)).layout(layout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, infos, null, longOutput));
            pipeline = longOutput.get(0);
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, layout, null);
        published.clear();
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final VolumeBricks bricks = state.volumeBricks;
        if (state.sections == null || scene == null) {
            bricks.writeDraws(state.frameIndex, state, VolumeBricks.Members.NONE, new Vector3d());
            return;
        }
        publishMaterials(state, scene.getMaterialCache());
        // The nearest sections first, while the atlas is full; far ones as coarse bricks, by how
        // big a voxel is on screen. m11 is 1 / tan(fov / 2), negative with Vulkan's y flip
        final double pixelsPerMeter =
                Math.abs(scene.getObserver().getProjectionMatrix().m11())
                        * sceneTarget.height()
                        / 2.0;
        bricks.rebalance(scene.getCamera().getPosition(), pixelsPerMeter);
        final List<VolumeBricks.Upload> uploads = bricks.takeUploads(MAX_BRICKS);
        upload(state, uploads);
        // Bricks standing in for meshes off the GPU stand in for them in the occupancy volume and
        // the shadow maps too, which draw them again where a new one came in
        for (VolumeBricks.Upload upload : uploads) {
            if (upload.position().level() > 0) {
                // A coarse brick, far past the occupancy volume, stands in for all its sections
                if (state.shadowInvalidation != null) {
                    final double size = Sections.SECTION_SIZE;
                    final double span = size * CoarseBricks.span(upload.position().level());
                    final double[] min = {
                        upload.position().x() * size,
                        upload.position().y() * size,
                        upload.position().z() * size
                    };
                    state.shadowInvalidation.add(
                            min, new double[] {min[0] + span, min[1] + span, min[2] + span});
                }
                continue;
            }
            if (!state.sections.bricksStandIn(upload.position())) {
                continue;
            }
            if (state.occupancy != null) {
                state.occupancy.brickArrived(upload.position());
            }
            if (state.shadowInvalidation != null) {
                final double size = Sections.SECTION_SIZE;
                state.shadowInvalidation.addSection(
                        new Vector3d(
                                upload.position().x() * size,
                                upload.position().y() * size,
                                upload.position().z() * size));
            }
        }
        bricks.writeDraws(
                state.frameIndex,
                state,
                GraphicsManager.getSettings().volumeDistance <= 0
                        ? VolumeBricks.Members.NONE
                        : state.sections.getVolumeSections(),
                scene.getCamera().getPosition());
    }

    /**
     * Record copying bricks into their slots, after earlier frames' walks are done reading the
     * slots and before this frame's.
     *
     * @param state The Vulkan state.
     * @param uploads The bricks.
     */
    private void upload(@NonNull VulkanState state, @NonNull List<VolumeBricks.Upload> uploads) {
        if (uploads.isEmpty()) {
            return;
        }
        final VolumeBricks bricks = state.volumeBricks;
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final SharedBuffer buffer = bricks.writeUploads(state.frameIndex, state, uploads);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    layout,
                    0,
                    stack.longs(bricks.getSet()),
                    null);
            final ByteBuffer constants = stack.calloc(CONSTANTS_SIZE);
            constants.putLong(0, buffer.deviceAddress);
            constants.putInt(Long.BYTES, uploads.size());
            constants.putInt(
                    Long.BYTES + Integer.BYTES,
                    (int) (VolumeBricks.blocksOffset(uploads.size()) / Integer.BYTES));
            constants.putInt(
                    Long.BYTES + 2 * Integer.BYTES, VolumeBricks.STAGED_SIZE / Integer.BYTES);
            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            vkCmdDispatch(commandBuffer, uploads.size() * GROUPS_PER_BRICK, 1, 1);
            bricks.recordCopies(commandBuffer, buffer, uploads, stack);
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    stack);
        }
    }

    /**
     * Record a global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source stages.
     * @param srcAccess The source access.
     * @param dstStage The destination stages.
     * @param dstAccess The destination access.
     * @param stack The stack to allocate on.
     */
    private static void barrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            @NonNull MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }

    /**
     * Publish the materials' colors, if any material changed since they last were: each one's
     * texture's average where it has one, as the texture's smallest mip, else its base color.
     *
     * @param state The Vulkan state.
     * @param cache The scene's materials.
     */
    private void publishMaterials(@NonNull VulkanState state, @NonNull MaterialCache cache) {
        final int count = cache.getMaterialCount();
        boolean changed = count != published.size();
        for (int i = 0; i < count && !changed; ++i) {
            changed = cache.getMaterial(i) != published.get(i);
        }
        if (!changed) {
            return;
        }
        published.clear();
        final TextureRegistry textures = state.textureRegistry;
        final float[] colors = new float[count * VolumeMaterials.VALUES];
        for (int i = 0; i < count; ++i) {
            final Material material = cache.getMaterial(i);
            published.add(material);
            final float[] average = textures.average(material.getTexture());
            final int base = i * VolumeMaterials.VALUES;
            if (average != null) {
                System.arraycopy(average, 0, colors, base, VolumeMaterials.VALUES);
            } else {
                final Vector4fc color = material.getBaseColor();
                colors[base] = ToneMapMath.decode(color.x());
                colors[base + 1] = ToneMapMath.decode(color.y());
                colors[base + 2] = ToneMapMath.decode(color.z());
                colors[base + 3] = color.w();
            }
        }
        ++version;
        state.sections.publishMaterials(new VolumeMaterials(colors, version));
    }
}
