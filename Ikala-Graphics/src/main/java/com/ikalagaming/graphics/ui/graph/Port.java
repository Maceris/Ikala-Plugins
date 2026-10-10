package com.ikalagaming.graphics.ui.graph;

import lombok.NonNull;

import java.util.Locale;

/**
 * Where a link attaches to a node: a side, and how far along it.
 *
 * @param side The side.
 * @param offset How far along the side from its middle, as a fraction of its length, from -0.5 (the
 *     left or top end) to 0.5 (the right or bottom end).
 */
public record Port(@NonNull Side side, float offset) {
    /** A side of a node. */
    public enum Side {
        /** Whichever side faces the other node. */
        AUTO,
        /** The top. */
        TOP,
        /** The bottom. */
        BOTTOM,
        /** The left. */
        LEFT,
        /** The right. */
        RIGHT,
        /** The middle of the node, leaving toward the other node. */
        CENTER;

        /**
         * Whether links leave this side up or down.
         *
         * @return True for the top and bottom.
         */
        public boolean vertical() {
            return this == TOP || this == BOTTOM;
        }

        /**
         * Whether links leave this side left or right.
         *
         * @return True for the left and right.
         */
        public boolean horizontal() {
            return this == LEFT || this == RIGHT;
        }
    }

    /** The side facing the other node, at its middle. */
    public static final Port AUTO = new Port(Side.AUTO, 0);

    /**
     * The middle of a side.
     *
     * @param side The side.
     * @return The port.
     */
    public static Port of(@NonNull Side side) {
        return new Port(side, 0);
    }

    /**
     * Read a port from a spec: a side name, optionally followed by an offset, like {@code bottom}
     * or {@code top 0.25}.
     *
     * @param text The text.
     * @return The port.
     * @throws IllegalArgumentException If it isn't a port.
     */
    public static Port parse(@NonNull String text) {
        String[] parts = text.trim().split("\\s+");
        Side side;
        try {
            side = Side.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "not a side, use auto, top, bottom, left, right or center: " + text, e);
        }
        float offset = 0;
        if (parts.length > 1) {
            try {
                offset = Float.parseFloat(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not an offset along the side: " + text, e);
            }
        }
        return new Port(side, Math.clamp(offset, -0.5f, 0.5f));
    }
}
