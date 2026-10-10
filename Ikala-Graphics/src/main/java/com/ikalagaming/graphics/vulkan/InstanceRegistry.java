package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.AnimationState;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Tracks everything placed in the scene: which plugin owns each instance, its model, transform,
 * material overrides and animation, and which instances changed since the GPU's copy of the table
 * was last updated. This is only bookkeeping, with no Vulkan calls, so it can be tested on its own;
 * {@link InstanceTable} owns the GPU copy.
 *
 * <p>Each slot has a generation that goes up when its instance is removed, so a handle to a removed
 * instance never resolves to whatever takes the slot next. Removing an instance doesn't free its
 * slot, since frames in flight may still read it: the caller {@link #free(int) frees} it once those
 * frames are done.
 *
 * <p>Instances are placed and changed from any thread, and plugins are unloaded from the event
 * dispatcher thread while the main thread renders, so every method holds {@link #lock}.
 */
public class InstanceRegistry {

    /**
     * Receives a changed instance, to write into the GPU table.
     *
     * <p>Called with the registry locked, so it must not call back into the registry.
     */
    @FunctionalInterface
    public interface ChangeWriter {
        /**
         * Write one slot.
         *
         * @param slot The slot.
         * @param alive Whether the slot holds an instance. If not, the other values are zero.
         * @param position The world position.
         * @param rotation The rotation.
         * @param scale The uniform scale.
         */
        void write(
                int slot,
                boolean alive,
                @NonNull Vector3dc position,
                @NonNull Quaternionfc rotation,
                float scale);
    }

    /**
     * Receives the instances of a model, to draw them.
     *
     * <p>Called with the registry locked, so it must not call back into the registry.
     */
    @FunctionalInterface
    public interface InstanceVisitor {
        /**
         * Visit one instance.
         *
         * @param slot The instance's slot.
         * @param materials The material override for each mesh, null entries for none. Don't change
         *     it.
         * @param animation The animation state, or null if it isn't animating.
         */
        void visit(int slot, @NonNull Material[] materials, @Nullable AnimationState animation);
    }

    /** How many slots to add when the table runs out. */
    private static final int SLOT_GROWTH = 256;

    /** An empty transform, written for removed slots. */
    private static final Vector3dc ZERO = new Vector3d();

    /** The identity rotation, written for removed slots. */
    private static final Quaternionfc IDENTITY = new Quaternionf();

    /** Guards every field below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The plugin that owns the instance in each slot, or null if the slot is free or retiring. */
    private String[] owners = new String[0];

    /** The handle given out for each slot, or null if the slot is free or retiring. */
    private InstanceHandle[] handles = new InstanceHandle[0];

    /** The world position of each slot. */
    private Vector3d[] positions = new Vector3d[0];

    /** The rotation of each slot. */
    private Quaternionf[] rotations = new Quaternionf[0];

    /** The uniform scale of each slot. */
    private float[] scales = new float[0];

    /** The material override of each mesh, per slot. */
    private Material[][] materials = new Material[0][];

    /** The animation state of each slot, or null. */
    private AnimationState[] animations = new AnimationState[0];

    /** The generation of each slot, which handles must match. */
    private int[] generations = new int[0];

    /** Slots that are free to reuse, lowest first, which keeps the table compact. */
    private final TreeSet<Integer> freeSlots = new TreeSet<>();

    /** Slots whose GPU copy is out of date. */
    private final BitSet changed = new BitSet();

    /** The slots of each model's instances, in the order they were placed. */
    private final Map<Model, LinkedHashSet<Integer>> byModel = new HashMap<>();

    /** How many instances are placed and not removed. */
    private int liveCount;

    /**
     * Place an instance. It is drawn from the next frame on.
     *
     * @param owner The key of the plugin that owns it.
     * @param model The model to place.
     * @param position The world position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @return The handle.
     */
    public InstanceHandle place(
            @NonNull String owner,
            @NonNull Model model,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        lock.lock();
        try {
            final int slot = takeSlot();
            final InstanceHandle handle = new InstanceHandle(slot, generations[slot], model);
            owners[slot] = owner;
            handles[slot] = handle;
            positions[slot].set(position);
            rotations[slot].set(rotation);
            scales[slot] = scale;
            materials[slot] = new Material[model.getMeshDataList().size()];
            animations[slot] = null;
            byModel.computeIfAbsent(model, ignored -> new LinkedHashSet<>()).add(slot);
            changed.set(slot);
            liveCount += 1;
            return handle;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change an instance's transform.
     *
     * @param owner The key of the plugin asking, which must own it, or null to skip the check.
     * @param handle The instance.
     * @param position The new position, or null to keep it.
     * @param rotation The new rotation, or null to keep it.
     * @param scale The new scale, or NaN to keep it.
     * @return False if the handle is stale or owned by another plugin.
     */
    public boolean transform(
            @Nullable String owner,
            @Nullable InstanceHandle handle,
            @Nullable Vector3dc position,
            @Nullable Quaternionfc rotation,
            float scale) {
        lock.lock();
        try {
            if (!isOwnedLocked(owner, handle)) {
                return false;
            }
            final int slot = handle.slot();
            if (position != null) {
                positions[slot].set(position);
            }
            if (rotation != null) {
                rotations[slot].set(rotation);
            }
            if (!Float.isNaN(scale)) {
                scales[slot] = scale;
            }
            changed.set(slot);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change the material one of an instance's meshes is drawn with.
     *
     * @param owner The key of the plugin asking, which must own it, or null to skip the check.
     * @param handle The instance.
     * @param meshIndex The mesh, by its index in the model.
     * @param material The material, or null to use the mesh's own.
     * @return False if the handle is stale or owned by another plugin.
     * @throws IndexOutOfBoundsException If the model has no such mesh.
     */
    public boolean setMaterial(
            @Nullable String owner,
            @Nullable InstanceHandle handle,
            int meshIndex,
            @Nullable Material material) {
        lock.lock();
        try {
            if (!isOwnedLocked(owner, handle)) {
                return false;
            }
            Material[] slotMaterials = materials[handle.slot()];
            if (meshIndex < 0 || meshIndex >= slotMaterials.length) {
                throw new IndexOutOfBoundsException(
                        "Mesh " + meshIndex + " of " + slotMaterials.length);
            }
            slotMaterials[meshIndex] = material;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change an instance's animation.
     *
     * @param owner The key of the plugin asking, which must own it, or null to skip the check.
     * @param handle The instance.
     * @param animation The animation state, or null to stop animating.
     * @return False if the handle is stale or owned by another plugin.
     */
    public boolean setAnimation(
            @Nullable String owner,
            @Nullable InstanceHandle handle,
            @Nullable AnimationState animation) {
        lock.lock();
        try {
            if (!isOwnedLocked(owner, handle)) {
                return false;
            }
            animations[handle.slot()] = animation;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Read an instance's position.
     *
     * @param handle The instance.
     * @param dest Where to store the position.
     * @return False if the handle is stale, leaving dest unchanged.
     */
    public boolean getPosition(@Nullable InstanceHandle handle, @NonNull Vector3d dest) {
        lock.lock();
        try {
            if (!isValidLocked(handle)) {
                return false;
            }
            dest.set(positions[handle.slot()]);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Read an instance's rotation.
     *
     * @param handle The instance.
     * @param dest Where to store the rotation.
     * @return False if the handle is stale, leaving dest unchanged.
     */
    public boolean getRotation(@Nullable InstanceHandle handle, @NonNull Quaternionf dest) {
        lock.lock();
        try {
            if (!isValidLocked(handle)) {
                return false;
            }
            dest.set(rotations[handle.slot()]);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle still refers to a placed instance.
     *
     * @param handle The handle.
     * @return True if it hasn't been removed.
     */
    public boolean isValid(@Nullable InstanceHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The plugin that owns an instance.
     *
     * @param handle The instance.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public String ownerOf(@Nullable InstanceHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? owners[handle.slot()] : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove an instance. Its handle goes stale and it stops being drawn from the next frame, but
     * its slot stays taken until the caller frees it.
     *
     * @param owner The key of the plugin asking, which must own it, or null to skip the check.
     * @param handle The instance.
     * @return The slot to free once no frame in flight can read it, or -1 if the handle was stale
     *     or owned by another plugin.
     */
    public int remove(@Nullable String owner, @Nullable InstanceHandle handle) {
        lock.lock();
        try {
            return isOwnedLocked(owner, handle) ? removeLocked(handle.slot()) : -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every instance a plugin owns.
     *
     * @param owner The plugin's key.
     * @return The slots to free once no frame in flight can read them.
     */
    public List<Integer> removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            List<Integer> removed = new ArrayList<>();
            for (int slot = 0; slot < handles.length; ++slot) {
                if (handles[slot] != null && owner.equals(owners[slot])) {
                    removed.add(removeLocked(slot));
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every instance of a model, such as when the model is replaced in the scene.
     *
     * @param model The model.
     * @return The slots to free once no frame in flight can read them.
     */
    public List<Integer> removeAllOf(@NonNull Model model) {
        lock.lock();
        try {
            List<Integer> removed = new ArrayList<>();
            LinkedHashSet<Integer> slots = byModel.get(model);
            if (slots != null) {
                for (int slot : List.copyOf(slots)) {
                    removed.add(removeLocked(slot));
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Free a removed instance's slot for reuse. Only call this once no frame in flight can read it.
     *
     * @param slot The slot.
     */
    public void free(int slot) {
        lock.lock();
        try {
            freeSlots.add(slot);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hand every slot that changed since the last call to a writer, and forget the changes.
     *
     * @param writer Receives each changed slot, lowest first.
     * @return How many slots changed.
     */
    public int takeChanged(@NonNull ChangeWriter writer) {
        lock.lock();
        try {
            int count = 0;
            for (int slot = changed.nextSetBit(0); slot >= 0; slot = changed.nextSetBit(slot + 1)) {
                if (handles[slot] != null) {
                    writer.write(slot, true, positions[slot], rotations[slot], scales[slot]);
                } else {
                    writer.write(slot, false, ZERO, IDENTITY, 0);
                }
                count += 1;
            }
            changed.clear();
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Visit the instances of a model, in the order they were placed.
     *
     * @param model The model.
     * @param visitor Receives each instance.
     * @return How many instances it has.
     */
    public int visit(@NonNull Model model, @NonNull InstanceVisitor visitor) {
        lock.lock();
        try {
            LinkedHashSet<Integer> slots = byModel.get(model);
            if (slots == null) {
                return 0;
            }
            for (int slot : slots) {
                visitor.visit(slot, materials[slot], animations[slot]);
            }
            return slots.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Visit the transform of every instance of a model, such as to draw its bounds.
     *
     * @param model The model.
     * @param visitor Receives each instance's position, rotation and scale. Don't keep them.
     */
    public void visitTransforms(@NonNull Model model, @NonNull ChangeWriter visitor) {
        lock.lock();
        try {
            LinkedHashSet<Integer> slots = byModel.get(model);
            if (slots != null) {
                for (int slot : slots) {
                    visitor.write(slot, true, positions[slot], rotations[slot], scales[slot]);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many instances a model has.
     *
     * @param model The model.
     * @return The number of instances.
     */
    public int countOf(@NonNull Model model) {
        lock.lock();
        try {
            LinkedHashSet<Integer> slots = byModel.get(model);
            return slots == null ? 0 : slots.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Count the instances a plugin owns.
     *
     * @param owner The plugin's key.
     * @return The number of instances it owns.
     */
    public int countOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int count = 0;
            for (int slot = 0; slot < handles.length; ++slot) {
                if (handles[slot] != null && owner.equals(owners[slot])) {
                    ++count;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many instances are placed.
     *
     * @return The number of instances not removed.
     */
    public int getInstanceCount() {
        lock.lock();
        try {
            return liveCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many slots the table has, which the GPU copy must hold.
     *
     * @return The slot count.
     */
    public int getSlotCapacity() {
        lock.lock();
        try {
            return handles.length;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a free slot, growing the table if there is none. The lock must be held.
     *
     * @return The slot.
     */
    private int takeSlot() {
        if (freeSlots.isEmpty()) {
            final int oldLength = handles.length;
            final int newLength = oldLength + SLOT_GROWTH;
            owners = Arrays.copyOf(owners, newLength);
            handles = Arrays.copyOf(handles, newLength);
            positions = Arrays.copyOf(positions, newLength);
            rotations = Arrays.copyOf(rotations, newLength);
            scales = Arrays.copyOf(scales, newLength);
            materials = Arrays.copyOf(materials, newLength);
            animations = Arrays.copyOf(animations, newLength);
            generations = Arrays.copyOf(generations, newLength);
            for (int slot = oldLength; slot < newLength; ++slot) {
                positions[slot] = new Vector3d();
                rotations[slot] = new Quaternionf();
                freeSlots.add(slot);
            }
        }
        return freeSlots.pollFirst();
    }

    /**
     * Check a handle against the tracked state. The lock must be held.
     *
     * @param handle The handle.
     * @return Whether the handle refers to the live instance in its slot.
     */
    private boolean isValidLocked(@Nullable InstanceHandle handle) {
        if (handle == null) {
            return false;
        }
        final int slot = handle.slot();
        return slot >= 0 && slot < handles.length && handle.equals(handles[slot]);
    }

    /**
     * Check a handle is valid and owned by a plugin. The lock must be held.
     *
     * @param owner The plugin's key, or null to only check the handle.
     * @param handle The handle.
     * @return Whether the handle is live and owned by that plugin.
     */
    private boolean isOwnedLocked(@Nullable String owner, @Nullable InstanceHandle handle) {
        return isValidLocked(handle) && (owner == null || owner.equals(owners[handle.slot()]));
    }

    /**
     * Clear a slot's instance and bump its generation, keeping the slot taken. The lock must be
     * held, and the slot must hold an instance.
     *
     * @param slot The slot.
     * @return The slot.
     */
    private int removeLocked(int slot) {
        final InstanceHandle handle = handles[slot];
        LinkedHashSet<Integer> slots = byModel.get(handle.model());
        if (slots != null) {
            slots.remove(slot);
            if (slots.isEmpty()) {
                byModel.remove(handle.model());
            }
        }
        owners[slot] = null;
        handles[slot] = null;
        materials[slot] = null;
        animations[slot] = null;
        generations[slot] += 1;
        changed.set(slot);
        liveCount -= 1;
        return slot;
    }
}
