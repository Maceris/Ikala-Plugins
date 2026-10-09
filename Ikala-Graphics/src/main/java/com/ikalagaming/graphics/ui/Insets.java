package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * Space inside the edges of a node, between its border and its children.
 *
 * @param left The space on the left.
 * @param top The space on the top.
 * @param right The space on the right.
 * @param bottom The space on the bottom.
 */
public record Insets(
        @NonNull Length left, @NonNull Length top, @NonNull Length right, @NonNull Length bottom) {
    /** No space. */
    public static final Insets NONE = all(Length.ZERO);

    /**
     * The same space on every side.
     *
     * @param length The space.
     * @return The insets.
     */
    public static Insets all(@NonNull Length length) {
        return new Insets(length, length, length, length);
    }

    /**
     * The same space on every side, in UI units.
     *
     * @param units The space in UI units.
     * @return The insets.
     */
    public static Insets all(float units) {
        return all(Length.u(units));
    }

    /**
     * Space on the left and right, and on the top and bottom.
     *
     * @param horizontal The space on the left and right.
     * @param vertical The space on the top and bottom.
     * @return The insets.
     */
    public static Insets symmetric(@NonNull Length horizontal, @NonNull Length vertical) {
        return new Insets(horizontal, vertical, horizontal, vertical);
    }
}
