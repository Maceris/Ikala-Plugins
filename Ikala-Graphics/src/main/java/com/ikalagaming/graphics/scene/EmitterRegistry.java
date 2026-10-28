package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.vulkan.ParticleMath;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
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
@Slf4j
public class EmitterRegistry {

    /** The most emitters the scene holds. */
    public static final int MAX_EMITTERS = 1024;

    /**
     * How far an emitter may move in a frame and still spread its spawns along the way, in meters;
     * past it, it counts as a jump and they spawn where it is now.
     */
    public static final double TELEPORT_DISTANCE = 4;

    /**
     * One frame's spawns from an emitter. Its spawns spread over the frame along the way it moved,
     * from where it was to where it is, and along its line if it has one.
     *
     * @param effect What it spawns.
     * @param position Where it is, in the world; for an effect around the camera, its offset.
     * @param count How many it spawns this frame, perhaps 0 for a linked emitter.
     * @param link Its link, or 0 for none, see ParticleEffect#needsLink.
     * @param end The far end of its line, in the world, or its position for a point.
     * @param from Where it was last frame, or its position after a jump or on its first frame.
     * @param fromEnd Where its line's far end was last frame, likewise.
     * @param rotation How it is turned, which its spawn box and direction turn by.
     * @param velocity How fast it moves, in meters a second, 0 after a jump.
     * @param moved How far it moved since last frame, which particles that follow it move by.
     */
    public record Spawn(
            @NonNull ParticleEffect effect,
            @NonNull Vector3dc position,
            int count,
            int link,
            @NonNull Vector3dc end,
            @NonNull Vector3dc from,
            @NonNull Vector3dc fromEnd,
            @NonNull Quaternionfc rotation,
            @NonNull Vector3dc velocity,
            @NonNull Vector3dc moved) {}

    /** An emitter in a slot. */
    private static final class Emitter {
        /** Where its effect comes from: a fixed one, or a loaded file's. */
        EffectSource source;

        /** Where it is, in the world. */
        final Vector3d position = new Vector3d();

        /** The far end of its line, in the world; its position for a point. */
        final Vector3d end = new Vector3d();

        /** Where it and its line's far end were when last collected, if {@link #placed}. */
        final Vector3d previous = new Vector3d();

        final Vector3d previousEnd = new Vector3d();

        /** Whether it was collected before, so {@link #previous} holds where it was. */
        boolean placed;

        /** Whether it jumped since it was last collected, so its spawns don't spread. */
        boolean jumped;

        /** How it is turned. */
        final Quaternionf rotation = new Quaternionf();

        /** The fraction of a particle its rate has built up, carried between frames. */
        final double[] carry = new double[1];

        /** Particles to spawn at once next frame, from its burst. */
        int pendingBurst;

        /** Its link, 1 to ParticleMath.MAX_LINKS, while its effect needs one; 0 for none. */
        int link;

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

    /** Which links emitters hold, by link; link 0 is none and never held. */
    private final boolean[] linksHeld = new boolean[ParticleMath.MAX_LINKS + 1];

    /** Whether running out of links was logged, so it is logged once. */
    private boolean linksFullLogged;

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
        return add(owner, new EffectSource(effect), position);
    }

