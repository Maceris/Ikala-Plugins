package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryUtil;

/**
 * The GPU's particles, kept across frames: a pool of {@link ParticleMath#PARTICLE_SIZE} byte
 * particles, two lists of the alive ones' indices that each frame's simulation reads from one and
 * writes to the other, a list of the free ones, and a small control buffer with their counts and
 * the indirect arguments the simulation and the billboards are dispatched and drawn with. The
 * control buffer is mapped, so the counts can be read back for showing.
 *
 * <p>Positions are relative to {@link #getAnchor() the anchor}, see {@link ParticleMath}.
 */
@Slf4j
public class ParticlePool {

    /** How many particles the pool holds. */
    @Getter private final int capacity;

    /** The particles. */
    @Getter private final SharedBuffer particles;

    /** The two lists of alive particles' indices, one after the other. */
    @Getter private final SharedBuffer aliveLists;

    /** The free particles' indices, used as a stack. */
    @Getter private final SharedBuffer deadList;

    /** Counts and indirect arguments, see {@link ParticleMath#CONTROL_ALIVE} and on. */
    @Getter private final SharedBuffer control;

    /** Each particle's light this frame, by its index in the pool. */
    @Getter private final SharedBuffer radiance;

    /**
     * The hits the simulation recorded this frame for splashes, {@link ParticleMath#MAX_HITS} of
     * {@link ParticleMath#HIT_SIZE} bytes, see {@code particle_splash.comp}.
     */
    @Getter private final SharedBuffer hits;

    /** Which alive list the last frame's simulation wrote, and the billboards draw. */
    @Getter private int current;

    /** Whether the free list must be filled before the next frame, as at the start. */
    private boolean resetPending = true;

    /** Where positions are kept relative to, in the world. */
    private final Vector3d anchor = new Vector3d();

    /**
     * Create the pool's buffers.
     *
     * @param state The Vulkan state.
     * @param capacity How many particles it holds.
     */
    public ParticlePool(@NonNull VulkanState state, int capacity) {
        this.capacity = capacity;
        log.debug("Creating a pool of {} particles", capacity);
        particles =
                SharedBuffer.allocateDeviceLocal(
                        (long) capacity * ParticleMath.PARTICLE_SIZE,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        aliveLists =
                SharedBuffer.allocateDeviceLocal(
                        2L * capacity * Integer.BYTES, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        deadList =
                SharedBuffer.allocateDeviceLocal(
                        (long) capacity * Integer.BYTES, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        radiance =
                SharedBuffer.allocateDeviceLocal(
                        (long) capacity * ParticleMath.RADIANCE_SIZE,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        hits =
                SharedBuffer.allocateDeviceLocal(
                        (long) ParticleMath.MAX_HITS * ParticleMath.HIT_SIZE,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        control =
                SharedBuffer.allocate(
                        ParticleMath.CONTROL_SIZE,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
    }

    /**
     * The address of an alive list.
     *
     * @param list 0 or 1.
     * @return Its device address.
     */
    public long aliveListAddress(int list) {
        return aliveLists.deviceAddress + (long) list * capacity * Integer.BYTES;
    }

    /** Swap the alive lists, after a frame's simulation wrote the other one. */
    public void swap() {
        current = 1 - current;
    }

    /** Remove every particle on the next frame, filling the free list again. */
    public void requestReset() {
        resetPending = true;
    }

    /**
     * Whether the free list is to be filled this frame, forgetting the request.
     *
     * @return True if every particle goes this frame.
     */
    public boolean takeReset() {
        final boolean reset = resetPending;
        resetPending = false;
        return reset;
    }

    /**
     * Where positions are kept relative to.
     *
     * @return The anchor, in the world.
     */
    public Vector3dc getAnchor() {
        return anchor;
    }

    /**
     * Move the anchor to a camera's cell, see {@link ParticleMath#anchorFor}.
     *
     * @param camera The camera's position.
     * @param shift Receives how far every particle must move to keep its place, old anchor minus
     *     new, zero when the anchor stays.
     * @return True if the anchor moved.
     */
    public boolean follow(@NonNull Vector3dc camera, @NonNull Vector3d shift) {
        final Vector3d next = ParticleMath.anchorFor(new Vector3d(camera), new Vector3d());
        shift.set(anchor).sub(next);
        if (next.equals(anchor)) {
            return false;
        }
        anchor.set(next);
        return true;
    }

    /**
     * How many particles were alive in the last finished frame, read from the mapped counts.
     *
     * @return The count.
     */
    public int readLiveCount() {
        final long address = control.allocationInfo.pMappedData();
        return MemoryUtil.memGetInt(address + ParticleMath.CONTROL_DRAW + Integer.BYTES);
    }

    /**
     * How many volumetric particles were alive in the last finished frame, which the fog volume
     * draws.
     *
     * @return The count.
     */
    public int readVolumetricCount() {
        final long address = control.allocationInfo.pMappedData();
        return MemoryUtil.memGetInt(address + ParticleMath.CONTROL_COUNTS);
    }

    /**
     * How many spawns found the pool full in the last finished frame.
     *
     * @return The count.
     */
    public int readDropped() {
        final long address = control.allocationInfo.pMappedData();
        return MemoryUtil.memGetInt(address + ParticleMath.CONTROL_DROPPED);
    }

    /**
     * Free the buffers once no frame in flight uses them.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(particles, state);
        SharedBuffer.free(aliveLists, state);
        SharedBuffer.free(deadList, state);
        SharedBuffer.free(control, state);
        SharedBuffer.free(radiance, state);
        SharedBuffer.free(hits, state);
    }
}
