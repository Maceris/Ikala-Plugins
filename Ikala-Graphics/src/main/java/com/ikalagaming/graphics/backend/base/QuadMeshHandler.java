package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.graph.QuadMesh;

import lombok.NonNull;

/** Defines the methods that are required for dealing with quad meshes in each backend. */
public interface QuadMeshHandler {
    /**
     * Initialize the mesh, setting it up for use.
     *
     * @param mesh The mesh to set up.
     */
    void initialize(@NonNull QuadMesh mesh);

    /**
     * Clean up the mesh resources.
     *
     * @param mesh The mesh to clean up.
     */
    void cleanup(@NonNull QuadMesh mesh);
}
