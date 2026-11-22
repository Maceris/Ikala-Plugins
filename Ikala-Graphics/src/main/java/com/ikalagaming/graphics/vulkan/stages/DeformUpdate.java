package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.Commands;
import com.ikalagaming.graphics.vulkan.DeformMath;
import com.ikalagaming.graphics.vulkan.DeformationField;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Keeps the deformation field, see {@link DeformationField}: each frame it moves the window to the
 * camera and clears what newly comes into it, fills prints back in, and presses the stamps queued,
 * with barriers between, so the passes after read this frame's prints.
 */
@Slf4j
public class DeformUpdate implements RenderStage {

    /** The push constants: three addresses, the origin, mode, count, time, and the rectangle. */
    private static final int CONSTANTS_SIZE = 64;

    /** Where in the push constants things are, see {@code deform.comp}. */
    private static final int ORIGIN_OFFSET = 3 * Long.BYTES;

    private static final int MODE_OFFSET = ORIGIN_OFFSET + 2 * Integer.BYTES;

    private static final int COUNT_OFFSET = MODE_OFFSET + Integer.BYTES;

    private static final int SECONDS_OFFSET = COUNT_OFFSET + Integer.BYTES;

    private static final int RECT_OFFSET = 48;

    /** The passes' modes. */
    private static final int MODE_CLEAR = 0;

    private static final int MODE_REFILL = 1;

    private static final int MODE_STAMP = 2;

    /** Texels a workgroup covers along each axis. */
    private static final int GROUP = 8;

    /** The shader. */
    @NonNull private final ShaderVulkan shader;

    /** The pipeline layout and pipeline, VK_NULL_HANDLE until set up. */
    private long layout = VK_NULL_HANDLE;

    private long pipeline = VK_NULL_HANDLE;

    /**
     * Set up the stage.
     *
     * @param shader The deformation compute shader.
     */
    public DeformUpdate(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing the deformation field");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(CONSTANTS_SIZE);
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
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, layout, null);
        pipeline = VK_NULL_HANDLE;
        layout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final DeformationField field = state.deformation;
        if (scene == null || field == null || state.sections == null) {
            return;
        }
        final int frame = state.frameIndex;
        final DeformationField.Frame work =
                field.prepare(
                        state, frame, scene.getCamera().getPosition(), state.sections.surfaces);
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[frame];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            final ByteBuffer constants = stack.calloc(CONSTANTS_SIZE);
            constants.putLong(0, field.getField().deviceAddress);
            constants.putLong(Long.BYTES, work.table());
            constants.putLong(2 * Long.BYTES, work.stampAddress());
            constants.putInt(ORIGIN_OFFSET, (int) field.getOriginX());
            constants.putInt(ORIGIN_OFFSET + Integer.BYTES, (int) field.getOriginZ());
            constants.putInt(COUNT_OFFSET, work.stamps());
            constants.putFloat(SECONDS_OFFSET, work.seconds());
            // The passes after, of earlier frames, may still read it
            barrier(commandBuffer, VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
            constants.putInt(MODE_OFFSET, MODE_CLEAR);
            for (DeformationField.Clear clear : work.clears()) {
                constants.putInt(RECT_OFFSET, (int) clear.x());
                constants.putInt(RECT_OFFSET + Integer.BYTES, (int) clear.z());
                constants.putInt(RECT_OFFSET + 2 * Integer.BYTES, clear.width());
                constants.putInt(RECT_OFFSET + 3 * Integer.BYTES, clear.depth());
                vkCmdPushConstants(
                        commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                vkCmdDispatch(commandBuffer, groups(clear.width()), groups(clear.depth()), 1);
            }
            if (!work.clears().isEmpty()) {
                barrier(commandBuffer, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT);
            }
            if (work.refill()) {
                constants.putInt(MODE_OFFSET, MODE_REFILL);
                vkCmdPushConstants(
                        commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                vkCmdDispatch(commandBuffer, groups(DeformMath.SIZE), groups(DeformMath.SIZE), 1);
            }
            if (work.stamps() > 0) {
                barrier(commandBuffer, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT);
                constants.putInt(MODE_OFFSET, MODE_STAMP);
                vkCmdPushConstants(
                        commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                vkCmdDispatch(commandBuffer, work.stampGroups(), 1, 1);
            }
            // For the passes after, and the next frame's
            Commands.memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
        }
    }

    /**
     * A barrier between compute passes writing and reading the field.
     *
     * @param commandBuffer The command buffer.
     * @param after The stages that wrote or read it before.
     */
    private static void barrier(@NonNull VkCommandBuffer commandBuffer, long after) {
        Commands.memoryBarrier(
                commandBuffer,
                after,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
    }

    /**
     * Workgroups to cover some texels.
     *
     * @param texels The texels.
     * @return The workgroups.
     */
    private static int groups(int texels) {
        return (texels + GROUP - 1) / GROUP;
    }
}
