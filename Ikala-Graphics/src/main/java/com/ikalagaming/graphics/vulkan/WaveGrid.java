package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.WaterRegistry;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * The wave amplitude grid around the camera, see {@link WaveletMath} and {@code
 * water_wavelets.comp}: two grids the GPU steps between, the one it steps from and the one it
 * writes, which the water then reads. Cells are kept by world cell, wrapping around, so the grid
 * follows the camera a cell at a time without moving anything. Which cells hold water is worked out
 * on the CPU from the tiles, only for the cells the grid newly covers or when the tiles change.
 */
public class WaveGrid {

    /** Cells in the grid. */
    private static final int CELLS = WaveletMath.GRID_SIZE * WaveletMath.GRID_SIZE;

    /** The longest step, in seconds, so a long frame doesn't send waves far past the cells. */
    private static final double LONGEST_STEP = 0.1;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** Floats a disturbance takes: x, z, radius and strength. */
    private static final int DISTURBANCE_FLOATS = 4;

    /** Threads along each side of the step's workgroups. */
    private static final int WORKGROUP_SIDE = 8;

    /** The step's shader. */
    @NonNull private final ShaderVulkan shader;

    /** The step's pipeline layout and pipeline, VK_NULL_HANDLE until set up. */
    private long layout = VK_NULL_HANDLE;

    private long pipeline = VK_NULL_HANDLE;

    /** The two grids, stepped between. */
    private final SharedBuffer[] grids = new SharedBuffer[2];

    /** Which grid the last step wrote. */
    private int current;

    /** Each frame in flight's copy of the mask and its disturbances. */
    private final SharedBuffer[] masks = new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    private final SharedBuffer[] disturbanceBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's wave paints. */
    private final SharedBuffer[] paintBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Floats in a wave paint: x, z, radius, strength, then its way's x and z, and padding. */
    private static final int PAINT_FLOATS = 8;

    /**
     * What a water cell's first mask value is over its wind climate: 1 for water with no wind, so
     * land, 0, stays apart; see {@code water_wavelets.comp}.
     */
    private static final int MASK_CLIMATE_BASE = 2;

    /** Mask values after the first, copied from a cell's data: flow along x and z, and depth. */
    private static final int MASK_COPIED = 3;

    /**
     * Values a cell of the mask holds: water or not, flow along x and z, and depth; then how far
     * water falls onto it, its surface's height at rest, and padding to a second vec4.
     */
    private static final int MASK_VALUES = 8;

    /** Where in a cell of the mask how far water falls onto it is. */
    private static final int MASK_PLUNGE = 4;

    /** Where in a cell of the mask its surface's height is, in the world. */
    private static final int MASK_SURFACE = 5;

    /**
     * Each cell's water, by world cell wrapping around: 1 if it holds water, else 0, its flow along
     * x and z, and its depth.
     */
    private final float[] mask = new float[CELLS * MASK_VALUES];

    /** The world cells water covers and their flow and depth, as of {@link #maskVersion}. */
    private Map<Long, float[]> waterCells = Map.of();

    /** The tiles' version the mask was worked out for, -1 for none. */
    private long maskVersion = -1;

    /** The grid's corner, in world cells, now and when the mask was last brought up to date. */
    @Getter private int cornerX;

    @Getter private int cornerZ;

    private int maskCornerX;

    private int maskCornerZ;

    /** Whether the last grid can be stepped from: false before the first step. */
    private boolean stepped;

    /** When the last step was, from {@link System#nanoTime()}, or 0 before. */
    private long lastNanos;

    /** Steps recorded, to seed each step's spray differently. */
    private int steps;

    /** What seeds step by from step to step, the golden ratio in 32 bits, so they spread. */
    private static final int SEED_STEP = 0x9E3779B9;

