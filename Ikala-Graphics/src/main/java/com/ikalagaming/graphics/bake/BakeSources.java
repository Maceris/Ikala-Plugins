package com.ikalagaming.graphics.bake;

import com.ikalagaming.graphics.MeshHandle;

import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * The CPU copies of every mesh registered for baking, by handle, with the plugin that owns each.
 * They go when the mesh is released or its plugin unloads. Safe from any thread.
 */
public final class BakeSources {

    /**
     * A source and who owns it.
     *
     * @param owner The owning plugin's key.
     * @param source The source.
     */
    private record Entry(@NonNull String owner, @NonNull BakeSource source) {}

    /** Every source, by its mesh. */
    private final Map<MeshHandle, Entry> sources = new ConcurrentHashMap<>();

    /**
     * Keep a mesh's source.
     *
     * @param owner The owning plugin's key.
     * @param mesh The mesh.
     * @param source Its source.
     */
    public void put(@NonNull String owner, @NonNull MeshHandle mesh, @NonNull BakeSource source) {
        sources.put(mesh, new Entry(owner, source));
    }

    /**
     * Find a mesh's source.
     *
     * @param mesh The mesh.
     * @return The source, or null if the mesh wasn't registered for baking or was released.
     */
    @Nullable public BakeSource get(@Nullable MeshHandle mesh) {
        if (mesh == null) {
            return null;
        }
        Entry entry = sources.get(mesh);
        return entry == null ? null : entry.source();
    }

    /**
     * Forget a mesh's source.
     *
     * @param mesh The mesh.
     */
    public void remove(@Nullable MeshHandle mesh) {
        if (mesh != null) {
            sources.remove(mesh);
        }
    }

    /**
     * Forget every source a plugin owns.
     *
     * @param owner The plugin's key.
     * @return How many were forgotten.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        int before = sources.size();
        sources.values().removeIf(entry -> entry.owner().equals(owner));
        return before - sources.size();
    }

    /** Forget everything, when the renderer shuts down. */
    public void clear() {
        sources.clear();
    }
}
