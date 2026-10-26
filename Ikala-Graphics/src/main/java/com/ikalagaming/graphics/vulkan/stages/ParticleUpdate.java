package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.EmitterRegistry;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.ParticleMath;
import com.ikalagaming.graphics.vulkan.ParticlePool;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves the scene's particles through the frame and spawns new ones, on the GPU, before the
 * transparency passes draw them (see {@link ParticleDraw}). Each frame: the control pass sets up
 * the simulation's dispatch from last frame's alive count; the simulation moves each alive
 * particle, freeing the dead and listing the rest; the emission takes free particles for each
 * emitter's spawns and lists them too; and the control pass sets up the billboards' draw. Clearing
 * frees every particle first, and when the pool's anchor steps the simulation moves every particle
 * by the step.
 */
@Slf4j
public class ParticleUpdate implements RenderStage {

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** The bookkeeping shader. */
    @NonNull private final ShaderVulkan controlShader;

    /** The simulation shader. */
    @NonNull private final ShaderVulkan simulateShader;

    /** The emission shader. */
    @NonNull private final ShaderVulkan emitShader;

    /** The pipelines and their layouts, VK_NULL_HANDLE until set up. */
    private long controlLayout = VK_NULL_HANDLE;

    private long controlPipeline = VK_NULL_HANDLE;
    private long simulateLayout = VK_NULL_HANDLE;
    private long simulatePipeline = VK_NULL_HANDLE;
    private long emitLayout = VK_NULL_HANDLE;
    private long emitPipeline = VK_NULL_HANDLE;

    /** Each frame in flight's emitter records, or null before its first spawns. */
    private final SharedBuffer[] emitterBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** This frame's spawns, a scratch list. */
    private final List<EmitterRegistry.Spawn> spawns = new ArrayList<>();

    /** When the last frame was recorded, from {@link System#nanoTime()}, or 0 before the first. */
    private long lastNanos;

    /** Frames recorded, to seed each frame's spawns differently. */
    private int frames;

    /** How many particles this frame's emitter records spawn in all. */
    private int spawnTotal;