    /**
     * Set up the grid.
     *
     * @param shader The step's compute shader.
     */
    public WaveGrid(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    /**
     * Create the pipeline and the grids.
     *
     * @param state The Vulkan state.
     */
    public void initialize(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Water.WAVELETS_CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
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
        final long bytes = (long) CELLS * WaveletMath.CELL_VALUES * Float.BYTES;
        for (int i = 0; i < grids.length; ++i) {
            grids[i] =
                    SharedBuffer.allocateDeviceLocal(
                            bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        stepped = false;
    }

    /**
     * Free everything.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (SharedBuffer[] buffers :
                new SharedBuffer[][] {grids, masks, disturbanceBuffers, paintBuffers}) {
            for (int i = 0; i < buffers.length; ++i) {
                if (buffers[i] != null) {
                    SharedBuffer.free(buffers[i], state);
                    buffers[i] = null;
                }
            }
        }
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, layout, null);
        layout = VK_NULL_HANDLE;
        maskVersion = -1;
        stepped = false;
    }

    /**
     * Step the grid: follow the camera, bring the mask up to date, and record the step, between
     * barriers against last frame's water reading the grid it writes and before this frame's water
     * reads it.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param frame The frame in flight.
     * @param camera The camera's position.
     * @param tilesVersion The tiles' version.
     * @param water The world cells water covers and their flow and depth, see {@link
     *     WaterMesh#waterCellData}, worked out again only when the version changes.
     * @param disturbances This frame's disturbances, in the world.
     * @param climates The wind climates' address, for each direction's share of the wind's waves.
     * @param paints This frame's wave paints, in the world.
     * @param profiles The wave profiles' address, for where crests break.
     * @param pool The particles, which take the hits the step records for spray and splashes, or
     *     null for none.
     * @param stack The stack to allocate on.
     */
    public void step(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            int frame,
            @NonNull Vector3dc camera,
            long tilesVersion,
            @NonNull Supplier<Map<Long, float[]>> water,
            @NonNull List<WaterRegistry.Disturbance> disturbances,
            long climates,
            @NonNull List<WaterRegistry.WavePaint> paints,
            long profiles,
            @Nullable ParticlePool pool,
            @NonNull MemoryStack stack) {
        final long nanos = System.nanoTime();
        final double seconds =
                lastNanos == 0 ? 0 : Math.min(LONGEST_STEP, (nanos - lastNanos) / NANOS_PER_SECOND);
        lastNanos = nanos;
        final int lastCornerX = cornerX;
        final int lastCornerZ = cornerZ;
        cornerX = (int) Math.floor(camera.x() / WaveletMath.CELL_SIZE) - WaveletMath.GRID_SIZE / 2;
        cornerZ = (int) Math.floor(camera.z() / WaveletMath.CELL_SIZE) - WaveletMath.GRID_SIZE / 2;
        updateMask(tilesVersion, water);

        masks[frame] = ensure(state, masks[frame], (long) mask.length * Float.BYTES);
        MemoryUtil.memFloatBuffer(masks[frame].allocationInfo.pMappedData(), mask.length).put(mask);
        final int count = Math.min(disturbances.size(), WaveletMath.MAX_DISTURBANCES);
        disturbanceBuffers[frame] =
                ensure(
                        state,
                        disturbanceBuffers[frame],
                        (long) WaveletMath.MAX_DISTURBANCES * DISTURBANCE_FLOATS * Float.BYTES);
        final long at = disturbanceBuffers[frame].allocationInfo.pMappedData();
        for (int i = 0; i < count; ++i) {
            final WaterRegistry.Disturbance d = disturbances.get(i);
            final long entry = at + (long) i * DISTURBANCE_FLOATS * Float.BYTES;
            MemoryUtil.memPutFloat(
                    entry, (float) (d.position().x() / WaveletMath.CELL_SIZE - cornerX));
            MemoryUtil.memPutFloat(
                    entry + Float.BYTES,
                    (float) (d.position().z() / WaveletMath.CELL_SIZE - cornerZ));
            MemoryUtil.memPutFloat(
                    entry + 2 * Float.BYTES, (float) (d.radius() / WaveletMath.CELL_SIZE));
            MemoryUtil.memPutFloat(entry + 3 * Float.BYTES, d.strength());
        }
        final int paintCount = Math.min(paints.size(), WaveletMath.MAX_PAINTS);
        paintBuffers[frame] =
                ensure(
                        state,
                        paintBuffers[frame],
                        (long) WaveletMath.MAX_PAINTS * PAINT_FLOATS * Float.BYTES);
        final long paintAt = paintBuffers[frame].allocationInfo.pMappedData();
        for (int i = 0; i < paintCount; ++i) {
            final WaterRegistry.WavePaint p = paints.get(i);
            final long entry = paintAt + (long) i * PAINT_FLOATS * Float.BYTES;
            MemoryUtil.memPutFloat(
                    entry, (float) (p.position().x() / WaveletMath.CELL_SIZE - cornerX));
            MemoryUtil.memPutFloat(
                    entry + Float.BYTES,
                    (float) (p.position().z() / WaveletMath.CELL_SIZE - cornerZ));
            MemoryUtil.memPutFloat(
                    entry + 2 * Float.BYTES, (float) (p.radius() / WaveletMath.CELL_SIZE));
            MemoryUtil.memPutFloat(entry + 3 * Float.BYTES, p.strength());
            MemoryUtil.memPutFloat(entry + 4 * Float.BYTES, (float) Math.cos(p.direction()));
            MemoryUtil.memPutFloat(entry + 5 * Float.BYTES, (float) Math.sin(p.direction()));
        }

        // Also after the particles' splash pass read the last hits and emptied their count
        barrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                stack);
        final int last = current;
        current = 1 - current;
        final boolean jumped =
                Math.abs(cornerX - lastCornerX) >= WaveletMath.GRID_SIZE
                        || Math.abs(cornerZ - lastCornerZ) >= WaveletMath.GRID_SIZE;
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Water.WAVELETS_CONSTANTS_SIZE);
        constants.putLong(ShaderBindings.Water.WAVELETS_LAST_OFFSET, grids[last].deviceAddress);
        constants.putLong(ShaderBindings.Water.WAVELETS_NEXT_OFFSET, grids[current].deviceAddress);
        constants.putLong(ShaderBindings.Water.WAVELETS_MASK_OFFSET, masks[frame].deviceAddress);
        constants.putLong(
                ShaderBindings.Water.WAVELETS_DISTURBANCES_OFFSET,
                disturbanceBuffers[frame].deviceAddress);
        constants.putInt(ShaderBindings.Water.WAVELETS_CORNER_OFFSET, cornerX);
        constants.putInt(ShaderBindings.Water.WAVELETS_CORNER_OFFSET + Integer.BYTES, cornerZ);
        constants.putInt(ShaderBindings.Water.WAVELETS_LAST_CORNER_OFFSET, lastCornerX);
        constants.putInt(
                ShaderBindings.Water.WAVELETS_LAST_CORNER_OFFSET + Integer.BYTES, lastCornerZ);
        constants.putFloat(ShaderBindings.Water.WAVELETS_SECONDS_OFFSET, (float) seconds);
        constants.putInt(ShaderBindings.Water.WAVELETS_DISTURBANCE_COUNT_OFFSET, count);
        constants.putInt(
                ShaderBindings.Water.WAVELETS_LAST_VALID_OFFSET, stepped && !jumped ? 1 : 0);
        constants.putLong(ShaderBindings.Water.WAVELETS_CLIMATES_OFFSET, climates);
        constants.putLong(
                ShaderBindings.Water.WAVELETS_PAINTS_OFFSET, paintBuffers[frame].deviceAddress);
        constants.putInt(ShaderBindings.Water.WAVELETS_PAINT_COUNT_OFFSET, paintCount);
        constants.putLong(ShaderBindings.Water.WAVELETS_PROFILES_OFFSET, profiles);
        constants.putFloat(
                ShaderBindings.Water.WAVELETS_CORNER_RENDER_OFFSET,
                (float) (cornerX * WaveletMath.CELL_SIZE - camera.x()));
        constants.putFloat(
                ShaderBindings.Water.WAVELETS_CORNER_RENDER_OFFSET + Float.BYTES,
                (float) (cornerZ * WaveletMath.CELL_SIZE - camera.z()));
        constants.putFloat(ShaderBindings.Water.WAVELETS_CAMERA_HEIGHT_OFFSET, (float) camera.y());
        constants.putInt(ShaderBindings.Water.WAVELETS_SEED_OFFSET, ++steps * SEED_STEP);
        if (pool != null) {
            constants.putLong(
                    ShaderBindings.Water.WAVELETS_HITS_OFFSET, pool.getWaterHits().deviceAddress);
            constants.putLong(
                    ShaderBindings.Water.WAVELETS_HIT_COUNT_OFFSET, pool.waterHitCountAddress());
            pool.waterHitsRecorded(camera);
        }
        vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        final int groups = WaveletMath.GRID_SIZE / WORKGROUP_SIDE;
        vkCmdDispatch(commandBuffer, groups, groups, 1);
        barrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                stack);
        stepped = true;
    }

