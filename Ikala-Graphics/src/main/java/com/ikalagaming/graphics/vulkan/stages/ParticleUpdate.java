package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
import com.ikalagaming.graphics.scene.EmitterRegistry;
import com.ikalagaming.graphics.scene.ParticleClock;
import com.ikalagaming.graphics.scene.ParticleEffect;
import com.ikalagaming.graphics.scene.ParticleEffectLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.ParticleMath;
import com.ikalagaming.graphics.vulkan.ParticlePool;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
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
 * emitter's spawns and lists them too; and the control pass sets up the billboards' draw. Between
 * the simulation and the emission, the splash pass spawns splashes where particles hit the world.
 * Linked emitters' particles look up a links table, written each frame with the sky occlusion map
 * as it was last drawn, for spawn boxes, weather around the camera and splashes. Clearing frees
 * every particle first, and when the pool's anchor steps the simulation moves every particle by the
 * step.
 */
@Slf4j
public class ParticleUpdate implements RenderStage {

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** What seeds step by from frame to frame, the golden ratio in 32 bits, so they spread. */
    private static final int SEED_STEP = 0x9E3779B9;

    /** The bookkeeping shader. */
    @NonNull private final ShaderVulkan controlShader;

    /** The simulation shader. */
    @NonNull private final ShaderVulkan simulateShader;

    /** The splash shader. */
    @NonNull private final ShaderVulkan splashShader;

    /** The emission shader. */
    @NonNull private final ShaderVulkan emitShader;

    /** The pipelines and their layouts, VK_NULL_HANDLE until set up. */
    private long controlLayout = VK_NULL_HANDLE;

    private long controlPipeline = VK_NULL_HANDLE;
    private long simulateLayout = VK_NULL_HANDLE;
    private long simulatePipeline = VK_NULL_HANDLE;
    private long emitLayout = VK_NULL_HANDLE;
    private long emitPipeline = VK_NULL_HANDLE;
    private long splashLayout = VK_NULL_HANDLE;
    private long splashPipeline = VK_NULL_HANDLE;

    /** Each frame in flight's links table, or null before its first frame. */
    private final SharedBuffer[] linkBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The sky map's matrix from the anchor's space, scratch. */
    private final Matrix4f skyMatrix = new Matrix4f();

    /** Whether this frame's links have splashes, so the splash pass runs. */
    private boolean splashes;

