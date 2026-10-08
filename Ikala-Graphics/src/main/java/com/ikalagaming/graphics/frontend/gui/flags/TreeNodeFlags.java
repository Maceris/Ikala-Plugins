package com.ikalagaming.graphics.frontend.gui.flags;

public class TreeNodeFlags {
    public static final int NONE = 0;

    /** Draw as selected. */
    public static final int SELECTED = 1;

    /** Draw frame with background (e.g. for collapsingHeader()). */
    public static final int FRAMED = 1 << 1;

    /** Hit testing to allow subsequent widgets to overlap this one. */
    public static final int ALLOW_OVERLAP = 1 << 2;

    /**
     * Don't do a treePush() when open (e.g. for collapsingHeader()) = no extra indent nor pushing
     * on ID stack.
     */
    public static final int NO_TREE_PUSH_ON_OPEN = 1 << 3;

    /**
     * Don't automatically and temporarily open node when logging is active (by default logging will
     * automatically open tree nodes).
     */
    public static final int NO_AUTO_OPEN_ON_LOG = 1 << 4;

    /** Default node to be open. */
    public static final int DEFAULT_OPEN = 1 << 5;

    /** Open on double-click instead of simple click (default for multi-select unless any flags). */
    public static final int OPEN_ON_DOUBLE_CLICK = 1 << 6;

    /** Open when clicking on the arrow part (default for multi-select unless any flags). */
    public static final int OPEN_ON_ARROW = 1 << 7;

    /** No collapsing, no arrow (use as a convenience for leaf nodes). */
    public static final int LEAF = 1 << 8;

    /** Display a bullet instead of arrow. Can still be marked open/close if not also LEAF. */
    public static final int BULLET = 1 << 9;

    /**
     * Use frame padding (even for an unframed text node) to vertically align text baseline to
     * regular widget height.
     */
    public static final int FRAME_PADDING = 1 << 10;

    /**
     * Extend hit box to the right-most edge, even if not framed. This is not the default in order
     * to allow adding other items on the same line without using ALLOW_OVERLAP.
     */
    public static final int SPAN_AVAIL_WIDTH = 1 << 11;

    /** Extend hit box to the left-most and right-most edges (cover the indent area). */
    public static final int SPAN_FULL_WIDTH = 1 << 12;

    /** Narrow hit box + narrow hovering highlight, will only cover the label text. */
    public static final int SPAN_LABEL_WIDTH = 1 << 13;

    /**
     * The frame will span all columns of its container table (the label will still fit in the
     * current column).
     */
    public static final int SPAN_ALL_COLUMNS = 1 << 14;

    /** The label will span all columns of its container table. */
    public static final int LABEL_SPAN_ALL_COLUMNS = 1 << 15;

    /** Nav: left arrow moves back to parent. */
    public static final int NAV_LEFT_JUMPS_TO_PARENT = 1 << 17;

    /**
     * No lines connecting the tree node hierarchy. If none of the DRAW_LINES flags are set, the
     * default comes from style.treeLinesFlags.
     */
    public static final int DRAW_LINES_NONE = 1 << 18;

    /**
     * Horizontal lines to child nodes, and a vertical line down to the treePop() position, covering
     * all the contents. Faster than DRAW_LINES_TO_NODES for large trees.
     */
    public static final int DRAW_LINES_FULL = 1 << 19;

    /**
     * Horizontal lines to child nodes, and a vertical line down to the bottom-most child node.
     * Slower than DRAW_LINES_FULL for large trees.
     */
    public static final int DRAW_LINES_TO_NODES = 1 << 20;

    /** Internal: clip the label to make space for a trailing button (collapsing header close). */
    public static final int INTERNAL_CLIP_LABEL_FOR_TRAILING_BUTTON = 1 << 28;

    // Combined flags
    public static final int COLLAPSING_HEADER = FRAMED | NO_TREE_PUSH_ON_OPEN | NO_AUTO_OPEN_ON_LOG;

    /** Internal: all the DRAW_LINES options. */
    public static final int INTERNAL_DRAW_LINES_MASK =
            DRAW_LINES_NONE | DRAW_LINES_FULL | DRAW_LINES_TO_NODES;

    /** Private constructor so this is not instantiated. */
    private TreeNodeFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
