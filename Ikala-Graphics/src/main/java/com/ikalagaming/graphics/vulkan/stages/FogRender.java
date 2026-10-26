package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.TaaMath;
import com.ikalagaming.graphics.scene.MediumRegistry;
import com.ikalagaming.graphics.scene.MediumVolume;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.FogVolume;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VoxelOitMath;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills and integrates the fog volume, see {@link FogVolume}, {@code fog_inject.comp} and {@code
 * fog_integrate.comp}. Run by {@link LightRender} once it has written the light stage's inputs,
 * which the injection lights the fog with, before it lights the scene through the fog.
 */
@Slf4j
public class FogRender {

    /** Threads along each side of a workgroup's tiles. */
    private static final int WORKGROUP_SIZE = 8;

    /**
     * Where the injection's push constants keep last frame's view from this frame's render space,
     * which takes in the camera's move.
     */
    private static final int INJECT_TO_PREVIOUS_OFFSET = 0;

    /** Where they keep last frame's projection's terms: m00, m11, m20 and m21. */
    private static final int INJECT_PREVIOUS_PROJECTION_OFFSET = 16 * Float.BYTES;

    /** Where they keep the tiles used. */
    private static final int INJECT_TILES_OFFSET =
            INJECT_PREVIOUS_PROJECTION_OFFSET + 4 * Float.BYTES;

    /** Where they keep the drawn area. */
    private static final int INJECT_SCREEN_OFFSET = INJECT_TILES_OFFSET + 2 * Integer.BYTES;

    /** Where they keep this frame's jitter within each froxel, across, down and in depth. */
    private static final int INJECT_JITTER_OFFSET = INJECT_SCREEN_OFFSET + 2 * Float.BYTES;

    /** Where they keep how much of the frames before is kept. */
    private static final int INJECT_HISTORY_OFFSET = INJECT_JITTER_OFFSET + 3 * Float.BYTES;

    /** Where they keep last frame's slices' log scale. */
    private static final int INJECT_PREVIOUS_LOG_SCALE_OFFSET = INJECT_HISTORY_OFFSET + Float.BYTES;

    /** Where they keep the address of this frame's medium volumes, 8 byte aligned. */
    private static final int INJECT_MEDIA_OFFSET =
            (int)
                    ((INJECT_PREVIOUS_LOG_SCALE_OFFSET + Float.BYTES + Long.BYTES - 1L)
                            / Long.BYTES
                            * Long.BYTES);

    /** The injection's push constants. */
    private static final int INJECT_CONSTANTS_SIZE = INJECT_MEDIA_OFFSET + Long.BYTES;

    /**
     * The size of the medium buffer's header, holding how many volumes follow, padded to a vec4,
     * see {@code fog_inject.comp}.
     */
    private static final int MEDIA_HEADER_SIZE = 4 * Integer.BYTES;

    /** The size of one medium volume in the buffer: seven vec4s. */
    private static final int MEDIUM_SIZE = 7 * 4 * Float.BYTES;

    /** The values of a vec4. */
    private static final int VEC4 = 4;

    /**
     * How long noise drifts before its offset wraps, in seconds, so floats keep their precision.
     */
    private static final double NOISE_PERIOD_SECONDS = 4096;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /**
     * How much of the frames before each froxel keeps, as Frostbite does: about ten frames' worth
     * of jittered samples, which smooths the froxels' steps and shadow edges.
     */
    private static final float HISTORY_WEIGHT = 0.9f;

    /** How far the camera can move in a frame, in meters, before the frames before are dropped. */
    private static final double MAX_CAMERA_MOVE = 10;

    /** Jitter positions before they repeat. */
    private static final int JITTER_SAMPLES = 16;

    /** The Halton bases of the jitter across, down and in depth. */
    private static final int[] JITTER_BASES = {2, 3, 5};

    /** Where the integration's tiles go in its push constants, after a matrix. */
    private static final int INTEGRATE_TILES_OFFSET = 16 * Float.BYTES;

    /** Where its drawn area goes. */
    private static final int INTEGRATE_SCREEN_OFFSET = INTEGRATE_TILES_OFFSET + 2 * Integer.BYTES;

    /** Where its slices' log scale goes. */
    private static final int INTEGRATE_LOG_SCALE_OFFSET = INTEGRATE_SCREEN_OFFSET + 2 * Float.BYTES;

