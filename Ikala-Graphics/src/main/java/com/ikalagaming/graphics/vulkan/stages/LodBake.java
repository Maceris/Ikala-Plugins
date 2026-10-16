package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.LodBakes;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;

/**
 * Bakes level of detail clusters' appearance into their tiles, see {@link LodBakes}: a budget of
 * tiles a frame, before anything draws, children before parents, with a barrier wherever the level
 * goes up so parents see their children's tiles.
 */
@Slf4j
public class LodBake implements RenderStage {

    /** The most texels baked in a frame, though a frame always bakes at least one tile. */
    private static final long TEXELS_PER_FRAME = 1L << 22;

    /** The size of the push constants: two addresses, the tile's corner and side, padding. */
    private static final int CONSTANTS_SIZE = 2 * Long.BYTES + 4 * Integer.BYTES;

    /** Threads in a workgroup, see lod_bake.comp. */
    private static final int GROUP = 64;

    /** The bake shader. */
    @NonNull private final ShaderVulkan shader;

    /** VkPipelineLayout of the bake pass. */
    private long layout;

    /** VkPipeline of the bake pass. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The bake compute shader.
     */
    public LodBake(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing level of detail bakes");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer output = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(
                                            stack.longs(
                                                    state.lodBakes.getSetLayout(state),
                                                    state.bindlessTextures
                                                            .getDescriptorSetLayout()))
                                    .pPushConstantRanges(ranges),
                            null,
                            output));
            layout = output.get(0);
            VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
            infos.get(0).sType$Default().stage(shader.shaderStages.get(0)).layout(layout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, infos, null, output));
            pipeline = output.get(0);
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, layout, null);
    }

    @Override
    public void render(
            Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig) {
        final LodBakes bakes = state.lodBakes;
        if (bakes == null || !bakes.hasJobs()) {
            return;
        }
        final List<LodBakes.Job> jobs = bakes.take(state, TEXELS_PER_FRAME);
        if (jobs.isEmpty()) {
            return;
        }
        final long[] offsets = new long[jobs.size() * 2];
        final SharedBuffer buffer = bakes.write(state.frameIndex, state, jobs, offsets);
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Earlier frames sample the pages; this writes other tiles of them
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            final ByteBuffer constants = stack.calloc(CONSTANTS_SIZE);
            int level = jobs.get(0).level();
            for (int i = 0; i < jobs.size(); ++i) {
                final LodBakes.Job job = jobs.get(i);
                if (job.level() != level) {
                    // Parents sample what their children's bakes just wrote
                    barrier(
                            commandBuffer,
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                            VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                            VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                            stack);
                    level = job.level();
                }
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_COMPUTE,
                        layout,
                        0,
                        stack.longs(bakes.setOf(job), state.bindlessTextures.getDescriptorSet()),
                        null);
                constants.putLong(0, buffer.deviceAddress + offsets[i * 2 + 1]);
                constants.putLong(Long.BYTES, buffer.deviceAddress + offsets[i * 2]);
                constants.putInt(2 * Long.BYTES, job.tile().x());
                constants.putInt(2 * Long.BYTES + Integer.BYTES, job.tile().y());
                constants.putInt(2 * Long.BYTES + 2 * Integer.BYTES, job.tile().size());
                vkCmdPushConstants(
                        commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
                final int texels = job.tile().size() * job.tile().size();
                vkCmdDispatch(commandBuffer, (texels + GROUP - 1) / GROUP, 1, 1);
            }
            // What was baked is drawn this frame
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
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
}
