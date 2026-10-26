package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.WaterHandle;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The scene's water tiles, which plugins place through {@link com.ikalagaming.graphics.Water}, each
 * owned by a plugin's owner key. Every change bumps a version, so the renderer rebuilds the
 * surfaces only when something changed. Thread safe.
 */
public class WaterRegistry {

    /** The most tiles the scene holds. */
    public static final int MAX_TILES = 4096;

    /** Guards everything below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The tiles by slot, null where free. */
    private final List<WaterTile> tiles = new ArrayList<>();

    /** Who owns each slot's tile. */
    private final List<String> owners = new ArrayList<>();

    /** How many times each slot was freed, to tell stale handles apart. */
    private final List<Integer> generations = new ArrayList<>();

    /** Slots free to reuse. */
    private final List<Integer> freeSlots = new ArrayList<>();

    /** How many tiles there are. */
    private int count;

    /** Bumped on every change. */
    private long version;

    /**
     * Place a tile.
     *
     * @param owner Who owns it.
     * @param tile The tile.
     * @return Its handle, or null if the scene already holds {@link #MAX_TILES}.
     */
    public @Nullable WaterHandle add(@NonNull String owner, @NonNull WaterTile tile) {
        lock.lock();
        try {
            if (count >= MAX_TILES) {
                return null;
            }
            final int slot;
            if (freeSlots.isEmpty()) {
                slot = tiles.size();
                tiles.add(tile);
                owners.add(owner);
                generations.add(0);
            } else {
                slot = freeSlots.removeLast();
                tiles.set(slot, tile);
                owners.set(slot, owner);
            }
            ++count;
            ++version;
            return new WaterHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change a tile its owner placed.
     *
     * @param owner Who asks.
     * @param handle The tile's handle.
     * @param tile What it is now.
     * @return Whether it was changed: false for a stale handle or another owner's tile.
     */
    public boolean update(
            @NonNull String owner, @Nullable WaterHandle handle, @NonNull WaterTile tile) {
        lock.lock();
        try {
            if (!owns(owner, handle)) {
                return false;
            }
            tiles.set(handle.slot(), tile);
            ++version;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a tile its owner placed.
     *
     * @param owner Who asks.
     * @param handle The tile's handle.
     * @return Whether it was removed.
     */
    public boolean remove(@NonNull String owner, @Nullable WaterHandle handle) {
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
     * Remove every tile an owner placed, as when its plugin unloads.
     *
     * @param owner The owner.
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
     * Whether a handle still refers to a tile.
     *
     * @param handle The handle.
     * @return False for null or stale handles.
     */
    public boolean isValid(@Nullable WaterHandle handle) {
        lock.lock();
        try {
            return live(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Who owns a tile.
     *
     * @param handle The tile's handle.
     * @return The owner, or null for a stale handle.
     */
    public @Nullable String ownerOf(@Nullable WaterHandle handle) {
        lock.lock();
        try {
            return live(handle) ? owners.get(handle.slot()) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Copy out every tile, if anything changed since a version.
     *
     * @param since The version last copied, or -1 for none.
     * @param into Filled with the tiles, if they changed; left as it is otherwise.
     * @return The version now; equal to since when nothing changed.
     */
    public long snapshotIfChanged(long since, @NonNull List<WaterTile> into) {
        lock.lock();
        try {
            if (version == since) {
                return version;
            }
            into.clear();
            for (WaterTile tile : tiles) {
                if (tile != null) {
                    into.add(tile);
                }
            }
            return version;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many tiles there are.
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
     * Whether a handle refers to the tile in its slot. Call with the lock held.
     *
     * @param handle The handle.
     * @return Whether it does.
     */
    private boolean live(@Nullable WaterHandle handle) {
        return handle != null
                && handle.slot() >= 0
                && handle.slot() < tiles.size()
                && tiles.get(handle.slot()) != null
                && generations.get(handle.slot()) == handle.generation();
    }

    /**
     * Whether an owner owns a live handle's tile. Call with the lock held.
     *
     * @param owner The owner.
     * @param handle The handle.
     * @return Whether it does.
     */
    private boolean owns(@NonNull String owner, @Nullable WaterHandle handle) {
        return live(handle) && owner.equals(owners.get(handle.slot()));
    }

    /**
     * Free a slot. Call with the lock held.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        tiles.set(slot, null);
        owners.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
        --count;
        ++version;
    }
}
