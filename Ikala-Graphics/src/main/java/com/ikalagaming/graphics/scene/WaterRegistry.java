package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.WaterHandle;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    /** The most disturbances waiting at once; past it the oldest are dropped. */
    public static final int MAX_WAITING_DISTURBANCES = 1024;

    /**
     * Something that set water rippling, waiting for the wave grid to take it in.
     *
     * @param position Where, in the world.
     * @param radius How far around it, in meters.
     * @param strength How high its ripples start, in meters.
     */
    public record Disturbance(@NonNull Vector3dc position, float radius, float strength) {}

    /** The disturbances waiting, oldest first. */
    private final Deque<Disturbance> disturbances = new ArrayDeque<>();

    /** The most wave paints waiting at once; past it new ones are dropped. */
    public static final int MAX_WAITING_PAINTS = 1024;

    /**
     * A patch of water whose wind waves are set by hand for a frame, as the water surface wavelets
     * paper's amplitude painting does: waves leave it the way it points.
     *
     * @param position Where, in the world.
     * @param radius How far around it, in meters; it fades out toward the edge.
     * @param direction The way its waves go, in radians from +x toward +z.
     * @param strength How tall they are, as a share of the wind's waves in the wind's own
     *     direction.
     */
    public record WavePaint(
            @NonNull Vector3dc position, float radius, float direction, float strength) {}

    /** This frame's wave paints. */
    private final List<WavePaint> paints = new ArrayList<>();

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

    /** The particles the water throws up, and who set them, null for the water's own. */
    private WaterEffects effects = WaterEffects.DEFAULT;

    private @Nullable String effectsOwner;

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
            if (owner.equals(effectsOwner)) {
                effects = WaterEffects.DEFAULT;
                effectsOwner = null;
            }
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
     * Set the water rippling somewhere; it shows from the next frame, on water near the camera.
     *
     * @param disturbance What disturbed it.
     */
    public void disturb(@NonNull Disturbance disturbance) {
        lock.lock();
        try {
            if (disturbances.size() >= MAX_WAITING_DISTURBANCES) {
                disturbances.removeFirst();
            }
            disturbances.addLast(disturbance);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the wind waves over a patch for the next frame.
     *
     * @param paint The patch.
     */
    public void paint(@NonNull WavePaint paint) {
        lock.lock();
        try {
            if (paints.size() < MAX_WAITING_PAINTS) {
                paints.add(paint);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take the waiting wave paints, up to a count, dropping the rest: they last one frame.
     *
     * @param most The most to take.
     * @param into Filled with them.
     */
    public void takePaints(int most, @NonNull List<WavePaint> into) {
        into.clear();
        lock.lock();
        try {
            into.addAll(paints.subList(0, Math.min(most, paints.size())));
            paints.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take the waiting disturbances, up to a count, oldest first.
     *
     * @param most The most to take.
     * @param into Filled with them.
     */
    public void takeDisturbances(int most, @NonNull List<Disturbance> into) {
        into.clear();
        lock.lock();
        try {
            while (into.size() < most && !disturbances.isEmpty()) {
                into.add(disturbances.removeFirst());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the particles the water throws up, for the whole scene, until the owner sets others or
     * goes, when the water's own come back.
     *
     * @param owner Who sets them.
     * @param effectsNow The effects, or null for the water's own.
     */
    public void setEffects(@NonNull String owner, @Nullable WaterEffects effectsNow) {
        lock.lock();
        try {
            effects = effectsNow == null ? WaterEffects.DEFAULT : effectsNow;
            effectsOwner = effectsNow == null ? null : owner;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The particles the water throws up.
     *
     * @return The effects.
     */
    public WaterEffects getEffects() {
        lock.lock();
        try {
            return effects;
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