    /** The integration's push constants. */
    private static final int INTEGRATE_CONSTANTS_SIZE = INTEGRATE_LOG_SCALE_OFFSET + Float.BYTES;

    /** The injection's shader. */
    @NonNull private final ShaderVulkan injectShader;

    /** The integration's shader. */
    @NonNull private final ShaderVulkan integrateShader;

    /** The pipelines and their layouts, VK_NULL_HANDLE until set up. */
    private long injectLayout;

    private long injectPipeline;
    private long integrateLayout;
    private long integratePipeline;

    /** Last frame's view, projection without jitter, camera, size and view distance. */
    private final Matrix4f previousView = new Matrix4f();

    private final Matrix4f previousProjection = new Matrix4f();
    private final Vector3d previousCamera = new Vector3d();
    private int previousWidth;
    private int previousHeight;
    private double previousViewDistance;

    /** Frames filled, for the jitter. */
    private long frames;

    /** When the first frame was filled, from {@link System#nanoTime()}, for the noise's drift. */
    private final long start = System.nanoTime();

    /** Each frame in flight's medium volumes, or null before its first. */
    private final SharedBuffer[] mediaBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The medium volumes, a scratch list. */
    private final List<MediumVolume> media = new ArrayList<>();

    /**
     * Set up the fog's passes.
     *
     * @param injectShader The injection's compute shader.
     * @param integrateShader The integration's compute shader.
     */
    public FogRender(@NonNull ShaderVulkan injectShader, @NonNull ShaderVulkan integrateShader) {
        this.injectShader = injectShader;
        this.integrateShader = integrateShader;
    }

