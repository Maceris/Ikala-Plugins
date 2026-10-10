package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Tracks the meshes in the shared geometry buffers: which plugin owns each one, where its vertices
 * and indices are, and whether its upload has happened. This is only bookkeeping, with no Vulkan
 * calls, so it can be tested on its own; {@link GeometryArena} owns the buffers.
 *
 * <p>Each slot has a generation that goes up when its mesh is removed, so a handle to a removed
 * mesh never resolves to whatever mesh takes the slot next. Removing a mesh doesn't free its slot
 * or ranges, since frames in flight may still draw from them: it returns a {@link Retired} for the
 * caller to {@link #free(Retired)} once those frames are done.
 *
 * <p>Meshes are registered from any thread, and plugins are unloaded from the event dispatcher
 * thread while the main thread renders, so every method holds {@link #lock}.
 */
public class MeshRegistry {

    /** Where a mesh is in its lifetime. */
    public enum State {
        /** The upload is queued, so nothing may draw the mesh yet. */
        PENDING,
        /** Uploaded, or at least recorded before any draw that could use it. */
        RESIDENT
    }

    /**
     * Where a mesh lives in the shared buffers.
     *
     * @param vertexOffset The index of its first vertex.
     * @param firstIndex The index of its first index.
     */
    public record Location(int vertexOffset, int firstIndex) {}

    /**
     * A removed mesh whose slot and ranges can be reused once no frame in flight can draw it.
     *
     * @param slot The slot.
     * @param vertexOffset Where its vertices start.
     * @param vertexCount How many vertices it had.
     * @param firstIndex Where its indices start.
     * @param indexCount How many indices it had.
     */
    public record Retired(
            int slot, int vertexOffset, int vertexCount, int firstIndex, int indexCount) {}

    /**
     * The most vertices or indices the shared buffers can hold when no limit is given, leaving room
     * for the allocator to double without overflowing an int.
     */
    public static final int MAX_ELEMENTS = Integer.MAX_VALUE / 64;

    /** How many slots to add when the table runs out. */
    private static final int SLOT_GROWTH = 256;

    /** Guards every field below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Hands out vertex ranges. */
    private final RangeAllocator vertices;

    /** Hands out index ranges. */
    private final RangeAllocator indices;

    /** The plugin that owns the mesh in each slot, or null if the slot is free or retiring. */
    private String[] owners = new String[0];

    /** The handle given out for each slot, or null if the slot is free or retiring. */
    private MeshHandle[] handles = new MeshHandle[0];

    /** The state of the mesh in each slot, or null if the slot is free or retiring. */
    private State[] states = new State[0];

    /** Where each slot's vertices start. */
    private int[] vertexOffsets = new int[0];

    /** Where each slot's indices start. */
    private int[] firstIndices = new int[0];

    /** The generation of each slot, which handles must match. */
    private int[] generations = new int[0];

    /** Slots that are free to reuse, lowest first, which keeps the table compact. */
    private final TreeSet<Integer> freeSlots = new TreeSet<>();

    /** How many meshes are registered and not removed. */
    private int liveCount;

    /** Slots whose GPU mesh table entry is out of date. */
    private final BitSet changed = new BitSet();

    /**
     * Receives a mesh table entry that changed, to write into the GPU's copy.
     *
     * <p>Called with the registry locked, so it must not call back into the registry.
     */
    @FunctionalInterface
    public interface EntryWriter {
        /**
         * Write one slot.
         *
         * @param slot The slot.
         * @param resident Whether it holds a mesh that can be drawn. If not, the rest is zero.
         * @param generation The slot's generation, which handles to its mesh match.
         * @param vertexOffset Where its vertices start.
         * @param firstIndex Where its indices start.
         * @param indexCount How many indices it has.
         * @param aabbMin The minimum corner of its bounding box.
         * @param aabbMax The maximum corner of its bounding box.
         */
        void write(
                int slot,
                boolean resident,
                int generation,
                int vertexOffset,
                int firstIndex,
                int indexCount,
                @NonNull Vector3fc aabbMin,
                @NonNull Vector3fc aabbMax);
    }

    /** An empty bounding box corner, written for slots with no mesh. */
    private static final Vector3fc ORIGIN = new Vector3f();

    /**
     * Create an empty registry.
     *
     * @param vertexCapacity How many vertices the shared buffers start out holding.
     * @param indexCapacity How many indices the shared buffers start out holding.
     */
    public MeshRegistry(int vertexCapacity, int indexCapacity) {
        this(vertexCapacity, indexCapacity, MAX_ELEMENTS, MAX_ELEMENTS);
    }

    /**
     * Create an empty registry with limits on how far the buffers can grow.
     *
     * @param vertexCapacity How many vertices the shared buffers start out holding.
     * @param indexCapacity How many indices the shared buffers start out holding.
     * @param maxVertices The most vertices the buffers can ever hold.
     * @param maxIndices The most indices the buffers can ever hold.
     */
    public MeshRegistry(int vertexCapacity, int indexCapacity, int maxVertices, int maxIndices) {
        vertices = new RangeAllocator(vertexCapacity, maxVertices);
        indices = new RangeAllocator(indexCapacity, maxIndices);
    }

    /**
     * Track a new mesh and reserve space for it. It starts out {@link State#PENDING}. The space may
     * grow, so check {@link #getVertexCapacity()} and {@link #getIndexCapacity()} before uploading.
     *
     * @param owner The key of the plugin that owns the mesh.
     * @param vertexCount The number of vertices.
     * @param indexCount The number of indices.
     * @param aabbMin The minimum corner of its bounding box.
     * @param aabbMax The maximum corner of its bounding box.
     * @return The handle, and where the mesh goes.
     * @throws IllegalStateException If the shared buffers can't fit it.
     */
    public Reservation add(
            @NonNull String owner,
            int vertexCount,
            int indexCount,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        return add(owner, MeshKind.STANDARD, vertexCount, indexCount, aabbMin, aabbMax);
    }

    /**
     * Reserve room for a mesh of a given kind, which its handle carries.
     *
     * @param owner The key of the plugin that owns it.
     * @param kind The kind of mesh.
     * @param vertexCount How many vertices it has.
     * @param indexCount How many indices it has.
     * @param aabbMin The minimum corner of its bounding box.
     * @param aabbMax The maximum corner of its bounding box.
     * @return The new handle, pending, and where the data goes.
     * @throws IllegalStateException If there is no room left at all.
     */
    public Reservation add(
            @NonNull String owner,
            @NonNull MeshKind kind,
            int vertexCount,
            int indexCount,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        lock.lock();
        try {
            final int vertexOffset = vertices.allocate(vertexCount);
            if (vertexOffset < 0) {
                throw new IllegalStateException("No room for " + vertexCount + " more vertices");
            }
            final int firstIndex = indices.allocate(indexCount);
            if (firstIndex < 0) {
                vertices.free(vertexOffset, vertexCount);
                throw new IllegalStateException("No room for " + indexCount + " more indices");
            }
            final int slot = takeSlot();
            final MeshHandle handle =
                    new MeshHandle(
                            slot,
                            generations[slot],
                            vertexCount,
                            indexCount,
                            new Vector3f(aabbMin),
                            new Vector3f(aabbMax),
                            kind);
            owners[slot] = owner;
            handles[slot] = handle;
            states[slot] = State.PENDING;
            vertexOffsets[slot] = vertexOffset;
            firstIndices[slot] = firstIndex;
            liveCount += 1;
            return new Reservation(handle, new Location(vertexOffset, firstIndex));
        } finally {
            lock.unlock();
        }
    }

    /**
     * A new mesh's handle and where it goes.
     *
     * @param handle The handle.
     * @param location Where its vertices and indices go.
     */
    public record Reservation(@NonNull MeshHandle handle, @NonNull Location location) {}

    /**
     * Whether a handle still refers to a live mesh.
     *
     * @param handle The handle to check.
     * @return True if the mesh has not been removed.
     */
    public boolean isValid(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle refers to a live mesh that can be drawn.
     *
     * @param handle The handle to check.
     * @return True if the mesh is valid and resident.
     */
    public boolean isResident(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) && states[handle.slot()] == State.RESIDENT;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark a mesh as resident, once its upload is recorded before anything that could draw it.
     *
     * @param handle The mesh.
     * @return True if it was marked, false if the handle was null or stale.
     */
    public boolean markResident(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            if (!isValidLocked(handle)) {
                return false;
            }
            states[handle.slot()] = State.RESIDENT;
            changed.set(handle.slot());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Where a mesh is, for drawing it.
     *
     * @param handle The mesh.
     * @return Where its data is, or null if the handle is null, stale or not resident yet.
     */
    public Location locate(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            if (!isValidLocked(handle) || states[handle.slot()] != State.RESIDENT) {
                return null;
            }
            return new Location(vertexOffsets[handle.slot()], firstIndices[handle.slot()]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Where a mesh goes, whether or not it is resident yet, for uploading it.
     *
     * @param handle The mesh.
     * @return Where its data goes, or null if the handle is null or stale.
     */
    public Location locatePending(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            if (!isValidLocked(handle)) {
                return null;
            }
            return new Location(vertexOffsets[handle.slot()], firstIndices[handle.slot()]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The plugin that owns a mesh.
     *
     * @param handle The mesh.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public String ownerOf(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? owners[handle.slot()] : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop tracking a mesh. Its handle goes stale right away, but its slot and ranges stay taken
     * until the caller frees the result.
     *
     * @param handle The mesh.
     * @return What to free once no frame can draw it, or null if the handle was null or stale.
     */
    public Retired remove(@Nullable MeshHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? removeLocked(handle.slot()) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop tracking every mesh a plugin owns.
     *
     * @param owner The plugin's key.
     * @return What to free once no frame can draw them.
     */
    public List<Retired> removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            List<Retired> removed = new ArrayList<>();
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
     * Stop tracking every mesh, such as when shutting down.
     *
     * @return What to free.
     */
    public List<Retired> removeAll() {
        lock.lock();
        try {
            List<Retired> removed = new ArrayList<>();
            for (int slot = 0; slot < handles.length; ++slot) {
                if (handles[slot] != null) {
                    removed.add(removeLocked(slot));
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Free a removed mesh's slot and ranges for reuse. Only call this once no frame in flight can
     * draw it.
     *
     * @param retired The removed mesh.
     */
    public void free(@NonNull Retired retired) {
        lock.lock();
        try {
            vertices.free(retired.vertexOffset(), retired.vertexCount());
            indices.free(retired.firstIndex(), retired.indexCount());
            freeSlots.add(retired.slot());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Count the meshes a plugin owns.
     *
     * @param owner The plugin's key.
     * @return The number of live meshes it owns.
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
     * How many slots the mesh table has.
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
     * Hand every slot whose GPU entry changed since the last call to a writer, and forget the
     * changes. A mesh's entry changes when it becomes resident and when it is removed.
     *
     * @param writer Receives each changed slot, lowest first.
     * @return The slot count, which the GPU table must hold, read with the changes.
     */
    public int takeChanged(@NonNull EntryWriter writer) {
        lock.lock();
        try {
            for (int slot = changed.nextSetBit(0); slot >= 0; slot = changed.nextSetBit(slot + 1)) {
                final MeshHandle handle = handles[slot];
                if (handle != null && states[slot] == State.RESIDENT) {
                    writer.write(
                            slot,
                            true,
                            generations[slot],
                            vertexOffsets[slot],
                            firstIndices[slot],
                            handle.indexCount(),
                            handle.aabbMin(),
                            handle.aabbMax());
                } else {
                    writer.write(slot, false, generations[slot], 0, 0, 0, ORIGIN, ORIGIN);
                }
            }
            changed.clear();
            return handles.length;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hand every slot to a writer as it is now, for building draw commands, which need each mesh's
     * offsets whether or not its GPU entry changed.
     *
     * @param writer Receives every slot, lowest first. Slots without a resident mesh have zero
     *     offsets and counts.
     * @return The slot count.
     */
    public int visitAll(@NonNull EntryWriter writer) {
        lock.lock();
        try {
            for (int slot = 0; slot < handles.length; ++slot) {
                final MeshHandle handle = handles[slot];
                if (handle != null && states[slot] == State.RESIDENT) {
                    writer.write(
                            slot,
                            true,
                            generations[slot],
                            vertexOffsets[slot],
                            firstIndices[slot],
                            handle.indexCount(),
                            handle.aabbMin(),
                            handle.aabbMax());
                } else {
                    writer.write(slot, false, generations[slot], 0, 0, 0, ORIGIN, ORIGIN);
                }
            }
            return handles.length;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many meshes are live.
     *
     * @return The number of registered meshes not yet removed.
     */
    public int getMeshCount() {
        lock.lock();
        try {
            return liveCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many vertices the shared vertex buffer must hold.
     *
     * @return The vertex capacity.
     */
    public int getVertexCapacity() {
        lock.lock();
        try {
            return vertices.getCapacity();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many indices the shared index buffer must hold.
     *
     * @return The index capacity.
     */
    public int getIndexCapacity() {
        lock.lock();
        try {
            return indices.getCapacity();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many vertices are in use, including meshes that are retiring.
     *
     * @return The used vertex count.
     */
    public int getVerticesUsed() {
        lock.lock();
        try {
            return vertices.getUsed();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many indices are in use, including meshes that are retiring.
     *
     * @return The used index count.
     */
    public int getIndicesUsed() {
        lock.lock();
        try {
            return indices.getUsed();
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
            states = Arrays.copyOf(states, newLength);
            vertexOffsets = Arrays.copyOf(vertexOffsets, newLength);
            firstIndices = Arrays.copyOf(firstIndices, newLength);
            generations = Arrays.copyOf(generations, newLength);
            for (int slot = oldLength; slot < newLength; ++slot) {
                freeSlots.add(slot);
            }
        }
        return freeSlots.pollFirst();
    }

    /**
     * Check a handle against the tracked state. The lock must be held.
     *
     * @param handle The handle.
     * @return Whether the handle refers to the live mesh in its slot.
     */
    private boolean isValidLocked(@Nullable MeshHandle handle) {
        if (handle == null) {
            return false;
        }
        final int slot = handle.slot();
        return slot >= 0 && slot < handles.length && handle.equals(handles[slot]);
    }

    /**
     * Clear a slot's mesh and bump its generation, keeping the slot taken. The lock must be held,
     * and the slot must hold a mesh.
     *
     * @param slot The slot.
     * @return What to free later.
     */
    private Retired removeLocked(int slot) {
        final MeshHandle handle = handles[slot];
        owners[slot] = null;
        handles[slot] = null;
        states[slot] = null;
        generations[slot] += 1;
        changed.set(slot);
        liveCount -= 1;
        return new Retired(
                slot,
                vertexOffsets[slot],
                handle.vertexCount(),
                firstIndices[slot],
                handle.indexCount());
    }
}
