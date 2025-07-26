package com.ikalagaming.graphics.frontend.gui.flags;

public class DrawFlags {
    public static final int NONE = 0;
    public static final int CLOSED = 1;
    public static final int ROUND_CORNERS_TOP_LEFT = 1 << 1;
    public static final int ROUND_CORNERS_TOP_RIGHT = 1 << 2;
    public static final int ROUND_CORNERS_BOTTOM_LEFT = 1 << 3;
    public static final int ROUND_CORNERS_BOTTOM_RIGHT = 1 << 4;
    public static final int ROUND_CORNERS_NONE = 1 << 5;

    // Combined flags
    public static final int ROUND_CORNERS_TOP = ROUND_CORNERS_TOP_LEFT | ROUND_CORNERS_TOP_RIGHT;
    public static final int ROUND_CORNERS_BOTTOM = ROUND_CORNERS_BOTTOM_LEFT | ROUND_CORNERS_BOTTOM_RIGHT;
    public static final int ROUND_CORNERS_LEFT = ROUND_CORNERS_TOP_LEFT | ROUND_CORNERS_BOTTOM_LEFT;
    public static final int ROUND_CORNERS_RIGHT = ROUND_CORNERS_TOP_RIGHT | ROUND_CORNERS_BOTTOM_RIGHT;
    public static final int ROUND_CORNERS_ALL = ROUND_CORNERS_TOP_LEFT | ROUND_CORNERS_TOP_RIGHT | ROUND_CORNERS_BOTTOM_LEFT | ROUND_CORNERS_BOTTOM_RIGHT;
    public static final int ROUND_CORNERS_DEFAULT = ROUND_CORNERS_ALL;
    public static final int ROUND_CORNERS_MASK = ROUND_CORNERS_ALL | ROUND_CORNERS_NONE;
}