    /**
     * Create the pipelines.
     *
     * @param state The Vulkan state.
     * @param lightSetLayout The light stage's own set's layout, the injection's set 0.
     */
    public void initialize(@NonNull VulkanState state, long lightSetLayout) {
        log.debug("Initializing the fog");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The light stage's sets, then the fog's
            injectLayout =
                    createLayout(
                            state,
                            stack.longs(
                                    lightSetLayout,
                                    state.bindlessTextures.getDescriptorSetLayout(),
                                    state.occupancy.getSetLayout(),
                                    state.environment.getSetLayout(),
                                    state.fogVolume.getInjectSetLayout()),
                            INJECT_CONSTANTS_SIZE,
                            stack);
            injectPipeline = createPipeline(state, injectShader, injectLayout, stack);
            integrateLayout =
                    createLayout(
                            state,
                            stack.longs(state.fogVolume.getIntegrateSetLayout()),
                            INTEGRATE_CONSTANTS_SIZE,
                            stack);
            integratePipeline = createPipeline(state, integrateShader, integrateLayout, stack);
        }
    }

    /**
     * Create a compute pipeline layout.
     *
     * @param state The Vulkan state.
     * @param setLayouts Its sets' layouts.
     * @param constantsSize The size of its push constants.
     * @param stack The stack to allocate on.
     * @return The VkPipelineLayout.
     */
    private static long createLayout(
            @NonNull VulkanState state,
            @NonNull LongBuffer setLayouts,
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
                                .pSetLayouts(setLayouts)
                                .pPushConstantRanges(ranges),
                        null,
                        longOutput));
        return longOutput.get(0);
    }

    /**
     * Create a compute pipeline.
     *
     * @param state The Vulkan state.
     * @param shader Its shader.
     * @param layout Its layout.
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

    /**
     * Destroy the pipelines.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < mediaBuffers.length; ++i) {
            if (mediaBuffers[i] != null) {
                SharedBuffer.free(mediaBuffers[i], state);
                mediaBuffers[i] = null;
            }
        }
        vkDestroyPipeline(state.device.logical, injectPipeline, null);
        vkDestroyPipeline(state.device.logical, integratePipeline, null);
        vkDestroyPipelineLayout(state.device.logical, injectLayout, null);
        vkDestroyPipelineLayout(state.device.logical, integrateLayout, null);
        injectPipeline = VK_NULL_HANDLE;
        integratePipeline = VK_NULL_HANDLE;
        injectLayout = VK_NULL_HANDLE;
        integrateLayout = VK_NULL_HANDLE;
    }

    /**
     * Record filling the fog volume and integrating it, ready for fragment shaders to read.
     *
     * @param commandBuffer The frame's command buffer, outside any rendering.
     * @param state The Vulkan state.
     * @param lightSets The light stage's sets 0 to 3, as it binds them this frame.
     * @param width The drawn area's width, in pixels.
     * @param height Its height.
     * @param scene The scene, for its camera and view distance.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            long @NonNull [] lightSets,
            int width,
            int height,
            @NonNull Scene scene) {
        final FogVolume volume = state.fogVolume;
        final double viewDistance = scene.getViewDistance();
        final Matrix4fc invProjection = scene.getProjection().getInverseJitteredProjectionMatrix();
        final Matrix4f view = scene.getCamera().getViewMatrix();
        final Matrix4f projection = scene.getProjection().getProjectionMatrix();
        final Vector3d camera = new Vector3d(scene.getCamera().getPosition());
        final Vector3d cameraMove = new Vector3d(camera).sub(previousCamera);
        // The frames before only blend in where they hold the same place on the same screen
        final boolean history =
                volume.isHistoryValid()
                        && width == previousWidth
                        && height == previousHeight
                        && cameraMove.length() <= MAX_CAMERA_MOVE;
        final int tilesX = Math.min(FogVolume.tiles(width), volume.getTilesX());
        final int tilesY = Math.min(FogVolume.tiles(height), volume.getTilesY());
        final int groupsX = (tilesX + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        final int groupsY = (tilesY + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // After an earlier frame's integration read the media and its surfaces read the fog
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, injectPipeline);
            LongBuffer sets = stack.mallocLong(lightSets.length + 1);
            sets.put(lightSets).put(volume.getInjectSet()).flip();
            vkCmdBindDescriptorSets(
                    commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, injectLayout, 0, sets, null);
            ByteBuffer inject = stack.calloc(INJECT_CONSTANTS_SIZE);
            // From this frame's render space to last frame's view, where it was when the camera was
            // where it was
            new Matrix4f(previousView)
                    .translate((float) cameraMove.x, (float) cameraMove.y, (float) cameraMove.z)
                    .get(INJECT_TO_PREVIOUS_OFFSET, inject);
            inject.putFloat(INJECT_PREVIOUS_PROJECTION_OFFSET, previousProjection.m00());
            inject.putFloat(
                    INJECT_PREVIOUS_PROJECTION_OFFSET + Float.BYTES, previousProjection.m11());
            inject.putFloat(
                    INJECT_PREVIOUS_PROJECTION_OFFSET + 2 * Float.BYTES, previousProjection.m20());
            inject.putFloat(
                    INJECT_PREVIOUS_PROJECTION_OFFSET + 3 * Float.BYTES, previousProjection.m21());
            inject.putInt(INJECT_TILES_OFFSET, tilesX)
                    .putInt(INJECT_TILES_OFFSET + Integer.BYTES, tilesY);
            inject.putFloat(INJECT_SCREEN_OFFSET, width);
            inject.putFloat(INJECT_SCREEN_OFFSET + Float.BYTES, height);
            final int sample = (int) (frames % JITTER_SAMPLES) + 1;
            for (int axis = 0; axis < JITTER_BASES.length; ++axis) {
                inject.putFloat(
                        INJECT_JITTER_OFFSET + axis * Float.BYTES,
                        (float) (TaaMath.halton(sample, JITTER_BASES[axis]) - 0.5));
            }
            inject.putFloat(INJECT_HISTORY_OFFSET, history ? HISTORY_WEIGHT : 0);
            inject.putFloat(
                    INJECT_PREVIOUS_LOG_SCALE_OFFSET,
                    VoxelOitMath.logScale(history ? previousViewDistance : viewDistance));
            inject.putLong(
                    INJECT_MEDIA_OFFSET,
                    writeMedia(state, scene.getMediumRegistry(), camera, viewDistance));
            vkCmdPushConstants(commandBuffer, injectLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, inject);
            vkCmdDispatch(commandBuffer, groupsX, groupsY, VoxelOitMath.SLICES);

            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, integratePipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    integrateLayout,
                    0,
                    stack.longs(volume.getIntegrateSet()),
                    null);
            ByteBuffer integrate = stack.calloc(INTEGRATE_CONSTANTS_SIZE);
            invProjection.get(0, integrate);
            integrate.putInt(INTEGRATE_TILES_OFFSET, tilesX);
            integrate.putInt(INTEGRATE_TILES_OFFSET + Integer.BYTES, tilesY);
            integrate.putFloat(INTEGRATE_SCREEN_OFFSET, width);
            integrate.putFloat(INTEGRATE_SCREEN_OFFSET + Float.BYTES, height);
            integrate.putFloat(INTEGRATE_LOG_SCALE_OFFSET, VoxelOitMath.logScale(viewDistance));
            vkCmdPushConstants(
                    commandBuffer, integrateLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, integrate);
            vkCmdDispatch(commandBuffer, groupsX, groupsY, 1);

            // The light stage, the sky and the transparent stages read the fog
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    stack);
        }
        volume.advance();
        ++frames;
        previousView.set(view);
        previousProjection.set(projection);
        previousCamera.set(camera);
        previousWidth = width;
        previousHeight = height;
        previousViewDistance = viewDistance;
    }

    /**
     * Write this frame's medium volumes, those that reach within the view distance, in render
     * space: for each, its middle and shape, the rotation from the world to its own frame, its half
     * size and edge, its albedo and density, its emission and noise scale, its noise's offset and
     * strength, and its anisotropy.
     *
     * @param state The Vulkan state.
     * @param registry The scene's medium volumes.
     * @param camera The camera's position, in the world.
     * @param viewDistance The view distance.
     * @return The buffer's device address.
     */
    private long writeMedia(
            @NonNull VulkanState state,
            @NonNull MediumRegistry registry,
            @NonNull Vector3d camera,
            double viewDistance) {
        registry.snapshot(media);
        SharedBuffer buffer = mediaBuffers[state.frameIndex];
        final long bytes = MEDIA_HEADER_SIZE + (long) media.size() * MEDIUM_SIZE;
        if (buffer == null) {
            buffer = SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            mediaBuffers[state.frameIndex] = buffer;
        }
        buffer.ensureCapacity(bytes, state);
        final long address = buffer.allocationInfo.pMappedData();
        final double seconds =
                ((System.nanoTime() - start) / NANOS_PER_SECOND) % NOISE_PERIOD_SECONDS;
        final Quaternionf toLocal = new Quaternionf();
        int count = 0;
        for (MediumVolume volume : media) {
            final double dx = volume.center().x() - camera.x;
            final double dy = volume.center().y() - camera.y;
            final double dz = volume.center().z() - camera.z;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) - volume.boundingRadius() > viewDistance
                    || volume.density() <= 0) {
                continue;
            }
            final long at = address + MEDIA_HEADER_SIZE + (long) count * MEDIUM_SIZE;
            final float[] values = {
                (float) dx,
                (float) dy,
                (float) dz,
                volume.shape().ordinal(),
                0,
                0,
                0,
                0,
                volume.halfSize().x(),
                volume.halfSize().y(),
                volume.halfSize().z(),
                volume.edge(),
                volume.albedo().x(),
                volume.albedo().y(),
                volume.albedo().z(),
                volume.density(),
                volume.emission().x(),
                volume.emission().y(),
                volume.emission().z(),
                volume.noiseScale(),
                (float) (volume.wind().x() * seconds),
                (float) (volume.wind().y() * seconds),
                (float) (volume.wind().z() * seconds),
                volume.noiseStrength(),
                volume.anisotropy(),
                0,
                0,
                0
            };
            volume.rotation().invert(toLocal);
            values[VEC4] = toLocal.x;
            values[VEC4 + 1] = toLocal.y;
            values[VEC4 + 2] = toLocal.z;
            values[VEC4 + 3] = toLocal.w;
            for (int i = 0; i < values.length; ++i) {
                MemoryUtil.memPutFloat(at + (long) i * Float.BYTES, values[i]);
            }
            ++count;
        }
        MemoryUtil.memPutInt(address, count);
        return buffer.deviceAddress;
    }

    /**
     * Note a frame without fog: the next frame with fog starts over, rather than blend in fog from
     * however long ago.
     *
     * @param state The Vulkan state.
     */
    public void skip(@NonNull VulkanState state) {
        state.fogVolume.forgetHistory();
    }

    /**
     * A global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
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
