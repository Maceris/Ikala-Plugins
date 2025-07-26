package com.ikalagaming.graphics.frontend.gui.flags;

public class TreeNodeFlags {
    public static final int NONE = 0;
    public static final int SELECTED = 1;
    public static final int FRAMED = 1 << 1;
    public static final int ALLOW_ITEM_OVERLAP = 1 << 2;
    public static final int NO_TREE_PUSH_ON_OPEN = 1 << 3;
    public static final int NO_AUTO_OPEN_ON_LOG = 1 << 4;
    public static final int DEFAULT_OPEN = 1 << 5;
    public static final int OPEN_ON_DOUBLE_CLICK = 1 << 6;
    public static final int OPEN_ON_ARROW = 1 << 7;
    public static final int LEAF = 1 << 8;
    public static final int BULLET = 1 << 9;
    public static final int FRAME_PADDING = 1 << 10;
    public static final int SPAN_AVAIL_WIDTH = 1 << 11;
    public static final int SPAN_FULL_WIDTH = 1 << 12;
    public static final int NAV_LEFT_JUMPS_BACK_HERE = 1 << 13;

    // Combined flags
    public static final int COLLAPSING_HEADER = FRAMED | NO_TREE_PUSH_ON_OPEN | NO_AUTO_OPEN_ON_LOG;
}
