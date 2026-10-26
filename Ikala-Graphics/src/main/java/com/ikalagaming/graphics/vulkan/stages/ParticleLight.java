package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.ParticleMath;
import com.ikalagaming.graphics.vulkan.ParticlePool;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Lights the particles the billboards draw this frame, once each, see {@code particle_light.comp}.
 * Run by {@link LightRender} once this frame's lights, clusters and shadows are in place, with the
 * sets it binds for the fog; {@link ParticleUpdate} left the dispatch's size, from the drawn count,
 * in the pool's control buffer.
 */
@Slf4j
public class ParticleLight {

    /** The lighting shader. */
    @NonNull private final ShaderVulkan shader;

    /** The pipeline and its layout, VK_NULL_HANDLE until set up. */
    private long layout = VK_NULL_HANDLE;

    private long pipeline = VK_NULL_HANDLE;

    /**
     * Set up the lighting.
     *
     * @param shader The lighting compute shader.
     */
    public ParticleLight(@NonNull ShaderVulkan shader) {
        this.shader = shader;
    }

    /**
     * Create the pipeline.
     *
     * @param state The Vulkan state.
     * @param lightSetLayout The light stage's own set's layout, set 0.
     */
    public void initialize(@NonNull VulkanState state, long lightSetLayout) {
        log.debug("Initializing particle lighting");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Particles.LIGHT_CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(
                                            stack.longs(
                                                    lightSetLayout,
                                                    state.bindlessTextures.getDescriptorSetLayout(),
                                                    state.occupancy.getSetLayout(),
                                                    state.environment.getSetLayout()))
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

    /**
     * Destroy the pipeline.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyPipeline(state.device.logical, pipeline, null);
        vkDestroyPipelineLayout(state.device.logical, layout, null);
        pipeline = VK_NULL_HANDLE;
        layout = VK_NULL_HANDLE;
    }

    /**
     * Record lighting the drawn particles, ready for the billboards to read.
     *
     * @param commandBuffer The frame's command buffer, outside any rendering.
     * @param state The Vulkan state.
     * @param lightSets The light stage's sets 0 to 3, as it binds them this frame.
     * @param scene The scene, for the camera.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            long @NonNull [] lightSets,
            @NonNull Scene scene) {
        final ParticlePool pool = state.particlePool;
        if (pool == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // After last frame's billboards read the lights
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    layout,
                    0,
                    stack.longs(lightSets),
                    null);
            final Vector3d anchor =
                    new Vector3d(pool.getAnchor()).sub(scene.getCamera().getPosition());
            ByteBuffer constants = stack.calloc(ShaderBindings.Particles.LIGHT_CONSTANTS_SIZE);
            final long[] addresses = {
                pool.getParticles().deviceAddress,
                pool.aliveListAddress(pool.getCurrent()),
                pool.getControl().deviceAddress,
                pool.getRadiance().deviceAddress
            };
            for (int i = 0; i < addresses.length; ++i) {
                constants.putLong(
                        ShaderBindings.Particles.LIGHT_POOL_OFFSET + i * Long.BYTES, addresses[i]);
            }
            constants.putFloat(ShaderBindings.Particles.LIGHT_ANCHOR_OFFSET, (float) anchor.x);
            constants.putFloat(
                    ShaderBindings.Particles.LIGHT_ANCHOR_OFFSET + Float.BYTES, (float) anchor.y);
            constants.putFloat(
                    ShaderBindings.Particles.LIGHT_ANCHOR_OFFSET + 2 * Float.BYTES,
                    (float) anchor.z);
            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
            vkCmdDispatchIndirect(
                    commandBuffer, pool.getControl().buffer, ParticleMath.CONTROL_DISPATCH);
            // The billboards read the lights
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    stack);
        }
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