    /**
     * Set up the stage.
     *
     * @param controlShader The bookkeeping compute shader.
     * @param simulateShader The simulation compute shader.
     * @param emitShader The emission compute shader.
     */
    public ParticleUpdate(
            @NonNull ShaderVulkan controlShader,
            @NonNull ShaderVulkan simulateShader,
            @NonNull ShaderVulkan emitShader) {
        this.controlShader = controlShader;
        this.simulateShader = simulateShader;
        this.emitShader = emitShader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing particles");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            controlLayout =
                    createLayout(state, ShaderBindings.Particles.CONTROL_CONSTANTS_SIZE, stack);
            controlPipeline = createPipeline(state, controlShader, controlLayout, stack);
            simulateLayout =
                    createLayout(state, ShaderBindings.Particles.SIMULATE_CONSTANTS_SIZE, stack);
            simulatePipeline = createPipeline(state, simulateShader, simulateLayout, stack);
            emitLayout = createLayout(state, ShaderBindings.Particles.EMIT_CONSTANTS_SIZE, stack);
            emitPipeline = createPipeline(state, emitShader, emitLayout, stack);
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < emitterBuffers.length; ++i) {
            if (emitterBuffers[i] != null) {
                SharedBuffer.free(emitterBuffers[i], state);
                emitterBuffers[i] = null;
            }
        }
        for (long pipeline : new long[] {controlPipeline, simulatePipeline, emitPipeline}) {
            vkDestroyPipeline(state.device.logical, pipeline, null);
        }
        for (long layout : new long[] {controlLayout, simulateLayout, emitLayout}) {
            vkDestroyPipelineLayout(state.device.logical, layout, null);
        }
        controlPipeline = VK_NULL_HANDLE;
        simulatePipeline = VK_NULL_HANDLE;
        emitPipeline = VK_NULL_HANDLE;
        controlLayout = VK_NULL_HANDLE;
        simulateLayout = VK_NULL_HANDLE;
        emitLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final ParticlePool pool = state.particlePool;
        if (scene == null || pool == null) {
            return;
        }
        final long now = System.nanoTime();
        final double realSeconds = lastNanos == 0 ? 0 : (now - lastNanos) / NANOS_PER_SECOND;
        lastNanos = now;
        // Edited effect files reach their emitters before this frame's spawns
        scene.getEffectLibrary().checkIfDue(now);
        final ParticleClock clock = scene.getParticleClock();
        // What the GPU reported a frame or two ago, read from the mapped counts
        clock.report(pool.readLiveCount(), pool.readDropped());
        final double seconds = clock.advance(realSeconds);
        scene.getEmitterRegistry().collect(seconds, spawns);
        final boolean reset = pool.takeReset() | clock.takeClear();
        final Vector3d shift = new Vector3d();
        pool.follow(scene.getCamera().getPosition(), shift);
        ++frames;

        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // After last frame's billboards read the particles and the draw's arguments
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            if (reset) {
                control(
                        commandBuffer,
                        pool,
                        ShaderBindings.Particles.MODE_RESET,
                        groups(pool.getCapacity()),
                        stack);
                computeBarrier(commandBuffer, stack);
            }
            control(commandBuffer, pool, ShaderBindings.Particles.MODE_PREPARE, 1, stack);
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    stack);
            simulate(commandBuffer, pool, shift, seconds, stack);
            computeBarrier(commandBuffer, stack);
            final int emitters = writeEmitters(state, pool);
            if (emitters > 0) {
                emit(commandBuffer, state, pool, emitters, stack);
                computeBarrier(commandBuffer, stack);
            }
            control(commandBuffer, pool, ShaderBindings.Particles.MODE_FINISH, 1, stack);
            // The billboards read the particles, their list and the draw's arguments; the CPU
            // reads the counts back
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT
                            | VK_PIPELINE_STAGE_2_HOST_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT
                            | VK_ACCESS_2_HOST_READ_BIT,
                    stack);
        }
        pool.swap();
    }

    /**
     * Record the bookkeeping pass.
     *
     * @param commandBuffer The command buffer.
     * @param pool The pool.
     * @param mode What it does.
     * @param groups How many workgroups.
     * @param stack The stack to allocate on.
     */
    private void control(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull ParticlePool pool,
            int mode,
            int groups,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, controlPipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.CONTROL_CONSTANTS_SIZE);
        constants.putLong(
                ShaderBindings.Particles.CONTROL_CONTROL_OFFSET, pool.getControl().deviceAddress);
        constants.putLong(
                ShaderBindings.Particles.CONTROL_DEAD_OFFSET, pool.getDeadList().deviceAddress);
        constants.putInt(ShaderBindings.Particles.CONTROL_MODE_OFFSET, mode);
        constants.putInt(ShaderBindings.Particles.CONTROL_CAPACITY_OFFSET, pool.getCapacity());
        constants.putInt(ShaderBindings.Particles.CONTROL_CURRENT_OFFSET, pool.getCurrent());
        vkCmdPushConstants(commandBuffer, controlLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, groups, 1, 1);
    }

    /**
     * Record the simulation, dispatched by the alive count the control pass turned into groups.
     *
     * @param commandBuffer The command buffer.
     * @param pool The pool.
     * @param shift How far every particle moves for the anchor's step.
     * @param seconds How long the frame was, in particle time.
     * @param stack The stack to allocate on.
     */
    private void simulate(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull ParticlePool pool,
            @NonNull Vector3d shift,
            double seconds,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, simulatePipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.SIMULATE_CONSTANTS_SIZE);
        final int current = pool.getCurrent();
        final long[] addresses = {
            pool.getParticles().deviceAddress,
            pool.aliveListAddress(current),
            pool.aliveListAddress(1 - current),
            pool.getDeadList().deviceAddress,
            pool.getControl().deviceAddress
        };
        for (int i = 0; i < addresses.length; ++i) {
            constants.putLong(
                    ShaderBindings.Particles.SIMULATE_POOL_OFFSET + i * Long.BYTES, addresses[i]);
        }
        constants.putFloat(ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET, (float) shift.x);
        constants.putFloat(
                ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET + Float.BYTES, (float) shift.y);
        constants.putFloat(
                ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET + 2 * Float.BYTES, (float) shift.z);
        constants.putFloat(ShaderBindings.Particles.SIMULATE_SECONDS_OFFSET, (float) seconds);
        constants.putInt(ShaderBindings.Particles.SIMULATE_CURRENT_OFFSET, current);
        vkCmdPushConstants(
                commandBuffer, simulateLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatchIndirect(
                commandBuffer, pool.getControl().buffer, ParticleMath.CONTROL_DISPATCH);
    }

    /**
     * Record the emission over this frame's emitter records.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param pool The pool.
     * @param emitters How many records were written.
     * @param stack The stack to allocate on.
     */
    private void emit(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull ParticlePool pool,
            int emitters,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, emitPipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.EMIT_CONSTANTS_SIZE);
        final int next = 1 - pool.getCurrent();
        final long[] addresses = {
            pool.getParticles().deviceAddress,
            pool.aliveListAddress(next),
            pool.getDeadList().deviceAddress,
            pool.getControl().deviceAddress,
            emitterBuffers[state.frameIndex].deviceAddress
        };
        for (int i = 0; i < addresses.length; ++i) {
            constants.putLong(
                    ShaderBindings.Particles.EMIT_POOL_OFFSET + i * Long.BYTES, addresses[i]);
        }
        constants.putInt(ShaderBindings.Particles.EMIT_EMITTER_COUNT_OFFSET, emitters);
        constants.putInt(ShaderBindings.Particles.EMIT_TOTAL_OFFSET, spawnTotal);
        constants.putInt(ShaderBindings.Particles.EMIT_NEXT_OFFSET, next);
        vkCmdPushConstants(commandBuffer, emitLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, groups(spawnTotal), 1, 1);
    }

    /**
     * Write this frame's spawns as emitter records, each with its first spawn's index, and note
     * their total. Spawns past the pool's capacity in all are left out, since they couldn't fit.
     *
     * @param state The Vulkan state.
     * @param pool The pool.
     * @return How many records were written.
     */
    private int writeEmitters(@NonNull VulkanState state, @NonNull ParticlePool pool) {
        spawnTotal = 0;
        if (spawns.isEmpty()) {
            return 0;
        }
        final long bytes = (long) spawns.size() * ParticleMath.EMITTER_SIZE;
        SharedBuffer buffer = emitterBuffers[state.frameIndex];
        if (buffer == null) {
            buffer = SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            emitterBuffers[state.frameIndex] = buffer;
        }
        buffer.ensureCapacity(bytes, state);
        final long address = buffer.allocationInfo.pMappedData();
        final Vector3d anchor = new Vector3d(pool.getAnchor());
        final Vector3f direction = new Vector3f();
        int first = 0;
        int written = 0;
        for (EmitterRegistry.Spawn spawn : spawns) {
            if (first >= pool.getCapacity()) {
                break;
            }
            final int count = Math.min(spawn.count(), pool.getCapacity() - first);
            final ParticleEffect effect = spawn.effect();
            final long at = address + (long) written * ParticleMath.EMITTER_SIZE;
            putVector(
                    at + ParticleMath.EMITTER_POSITION,
                    (float) (spawn.position().x() - anchor.x),
                    (float) (spawn.position().y() - anchor.y),
                    (float) (spawn.position().z() - anchor.z));
            MemoryUtil.memPutInt(at + ParticleMath.EMITTER_POSITION + 3 * Float.BYTES, first);
            direction.set(effect.getDirection());
            if (direction.lengthSquared() > 0) {
                direction.normalize();
            } else {
                direction.set(0, 1, 0);
            }
            putVector(at + ParticleMath.EMITTER_DIRECTION, direction.x, direction.y, direction.z);
            MemoryUtil.memPutFloat(
                    at + ParticleMath.EMITTER_DIRECTION + 3 * Float.BYTES,
                    (float) Math.cos(Math.clamp(effect.getSpread(), 0, Math.PI)));
            putVector(
                    at + ParticleMath.EMITTER_ACCELERATION,
                    effect.getAcceleration().x(),
                    effect.getAcceleration().y(),
                    effect.getAcceleration().z());
            MemoryUtil.memPutFloat(
                    at + ParticleMath.EMITTER_ACCELERATION + 3 * Float.BYTES,
                    Math.max(0, effect.getDrag()));
            putFloats(
                    at + ParticleMath.EMITTER_LIFETIME,
                    Math.max(0, effect.getLifetimeMin()),
                    Math.max(0, effect.getLifetimeMax()),
                    effect.getSpeedMin(),
                    effect.getSpeedMax());
            putFloats(
                    at + ParticleMath.EMITTER_COLOR_START,
                    effect.getColorStart().x(),
                    effect.getColorStart().y(),
                    effect.getColorStart().z(),
                    effect.getColorStart().w());
            putFloats(
                    at + ParticleMath.EMITTER_COLOR_END,
                    effect.getColorEnd().x(),
                    effect.getColorEnd().y(),
                    effect.getColorEnd().z(),
                    effect.getColorEnd().w());
            putFloats(
                    at + ParticleMath.EMITTER_SIZES,
                    Math.max(0, effect.getSizeStart()),
                    Math.max(0, effect.getSizeEnd()),
                    Math.max(0, effect.getEmissive()),
                    Math.max(0, effect.getSpawnRadius()));
            MemoryUtil.memPutInt(at + ParticleMath.EMITTER_COUNT, count);
            MemoryUtil.memPutInt(
                    at + ParticleMath.EMITTER_COUNT + Integer.BYTES, frames * 0x9E3779B9 + written);
            first += count;
            ++written;
        }
        spawnTotal = first;
        return written;
    }

    /**
     * Write three floats.
     *
     * @param at Where.
     * @param x The first.
     * @param y The second.
     * @param z The third.
     */
    private static void putVector(long at, float x, float y, float z) {
        MemoryUtil.memPutFloat(at, x);
        MemoryUtil.memPutFloat(at + Float.BYTES, y);
        MemoryUtil.memPutFloat(at + 2 * Float.BYTES, z);
    }

    /**
     * Write four floats.
     *
     * @param at Where.
     * @param x The first.
     * @param y The second.
     * @param z The third.
     * @param w The fourth.
     */
    private static void putFloats(long at, float x, float y, float z, float w) {
        putVector(at, x, y, z);
        MemoryUtil.memPutFloat(at + 3 * Float.BYTES, w);
    }

    /**
     * Workgroups for a number of threads.
     *
     * @param threads The threads.
     * @return The workgroups, at least 1.
     */
    private static int groups(int threads) {
        return Math.max(
                1, (threads + ParticleMath.WORKGROUP_SIZE - 1) / ParticleMath.WORKGROUP_SIZE);
    }

    /**
     * A barrier between two compute passes on the pool.
     *
     * @param commandBuffer The command buffer.
     * @param stack The stack to allocate on.
     */
    private static void computeBarrier(
            @NonNull VkCommandBuffer commandBuffer, @NonNull MemoryStack stack) {
        barrier(
                commandBuffer,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                stack);
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

    /**
     * Create a compute pipeline layout with only push constants.
     *
     * @param state The Vulkan state.
     * @param constantsSize The size of its push constants.
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
}
