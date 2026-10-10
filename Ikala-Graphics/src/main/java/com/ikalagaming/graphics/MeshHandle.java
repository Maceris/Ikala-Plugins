package com.ikalagaming.graphics;

import lombok.NonNull;
import org.joml.Vector3fc;

/**
 * A handle to a mesh in the GPU's shared geometry buffers, along with the facts about it that never
 * change. Holding a handle does not keep the mesh alive: meshes are released explicitly through
 * {@link Meshes#release(MeshHandle)}, or when the plugin that registered them is unloaded. A handle
 * whose mesh was released is stale, and draws nothing.
 *
 * @param slot The mesh's slot in the renderer's mesh table.
 * @param generation How many times the slot had been released when this mesh took it. A stale
 *     handle has an older generation than the mesh currently in the slot.
 * @param vertexCount The number of vertices.
 * @param indexCount The number of indices, three per triangle.
 * @param aabbMin The minimum corner of the mesh's bounding box, in model space.
 * @param aabbMax The maximum corner of the mesh's bounding box, in model space.
 * @param kind Which vertex format the mesh is in and how it is drawn. Each kind's geometry buffers
 *     have their own slots, so the kind is part of what identifies the mesh.
 * @see Meshes
 */
public record MeshHandle(
        int slot,
        int generation,
        int vertexCount,
        int indexCount,
        @NonNull Vector3fc aabbMin,
        @NonNull Vector3fc aabbMax,
        @NonNull MeshKind kind) {

    /**
     * A handle to a standard mesh.
     *
     * @param slot The mesh's slot.
     * @param generation The slot's generation.
     * @param vertexCount The number of vertices.
     * @param indexCount The number of indices.
     * @param aabbMin The minimum corner of the bounding box.
     * @param aabbMax The maximum corner of the bounding box.
     */
    public MeshHandle(
            int slot,
            int generation,
            int vertexCount,
            int indexCount,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        this(slot, generation, vertexCount, indexCount, aabbMin, aabbMax, MeshKind.STANDARD);
    }

    @Override
    public boolean equals(Object other) {
        // The slot, generation and buffers identify a mesh; the rest only describes it
        return other instanceof MeshHandle handle
                && slot == handle.slot
                && generation == handle.generation
                && kind.isBaked() == handle.kind.isBaked();
    }

    @Override
    public int hashCode() {
        return (31 * slot + generation) * 2 + (kind.isBaked() ? 1 : 0);
    }
}
