package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.vulkan.ParticleMath;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The scene's particle emitters, each owned by a plugin. Plugins change them from any thread
 * through {@link com.ikalagaming.graphics.Particles}, and the render thread collects each frame's
 * spawns, so every method holds {@link #lock}.
 */
public class EmitterRegistry {

    /** The most emitters the scene holds. */
    public static final int MAX_EMITTERS = 1024;

    /**
     * One frame's spawns from an emitter.
     *
     * @param effect What it spawns.
     * @param position Where it is, in the world.
     * @param count How many it spawns this frame.
     */
    public record Spawn(@NonNull ParticleEffect effect, @NonNull Vector3dc position, int count) {}

    /** An emitter in a slot. */
    private static final class Emitter {
        /** What it spawns. */
        ParticleEffect effect;

        /** Where it is, in the world. */
        final Vector3d position = new Vector3d();

        /** The fraction of a particle its rate has built up, carried between frames. */
        final double[] carry = new double[1];

        /** Particles to spawn at once next frame, from its burst. */
        int pendingBurst;

        /** The owning context's key. */
        String owner;
    }

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Each slot's emitter, null when free. */
    private final List<Emitter> emitters = new ArrayList<>();

    /** How many times each slot was freed. */
    private final List<Integer> generations = new ArrayList<>();

    /** Slots free to take again. */
    private final List<Integer> freeSlots = new ArrayList<>();

    /** How many emitters there are. */
    private int count;

    /**
     * Add an emitter, which spawns its effect's burst on the next frame and its rate from then on.
     *
     * @param owner The owning context's key.
     * @param effect What it spawns.
     * @param position Where it is, in the world.
     * @return Its handle, or null if the scene holds {@link #MAX_EMITTERS} already.
     */
    public @Nullable EmitterHandle add(
            @NonNull String owner, @NonNull ParticleEffect effect, @NonNull Vector3dc position) {
        lock.lock();
        try {
            if (count >= MAX_EMITTERS) {
                return null;
            }
            final Emitter emitter = new Emitter();
            emitter.effect = effect;
            emitter.position.set(position);
            emitter.pendingBurst = Math.max(0, effect.getBurst());
            emitter.owner = owner;
            final int slot;
            if (freeSlots.isEmpty()) {
                slot = emitters.size();
                emitters.add(emitter);
                generations.add(0);
            } else {
                slot = freeSlots.removeLast();
                emitters.set(slot, emitter);
            }
            ++count;
            return new EmitterHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move an emitter the owner placed. Particles it already spawned stay where they are.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param position Where it is now, in the world.
     * @return False if the handle is stale or another owner's.
     */
    public boolean move(
            @NonNull String owner, @Nullable EmitterHandle handle, @NonNull Vector3dc position) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            emitters.get(handle.slot()).position.set(position);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change what an emitter the owner placed spawns from now on.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param effect What it spawns now.
     * @return False if the handle is stale or another owner's.
     */
    public boolean setEffect(
            @NonNull String owner, @Nullable EmitterHandle handle, @NonNull ParticleEffect effect) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            emitters.get(handle.slot()).effect = effect;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Spawn extra particles from an emitter the owner placed, all at once on the next frame.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param particles How many.
     * @return False if the handle is stale or another owner's.
     */
    public boolean burst(@NonNull String owner, @Nullable EmitterHandle handle, int particles) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            final Emitter emitter = emitters.get(handle.slot());
            emitter.pendingBurst =
                    (int) Math.min(Integer.MAX_VALUE, (long) emitter.pendingBurst + particles);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove an emitter the owner placed. Its particles live out their lives.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @return False if the handle is stale or another owner's.
     */
    public boolean remove(@NonNull String owner, @Nullable EmitterHandle handle) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            free(handle.slot());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every emitter an owner placed, as when its plugin unloads.
     *
     * @param owner The owning context's key.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int removed = 0;
            for (int slot = 0; slot < emitters.size(); ++slot) {
                final Emitter emitter = emitters.get(slot);
                if (emitter != null && owner.equals(emitter.owner)) {
                    free(slot);
                    ++removed;
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle still refers to an emitter.
     *
     * @param handle The handle.
     * @return False if it is null or stale.
     */
    public boolean isValid(@Nullable EmitterHandle handle) {
        lock.lock();
        try {
            return live(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Which owner a handle's emitter belongs to.
     *
     * @param handle The handle.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public @Nullable String ownerOf(@Nullable EmitterHandle handle) {
        lock.lock();
        try {
            return live(handle) ? emitters.get(handle.slot()).owner : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Collect this frame's spawns: each emitter's rate over the frame, keeping the fraction for the
     * next, plus any bursts waiting. Emitters with nothing to spawn are left out.
     *
     * @param seconds How long the frame was, in the particles' own time.
     * @param into Receives the spawns, cleared first.
     */
    public void collect(double seconds, @NonNull List<Spawn> into) {
        into.clear();
        lock.lock();
        try {
            for (Emitter emitter : emitters) {
                if (emitter == null) {
                    continue;
                }
                final long spawns =
                        (long) ParticleMath.spawns(emitter.effect.getRate(), seconds, emitter.carry)
                                + emitter.pendingBurst;
                emitter.pendingBurst = 0;
                if (spawns > 0) {
                    into.add(
                            new Spawn(
                                    emitter.effect,
                                    new Vector3d(emitter.position),
                                    (int) Math.min(Integer.MAX_VALUE, spawns)));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many emitters there are.
     *
     * @return The count.
     */
    public int getCount() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle refers to an emitter now. Hold the lock.
     *
     * @param handle The handle.
     * @return True if so.
     */
    private boolean live(@Nullable EmitterHandle handle) {
        return handle != null
                && handle.slot() >= 0
                && handle.slot() < emitters.size()
                && emitters.get(handle.slot()) != null
                && generations.get(handle.slot()) == handle.generation();
    }

    /**
     * Whether an owner's handle refers to its emitter now. Hold the lock.
     *
     * @param owner The owner's key.
     * @param handle The handle.
     * @return True if so.
     */
    private boolean owns(@NonNull String owner, @Nullable EmitterHandle handle) {
        return live(handle) && owner.equals(emitters.get(handle.slot()).owner);
    }

    /**
     * Free a slot, making handles to it stale. Hold the lock.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        emitters.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
        --count;
    }
}
