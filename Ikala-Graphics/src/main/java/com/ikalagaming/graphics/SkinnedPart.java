package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

/**
 * One mesh a skinned instance draws, the material it is drawn with, and how its instance's tint
 * colors color it.
 *
 * <p>A tint mask is a texture whose four channels say how much of a tint color each texel takes.
 * {@code tintSlots} maps each mask channel to one of the instance's {@value Skins#TINT_SLOTS} tint
 * colors (see {@link Skins#setTints}), or to none. The surface's color is multiplied, in linear
 * light, by each mapped color, as much as its channel says: {@code color *= mix(white, tint,
 * mask)}. A part with slots but no mask takes the red channel's color everywhere, so plain parts
 * can be tinted whole.
 *
 * @param mesh The skinned mesh.
 * @param material The material, which must be in the scene's material cache, or null for the
 *     default material.
 * @param tintMask The tint mask, linear rather than sRGB, or null for none.
 * @param tintSlots Which tint color each mask channel takes, from {@link #slots}, or {@link
 *     #NO_TINT}.
 * @see Skins#setParts(SkinnedInstanceHandle, java.util.List)
 */
public record SkinnedPart(
        @NonNull SkinnedMeshHandle mesh, Material material, TextureHandle tintMask, int tintSlots) {
    /** Slots that take no tint for any channel. */
    public static final int NO_TINT = 0xFFFF_FFFF;

    /** The slot number that means a channel takes no tint. */
    private static final int NO_SLOT = 0xFF;

    /** Bits per channel's slot in packed slots. */
    private static final int SLOT_BITS = 8;

    /**
     * A part drawn as its material says, with no tint.
     *
     * @param mesh The skinned mesh.
     * @param material The material, which must be in the scene's material cache, or null for the
     *     default material.
     */
    public SkinnedPart(@NonNull SkinnedMeshHandle mesh, Material material) {
        this(mesh, material, null, NO_TINT);
    }

    /**
     * Pack which tint color each mask channel takes.
     *
     * @param red The red channel's slot, 0 to {@value Skins#TINT_SLOTS} - 1, or -1 for none.
     * @param green The green channel's slot, or -1.
     * @param blue The blue channel's slot, or -1.
     * @param alpha The alpha channel's slot, or -1.
     * @return The packed slots.
     * @throws IllegalArgumentException If a slot is out of range.
     */
    public static int slots(int red, int green, int blue, int alpha) {
        return slot(red)
                | slot(green) << SLOT_BITS
                | slot(blue) << 2 * SLOT_BITS
                | slot(alpha) << 3 * SLOT_BITS;
    }

    /**
     * Check and encode one channel's slot.
     *
     * @param slot The slot, or -1 for none.
     * @return The encoded slot.
     */
    private static int slot(int slot) {
        if (slot < -1 || slot >= Skins.TINT_SLOTS) {
            throw new IllegalArgumentException(
                    "Tint slots go from 0 to " + (Skins.TINT_SLOTS - 1) + ", or -1 for none");
        }
        return slot < 0 ? NO_SLOT : slot;
    }
}