    /** The camera's position this frame, which emitters around the camera follow. */
    private final Vector3d cameraPosition = new Vector3d();

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
     * @param splashShader The splash compute shader.
     * @param emitShader The emission compute shader.
     */
    public ParticleUpdate(
            @NonNull ShaderVulkan controlShader,
            @NonNull ShaderVulkan simulateShader,
            @NonNull ShaderVulkan splashShader,
            @NonNull ShaderVulkan emitShader) {
        this.controlShader = controlShader;
        this.simulateShader = simulateShader;
        this.splashShader = splashShader;
        this.emitShader = emitShader;
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing particles");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            controlLayout =
                    createLayout(
                            state, ShaderBindings.Particles.CONTROL_CONSTANTS_SIZE, null, stack);
            controlPipeline = createPipeline(state, controlShader, controlLayout, stack);
            // The occupancy volume, then the bindless textures for the sky occlusion map
            final LongBuffer worldSets =
                    stack.longs(
                            state.occupancy.getSetLayout(),
                            state.bindlessTextures.getDescriptorSetLayout());
            simulateLayout =
                    createLayout(
                            state,
                            ShaderBindings.Particles.SIMULATE_CONSTANTS_SIZE,
                            worldSets,
                            stack);
            simulatePipeline = createPipeline(state, simulateShader, simulateLayout, stack);
            splashLayout =
                    createLayout(
                            state, ShaderBindings.Particles.SPLASH_CONSTANTS_SIZE, null, stack);
            splashPipeline = createPipeline(state, splashShader, splashLayout, stack);
            emitLayout =
                    createLayout(
                            state, ShaderBindings.Particles.EMIT_CONSTANTS_SIZE, worldSets, stack);
            emitPipeline = createPipeline(state, emitShader, emitLayout, stack);
        }
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        for (SharedBuffer[] buffers : new SharedBuffer[][] {emitterBuffers, linkBuffers}) {
            for (int i = 0; i < buffers.length; ++i) {
                if (buffers[i] != null) {
                    SharedBuffer.free(buffers[i], state);
                    buffers[i] = null;
                }
            }
        }
        for (long pipeline :
                new long[] {controlPipeline, simulatePipeline, splashPipeline, emitPipeline}) {
            vkDestroyPipeline(state.device.logical, pipeline, null);
        }
        for (long layout : new long[] {controlLayout, simulateLayout, splashLayout, emitLayout}) {
            vkDestroyPipelineLayout(state.device.logical, layout, null);
        }
        splashPipeline = VK_NULL_HANDLE;
        splashLayout = VK_NULL_HANDLE;
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
        if (reset) {
            // Spawns are seeded from the frame count, so a cleared run repeats its particles
            frames = 0;
        }
        final Vector3d shift = new Vector3d();
        pool.follow(scene.getCamera().getPosition(), shift);
        ++frames;
        cameraPosition.set(scene.getCamera().getPosition());
        writeLinks(state, pool);

        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // After last frame's billboards read the particles and the draw's arguments, and after
            // the sky occlusion map was last drawn
            barrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT
                            | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
                            | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
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
            simulate(commandBuffer, state, pool, shift, seconds, stack);
            computeBarrier(commandBuffer, stack);
            if (splashes) {
                splash(commandBuffer, state, pool, stack);
                computeBarrier(commandBuffer, stack);
            }
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
     * @param state The Vulkan state, for the occupancy volume particles collide with.
     * @param pool The pool.
     * @param shift How far every particle moves for the anchor's step.
     * @param seconds How long the frame was, in particle time.
     * @param stack The stack to allocate on.
     */
    private void simulate(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull ParticlePool pool,
            @NonNull Vector3d shift,
            double seconds,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, simulatePipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                simulateLayout,
                0,
                stack.longs(state.occupancy.getSet(), state.bindlessTextures.getDescriptorSet()),
                null);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.SIMULATE_CONSTANTS_SIZE);
        final int current = pool.getCurrent();
        final long[] addresses = {
            pool.getParticles().deviceAddress,
            pool.aliveListAddress(current),
            pool.aliveListAddress(1 - current),
            pool.getDeadList().deviceAddress,
            pool.getControl().deviceAddress,
            linkBuffers[state.frameIndex].deviceAddress
        };
        for (int i = 0; i < addresses.length; ++i) {
            constants.putLong(
                    ShaderBindings.Particles.SIMULATE_POOL_OFFSET + i * Long.BYTES, addresses[i]);
        }
        constants.putLong(
                ShaderBindings.Particles.SIMULATE_HITS_OFFSET, pool.getHits().deviceAddress);
        constants.putFloat(ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET, (float) shift.x);
        constants.putFloat(
                ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET + Float.BYTES, (float) shift.y);
        constants.putFloat(
                ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET + 2 * Float.BYTES, (float) shift.z);
        constants.putFloat(ShaderBindings.Particles.SIMULATE_SECONDS_OFFSET, (float) seconds);
        constants.putInt(ShaderBindings.Particles.SIMULATE_CURRENT_OFFSET, current);
        // The finest level as the image holds it: the voxelizer moves the level later in the
        // frame, after the particles move
        final OccupancyClipmap clipmap = state.occupancy.getClipmap();
        final long[] origin = clipmap.origin(0);
        final int voxel = OccupancyClipmap.VOXEL_SIZES[0];
        for (int axis = 0; axis < 3; ++axis) {
            constants.putFloat(
                    ShaderBindings.Particles.SIMULATE_OCCUPANCY_ORIGIN_OFFSET + axis * Float.BYTES,
                    (float) ((double) origin[axis] * voxel - pool.getAnchor().get(axis)));
            constants.putInt(
                    ShaderBindings.Particles.SIMULATE_OCCUPANCY_TEXEL_OFFSET + axis * Integer.BYTES,
                    OccupancyClipmap.texel(origin[axis]));
        }
        constants.putFloat(
                ShaderBindings.Particles.SIMULATE_OCCUPANCY_ORIGIN_OFFSET + 3 * Float.BYTES, voxel);
        constants.putInt(
                ShaderBindings.Particles.SIMULATE_OCCUPANCY_TEXEL_OFFSET + 3 * Integer.BYTES,
                GraphicsManager.getSettings().occupancy && clipmap.isValid(0) ? 1 : 0);
        vkCmdPushConstants(
                commandBuffer, simulateLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatchIndirect(
                commandBuffer, pool.getControl().buffer, ParticleMath.CONTROL_DISPATCH);
    }

    /**
     * Record the splash pass, a thread for each hit a frame can hold.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param pool The pool.
     * @param stack The stack to allocate on.
     */
    private void splash(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull ParticlePool pool,
            @NonNull MemoryStack stack) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, splashPipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.SPLASH_CONSTANTS_SIZE);
        final int next = 1 - pool.getCurrent();
        final long[] addresses = {
            pool.getParticles().deviceAddress,
            pool.aliveListAddress(next),
            pool.getDeadList().deviceAddress,
            pool.getControl().deviceAddress,
            linkBuffers[state.frameIndex].deviceAddress,
            pool.getHits().deviceAddress
        };
        for (int i = 0; i < addresses.length; ++i) {
            constants.putLong(
                    ShaderBindings.Particles.SPLASH_POOL_OFFSET + i * Long.BYTES, addresses[i]);
        }
        constants.putInt(ShaderBindings.Particles.SPLASH_NEXT_OFFSET, next);
        vkCmdPushConstants(commandBuffer, splashLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(commandBuffer, groups(ParticleMath.MAX_HITS), 1, 1);
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
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                emitLayout,
                0,
                stack.longs(state.occupancy.getSet(), state.bindlessTextures.getDescriptorSet()),
                null);
        ByteBuffer constants = stack.calloc(ShaderBindings.Particles.EMIT_CONSTANTS_SIZE);
        final int next = 1 - pool.getCurrent();
        final long[] addresses = {
            pool.getParticles().deviceAddress,
            pool.aliveListAddress(next),
            pool.getDeadList().deviceAddress,
            pool.getControl().deviceAddress,
            emitterBuffers[state.frameIndex].deviceAddress,
            linkBuffers[state.frameIndex].deviceAddress
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
     * their total. Spawns past the pool's capacity in all are left out, since they couldn't fit,
     * and linked emitters that spawn nothing this frame have no record.
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
        final Vector3d position = new Vector3d();
        int first = 0;
        int written = 0;
        for (EmitterRegistry.Spawn spawn : spawns) {
            if (first >= pool.getCapacity()) {
                break;
            }
            if (spawn.count() <= 0) {
                continue;
            }
            final int count = Math.min(spawn.count(), pool.getCapacity() - first);
            emitterPosition(spawn, position).sub(pool.getAnchor());
            putEmitter(
                    address + (long) written * ParticleMath.EMITTER_SIZE,
                    spawn.effect(),
                    position,
                    first,
                    count,
                    frames * SEED_STEP + written,
                    spawn.link());
            first += count;
            ++written;
        }
        spawnTotal = first;
        return written;
    }

    /**
     * Write this frame's links table: the sky occlusion map as it was last drawn, seen from the
     * anchor, and each linked emitter's box and splash; links no emitter holds are left empty.
     *
     * @param state The Vulkan state.
     * @param pool The pool, its anchor already moved for this frame.
     */
    private void writeLinks(@NonNull VulkanState state, @NonNull ParticlePool pool) {
        SharedBuffer buffer = linkBuffers[state.frameIndex];
        if (buffer == null) {
            buffer =
                    SharedBuffer.allocate(
                            ParticleMath.LINKS_SIZE, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            linkBuffers[state.frameIndex] = buffer;
        }
        final long address = buffer.allocationInfo.pMappedData();
        MemoryUtil.memSet(address, 0, ParticleMath.LINKS_SIZE);
        final TextureInfoVulkan skyMap = state.perFrameData[state.frameIndex].skyMap;
        final boolean sky =
                GraphicsManager.getSettings().skyOcclusion
                        && skyMap != null
                        && state.skyOcclusion.lastView(pool.getAnchor(), skyMatrix);
        skyMatrix.getToAddress(address + ParticleMath.LINKS_SKY_MATRIX);
        MemoryUtil.memPutInt(address + ParticleMath.LINKS_SKY_MAP, sky ? skyMap.bindlessIndex : -1);
        MemoryUtil.memPutFloat(
                address + ParticleMath.LINKS_SKY_DEPTH, state.skyOcclusion.lastDepthRange());
        MemoryUtil.memPutInt(address + ParticleMath.LINKS_SEED, frames * SEED_STEP);
        splashes = false;
        final Vector3d position = new Vector3d();
        final Vector3d origin = new Vector3d();
        for (EmitterRegistry.Spawn spawn : spawns) {
            if (spawn.link() == 0) {
                continue;
            }
            final ParticleEffect effect = spawn.effect();
            final long at =
                    address
                            + ParticleMath.LINKS_HEADER
                            + (long) (spawn.link() - 1) * ParticleMath.LINK_SIZE;
            emitterPosition(spawn, position).sub(pool.getAnchor());
            putFloats(
                    at + ParticleMath.LINK_CENTER,
                    (float) position.x,
                    (float) position.y,
                    (float) position.z,
                    effect.isAroundCamera() ? 1 : 0);
            putVector(
                    at + ParticleMath.LINK_HALF_SIZE,
                    Math.max(0, effect.getSpawnBox().x()) / 2,
                    Math.max(0, effect.getSpawnBox().y()) / 2,
                    Math.max(0, effect.getSpawnBox().z()) / 2);
            final ParticleEffect splash = effect.getSplash();
            final int splashCount =
                    splash == null
                            ? 0
                            : Math.clamp(effect.getSplashCount(), 0, ParticleMath.MAX_SPLASH_COUNT);
            MemoryUtil.memPutInt(at + ParticleMath.LINK_SPLASH_COUNT, splashCount);
            if (splashCount > 0) {
                splashes = true;
                putEmitter(at + ParticleMath.LINK_SPLASH, splash, origin, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Where an emitter's particles spawn around: where it is, or for one around the camera, its
     * offset from the camera.
     *
     * @param spawn Its spawns this frame.
     * @param position Receives the place, in the world.
     * @return The position, for chaining.
     */
    private Vector3d emitterPosition(
            @NonNull EmitterRegistry.Spawn spawn, @NonNull Vector3d position) {
        position.set(spawn.position());
        if (spawn.effect().isAroundCamera()) {
            position.add(cameraPosition);
        }
        return position;
    }

    /**
     * Write one emitter record.
     *
     * @param at Where.
     * @param effect What it spawns.
     * @param position Where it is, relative to the anchor.
     * @param first Its first spawn's index among the frame's.
     * @param count How many it spawns.
     * @param seed Its random seed.
     * @param link Its link, or 0 for none.
     */
    private static void putEmitter(
            long at,
            @NonNull ParticleEffect effect,
            @NonNull Vector3d position,
            int first,
            int count,
            int seed,
            int link) {
        putVector(
                at + ParticleMath.EMITTER_POSITION,
                (float) position.x,
                (float) position.y,
                (float) position.z);
        MemoryUtil.memPutInt(at + ParticleMath.EMITTER_POSITION + 3 * Float.BYTES, first);
        final Vector3f direction = new Vector3f(effect.getDirection());
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
        MemoryUtil.memPutInt(at + ParticleMath.EMITTER_COUNT + Integer.BYTES, seed);
        MemoryUtil.memPutInt(
                at + ParticleMath.EMITTER_BEHAVIOR,
                ParticleMath.packBehavior(
                        (float)
                                Math.clamp(
                                        effect.getAnisotropy(),
                                        -ParticleEffectLoader.MAX_ANISOTROPY,
                                        ParticleEffectLoader.MAX_ANISOTROPY),
                        effect.isLit(),
                        effect.getCollision().ordinal(),
                        effect.getBounce(),
                        effect.getFriction(),
                        effect.isVolumetric(),
                        link));
        MemoryUtil.memPutFloat(at + ParticleMath.EMITTER_STRETCH, Math.max(0, effect.getStretch()));
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
     * Create a compute pipeline layout.
     *
     * @param state The Vulkan state.
     * @param constantsSize The size of its push constants.
     * @param setLayouts Its descriptor sets' layouts, or null for none.
     * @param stack The stack to allocate on.
     * @return The VkPipelineLayout.
     */
    private static long createLayout(
            @NonNull VulkanState state,
            int constantsSize,
            LongBuffer setLayouts,
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
}