    /**
     * Add an emitter whose effect comes from a source, such as a loaded file's, which it follows
     * when the file is reloaded.
     *
     * @param owner The owning context's key.
     * @param source Where its effect comes from.
     * @param position Where it is, in the world.
     * @return Its handle, or null if the scene holds {@link #MAX_EMITTERS} already.
     */
    public @Nullable EmitterHandle add(
            @NonNull String owner, @NonNull EffectSource source, @NonNull Vector3dc position) {
        lock.lock();
        try {
            if (count >= MAX_EMITTERS) {
                return null;
            }
            final Emitter emitter = new Emitter();
            emitter.source = source;
            emitter.position.set(position);
            emitter.end.set(position);
            emitter.pendingBurst = Math.max(0, source.get().getBurst());
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
     * Move an emitter the owner placed, making it a point if it had a line. The next frame's spawns
     * spread along the way it moved, unless it moved past {@link #TELEPORT_DISTANCE}. Particles it
     * already spawned stay where they are, unless its effect follows it.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param position Where it is now, in the world.
     * @return False if the handle is stale or another owner's.
     */
    public boolean move(
            @NonNull String owner, @Nullable EmitterHandle handle, @NonNull Vector3dc position) {
        return moveLine(owner, handle, position, position);
    }

    /**
     * Move an emitter the owner placed and make it a line, which spawns anywhere along it, as along
     * a blade; the next frame's spawns sweep the area between where the line was and where it is.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param from One end, in the world, which is its position.
     * @param to The other end.
     * @return False if the handle is stale or another owner's.
     */
    public boolean moveLine(
            @NonNull String owner,
            @Nullable EmitterHandle handle,
            @NonNull Vector3dc from,
            @NonNull Vector3dc to) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            final Emitter emitter = emitters.get(handle.slot());
            emitter.position.set(from);
            emitter.end.set(to);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move an emitter the owner placed, making it a point, without spreading the next frame's
     * spawns along the way.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param position Where it is now, in the world.
     * @return False if the handle is stale or another owner's.
     */
    public boolean teleport(
            @NonNull String owner, @Nullable EmitterHandle handle, @NonNull Vector3dc position) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            final Emitter emitter = emitters.get(handle.slot());
            emitter.position.set(position);
            emitter.end.set(position);
            emitter.jumped = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Turn an emitter the owner placed: its effect's spawn box and direction turn with it, as on a
     * bone. Effects around the camera don't turn.
     *
     * @param owner The owning context's key.
     * @param handle The emitter's handle.
     * @param rotation How it is turned, from the world's axes.
     * @return False if the handle is stale or another owner's.
     */
    public boolean orient(
            @NonNull String owner, @Nullable EmitterHandle handle, @NonNull Quaternionfc rotation) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            emitters.get(handle.slot()).rotation.set(rotation).normalize();
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
            emitters.get(handle.slot()).source = new EffectSource(effect);
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
     * next, plus any bursts waiting, with the way it moved since it was last collected. Emitters
     * with nothing to spawn are left out. A frame that took no time, as while paused, moves
     * nothing, and the way builds up until one does.
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
                final ParticleEffect effect = emitter.source.get();
                updateLink(emitter, effect);
                final long spawns =
                        (long) ParticleMath.spawns(effect.getRate(), seconds, emitter.carry)
                                + emitter.pendingBurst;
                emitter.pendingBurst = 0;
                final Vector3d position = new Vector3d(emitter.position);
                final Vector3d end = new Vector3d(emitter.end);
                final Vector3d from = new Vector3d(position);
                final Vector3d fromEnd = new Vector3d(end);
                final Vector3d velocity = new Vector3d();
                final Vector3d moved = new Vector3d();
                final boolean around = effect.isAroundCamera();
                if (emitter.placed && !around && seconds > 0) {
                    moved.set(position).sub(emitter.previous);
                    final boolean spread =
                            !emitter.jumped
                                    && moved.length() <= TELEPORT_DISTANCE
                                    && end.distance(emitter.previousEnd) <= TELEPORT_DISTANCE;
                    if (spread) {
                        from.set(emitter.previous);
                        fromEnd.set(emitter.previousEnd);
                        velocity.set(moved).div(seconds);
                    }
                }
                if (!emitter.placed || seconds > 0) {
                    emitter.previous.set(position);
                    emitter.previousEnd.set(end);
                    emitter.placed = true;
                    emitter.jumped = false;
                }
                // A linked emitter is listed even when it spawns nothing, so its link stays current
                // for the particles it spawned before
                if (spawns > 0 || emitter.link != 0) {
                    into.add(
                            new Spawn(
                                    effect,
                                    position,
                                    (int) Math.min(Integer.MAX_VALUE, spawns),
                                    emitter.link,
                                    end,
                                    from,
                                    fromEnd,
                                    around ? new Quaternionf() : new Quaternionf(emitter.rotation),
                                    velocity,
                                    moved));
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
     * Take a link for an emitter whose effect needs one, or let go of one it no longer needs. With
     * every link taken, it goes without: its particles spawn in a ball and don't splash.
     *
     * @param emitter The emitter.
     * @param effect Its effect this frame.
     */
    private void updateLink(@NonNull Emitter emitter, @NonNull ParticleEffect effect) {
        if (!effect.needsLink()) {
            releaseLink(emitter);
            return;
        }
        if (emitter.link != 0) {
            return;
        }
        for (int link = 1; link < linksHeld.length; ++link) {
            if (!linksHeld[link]) {
                linksHeld[link] = true;
                emitter.link = link;
                return;
            }
        }
        if (!linksFullLogged) {
            linksFullLogged = true;
            log.warn(
                    "More than {} emitters need links; the rest spawn in a ball, without splashes",
                    ParticleMath.MAX_LINKS);
        }
    }

    /**
     * Let go of an emitter's link, if it holds one.
     *
     * @param emitter The emitter.
     */
    private void releaseLink(@NonNull Emitter emitter) {
        if (emitter.link != 0) {
            linksHeld[emitter.link] = false;
            emitter.link = 0;
        }
    }

    /**
     * Free a slot, making handles to it stale. Hold the lock.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        releaseLink(emitters.get(slot));
        emitters.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
        --count;
    }
}
