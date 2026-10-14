package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.MeshRegistry;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VolumeBricks;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;

/**
 * Keeps the volume bricks up to date, see {@link VolumeBricks}: takes the sections whose bakes
 * changed, voxelizes the next batch of them from their cut out and translucent meshes, a thread a
 * triangle into fixed-point accumulators, then resolves those into the bricks' slots of the atlas,
 * filling in the blocks whose faces were left out, and clears the accumulators. Also writes the
 * frame's list of bricks to draw, for {@link VolumeSplat} and {@link VolumeColor}.
 *
 * <p>Runs after the scene stage has written the frame's materials, which the voxelizer reads the
 * colors and opacities from.
 */
@Slf4j
public class VolumeVoxelize implements RenderStage {

    /** The size of a job in the job buffer, see volume_voxelize.comp. */
    private static final int JOB_SIZE = 8 * Integer.BYTES;

    /** The size of the voxelize push constants: five addresses, three counts. */
    private static final int VOXELIZE_CONSTANTS_SIZE = 5 * Long.BYTES + 3 * Integer.BYTES;

    /** Where the counts start in the voxelize push constants. */
    private static final int COUNTS_OFFSET = 5 * Long.BYTES;

    /** The size of the resolve push constants: two addresses. */
    private static final int RESOLVE_CONSTANTS_SIZE = 2 * Long.BYTES;

    /** Threads in a voxelize workgroup. */
    private static final int VOXELIZE_GROUP = 64;

    /** Threads along each side of a resolve workgroup. */
    private static final int RESOLVE_GROUP = 4;

    /**
     * Accumulators per voxel: area times optical depth, the weighted color, the weight, then the
     * block faces' bits, area times optical depth, and area, and the cut out faces' material.
     */
    private static final int ACCUMULATORS = 9;

    /** The most bricks voxelized a frame. */
    private static final int MAX_BRICKS = 32;

    /** The most triangles voxelized a frame, once at least one brick is. */
    private static final long MAX_TRIANGLES = 262_144;

    /** The voxelize shader. */
    @NonNull private final ShaderVulkan voxelizeShader;

    /** The resolve shader. */
    @NonNull private final ShaderVulkan resolveShader;

    /** VkPipelineLayout of the voxelize pass. */
    private long voxelizeLayout;

    /** VkPipeline of the voxelize pass. */
    private long voxelizePipeline;

    /** VkPipelineLayout of the resolve pass. */
    private long resolveLayout;

    /** VkPipeline of the resolve pass. */
    private long resolvePipeline;

    /** Each frame in flight's job buffer, written by the CPU. */
    private final SharedBuffer[] jobBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's batch slots, written by the CPU. */
    private final SharedBuffer[] slotBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The accumulators of a batch, cleared after each resolve. */
    private SharedBuffer accumulators;

