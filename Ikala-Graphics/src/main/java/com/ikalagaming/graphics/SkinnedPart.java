package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

/**
 * One mesh a skinned instance draws, and the material it is drawn with.
 *
 * @param mesh The skinned mesh.
 * @param material The material, which must be in the scene's material cache, or null for the
 *     default material.
 * @see Skins#setParts(SkinnedInstanceHandle, java.util.List)
 */
public record SkinnedPart(@NonNull SkinnedMeshHandle mesh, Material material) {}
