package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Tracks which plugin owns each texture that was handed out as a {@link TextureHandle}, indexed by
 * bindless slot. This is only bookkeeping: removing a texture returns its Vulkan objects so the
 * caller can destroy them, and the slot itself is released by {@link BindlessTextures}.
 *
 * <p>Each slot has a generation that goes up whenever its texture is removed, so a handle to a
 * removed texture never resolves to whatever texture takes the slot next.
 *
 * <p>Plugins are unloaded from the event dispatcher thread while the main thread renders, so every
 * method holds {@link #lock}. Nothing else is called while holding it.
 */
public class TextureRegistry {

    /** Guards every field below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The texture in each slot, or null if no handle owns the slot. */
    private final TextureInfoVulkan[] infos;

    /** The plugin that owns the texture in each slot, or null if no handle owns the slot. */
    private final String[] owners;

    /** The generation of each slot, which handles must match. */
    private final int[] generations;

    /** The handle that was given out for each slot, or null if no handle owns the slot. */
    private final TextureHandle[] handles;

    /**
     * Create an empty registry.
     *
     * @param capacity The number of bindless slots.
     */
    public TextureRegistry(int capacity) {
        infos = new TextureInfoVulkan[capacity];
        owners = new String[capacity];
        generations = new int[capacity];
        handles = new TextureHandle[capacity];
    }

    /**
     * Track a texture that already has a bindless slot.
     *
     * @param owner The name of the plugin that owns the texture.
     * @param info The texture, registered with {@link BindlessTextures}.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param format The texture format.
     * @return The handle for the texture.
     * @throws IllegalArgumentException If the slot is out of range or already owned.
     */
    public TextureHandle add(
            @NonNull String owner,
            @NonNull TextureInfoVulkan info,
            int width,
            int height,
            @NonNull Format format) {
        final int slot = info.bindlessIndex;
        lock.lock();
        try {
            if (slot < 0 || slot >= infos.length) {
                throw new IllegalArgumentException("Bindless slot " + slot + " is out of range");
            }
            if (infos[slot] != null) {
                throw new IllegalArgumentException("Bindless slot " + slot + " is already owned");
            }
            final TextureHandle handle =
                    new TextureHandle(slot, generations[slot], width, height, format);
            infos[slot] = info;
            owners[slot] = owner;
            handles[slot] = handle;
            return handle;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle still refers to a live texture.
     *
     * @param handle The handle to check.
     * @return True if the texture has not been removed.
     */
    public boolean isValid(@Nullable TextureHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Look up the texture for a handle.
     *
     * @param handle The handle.
     * @return The texture, or null if the handle is null or stale.
     */
    public TextureInfoVulkan resolve(@Nullable TextureHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? infos[handle.slot()] : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The bindless slot a shader should sample for a handle.
     *
     * @param handle The handle.
     * @return The handle's slot, or the default texture's slot if the handle is null or stale.
     */
    public int slotOrDefault(@Nullable TextureHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle)
                    ? handle.slot()
                    : ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The plugin that owns a handle's texture.
     *
     * @param handle The handle.
     * @return The owner's name, or null if the handle is null or stale.
     */
    public String ownerOf(@Nullable TextureHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? owners[handle.slot()] : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop tracking a texture. The caller is responsible for destroying it and releasing its slot.
     *
     * @param handle The handle to remove.
     * @return The texture to destroy, or null if the handle was null or already stale.
     */
    public TextureInfoVulkan remove(@Nullable TextureHandle handle) {
        lock.lock();
        try {
            return isValidLocked(handle) ? removeLocked(handle.slot()) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop tracking every texture a plugin owns.
     *
     * @param owner The plugin name.
     * @return The textures to destroy.
     */
    public List<TextureInfoVulkan> removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            List<TextureInfoVulkan> removed = new ArrayList<>();
            for (int slot = 0; slot < infos.length; ++slot) {
                if (infos[slot] != null && owner.equals(owners[slot])) {
                    removed.add(removeLocked(slot));
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stop tracking every texture, such as when shutting down.
     *
     * @return The textures to destroy.
     */
    public List<TextureInfoVulkan> removeAll() {
        lock.lock();
        try {
            List<TextureInfoVulkan> removed = new ArrayList<>();
            for (int slot = 0; slot < infos.length; ++slot) {
                if (infos[slot] != null) {
                    removed.add(removeLocked(slot));
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Count the textures a plugin owns.
     *
     * @param owner The plugin name.
     * @return The number of live textures it owns.
     */
    public int countOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int count = 0;
            for (int slot = 0; slot < infos.length; ++slot) {
                if (infos[slot] != null && owner.equals(owners[slot])) {
                    ++count;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check a handle against the tracked state. The lock must be held.
     *
     * @param handle The handle.
     * @return Whether the handle refers to the live texture in its slot.
     */
    private boolean isValidLocked(@Nullable TextureHandle handle) {
        if (handle == null) {
            return false;
        }
        final int slot = handle.slot();
        return slot >= 0 && slot < infos.length && handle.equals(handles[slot]);
    }

    /**
     * Clear a slot and bump its generation. The lock must be held, and the slot must be owned.
     *
     * @param slot The slot to clear.
     * @return The texture that was in the slot.
     */
    private TextureInfoVulkan removeLocked(int slot) {
        final TextureInfoVulkan info = infos[slot];
        infos[slot] = null;
        owners[slot] = null;
        handles[slot] = null;
        generations[slot] += 1;
        return info;
    }
}
