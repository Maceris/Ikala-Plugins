package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Projection;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.LightRegistry;
import com.ikalagaming.graphics.vulkan.ClusterMath;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Writes this frame's point lights and spotlights into the light buffer, then sorts them into the
 * clusters of the view on the GPU, so the light stage only looks at the lights that can reach each
 * pixel. See {@link ClusterMath} and {@code light_cull.comp}.
 */
@Slf4j
public class LightCull implements RenderStage {

    /**
     * What light culling found in a frame, for the debug window.
     *
     * @param lights How many lights there were.
     * @param busiest The most lights any one cluster touched.
     * @param overflowing How many clusters touched more lights than they can list.
     * @param listed How many lights were listed across every cluster.
     */
    public record Stats(int lights, int busiest, int overflowing, long listed) {
        /** Before any frame has been read back. */
        public static final Stats NONE = new Stats(0, 0, 0, 0);
    }

    /** The shader to run. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The light culling compute shader.
     */
    public LightCull(@NonNull ShaderVulkan shader) {
        this.shader = shader;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing light culling");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.LightCull.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pPushConstantRanges(pushConstantRanges);
            checkError(vkCreatePipelineLayout(state.device.logical, layoutInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkComputePipelineCreateInfo.Buffer pipelineInfos =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfos
                    .get(0)
                    .sType$Default()
                    .stage(shader.shaderStages.get(0))
                    .layout(pipelineLayout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, pipelineInfos, null, longOutput));
            pipeline = longOutput.get(0);
        }
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
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];

        // The fence for this frame's last use was waited on, so its stats are in
        readStats(state, frameData);
        frameData.lightCount = updateLights(scene, state, frameData);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer constants = stack.calloc(ShaderBindings.LightCull.PUSH_CONSTANTS_SIZE);
            scene.getProjection()
                    .getInverseProjectionMatrix()
                    .get(
                            ShaderBindings.LightCull.PUSH_CONSTANT_INVERSE_PROJECTION_OFFSET,
                            constants);
            constants.putLong(
                    ShaderBindings.LightCull.PUSH_CONSTANT_LIGHTS_OFFSET,
                    frameData.lights.deviceAddress);
            constants.putLong(
                    ShaderBindings.LightCull.PUSH_CONSTANT_CLUSTERS_OFFSET,
                    frameData.lightClusters.deviceAddress);
            constants.putLong(
                    ShaderBindings.LightCull.PUSH_CONSTANT_STATS_OFFSET,
                    frameData.lightClusterStats.deviceAddress);
            constants.putInt(
                    ShaderBindings.LightCull.PUSH_CONSTANT_LIGHT_COUNT_OFFSET,
                    frameData.lightCount);
            constants.putFloat(
                    ShaderBindings.LightCull.PUSH_CONSTANT_LOG_SCALE_OFFSET,
                    ClusterMath.logScale(scene.getViewDistance()));
            constants.putFloat(
                    ShaderBindings.LightCull.PUSH_CONSTANT_FAR_PLANE_OFFSET, Projection.Z_FAR);

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            final int groupSize = ShaderBindings.LightCull.WORKGROUP_SIZE;
            vkCmdDispatch(commandBuffer, (ClusterMath.COUNT + groupSize - 1) / groupSize, 1, 1);

