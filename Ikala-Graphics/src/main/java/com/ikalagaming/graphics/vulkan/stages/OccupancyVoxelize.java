package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.MeshRegistry;
import com.ikalagaming.graphics.vulkan.OccupancyVolume;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;

/**
 * Keeps the occupancy volume up to date, see {@link OccupancyVolume}: the levels follow the camera,
 * then the boxes they newly cover and the boxes that changed are cleared, then this frame's queued
 * section meshes are voxelized into them, a thread a triangle, and the surfaces of the volume
 * bricks standing in for meshes off the GPU marked, a thread a brick voxel.
 */
@Slf4j
public class OccupancyVoxelize implements RenderStage {

    /** The size of a job in the job buffer, see occupancy_voxelize.comp. */
    private static final int JOB_SIZE = 8 * Integer.BYTES;

    /** The size of the voxelize push constants: three addresses, two counts, three origins. */
    private static final int VOXELIZE_CONSTANTS_SIZE =
            3 * Long.BYTES + 2 * Integer.BYTES + OccupancyClipmap.LEVELS * 4 * Integer.BYTES;

    /** Where the origins start in the voxelize push constants. */
    private static final int ORIGINS_OFFSET = 3 * Long.BYTES + 2 * Integer.BYTES;

    /** The size of the clear push constants: the first texel and level, and the size. */
    private static final int CLEAR_CONSTANTS_SIZE = 8 * Integer.BYTES;

    /** The size of a brick job in its buffer, see occupancy_bricks.comp. */
    private static final int BRICK_JOB_SIZE = 8 * Integer.BYTES;

    /**
     * Where the origins start in the brick push constants: after two addresses and the count,
     * padded to 16 bytes as an ivec4 is aligned.
     */
    private static final int BRICK_ORIGINS_OFFSET = 2 * Long.BYTES + 4 * Integer.BYTES;

    /** The size of the brick push constants: the addresses and count, then three origins. */
    private static final int BRICK_CONSTANTS_SIZE =
            BRICK_ORIGINS_OFFSET + OccupancyClipmap.LEVELS * 4 * Integer.BYTES;

    /** Threads in a brick workgroup, see occupancy_bricks.comp. */
    private static final int BRICK_GROUP = 64;

    /** Workgroups a brick job takes: a thread a voxel. */
    private static final int GROUPS_PER_BRICK =
            VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK / BRICK_GROUP;

    /** Threads in a voxelize workgroup. */
    private static final int VOXELIZE_GROUP = 64;

    /** Threads along each side of a clear workgroup. */
    private static final int CLEAR_GROUP = 4;

    /** The clear shader. */
    @NonNull private final ShaderVulkan clearShader;

    /** The voxelize shader. */
    @NonNull private final ShaderVulkan voxelizeShader;

    /** The brick shader. */
    @NonNull private final ShaderVulkan brickShader;

    /** VkPipelineLayout of the brick pass. */
    private long brickLayout;

    /** VkPipeline of the brick pass. */
    private long brickPipeline;

    /** Each frame in flight's brick job buffer, written by the CPU. */
    private final SharedBuffer[] brickJobBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** VkPipelineLayout of the clear pass. */
    private long clearLayout;

    /** VkPipeline of the clear pass. */
    private long clearPipeline;

    /** VkPipelineLayout of the voxelize pass. */
    private long voxelizeLayout;

    /** VkPipeline of the voxelize pass. */
    private long voxelizePipeline;

