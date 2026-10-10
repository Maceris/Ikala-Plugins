package com.ikalagaming.graphics.ui.graph;

import java.util.Locale;

/** How a link is routed between two nodes. */
public enum Route {
    /** A straight line. */
    STRAIGHT,
    /** A smooth curve that leaves and enters along the sides it connects. */
    CURVE,
    /**
     * Horizontal and vertical lines only. Links that leave the same side of a node share a trunk,
     * like a tree.
     */
    ORTHOGONAL;

    /**
     * Read a route from a style or spec.
     *
     * @param words The text, like {@code orthogonal}.
     * @return The route, or null if it isn't one.
     */
    public static Route parse(String words) {
        if (words == null) {
            return null;
        }
        try {
            return valueOf(words.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
