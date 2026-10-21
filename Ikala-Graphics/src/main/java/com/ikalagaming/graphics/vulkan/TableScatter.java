package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Writes scattered table entries with a compute dispatch, see {@code table_scatter.comp}, for
 * {@link DeviceTable} when its changed entries fall in too many separate runs for one copy. Render
 * thread.
 */
public class TableScatter {

    /** Where the packed entries' address is in the push constants. */
    private static final int SOURCE_OFFSET = 0;

    /** Where the indices' address is in the push constants. */
    private static final int INDICES_OFFSET = Long.BYTES;

    /** Where the table's address is in the push constants. */
    private static final int TABLE_OFFSET = 2 * Long.BYTES;

    /** Where the entry count is in the push constants. */
    private static final int COUNT_OFFSET = 3 * Long.BYTES;

    /** Where the words in an entry are in the push constants. */
    private static final int ENTRY_WORDS_OFFSET = COUNT_OFFSET + Integer.BYTES;

    /** The size of the push constants in bytes. */
    private static final int PUSH_CONSTANTS_SIZE = ENTRY_WORDS_OFFSET + Integer.BYTES;

    /** Entries a workgroup writes. Matches table_scatter.comp. */
    private static final int WORKGROUP_SIZE = 64;

    /** VkPipelineLayout pointer. */
    private final long pipelineLayout;

    /** VkPipeline pointer. */
    private final long pipeline;

    /**
     * Make the pipeline.
     *
     * @param state The Vulkan state.
     * @param shader The scatter compute shader.
     */
    public TableScatter(@NonNull VulkanState state, @NonNull ShaderVulkan shader) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pPushConstantRanges(ranges),
                            null,
                            longOutput));
            pipelineLayout = longOutput.get(0);
            VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
            infos.get(0).sType$Default().stage(shader.shaderStages.get(0)).layout(pipelineLayout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, infos, null, longOutput));
            pipeline = longOutput.get(0);
        }
    }

    /**
     * Record the scatter. The caller orders it against what reads and writes the table.
     *
     * @param commandBuffer The command buffer, outside a render pass.
     * @param source The packed entries' device address.
     * @param indices The indices' device address, a uint each.
     * @param table The table's device address.
     * @param count How many entries.
     * @param entrySize The size of an entry, in bytes, a multiple of 4.
     * @param stack The stack to allocate on.
     */
    void record(
            @NonNull VkCommandBuffer commandBuffer,
            long source,
            long indices,
            long table,
            int count,
            int entrySize,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        ByteBuffer constants = stack.calloc(PUSH_CONSTANTS_SIZE);
        constants.putLong(SOURCE_OFFSET, source);
        constants.putLong(INDICES_OFFSET, indices);
        constants.putLong(TABLE_OFFSET, table);
        constants.putInt(COUNT_OFFSET, count);
        constants.putInt(ENTRY_WORDS_OFFSET, entrySize / Integer.BYTES);
        vkCmdPushConstants(
                commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, (count + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE, 1, 1);
    }

    /**
     * Destroy the pipeline. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
    }
}
