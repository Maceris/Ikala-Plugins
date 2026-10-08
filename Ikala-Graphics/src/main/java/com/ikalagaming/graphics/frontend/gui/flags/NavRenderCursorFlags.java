package com.ikalagaming.graphics.frontend.gui.flags;

/** Flags for rendering the navigation cursor. */
public class NavRenderCursorFlags {
    public static final int NONE = 0;

    /** Compact highlight, with no padding/distance from the focused item. */
    public static final int COMPACT = 1 << 1;

    /**
     * Draw the highlight if the item is the navigation item, even when the navigation cursor is not
     * visible (e.g. when using the mouse).
     */
    public static final int ALWAYS_DRAW = 1 << 2;

    /** Private constructor so this is not instantiated. */
    private NavRenderCursorFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
