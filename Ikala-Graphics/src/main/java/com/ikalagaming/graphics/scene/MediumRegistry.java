package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.MediumHandle;

import lombok.NonNull;

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

    /** A volume and the owner that placed it; the volume is replaced in place on update. */
    private static final class Entry {
        /** The owning context's key. */
        private final String owner;

        /** The volume. */
        private MediumVolume volume;

        /**
         * Make an entry.
         *
         * @param owner The owning context's key.
         * @param volume The volume.
         */
        private Entry(@NonNull String owner, @NonNull MediumVolume volume) {
            this.owner = owner;
            this.volume = volume;
        }
    }

    /** The volumes by slot. */
    private final SlotMap<Entry> entries = new SlotMap<>();

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
            if (entries.size() >= MAX_VOLUMES) {
                return null;
            }
            final int slot = entries.add(new Entry(owner, volume));
            return new MediumHandle(slot, entries.generation(slot));
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
            entries.get(handle.slot()).volume = volume;
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
            entries.free(handle.slot());
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
            for (int slot = 0; slot < entries.slots(); ++slot) {
                final Entry entry = entries.get(slot);
                if (entry != null && owner.equals(entry.owner)) {
                    entries.free(slot);
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
            return live(handle) != null;
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
            final Entry entry = live(handle);
            return entry == null ? null : entry.owner;
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
            for (int slot = 0; slot < entries.slots(); ++slot) {
                final Entry entry = entries.get(slot);
                if (entry != null) {
                    into.add(entry.volume);
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
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The entry a handle refers to now. Hold the lock.
     *
     * @param handle The handle.
     * @return The entry, or null if the handle is null or stale.
     */
    private @Nullable Entry live(@Nullable MediumHandle handle) {
        return handle == null ? null : entries.get(handle.slot(), handle.generation());
    }

    /**
     * Whether an owner's handle refers to its volume now. Hold the lock.
     *
     * @param owner The owner's key.
     * @param handle The handle.
     * @return True if so.
     */
    private boolean owns(@NonNull String owner, @Nullable MediumHandle handle) {
        final Entry entry = live(handle);
        return entry != null && owner.equals(entry.owner);
    }
}
