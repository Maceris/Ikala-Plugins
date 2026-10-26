package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.MediumHandle;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The scene's medium volumes, each owned by a plugin. Plugins change them from any thread through
 * {@link com.ikalagaming.graphics.Media}, and the render thread takes a copy each frame, so every
 * method holds {@link #lock}.
 */
public class MediumRegistry {

    /** The most medium volumes the scene holds. */
    public static final int MAX_VOLUMES = 256;

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Each slot's volume, null when free. */
    private final List<MediumVolume> volumes = new ArrayList<>();

    /** Each slot's owner key, null when free. */
    private final List<String> owners = new ArrayList<>();

    /** How many times each slot was freed. */
    private final List<Integer> generations = new ArrayList<>();

    /** Slots free to take again. */
    private final List<Integer> freeSlots = new ArrayList<>();

    /** How many volumes there are. */
    private int count;

    /**
     * Add a volume.
     *
     * @param owner The owning context's key.
     * @param volume The volume.
     * @return Its handle, or null if the scene holds {@link #MAX_VOLUMES} already.
     */
    public @Nullable MediumHandle add(@NonNull String owner, @NonNull MediumVolume volume) {
        lock.lock();
        try {
            if (count >= MAX_VOLUMES) {
                return null;
            }
            final int slot;
            if (freeSlots.isEmpty()) {
                slot = volumes.size();
                volumes.add(volume);
                owners.add(owner);
                generations.add(0);
            } else {
                slot = freeSlots.removeLast();
                volumes.set(slot, volume);
                owners.set(slot, owner);
            }
            ++count;
            return new MediumHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replace a volume the owner placed.
     *
     * @param owner The owning context's key.
     * @param handle The volume's handle.
     * @param volume What it is now.
     * @return False if the handle is stale or another owner's.
     */
    public boolean update(
            @NonNull String owner, @Nullable MediumHandle handle, @NonNull MediumVolume volume) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            volumes.set(handle.slot(), volume);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a volume the owner placed.
     *
     * @param owner The owning context's key.
     * @param handle The volume's handle.
     * @return False if the handle is stale or another owner's.
     */
    public boolean remove(@NonNull String owner, @Nullable MediumHandle handle) {
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
     * Remove every volume an owner placed, as when its plugin unloads.
     *
     * @param owner The owning context's key.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int removed = 0;
            for (int slot = 0; slot < owners.size(); ++slot) {
                if (owner.equals(owners.get(slot))) {
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
     * Whether a handle still refers to a volume.
     *
     * @param handle The handle.
     * @return False if it is null or stale.
     */
    public boolean isValid(@Nullable MediumHandle handle) {
        lock.lock();
        try {
            return live(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Which owner a handle's volume belongs to.
     *
     * @param handle The handle.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public @Nullable String ownerOf(@Nullable MediumHandle handle) {
        lock.lock();
        try {
            return live(handle) ? owners.get(handle.slot()) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Copy the volumes, for drawing a frame.
     *
     * @param into Receives them, cleared first.
     */
    public void snapshot(@NonNull List<MediumVolume> into) {
        into.clear();
        lock.lock();
        try {
            for (MediumVolume volume : volumes) {
                if (volume != null) {
                    into.add(volume);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many volumes there are.
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
     * Whether a handle refers to a volume now. Hold the lock.
     *
     * @param handle The handle.
     * @return True if so.
     */
    private boolean live(@Nullable MediumHandle handle) {
        return handle != null
                && handle.slot() >= 0
                && handle.slot() < volumes.size()
                && volumes.get(handle.slot()) != null
                && generations.get(handle.slot()) == handle.generation();
    }

    /**
     * Whether an owner's handle refers to its volume now. Hold the lock.
     *
     * @param owner The owner's key.
     * @param handle The handle.
     * @return True if so.
     */
    private boolean owns(@NonNull String owner, @Nullable MediumHandle handle) {
        return live(handle) && owner.equals(owners.get(handle.slot()));
    }

    /**
     * Free a slot, making handles to it stale. Hold the lock.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        volumes.set(slot, null);
        owners.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
        --count;
    }
}
