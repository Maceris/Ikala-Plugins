package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Culls every instance on the GPU and fills in the draw commands. For each instance and pass, each
 * of the instance's meshes that may be visible is added to that mesh's draw command, and its slot
 * and material written into the room set aside for that mesh in a visible list. The scene and
 * shadow stages then draw each list with one indirect call.
 *
 * <p>There are two of these stages, one for each phase, see {@code cull.comp}. The early one culls
 * the shadow cascades by their frusta and the scene by last frame's visibility, before anything is
 * drawn. The late one runs after the depth pyramid is built, tests the scene against it, and only
 * runs while occlusion culling is on.
 */
@Slf4j
public class InstanceCull implements RenderStage {

    /** The shader to run. */
    @NonNull private final ShaderVulkan shader;

    /** Which phase this is, {@link ShaderBindings.Cull#PHASE_EARLY} or {@code PHASE_LATE}. */
    private final int phase;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the stage.
     *
     * @param shader The culling compute shader.
     * @param phase Which phase this is, {@link ShaderBindings.Cull#PHASE_EARLY} or {@link
     *     ShaderBindings.Cull#PHASE_LATE}.
     */
    public InstanceCull(@NonNull ShaderVulkan shader, int phase) {
        this.shader = shader;
        this.phase = phase;
        pipelineLayout = VK_NULL_HANDLE;
        pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing instance culling, phase {}", phase);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Cull.PUSH_CONSTANTS_SIZE);
            // The buffers are all passed by device address, the only set is the depth pyramid
            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(stack.longs(state.depthPyramid.getReadSetLayout()))
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
        final int slots = frameData.instanceCount;
        if (slots == 0
                || frameData.meshSlotCount == 0
                || (phase == ShaderBindings.Cull.PHASE_LATE && !frameData.occlusion)) {
            return;
        }
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    0,
                    stack.longs(state.depthPyramid.getReadSet()),
                    null);
            ByteBuffer constants = stack.calloc(ShaderBindings.Cull.PUSH_CONSTANTS_SIZE);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_INSTANCES_OFFSET,
                    state.instances.getInstances().getBuffer().deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_MODEL_MESHES_OFFSET,
                    state.instances.getModelMeshes().getBuffer().deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_OVERRIDES_OFFSET,
                    state.instances.getOverrides().getBuffer().deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_MESHES_OFFSET,
                    state.geometry.getMeshTable().getBuffer().deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_MATRICES_OFFSET,
                    frameData.sceneModelMatrices.deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_COMMANDS_OFFSET,
                    frameData.sceneDrawCommands.deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_VISIBLE_OFFSET,
                    frameData.visibleInstances.deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_FRUSTA_OFFSET,
                    frameData.cullFrusta.deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_COUNTERS_OFFSET,
                    frameData.cullCounters.deviceAddress);
            constants.putInt(ShaderBindings.Cull.PUSH_CONSTANT_SLOT_COUNT_OFFSET, slots);
            constants.putInt(
                    ShaderBindings.Cull.PUSH_CONSTANT_MESH_SLOT_COUNT_OFFSET,
                    frameData.meshSlotCount);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_HISTORY_OFFSET,
                    state.instances.getHistory().getBuffer().deviceAddress);
            constants.putLong(
                    ShaderBindings.Cull.PUSH_CONSTANT_VIEW_OFFSET,
                    frameData.cullView.deviceAddress);
            constants.putInt(ShaderBindings.Cull.PUSH_CONSTANT_PHASE_OFFSET, phase);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            final int groups =
                    (slots + ShaderBindings.Cull.WORKGROUP_SIZE - 1)
                            / ShaderBindings.Cull.WORKGROUP_SIZE;
            // Early, the scene and every cascade; late, only the scene
            final int passes =
                    phase == ShaderBindings.Cull.PHASE_EARLY ? InstanceDrawUpdate.PASS_COUNT : 1;
            vkCmdDispatch(commandBuffer, groups, passes, 1);

            // Draws read the commands and visible lists, the CPU reads the counters later, and
            // next frame's culling reads the visibility history
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(
                            VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT
                                    | VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                                    | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                                    | VK_PIPELINE_STAGE_2_HOST_BIT)
                    .dstAccessMask(
                            VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
                                    | VK_ACCESS_2_HOST_READ_BIT);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
        }
    }
}
