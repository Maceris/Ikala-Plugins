package com.ikalagaming.graphics;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * A decal projected onto a skinned instance, like an emblem on a character's chest. It is fixed to
 * the instance at rest: a box in the meshes' rest space, given as the matrix from rest space into
 * it, where x and y run from 0 to 1 across the decal and z from -1 to 1 through its depth, with +z
 * out of the surface it lies on. Parts that take decals (see {@link SkinnedPart#decals}) show it
 * wherever their rest surface lies in the box facing +z, so it stays put on them as they move.
 *
 * <p>Its art is a mask like a tint mask: red takes the first color, green the second, and alpha is
 * how much of the surface it covers. Each color is one of the instance's tint slots (see {@link
 * Skins#setTints}), or its fallback where the slot is unset.
 *
 * @param art The art, linear rather than sRGB.
 * @param restToBox The matrix from the meshes' rest space into the box.
 * @param slotA The tint slot red takes, 0 to {@value Skins#TINT_SLOTS} - 1, or -1 for its fallback
 *     always.
 * @param slotB The tint slot green takes, or -1.
 * @param fallbackA Red's color while its slot is unset, packed as {@link SkinnedPart#pack} packs.
 * @param fallbackB Green's color while its slot is unset.
 */
public record SkinnedDecal(
        @NonNull TextureHandle art,
        @NonNull Matrix4fc restToBox,
        int slotA,
        int slotB,
        int fallbackA,
        int fallbackB) {
    /**
     * Check the slots and keep a copy of the matrix.
     *
     * @throws IllegalArgumentException If a slot is out of range.
     */
    public SkinnedDecal {
        for (int slot : new int[] {slotA, slotB}) {
            if (slot < -1 || slot >= Skins.TINT_SLOTS) {
                throw new IllegalArgumentException(
                        "Tint slots go from 0 to " + (Skins.TINT_SLOTS - 1) + ", or -1 for none");
            }
        }
        restToBox = new Matrix4f(restToBox);
    }
}