            // The light stage reads the clusters, and the CPU reads the stats a frame later
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(
                            VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_HOST_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_HOST_READ_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
        }
    }

    /**
     * Read back what the last culling with this frame's buffers found, and clear the stats for this
     * frame. The stats buffer is host coherent.
     *
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private static void readStats(@NonNull VulkanState state, @NonNull PerFrameData frameData) {
        IntBuffer stats =
                MemoryUtil.memIntBuffer(
                        frameData.lightClusterStats.allocationInfo.pMappedData(),
                        ShaderBindings.LightCull.STATS_COUNT);
        state.lightStats =
                new Stats(
                        frameData.lightCount,
                        stats.get(ShaderBindings.LightCull.STATS_BUSIEST),
                        stats.get(ShaderBindings.LightCull.STATS_OVERFLOWING),
                        Integer.toUnsignedLong(stats.get(ShaderBindings.LightCull.STATS_LISTED)));
        for (int i = 0; i < ShaderBindings.LightCull.STATS_COUNT; ++i) {
            stats.put(i, 0);
        }
    }

    /**
     * Write the point lights and spotlights, in view space, into this frame's buffer.
     *
     * @param scene The scene to fetch lights from.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     * @return How many lights were written.
     */
    private static int updateLights(
            @NonNull Scene scene, @NonNull VulkanState state, @NonNull PerFrameData frameData) {
        final LightRegistry registry = scene.getLightRegistry();
        // Lights added after this are left for the next frame, so the buffer can't overflow
        final int capacity = registry.getLightCount();
        frameData.lights.ensureCapacity(
                (long) Math.max(capacity, 1) * ShaderBindings.Light.LightStruct.SIZEOF, state);
        if (capacity == 0) {
            return 0;
        }

        final ByteBuffer buffer =
                MemoryUtil.memByteBuffer(
                        frameData.lights.allocationInfo.pMappedData(),
                        capacity * ShaderBindings.Light.LightStruct.SIZEOF);
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        final Vector3dc origin = scene.getCamera().getPosition();
        final Vector4f scratch = new Vector4f();
        final int[] written = {0};
        registry.visit(
                light -> {
                    if (written[0] < capacity) {
                        putLight(
                                buffer,
                                written[0] * ShaderBindings.Light.LightStruct.SIZEOF,
                                light,
                                viewMatrix,
                                origin,
                                scratch);
                        written[0] += 1;
                    }
                });
        return written[0];
    }

    /**
     * Put a light struct into the lights buffer, with the position and direction converted to view
     * space.
     *
     * @param buffer The buffer to write into.
     * @param offset Where the struct starts, in bytes.
     * @param light The light.
     * @param viewMatrix The camera view matrix, from render space to view space.
     * @param origin The world position of the render space origin, the camera position.
     * @param scratch A vector to do math in, so we don't allocate one per light.
     */
    private static void putLight(
            @NonNull ByteBuffer buffer,
            int offset,
            @NonNull LightRegistry.View light,
            @NonNull Matrix4f viewMatrix,
            @NonNull Vector3dc origin,
            @NonNull Vector4f scratch) {
        // Relative to the camera in double precision first, so it stays exact far from the origin
        final Vector3dc position = light.position();
        scratch.set(
                (float) (position.x() - origin.x()),
                (float) (position.y() - origin.y()),
                (float) (position.z() - origin.z()),
                1);
        scratch.mul(viewMatrix);
        putVector(buffer, offset + ShaderBindings.Light.LightStruct.POSITION, scratch);
        buffer.putFloat(offset + ShaderBindings.Light.LightStruct.RANGE, light.range());
        light.color().get(offset + ShaderBindings.Light.LightStruct.COLOR, buffer);
        buffer.putFloat(offset + ShaderBindings.Light.LightStruct.INTENSITY, light.intensity());

        // A direction, so w is 0 and the camera translation (none in render space) never applies
        scratch.set(light.direction(), 0);
        scratch.mul(viewMatrix);
        putVector(buffer, offset + ShaderBindings.Light.LightStruct.DIRECTION, scratch);
        buffer.putFloat(offset + ShaderBindings.Light.LightStruct.COS_OUTER, light.cosOuter());
        buffer.putFloat(offset + ShaderBindings.Light.LightStruct.COS_INNER, light.cosInner());
        buffer.putInt(
                offset + ShaderBindings.Light.LightStruct.TYPE, light.type().getShaderValue());
    }

    /**
     * Put the x, y and z of a vector into a buffer.
     *
     * @param buffer The buffer.
     * @param offset Where x goes, in bytes.
     * @param vector The vector.
     */
    private static void putVector(
            @NonNull ByteBuffer buffer, int offset, @NonNull Vector4f vector) {
        buffer.putFloat(offset, vector.x);
        buffer.putFloat(offset + Float.BYTES, vector.y);
        buffer.putFloat(offset + 2 * Float.BYTES, vector.z);
    }
}
