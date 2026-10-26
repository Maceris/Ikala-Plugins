package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.EffectHandle;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * The particle effects plugins loaded from files, each owned by a plugin. With hot reload on, files
 * are checked for changes now and then, and a changed file's emitters take its new version; an edit
 * that doesn't load is reported once and the last good version stays. Plugins load effects from any
 * thread, so every method holds {@link #lock}.
 */
@Slf4j
public class EffectLibrary {

    /**
     * How often files are checked for changes with hot reload on, in nanoseconds: half a second.
     */
    public static final long CHECK_INTERVAL_NANOS = 500_000_000L;

    /** A loaded file in a slot. */
    private static final class Entry {
        /** The file. */
        Path file;

        /** Its effect, shared with the emitters spawned from it. */
        EffectSource source;

        /** When the file was last changed when last read, or -1 if it couldn't be read. */
        long stamp;

        /** The owning context's key. */
        String owner;

        /** Why the file's last edit didn't load, or null if it did. */
        String error;
    }

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Each slot's file, null when free. */
    private final List<Entry> entries = new ArrayList<>();

    /** How many times each slot was freed. */
    private final List<Integer> generations = new ArrayList<>();

    /** Slots free to take again. */
    private final List<Integer> freeSlots = new ArrayList<>();

    /** Whether files are checked for changes. */
    private boolean hotReload;

    /** When files are next due a check, from {@link System#nanoTime()}. */
    private long nextCheck;

    /**
     * Load an effect from a file, owned by a plugin.
     *
     * @param owner The owning context's key.
     * @param file The file.
     * @return Its handle.
     * @throws ParticleEffectException If the file isn't an effect.
     * @throws UncheckedIOException If it can't be read.
     */
    public EffectHandle load(@NonNull String owner, @NonNull Path file) {
        final Path absolute = file.toAbsolutePath().normalize();
        final long stamp = stamp(absolute);
        final ParticleEffect effect = ParticleEffectLoader.load(absolute);
        lock.lock();
        try {
            final Entry entry = new Entry();
            entry.file = absolute;
            entry.source = new EffectSource(effect);
            entry.stamp = stamp;
            entry.owner = owner;
            final int slot;
            if (freeSlots.isEmpty()) {
                slot = entries.size();
                entries.add(entry);
                generations.add(0);
            } else {
                slot = freeSlots.removeLast();
                entries.set(slot, entry);
            }
            return new EffectHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * The source an owner's effect handle names, for spawning emitters from it.
     *
     * @param owner The owning context's key.
     * @param handle The handle.
     * @return The source, or null if the handle is stale or another owner's.
     */
    public @Nullable EffectSource sourceFor(@NonNull String owner, @Nullable EffectHandle handle) {
        lock.lock();
        try {
            final Entry entry = live(handle);
            return entry != null && owner.equals(entry.owner) ? entry.source : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The effect a handle names now.
     *
     * @param handle The handle.
     * @return The effect, or null if the handle is stale.
     */
    public @Nullable ParticleEffect effectOf(@Nullable EffectHandle handle) {
        lock.lock();
        try {
            final Entry entry = live(handle);
            return entry == null ? null : entry.source.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Why a loaded file's last edit didn't load, if it didn't; its last good effect stays.
     *
     * @param handle The handle.
     * @return The problem, or null if the last edit loaded or the handle is stale.
     */
    public @Nullable String errorOf(@Nullable EffectHandle handle) {
        lock.lock();
        try {
            final Entry entry = live(handle);
            return entry == null ? null : entry.error;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Which owner a handle's effect belongs to.
     *
     * @param handle The handle.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public @Nullable String ownerOf(@Nullable EffectHandle handle) {
        lock.lock();
        try {
            final Entry entry = live(handle);
            return entry == null ? null : entry.owner;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forget an effect an owner loaded. Emitters spawned from it keep its last version.
     *
     * @param owner The owning context's key.
     * @param handle The handle.
     * @return False if the handle is stale or another owner's.
     */
    public boolean unload(@NonNull String owner, @Nullable EffectHandle handle) {
        lock.lock();
        try {
            final Entry entry = live(handle);
            if (entry == null || !owner.equals(entry.owner)) {
                return false;
            }
            free(handle.slot());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forget every effect an owner loaded, as when its plugin unloads.
     *
     * @param owner The owning context's key.
     * @return How many were forgotten.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int removed = 0;
            for (int slot = 0; slot < entries.size(); ++slot) {
                final Entry entry = entries.get(slot);
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
     * Turn checking files for changes on or off, for editing effects while watching them.
     *
     * @param enabled Whether to check.
     */
    public void setHotReload(boolean enabled) {
        lock.lock();
        try {
            hotReload = enabled;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether files are checked for changes.
     *
     * @return True if hot reload is on.
     */
    public boolean isHotReload() {
        lock.lock();
        try {
            return hotReload;
        } finally {
            lock.unlock();
        }
    }

    /**
     * With hot reload on, check the files for changes if a check is due. Render thread, once a
     * frame.
     *
     * @param now The time, from {@link System#nanoTime()}.
     * @return How many effects were reloaded.
     */
    public int checkIfDue(long now) {
        lock.lock();
        try {
            if (!hotReload || now - nextCheck < 0) {
                return 0;
            }
            nextCheck = now + CHECK_INTERVAL_NANOS;
        } finally {
            lock.unlock();
        }
        return reloadChanged();
    }

    /**
     * Read again every file changed since it was last read. A file that no longer loads keeps its
     * last good effect, and is reported once until it changes again.
     *
     * @return How many effects were reloaded.
     */
    public int reloadChanged() {
        final List<Entry> changed = new ArrayList<>();
        lock.lock();
        try {
            for (Entry entry : entries) {
                if (entry == null) {
                    continue;
                }
                final long stamp = stamp(entry.file);
                if (stamp != entry.stamp) {
                    // Noted either way, so a broken edit is reported once
                    entry.stamp = stamp;
                    changed.add(entry);
                }
            }
        } finally {
            lock.unlock();
        }
        int reloaded = 0;
        for (Entry entry : changed) {
            String error = null;
            try {
                entry.source.set(ParticleEffectLoader.load(entry.file));
                log.info("Reloaded particle effect {}", entry.file);
                ++reloaded;
            } catch (ParticleEffectException | UncheckedIOException e) {
                error = e.getMessage();
                log.warn("Could not reload particle effect {}: {}", entry.file, error);
            }
            lock.lock();
            try {
                entry.error = error;
            } finally {
                lock.unlock();
            }
        }
        return reloaded;
    }

    /**
     * When a file was last changed.
     *
     * @param file The file.
     * @return Its modification time in milliseconds, or -1 if it can't be read.
     */
    private static long stamp(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * The entry a handle names now. Hold the lock.
     *
     * @param handle The handle.
     * @return The entry, or null if the handle is null or stale.
     */
    private @Nullable Entry live(@Nullable EffectHandle handle) {
        if (handle == null
                || handle.slot() < 0
                || handle.slot() >= entries.size()
                || generations.get(handle.slot()) != handle.generation()) {
            return null;
        }
        return entries.get(handle.slot());
    }

    /**
     * Free a slot, making handles to it stale. Hold the lock.
     *
     * @param slot The slot.
     */
    private void free(int slot) {
        entries.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
    }
}