    /**
     * The address of the grid the last step wrote, for the water to read.
     *
     * @return The device address.
     */
    public long currentAddress() {
        return grids[current].deviceAddress;
    }

    /**
     * Bring the mask up to date: every cell when the tiles changed, else only the cells the grid
     * newly covers.
     *
     * @param tilesVersion The tiles' version.
     * @param water The world cells water covers and their flow and depth.
     */
    private void updateMask(long tilesVersion, Supplier<Map<Long, float[]>> water) {
        final boolean all = tilesVersion != maskVersion;
        if (all) {
            waterCells = water.get();
            maskVersion = tilesVersion;
        } else if (cornerX == maskCornerX && cornerZ == maskCornerZ) {
            return;
        }
        for (int z = 0; z < WaveletMath.GRID_SIZE; ++z) {
            final int worldZ = cornerZ + z;
            final boolean newRow =
                    worldZ < maskCornerZ || worldZ >= maskCornerZ + WaveletMath.GRID_SIZE;
            for (int x = 0; x < WaveletMath.GRID_SIZE; ++x) {
                final int worldX = cornerX + x;
                final boolean newCell =
                        newRow
                                || worldX < maskCornerX
                                || worldX >= maskCornerX + WaveletMath.GRID_SIZE;
                if (all || newCell) {
                    final int at = slot(worldX, worldZ) * MASK_VALUES;
                    final float[] data = waterCells.get(WaterMesh.worldKey(worldX, worldZ));
                    mask[at] = data == null ? 0 : MASK_CLIMATE_BASE + data[WaterMesh.CELL_CLIMATE];
                    for (int i = 0; i < MASK_COPIED; ++i) {
                        mask[at + 1 + i] = data == null ? 0 : data[i];
                    }
                    mask[at + MASK_PLUNGE] = data == null ? 0 : data[WaterMesh.CELL_PLUNGE];
                    mask[at + MASK_SURFACE] = data == null ? 0 : data[WaterMesh.CELL_SURFACE];
                }
            }
        }
        maskCornerX = cornerX;
        maskCornerZ = cornerZ;
    }

    /**
     * Where a world cell is kept, wrapping around the grid, as the shaders find it.
     *
     * @param x The world cell along x.
     * @param z Along z.
     * @return The cell's slot.
     */
    public static int slot(int x, int z) {
        final int mask = WaveletMath.GRID_SIZE - 1;
        return (z & mask) * WaveletMath.GRID_SIZE + (x & mask);
    }

    /**
     * A host buffer of at least a size, made or grown as needed.
     *
     * @param state The Vulkan state.
     * @param buffer The buffer so far, or null.
     * @param bytes The size needed.
     * @return The buffer.
     */
    private static SharedBuffer ensure(
            @NonNull VulkanState state, SharedBuffer buffer, long bytes) {
        if (buffer == null) {
            return SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        buffer.ensureCapacity(bytes, state);
        return buffer;
    }

    /**
     * A global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source stages.
     * @param srcAccess The source accesses.
     * @param dstStage The destination stages.
     * @param dstAccess The destination accesses.
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
