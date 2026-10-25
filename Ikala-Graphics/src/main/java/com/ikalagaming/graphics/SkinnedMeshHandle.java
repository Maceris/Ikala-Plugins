package com.ikalagaming.graphics;

import lombok.NonNull;

/**
 * A handle to a skinned mesh: a mesh whose vertices follow a skeleton's bones, drawn through {@link
 * Skins}. It lives in its own geometry buffers, apart from the meshes {@link Meshes} registers, so
 * it can't be placed as a plain instance. Holding a handle does not keep the mesh alive; see {@link
 * Skins#releaseMesh(SkinnedMeshHandle)}.
 *
 * @param mesh The mesh in the skinned geometry buffers.
 * @param jointCount How many bones the mesh needs: one more than the highest bone index any of its
 *     vertices names. A skinned instance needs at least this many bones to draw it.
 * @see Skins
 */
public record SkinnedMeshHandle(@NonNull MeshHandle mesh, int jointCount) {}