    /** Each frame in flight's job buffer, written by the CPU. */
    private final SharedBuffer[] jobBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * Set up the stage.
     *
     * @param clearShader The clear compute shader.
     * @param voxelizeShader The voxelize compute shader.
     * @param brickShader The brick compute shader.
     */
    public OccupancyVoxelize(
            @NonNull ShaderVulkan clearShader,
            @NonNull ShaderVulkan voxelizeShader,
            @NonNull ShaderVulkan brickShader) {
        this.clearShader = clearShader;
        this.voxelizeShader = voxelizeShader;
        this.brickShader = brickShader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing occupancy voxelizing");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            clearLayout = createLayout(state, CLEAR_CONSTANTS_SIZE, stack);
            clearPipeline = createPipeline(state, clearShader, clearLayout, stack);
            voxelizeLayout = createLayout(state, VOXELIZE_CONSTANTS_SIZE, stack);
            voxelizePipeline = createPipeline(state, voxelizeShader, voxelizeLayout, stack);
            brickLayout = createLayout(state, BRICK_CONSTANTS_SIZE, stack);
            brickPipeline = createPipeline(state, brickShader, brickLayout, stack);
        }
        for (int i = 0; i < jobBuffers.length; ++i) {
            jobBuffers[i] =
                    SharedBuffer.allocate(
                            (long) JOB_SIZE * 1024, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            brickJobBuffers[i] =
                    SharedBuffer.allocate(
                            (long) BRICK_JOB_SIZE * 1024,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
    }

    /**
     * A pipeline layout with the volume's set and push constants.
     *
     * @param state The Vulkan state.
     * @param constantsSize The size of the push constants.
     * @param stack The stack to allocate on.
     * @return The VkPipelineLayout.
     */
    private static long createLayout(
            @NonNull VulkanState state, int constantsSize, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
        ranges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(constantsSize);
        checkError(
                vkCreatePipelineLayout(
                        state.device.logical,
                        VkPipelineLayoutCreateInfo.calloc(stack)
                                .sType$Default()
                                .pSetLayouts(stack.longs(state.occupancy.getSetLayout()))
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
        vkDestroyPipeline(state.device.logical, clearPipeline, null);
        vkDestroyPipeline(state.device.logical, voxelizePipeline, null);
        vkDestroyPipelineLayout(state.device.logical, clearLayout, null);
        vkDestroyPipelineLayout(state.device.logical, voxelizeLayout, null);
        vkDestroyPipeline(state.device.logical, brickPipeline, null);
        vkDestroyPipelineLayout(state.device.logical, brickLayout, null);
        for (int i = 0; i < jobBuffers.length; ++i) {
            SharedBuffer.free(jobBuffers[i], state);
            jobBuffers[i] = null;
            SharedBuffer.free(brickJobBuffers[i], state);
            brickJobBuffers[i] = null;
        }
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final OccupancyVolume volume = state.occupancy;
        if (!GraphicsManager.getSettings().occupancy) {
            // Nothing is kept up to date meanwhile, so it starts over when turned on again
            wasOn = false;
            return;
        }
        if (!wasOn) {
            volume.invalidateAll();
            wasOn = true;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        volume.follow(scene.getCamera().getPosition());
        final List<OccupancyClipmap.Box> clears = volume.takeClears();
        final OccupancyVolume.Jobs work = volume.takeJobs(state.sections, state.volumeBricks);
        final List<OccupancyVolume.Job> jobs = work.meshes();
        final List<OccupancyVolume.BrickJob> bricks = work.bricks();
        if (clears.isEmpty() && jobs.isEmpty() && bricks.isEmpty()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The light stage of an earlier frame may still read the voxels being written
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            final LongBuffer sets = stack.longs(volume.getSet());
            if (!clears.isEmpty()) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, clearPipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, clearLayout, 0, sets, null);
                final ByteBuffer constants = stack.calloc(CLEAR_CONSTANTS_SIZE);
                for (OccupancyClipmap.Box box : clears) {
                    final int[] size = new int[3];
                    for (int axis = 0; axis < 3; ++axis) {
                        constants.putInt(
                                axis * Integer.BYTES, OccupancyClipmap.texel(box.min()[axis]));
                        size[axis] = (int) (box.max()[axis] - box.min()[axis]);
                        constants.putInt((4 + axis) * Integer.BYTES, size[axis]);
                    }
                    constants.putInt(3 * Integer.BYTES, box.level());
                    vkCmdPushConstants(
                            commandBuffer, clearLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                    vkCmdDispatch(
                            commandBuffer,
                            groups(size[0], CLEAR_GROUP),
                            groups(size[1], CLEAR_GROUP),
                            groups(size[2], CLEAR_GROUP));
                }
                barrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                        stack);
            }
            writeJobs(state, jobs);
            if (cutoutTriangles + otherTriangles > 0) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, voxelizePipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_COMPUTE,
                        voxelizeLayout,
                        0,
                        sets,
                        null);
                final GeometryArena arena = state.bakedGeometry;
                final ByteBuffer constants = stack.calloc(VOXELIZE_CONSTANTS_SIZE);
                constants.putLong(Long.BYTES, arena.getVertices().deviceAddress);
                constants.putLong(2 * Long.BYTES, arena.getIndices().deviceAddress);
                putOrigins(volume, constants, ORIGINS_OFFSET);
                // Cut out surfaces first, so where an opaque one shares a voxel its full coverage,
                // written after a barrier, is what stays
                final long jobs0 = jobBuffers[state.frameIndex].deviceAddress;
                dispatchJobs(commandBuffer, constants, jobs0, cutoutJobCount, cutoutTriangles);
            }
            // Bricks only stand in for cut out and translucent surfaces, so they go with the cut
            // out meshes, before the barrier
            final boolean marked = markBricks(state, commandBuffer, bricks, sets, stack);
            if (cutoutTriangles + otherTriangles > 0) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, voxelizePipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_COMPUTE,
                        voxelizeLayout,
                        0,
                        sets,
                        null);
                final ByteBuffer constants = stack.calloc(VOXELIZE_CONSTANTS_SIZE);
                putOrigins(volume, constants, ORIGINS_OFFSET);
                constants.putLong(Long.BYTES, state.bakedGeometry.getVertices().deviceAddress);
                constants.putLong(2 * Long.BYTES, state.bakedGeometry.getIndices().deviceAddress);
                final long jobs0 = jobBuffers[state.frameIndex].deviceAddress;
                if ((cutoutTriangles > 0 || marked) && otherTriangles > 0) {
                    barrier(
                            commandBuffer,
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                            VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                            VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                            stack);
                }
                dispatchJobs(
                        commandBuffer,
                        constants,
                        jobs0 + (long) cutoutJobCount * JOB_SIZE,
                        otherJobCount,
                        otherTriangles);
            }
            // The light stage reads the voxels
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    stack);
        }
    }

    /**
     * Write each level's origin, as texels, into push constants.
     *
     * @param volume The volume.
     * @param constants The push constants.
     * @param offset Where the origins start.
     */
    private static void putOrigins(
            @NonNull OccupancyVolume volume, @NonNull ByteBuffer constants, int offset) {
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final long[] origin = volume.getClipmap().origin(level);
            for (int axis = 0; axis < 3; ++axis) {
                constants.putInt(
                        offset + (level * 4 + axis) * Integer.BYTES,
                        OccupancyClipmap.texel(origin[axis]));
            }
        }
    }

    /**
     * Mark this frame's bricks' surfaces into the volume.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The command buffer to record into.
     * @param bricks The brick jobs.
     * @param sets The volume's set.
     * @param stack The stack to allocate on.
     * @return Whether anything was dispatched.
     */
    private boolean markBricks(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull List<OccupancyVolume.BrickJob> bricks,
            @NonNull LongBuffer sets,
            @NonNull MemoryStack stack) {
        if (bricks.isEmpty() || state.volumeBricks == null) {
            return false;
        }
        final SharedBuffer buffer = brickJobBuffers[state.frameIndex];
        buffer.ensureCapacity((long) bricks.size() * BRICK_JOB_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(), bricks.size() * BRICK_JOB_SIZE);
        final OccupancyClipmap clipmap = state.occupancy.getClipmap();
        for (int i = 0; i < bricks.size(); ++i) {
            final OccupancyVolume.BrickJob job = bricks.get(i);
            final int base = i * BRICK_JOB_SIZE;
            final int size = OccupancyClipmap.VOXEL_SIZES[job.level()];
            final long[] origin = clipmap.origin(job.level());
            for (int axis = 0; axis < 3; ++axis) {
                out.putInt(
                        base + axis * Integer.BYTES,
                        (int) (job.corner()[axis] - origin[axis] * size));
            }
            out.putInt(base + 3 * Integer.BYTES, job.level());
            out.putInt(base + 4 * Integer.BYTES, job.slot());
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, brickPipeline);
        vkCmdBindDescriptorSets(
                commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, brickLayout, 0, sets, null);
        final ByteBuffer constants = stack.calloc(BRICK_CONSTANTS_SIZE);
        constants.putLong(0, buffer.deviceAddress);
        constants.putLong(Long.BYTES, state.volumeBricks.getSurfaces().deviceAddress);
        constants.putInt(2 * Long.BYTES, bricks.size());
        putOrigins(state.occupancy, constants, BRICK_ORIGINS_OFFSET);
        vkCmdPushConstants(commandBuffer, brickLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, bricks.size() * GROUPS_PER_BRICK, 1, 1);
        return true;
    }

    /** Whether the volume was kept up to date last frame. */
    private boolean wasOn;

    /** How many of this frame's jobs are for cut out surfaces, which come first. */
    private int cutoutJobCount;

    /** How many triangles the cut out jobs this frame have. */
    private long cutoutTriangles;

    /** How many of this frame's jobs follow the cut out ones. */
    private int otherJobCount;

    /** How many triangles the jobs after the cut out ones have. */
    private long otherTriangles;

    /**
     * Write this frame's jobs into its job buffer, with the meshes as they are now: the cut out
     * jobs first, then the rest, setting the counts of each.
     *
     * @param state The Vulkan state.
     * @param jobs The jobs.
     */
    private void writeJobs(@NonNull VulkanState state, @NonNull List<OccupancyVolume.Job> jobs) {
        final SharedBuffer buffer = jobBuffers[state.frameIndex];
        buffer.ensureCapacity((long) Math.max(1, jobs.size()) * JOB_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(), Math.max(1, jobs.size()) * JOB_SIZE);
        final MeshRegistry registry = state.bakedGeometry.getRegistry();
        final OccupancyClipmap clipmap = state.occupancy.getClipmap();
        long triangles = 0;
        int count = 0;
        // The cut out jobs, then the rest, each group counting its triangles from 0
        for (int group = 0; group < 2; ++group) {
            for (OccupancyVolume.Job job : jobs) {
                final MeshRegistry.Location at = registry.locate(job.mesh());
                final int jobTriangles = job.mesh().indexCount() / 3;
                final boolean cutout = job.bit() == OccupancyVolume.CUTOUT_BIT;
                if (at == null || jobTriangles == 0 || cutout != (group == 0)) {
                    continue;
                }
                final int base = count * JOB_SIZE;
                final int size = OccupancyClipmap.VOXEL_SIZES[job.level()];
                final long[] origin = clipmap.origin(job.level());
                out.putInt(base, (int) triangles);
                out.putInt(base + Integer.BYTES, at.firstIndex());
                out.putInt(base + 2 * Integer.BYTES, at.vertexOffset());
                out.putInt(base + 3 * Integer.BYTES, jobTriangles);
                for (int axis = 0; axis < 3; ++axis) {
                    out.putInt(
                            base + (4 + axis) * Integer.BYTES,
                            (int) (job.corner()[axis] - origin[axis] * size));
                }
                out.putInt(base + 7 * Integer.BYTES, job.level() | (job.bit() << Byte.SIZE));
                triangles += jobTriangles;
                ++count;
            }
            if (group == 0) {
                cutoutJobCount = count;
                cutoutTriangles = triangles;
                triangles = 0;
            }
        }
        otherJobCount = count - cutoutJobCount;
        otherTriangles = triangles;
    }

    /**
     * Voxelize one group of this frame's jobs.
     *
     * @param commandBuffer The command buffer to record into.
     * @param constants The push constants, with the geometry addresses and origins already in.
     * @param jobs The device address of the group's first job.
     * @param count How many jobs the group has.
     * @param triangles How many triangles they have.
     */
    private void dispatchJobs(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull ByteBuffer constants,
            long jobs,
            int count,
            long triangles) {
        if (triangles == 0) {
            return;
        }
        constants.putLong(0, jobs);
        constants.putInt(3 * Long.BYTES, count);
        constants.putInt(3 * Long.BYTES + Integer.BYTES, (int) triangles);
        vkCmdPushConstants(
                commandBuffer, voxelizeLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, groups(triangles, VOXELIZE_GROUP), 1, 1);
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
