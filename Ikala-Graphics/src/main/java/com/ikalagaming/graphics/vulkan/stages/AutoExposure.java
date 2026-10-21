package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.vulkan.ExposureMath;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Automatic exposure, for the tone map stage: meters the scene color before tone mapping and adapts
 * to it over time, see {@code auto_exposure.comp} and {@link ExposureMath}. The exposure stays on
 * the GPU, in a small state buffer the tone map reads by its address; it is host visible so the
 * debug window can show it.
 */
public class AutoExposure {

    /** The size of the histogram, a uint a bin. */
    private static final int HISTOGRAM_SIZE = ExposureMath.HISTOGRAM_BINS * Integer.BYTES;

    /** The size of the state: adapted, exposure, valid, target. */
    private static final int STATE_SIZE = 4 * Float.BYTES;

    /** Where the exposure is in the state. */
    private static final int STATE_EXPOSURE_OFFSET = Float.BYTES;

    /** Where whether the state is valid is in it. */
    private static final int STATE_VALID_OFFSET = 2 * Float.BYTES;

    /** Where this frame's target is in the state. */
    private static final int STATE_TARGET_OFFSET = 3 * Float.BYTES;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** The longest frame adapted over, so a stall doesn't jump the exposure. */
    private static final double MAX_FRAME_SECONDS = 0.1;

    /** The shader. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout = VK_NULL_HANDLE;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline = VK_NULL_HANDLE;

    /** The histogram, device local, cleared by the adapt pass. */
    private SharedBuffer histogram;

    /** The adapted state, host visible. */
    private SharedBuffer exposureState;

    /** When the last frame metered, from {@link System#nanoTime()}, or 0 for none yet. */
    private long lastNanos;

    /**
     * Set up the helper.
     *
     * @param shader The auto exposure compute shader.
     */
    public AutoExposure(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    /**
     * Make the pipeline and buffers.
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
                    .size(ShaderBindings.AutoExposure.PUSH_CONSTANTS_SIZE);
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(state.bindlessTextures.getDescriptorSetLayout()))
                            .pPushConstantRanges(ranges);
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
        histogram =
                SharedBuffer.allocateDeviceLocal(
                        HISTOGRAM_SIZE, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        exposureState =
                SharedBuffer.allocate(STATE_SIZE, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        MemoryUtil.memSet(exposureState.allocationInfo.pMappedData(), 0, STATE_SIZE);
        state.immediateCommands.submit(
                state,
                commandBuffer ->
                        vkCmdFillBuffer(commandBuffer, histogram.buffer, 0, HISTOGRAM_SIZE, 0));
    }

    /**
     * Free the pipeline and buffers. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        SharedBuffer.free(histogram, state);
        SharedBuffer.free(exposureState, state);
    }

    /**
     * The state buffer's address, for the tone map to read the exposure from.
     *
     * @return The device address.
     */
    public long getStateAddress() {
        return exposureState.deviceAddress;
    }

    /**
     * The exposure last worked out, for the debug window.
     *
     * @return The exposure, before compensation, or NaN before any.
     */
    public float getExposure() {
        final long base = exposureState.allocationInfo.pMappedData();
        return MemoryUtil.memGetInt(base + STATE_VALID_OFFSET) != 0
                ? MemoryUtil.memGetFloat(base + STATE_EXPOSURE_OFFSET)
                : Float.NaN;
    }

    /**
     * The average log2 luminance metered last, for the debug window.
     *
     * @return The log2 luminance, or NaN before any.
     */
    public float getMetered() {
        final long base = exposureState.allocationInfo.pMappedData();
        return MemoryUtil.memGetInt(base + STATE_VALID_OFFSET) != 0
                ? MemoryUtil.memGetFloat(base + STATE_TARGET_OFFSET)
                : Float.NaN;
    }

    /**
     * Forget what was adapted to, while automatic exposure is off, so it starts at once from the
     * scene when it is turned on.
     */
    public void reset() {
        MemoryUtil.memPutInt(exposureState.allocationInfo.pMappedData() + STATE_VALID_OFFSET, 0);
        lastNanos = 0;
    }

    /**
     * Meter the scene color and adapt to it. The color must be readable by compute shaders.
     *
     * @param commandBuffer The command buffer, outside a render pass.
     * @param state The Vulkan state.
     * @param source The scene color.
     * @param width Its width to meter.
     * @param height Its height to meter.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull TextureInfoVulkan source,
            int width,
            int height) {
        final long now = System.nanoTime();
        final double seconds =
                lastNanos == 0
                        ? 0
                        : Math.min((now - lastNanos) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
        lastNanos = now;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Last frame's tone map read the state, and its adapt pass cleared the histogram
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    0,
                    stack.longs(state.bindlessTextures.getDescriptorSet()),
                    null);
            ByteBuffer constants = stack.calloc(ShaderBindings.AutoExposure.PUSH_CONSTANTS_SIZE);
            constants.putLong(
                    ShaderBindings.AutoExposure.HISTOGRAM_OFFSET, histogram.deviceAddress);
            constants.putLong(
                    ShaderBindings.AutoExposure.STATE_OFFSET, exposureState.deviceAddress);
            constants.putInt(
                    ShaderBindings.AutoExposure.SCENE_COLOR_INDEX_OFFSET, source.bindlessIndex);
            constants.putInt(ShaderBindings.AutoExposure.WIDTH_OFFSET, width);
            constants.putInt(ShaderBindings.AutoExposure.HEIGHT_OFFSET, height);
            constants.putFloat(ShaderBindings.AutoExposure.DELTA_SECONDS_OFFSET, (float) seconds);
            constants.putFloat(ShaderBindings.AutoExposure.SPEED_UP_OFFSET, ExposureMath.SPEED_UP);
            constants.putFloat(
                    ShaderBindings.AutoExposure.SPEED_DOWN_OFFSET, ExposureMath.SPEED_DOWN);
            constants.putFloat(
                    ShaderBindings.AutoExposure.MIN_EXPOSURE_OFFSET, ExposureMath.MIN_EXPOSURE);
            constants.putFloat(
                    ShaderBindings.AutoExposure.MAX_EXPOSURE_OFFSET, ExposureMath.MAX_EXPOSURE);

            constants.putInt(
                    ShaderBindings.AutoExposure.MODE_OFFSET,
                    ShaderBindings.AutoExposure.MODE_HISTOGRAM);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            final int group = ShaderBindings.AutoExposure.WORKGROUP_SIZE;
            vkCmdDispatch(
                    commandBuffer, (width + group - 1) / group, (height + group - 1) / group, 1);
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);

            constants.putInt(
                    ShaderBindings.AutoExposure.MODE_OFFSET,
                    ShaderBindings.AutoExposure.MODE_ADAPT);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            vkCmdDispatch(commandBuffer, 1, 1, 1);
            // The tone map reads the exposure
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_HOST_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_HOST_READ_BIT,
                    stack);
        }
    }

    /**
     * Order work against earlier work.
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
