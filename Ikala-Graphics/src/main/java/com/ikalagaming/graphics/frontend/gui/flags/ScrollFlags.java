package com.ikalagaming.graphics.frontend.gui.flags;

public class ScrollFlags {

    public static final int NONE = 0;

    /** If the item is not visible, scroll on the X axis just enough to bring it back into view. */
    public static final int KEEP_VISIBLE_EDGE_X = 1;

    /** If the item is not visible, scroll on the Y axis just enough to bring it back into view. */
    public static final int KEEP_VISIBLE_EDGE_Y = 1 << 1;

    /** If the item is not visible, scroll on the X axis just enough to center it on the X axis. */
    public static final int KEEP_VISIBLE_CENTER_X = 1 << 2;

    /** If the item is not visible, scroll on the Y axis just enough to center it on the Y axis. */
    public static final int KEEP_VISIBLE_CENTER_Y = 1 << 3;

    /** Always center the item on the X axis. */
    public static final int ALWAYS_CENTER_X = 1 << 4;

    /** Always center the item on the Y axis. */
    public static final int ALWAYS_CENTER_Y = 1 << 5;

    /** Don't scroll the parent window even if it would be required to keep the item visible. */
    public static final int NO_SCROLL_PARENT = 1 << 6;

    // Combined flags
    public static final int MASK_X = KEEP_VISIBLE_EDGE_X | KEEP_VISIBLE_CENTER_X | ALWAYS_CENTER_X;
    public static final int MASK_Y = KEEP_VISIBLE_EDGE_Y | KEEP_VISIBLE_CENTER_Y | ALWAYS_CENTER_Y;
}
