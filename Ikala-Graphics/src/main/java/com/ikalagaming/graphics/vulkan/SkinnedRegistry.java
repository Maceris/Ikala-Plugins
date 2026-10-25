package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.SkinnedInstanceHandle;
import com.ikalagaming.graphics.SkinnedPart;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * The skinned instances in the scene: who owns each, where it is, what it draws, and its latest
 * pose. Plugins change them from any thread; the render thread reads them all each frame through
 * {@link #read(Consumer)}, under the same lock.
 *
 * @author Ches Burks
 */
public final class SkinnedRegistry {
    /** The most bones one instance can have. */
    public static final int MAX_BONES = 1024;

    /** Floats per bone in a palette: one column-major 4x4 matrix. */
    public static final int MATRIX_FLOATS = 16;

    /** One instance's state. */
    public static final class Entry {
        /** The plugin that placed it. */
        String owner;

        /** The slot's generation, which handles must match. */
        int generation;

        /** How many bones its palette holds. */
        int boneCount;

        /** Its world position. */
        final Vector3d position = new Vector3d();

        /** Its rotation. */
        final Quaternionf rotation = new Quaternionf();

        /** Its uniform scale. */
        float scale = 1;

        /** What it draws. */
        List<SkinnedPart> parts = List.of();

        /** Its latest skinning matrices, or null before the first pose. */
        float[] palette;

        /** The minimum corner of its posed bounds, in model space. */
        final Vector3f boundsMin = new Vector3f();

        /** The maximum corner of its posed bounds, in model space. */
        final Vector3f boundsMax = new Vector3f();

        /** Which mask regions are hidden, one bit per region. */
        long hiddenRegions;

        /** Whether it casts shadows. */
        boolean castsShadows = true;

        /**
         * Its world position.
         *
         * @return The position, which must not be changed.
         */
        public Vector3dc position() {
            return position;
        }

        /**
         * Its rotation.
         *
         * @return The rotation, which must not be changed.
         */
        public Quaternionfc rotation() {
            return rotation;
        }

        /**
         * Its uniform scale.
         *
         * @return The scale.
         */
        public float scale() {
            return scale;
        }

        /**
         * What it draws.
         *
         * @return The parts.
         */
        public List<SkinnedPart> parts() {
            return parts;
        }

        /**
         * How many bones its palette holds.
         *
         * @return The bone count.
         */
        public int boneCount() {
            return boneCount;
        }

        /**
         * Its latest skinning matrices.
         *
         * @return The palette, {@value #MATRIX_FLOATS} floats per bone, which must not be changed.
         */
        public float[] palette() {
            return palette;
        }

        /**
         * The minimum corner of its posed bounds, in model space.
         *
         * @return The corner, which must not be changed.
         */
        public Vector3fc boundsMin() {
            return boundsMin;
        }

        /**
         * The maximum corner of its posed bounds, in model space.
         *
         * @return The corner, which must not be changed.
         */
        public Vector3fc boundsMax() {
            return boundsMax;
        }

        /**
         * Which mask regions are hidden.
         *
         * @return One bit per region.
         */
        public long hiddenRegions() {
            return hiddenRegions;
        }

        /**
         * Whether it casts shadows.
         *
         * @return True if it does.
         */
        public boolean castsShadows() {
            return castsShadows;
        }
    }

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Instances by slot, null for free slots. */
    private final List<Entry> slots = new ArrayList<>();

    /** Each slot's generation, kept while it is free. */
    private final List<Integer> generations = new ArrayList<>();

    /** Free slots, reused last freed first. */
    private final List<Integer> free = new ArrayList<>();

    /** The drawable instances handed to {@link #read(Consumer)}, kept to avoid allocating. */
    private final List<Entry> drawable = new ArrayList<>();

    /**
     * Place an instance with nothing to draw and no pose yet.
     *
     * @param owner The plugin placing it.
     * @param boneCount How many bones its palette holds.
     * @param position Its world position.
     * @param rotation Its rotation.
     * @param scale Its uniform scale.
     * @return The handle.
     * @throws IllegalArgumentException If the bone count is out of range.
     */
    public SkinnedInstanceHandle place(
            @NonNull String owner,
            int boneCount,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        if (boneCount < 1 || boneCount > MAX_BONES) {
            throw new IllegalArgumentException(
                    "An instance has 1 to " + MAX_BONES + " bones, not " + boneCount);
        }
        lock.lock();
        try {
            final int slot;
            if (free.isEmpty()) {
                slot = slots.size();
                slots.add(null);
                generations.add(0);
            } else {
                slot = free.remove(free.size() - 1);
            }
            Entry entry = new Entry();
            entry.owner = owner;
            entry.generation = generations.get(slot);
            entry.boneCount = boneCount;
            entry.position.set(position);
            entry.rotation.set(rotation);
            entry.scale = scale;
            slots.set(slot, entry);
            return new SkinnedInstanceHandle(slot, entry.generation);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Find a live instance that an owner may change.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @return The entry, or null if the handle is null, stale or someone else's.
     */
    private Entry find(String owner, SkinnedInstanceHandle handle) {
        if (handle == null || handle.slot() < 0 || handle.slot() >= slots.size()) {
            return null;
        }
        Entry entry = slots.get(handle.slot());
        if (entry == null || entry.generation != handle.generation()) {
            return null;
        }
        return owner == null || owner.equals(entry.owner) ? entry : null;
    }

    /**
     * The owner of an instance.
     *
     * @param handle The handle.
     * @return The owner, or null if the handle is null or stale.
     */
    public String ownerOf(SkinnedInstanceHandle handle) {
        lock.lock();
        try {
            Entry entry = find(null, handle);
            return entry == null ? null : entry.owner;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle refers to a live instance.
     *
     * @param handle The handle.
     * @return False if it is null or stale.
     */
    public boolean isValid(SkinnedInstanceHandle handle) {
        return ownerOf(handle) != null;
    }

    /**
     * Change what an instance draws.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @param parts The parts, each needing no more bones than the instance has.
     * @return False if the handle is null, stale or someone else's.
     * @throws IllegalArgumentException If a part needs more bones than the instance has.
     */
    public boolean setParts(
            @NonNull String owner, SkinnedInstanceHandle handle, @NonNull List<SkinnedPart> parts) {
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            for (SkinnedPart part : parts) {
                if (part.mesh().jointCount() > entry.boneCount) {
                    throw new IllegalArgumentException(
                            "A part needs "
                                    + part.mesh().jointCount()
                                    + " bones, but the instance"
                                    + " has "
                                    + entry.boneCount);
                }
            }
            entry.parts = List.copyOf(parts);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change an instance's pose: its skinning matrices and the bounds they put it in.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @param palette The skinning matrices, column major, {@value #MATRIX_FLOATS} floats per bone.
     * @param offset Where in the palette the instance's first bone starts.
     * @param boundsMin The minimum corner of the posed bounds, in model space.
     * @param boundsMax The maximum corner of the posed bounds, in model space.
     * @return False if the handle is null, stale or someone else's.
     * @throws IllegalArgumentException If the palette is too short or the bounds aren't finite.
     */
    public boolean setPose(
            @NonNull String owner,
            SkinnedInstanceHandle handle,
            float @NonNull [] palette,
            int offset,
            @NonNull Vector3fc boundsMin,
            @NonNull Vector3fc boundsMax) {
        if (!boundsMin.isFinite() || !boundsMax.isFinite()) {
            throw new IllegalArgumentException("The bounds must be finite");
        }
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            final int floats = entry.boneCount * MATRIX_FLOATS;
            if (offset < 0 || palette.length - offset < floats) {
                throw new IllegalArgumentException(
                        "The palette needs " + floats + " floats from " + offset);
            }
            if (entry.palette == null) {
                entry.palette = new float[floats];
            }
            System.arraycopy(palette, offset, entry.palette, 0, floats);
            entry.boundsMin.set(boundsMin).min(boundsMax);
            entry.boundsMax.set(boundsMax).max(boundsMin);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change some of an instance's transform.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @param position The new world position, or null to keep it.
     * @param rotation The new rotation, or null to keep it.
     * @param scale The new uniform scale, or NaN to keep it.
     * @return False if the handle is null, stale or someone else's.
     */
    public boolean transform(
            @NonNull String owner,
            SkinnedInstanceHandle handle,
            Vector3dc position,
            Quaternionfc rotation,
            float scale) {
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            if (position != null) {
                entry.position.set(position);
            }
            if (rotation != null) {
                entry.rotation.set(rotation);
            }
            if (!Float.isNaN(scale)) {
                entry.scale = scale;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change which mask regions of an instance are hidden.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @param regions One bit per region, set to hide it.
     * @return False if the handle is null, stale or someone else's.
     */
    public boolean setHiddenRegions(
            @NonNull String owner, SkinnedInstanceHandle handle, long regions) {
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.hiddenRegions = regions;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change whether an instance casts shadows.
     *
     * @param owner The plugin changing it.
     * @param handle The handle.
     * @param casts Whether it casts shadows.
     * @return False if the handle is null, stale or someone else's.
     */
    public boolean setCastsShadows(
            @NonNull String owner, SkinnedInstanceHandle handle, boolean casts) {
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.castsShadows = casts;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove an instance.
     *
     * @param owner The plugin removing it.
     * @param handle The handle.
     * @return False if the handle is null, stale or someone else's.
     */
    public boolean remove(@NonNull String owner, SkinnedInstanceHandle handle) {
        lock.lock();
        try {
            Entry entry = find(owner, handle);
            if (entry == null) {
                return false;
            }
            free(handle.slot());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every instance a plugin placed.
     *
     * @param owner The plugin.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int removed = 0;
            for (int slot = 0; slot < slots.size(); ++slot) {
                Entry entry = slots.get(slot);
                if (entry != null && owner.equals(entry.owner)) {
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
     * Free a slot, making its handles stale. The lock must be held.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        slots.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        free.add(slot);
    }

    /**
     * Read every instance that can be drawn: posed, with something to draw. The lock is held while
     * the reader runs, so nothing changes under it; keep it short.
     *
     * @param reader Given the drawable instances, in slot order. The list is only valid during the
     *     call.
     */
    public void read(@NonNull Consumer<List<Entry>> reader) {
        lock.lock();
        try {
            drawable.clear();
            for (Entry entry : slots) {
                if (entry != null && entry.palette != null && !entry.parts.isEmpty()) {
                    drawable.add(entry);
                }
            }
            reader.accept(drawable);
        } finally {
            drawable.clear();
            lock.unlock();
        }
    }
}
