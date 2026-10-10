package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;

import lombok.Getter;
import lombok.NonNull;

/**
 * Which vertex format a mesh is in and how it is drawn. Each kind gets its own draw commands and
 * pipeline, and the baked kinds share a separate set of geometry buffers from standard meshes, so a
 * mesh handle is only meaningful together with its kind.
 */
@Getter
public enum MeshKind {
    /** A mesh registered from a model, in the full vertex format. */
    STANDARD(false),
    /** Part of a baked section that hides what is behind it. */
    BAKED_OPAQUE(true),
    /** Part of a baked section with holes cut out by its textures. */
    BAKED_CUTOUT(true),
    /** Part of a baked section that can be seen through. */
    BAKED_TRANSLUCENT(true);

    /** Every kind, in order, without allocating a new array each time. */
    public static final MeshKind[] ALL = values();

    /**
     * Whether the mesh is in the compact baked vertex format. -- GETTER -- Whether it is baked.
     *
     * @return True for the baked kinds.
     */
    private final boolean baked;

    /**
     * Set up a kind.
     *
     * @param baked Whether it is in the baked vertex format.
     */
    MeshKind(boolean baked) {
        this.baked = baked;
    }

    /**
     * The baked kind for a material transparency.
     *
     * @param transparency The transparency.
     * @return The kind its baked geometry is drawn as.
     */
    public static MeshKind baked(@NonNull Material.Transparency transparency) {
        return switch (transparency) {
            case OPAQUE -> BAKED_OPAQUE;
            case CUTOUT -> BAKED_CUTOUT;
            case TRANSLUCENT -> BAKED_TRANSLUCENT;
        };
    }
}
