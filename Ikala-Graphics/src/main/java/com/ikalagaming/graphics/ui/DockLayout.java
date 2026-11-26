package com.ikalagaming.graphics.ui;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The layout a {@link DockSpace} starts with, before the user changes it, and goes back to when it
 * is reset. Each split takes a share of what is left in the middle for its panels, in the order the
 * splits were added, and the panels given to {@link #center} fill what remains.
 *
 * <p>Panels are named by their IDs. Several panels in one place become tabs, in the order given.
 */
public final class DockLayout {
    /** Which side of the middle a split takes. */
    public enum Side {
        /** The left edge. */
        LEFT,
        /** The right edge. */
        RIGHT,
        /** The top edge. */
        TOP,
        /** The bottom edge. */
        BOTTOM
    }

    /**
     * One split.
     *
     * @param side The side it takes.
     * @param fraction How much it takes, from 0 to 1.
     * @param panels The IDs of the panels docked there.
     * @param ofPrevious Whether it divides the split before it, rather than the middle.
     */
    public record Split(
            @NonNull Side side, float fraction, @NonNull List<String> panels, boolean ofPrevious) {
        /**
         * Check the split.
         *
         * @param side The side it takes.
         * @param fraction How much it takes, from 0 to 1.
         * @param panels The IDs of the panels docked there.
         * @param ofPrevious Whether it divides the split before it, rather than the middle.
         */
        public Split {
            if (!(fraction > 0 && fraction < 1)) {
                throw new IllegalArgumentException(
                        "A split takes a fraction between 0 and 1, not " + fraction);
            }
            panels = List.copyOf(panels);
        }
    }

    /** The splits, in order. */
    private final List<Split> splits = new ArrayList<>();

    /** The panels docked in the middle. */
    private List<String> center = List.of();

    /**
     * Take part of the middle for some panels.
     *
     * @param side The side of the middle to take.
     * @param fraction How much of the middle to take, from 0 to 1.
     * @param panelIDs The IDs of the panels to dock there, as tabs in this order.
     * @return This layout.
     */
    public DockLayout split(@NonNull Side side, float fraction, @NonNull String... panelIDs) {
        splits.add(new Split(side, fraction, List.of(panelIDs), false));
        return this;
    }

    /**
     * Divide the split made just before, like putting an outliner under a file list. The panels of
     * that split keep the rest of it.
     *
     * @param side The side of the previous split to take.
     * @param fraction How much of it to take, from 0 to 1.
     * @param panelIDs The IDs of the panels to dock there, as tabs in this order.
     * @return This layout.
     * @throws IllegalStateException If there is no split before it.
     */
    public DockLayout splitPrevious(
            @NonNull Side side, float fraction, @NonNull String... panelIDs) {
        if (splits.isEmpty()) {
            throw new IllegalStateException("There is no split to divide");
        }
        splits.add(new Split(side, fraction, List.of(panelIDs), true));
        return this;
    }

    /**
     * Dock panels in what is left of the middle.
     *
     * @param panelIDs The IDs of the panels, as tabs in this order.
     * @return This layout.
     */
    public DockLayout center(@NonNull String... panelIDs) {
        center = List.of(panelIDs);
        return this;
    }

    /**
     * The splits, in the order they are made.
     *
     * @return The splits.
     */
    public List<Split> getSplits() {
        return List.copyOf(splits);
    }

    /**
     * The panels docked in what is left of the middle.
     *
     * @return The panel IDs.
     */
    public List<String> getCenter() {
        return center;
    }
}