    /**
     * Set up the stage.
     *
     * @param voxelizeShader The voxelize compute shader.
     * @param resolveShader The resolve compute shader.
     */
    public VolumeVoxelize(
            @NonNull ShaderVulkan voxelizeShader, @NonNull ShaderVulkan resolveShader) {
        this.voxelizeShader = voxelizeShader;
        this.resolveShader = resolveShader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing volume voxelizing");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            voxelizeLayout =
                    createLayout(
                            state,
                            state.bindlessTextures.getDescriptorSetLayout(),
                            VOXELIZE_CONSTANTS_SIZE,
                            stack);
            voxelizePipeline = createPipeline(state, voxelizeShader, voxelizeLayout, stack);
            resolveLayout =
                    createLayout(
                            state,
                            state.volumeBricks.getSetLayout(),
                            RESOLVE_CONSTANTS_SIZE,
                            stack);
            resolvePipeline = createPipeline(state, resolveShader, resolveLayout, stack);
        }
        for (int i = 0; i < jobBuffers.length; ++i) {
            jobBuffers[i] =
                    SharedBuffer.allocate(
                            (long) JOB_SIZE * 1024, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            slotBuffers[i] =
                    SharedBuffer.allocate(
                            (long) MAX_BRICKS * Integer.BYTES,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        final long voxels = (long) VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;
        accumulators =
                SharedBuffer.allocateDeviceLocal(
                        MAX_BRICKS * voxels * ACCUMULATORS * Integer.BYTES,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        state.immediateCommands.submit(
                state,
                commandBuffer ->
                        vkCmdFillBuffer(commandBuffer, accumulators.buffer, 0, VK_WHOLE_SIZE, 0));
    }

    /**
     * A compute pipeline layout with one set and push constants.
     *
     * @param state The Vulkan state.
     * @param setLayout The set's layout.
     * @param constantsSize The size of the push constants.
     * @param stack The stack to allocate on.
     * @return The VkPipelineLayout.
     */
    private static long createLayout(
            @NonNull VulkanState state,
            long setLayout,
            int constantsSize,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
        ranges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(constantsSize);
        checkError(
                vkCreatePipelineLayout(
                        state.device.logical,
                        VkPipelineLayoutCreateInfo.calloc(stack)
                                .sType$Default()
                                .pSetLayouts(stack.longs(setLayout))
                                .pPushConstantRanges(ranges),
                        null,
                        longOutput));
        return longOutput.get(0);
    }

    /**
     * A compute pipeline.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param layout The VkPipelineLayout.
     * @param stack The stack to allocate on.
     * @return The VkPipeline.
     */
    private static long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            long layout,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
        infos.get(0).sType$Default().stage(shader.shaderStages.get(0)).layout(layout);
        checkError(
                vkCreateComputePipelines(
                        state.device.logical, VK_NULL_HANDLE, infos, null, longOutput));
        return longOutput.get(0);
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, voxelizePipeline, null);
        vkDestroyPipeline(state.device.logical, resolvePipeline, null);
        vkDestroyPipelineLayout(state.device.logical, voxelizeLayout, null);
        vkDestroyPipelineLayout(state.device.logical, resolveLayout, null);
        for (int i = 0; i < jobBuffers.length; ++i) {
            SharedBuffer.free(jobBuffers[i], state);
            jobBuffers[i] = null;
            SharedBuffer.free(slotBuffers[i], state);
            slotBuffers[i] = null;
        }
        SharedBuffer.free(accumulators, state);
        accumulators = null;
    }

    @Override
    public void render(
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final VolumeBricks bricks = state.volumeBricks;
        if (state.sections == null || scene == null) {
            bricks.writeDraws(state.frameIndex, state, List.of(), new Vector3d());
            return;
        }
        bricks.changed(state.sections.takeVolumeChanges());
        if (GraphicsManager.getSettings().volumeDistance <= 0) {
            bricks.writeDraws(state.frameIndex, state, List.of(), scene.getCamera().getPosition());
            return;
        }
        final List<VolumeBricks.Batch> batches =
                bricks.takeBatches(state.sections, MAX_BRICKS, MAX_TRIANGLES);
        final long triangles = writeJobs(state, batches);
        if (triangles > 0) {
            voxelize(scene, state, batches, triangles);
            bricks.voxelized(batches);
        }
        bricks.writeDraws(
                state.frameIndex,
                state,
                state.sections.getVolumeSections(),
                scene.getCamera().getPosition());
    }

    /** How many jobs this frame has. */
    private int jobCount;

    /**
     * Write this frame's jobs and slots, with the meshes as they are now.
     *
     * @param state The Vulkan state.
     * @param batches The batches.
     * @return How many triangles the jobs have.
     */
    private long writeJobs(@NonNull VulkanState state, @NonNull List<VolumeBricks.Batch> batches) {
        int meshes = 0;
        for (VolumeBricks.Batch batch : batches) {
            meshes += batch.meshes().size();
        }
        final SharedBuffer buffer = jobBuffers[state.frameIndex];
        buffer.ensureCapacity((long) Math.max(1, meshes) * JOB_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(), Math.max(1, meshes) * JOB_SIZE);
        final ByteBuffer slots =
                MemoryUtil.memByteBuffer(
                        slotBuffers[state.frameIndex].allocationInfo.pMappedData(),
                        MAX_BRICKS * Integer.BYTES);
        final MeshRegistry registry = state.bakedGeometry.getRegistry();
        long triangles = 0;
        int count = 0;
        for (int brick = 0; brick < batches.size(); ++brick) {
            final VolumeBricks.Batch batch = batches.get(brick);
            slots.putInt(brick * Integer.BYTES, batch.slot());
            for (MeshHandle mesh : batch.meshes()) {
                final MeshRegistry.Location at = registry.locate(mesh);
                final int meshTriangles = mesh.indexCount() / 3;
                if (at == null || meshTriangles == 0) {
                    continue;
                }
                final int base = count * JOB_SIZE;
                out.putInt(base, (int) triangles);
                out.putInt(base + Integer.BYTES, at.firstIndex());
                out.putInt(base + 2 * Integer.BYTES, at.vertexOffset());
                out.putInt(base + 3 * Integer.BYTES, meshTriangles);
                out.putInt(base + 4 * Integer.BYTES, brick);
                out.putInt(
                        base + 5 * Integer.BYTES,
                        mesh.kind() == MeshKind.BAKED_TRANSLUCENT ? 1 : 0);
                triangles += meshTriangles;
                ++count;
            }
        }
        jobCount = count;
        return triangles;
    }

    /**
     * Voxelize this frame's jobs into the accumulators, then resolve them into the atlas.
     *
     * @param scene The scene, for its materials.
     * @param state The Vulkan state.
     * @param batches The batches.
     * @param triangles How many triangles the jobs have.
     */
    private void voxelize(
            @NonNull Scene scene,
            @NonNull VulkanState state,
            @NonNull List<VolumeBricks.Batch> batches,
            long triangles) {
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The batch's slots' cell masks and materials start empty, once earlier frames' walks
            // are done reading
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    VK_PIPELINE_STAGE_2_CLEAR_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    stack);
            final SharedBuffer masks = state.volumeBricks.getMasks();
            final SharedBuffer materials = state.volumeBricks.getMaterials();
            for (VolumeBricks.Batch batch : batches) {
                vkCmdFillBuffer(
                        commandBuffer,
                        masks.buffer,
                        (long) batch.slot() * VolumeBricks.MASKS_SIZE,
                        VolumeBricks.MASKS_SIZE,
                        0);
                vkCmdFillBuffer(
                        commandBuffer,
                        materials.buffer,
                        (long) batch.slot() * VolumeBricks.MATERIALS_SIZE,
                        VolumeBricks.MATERIALS_SIZE,
                        0);
            }
            // Earlier frames' marches may still read the slots being written, and the
            // accumulators were last cleared
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
                            | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, voxelizePipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    voxelizeLayout,
                    0,
                    stack.longs(state.bindlessTextures.getDescriptorSet()),
                    null);
            final ByteBuffer constants = stack.calloc(VOXELIZE_CONSTANTS_SIZE);
            constants.putLong(0, jobBuffers[state.frameIndex].deviceAddress);
            constants.putLong(Long.BYTES, state.bakedGeometry.getVertices().deviceAddress);
            constants.putLong(2 * Long.BYTES, state.bakedGeometry.getIndices().deviceAddress);
            constants.putLong(3 * Long.BYTES, frameData.materials.deviceAddress);
            constants.putLong(4 * Long.BYTES, accumulators.deviceAddress);
            constants.putInt(COUNTS_OFFSET, jobCount);
            constants.putInt(COUNTS_OFFSET + Integer.BYTES, (int) triangles);
            constants.putInt(
                    COUNTS_OFFSET + 2 * Integer.BYTES, scene.getMaterialCache().getMaterialCount());
            vkCmdPushConstants(
                    commandBuffer, voxelizeLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            vkCmdDispatch(commandBuffer, groups(triangles, VOXELIZE_GROUP), 1, 1);

            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, resolvePipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    resolveLayout,
                    0,
                    stack.longs(state.volumeBricks.getSet()),
                    null);
            final ByteBuffer resolveConstants = stack.calloc(RESOLVE_CONSTANTS_SIZE);
            resolveConstants.putLong(0, accumulators.deviceAddress);
            resolveConstants.putLong(Long.BYTES, slotBuffers[state.frameIndex].deviceAddress);
            vkCmdPushConstants(
                    commandBuffer, resolveLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, resolveConstants);
            vkCmdDispatch(
                    commandBuffer,
                    VolumeMath.BRICK / RESOLVE_GROUP,
                    VolumeMath.BRICK / RESOLVE_GROUP,
                    batches.size() * VolumeMath.BRICK / RESOLVE_GROUP);

            // The walks read the bricks and masks, and the accumulators are cleared once read
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_CLEAR_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    stack);
            final long voxels = (long) VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;
            vkCmdFillBuffer(
                    commandBuffer,
                    accumulators.buffer,
                    0,
                    batches.size() * voxels * ACCUMULATORS * Integer.BYTES,
                    0);
        }
    }

    /**
     * How many workgroups cover some threads.
     *
     * @param threads The threads.
     * @param group The threads in a group.
     * @return The groups.
     */
    private static int groups(long threads, int group) {
        return (int) ((threads + group - 1) / group);
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
}
