package com.ikalagaming.graphics.frontend.gui.flags;

public class NextWindowFlags {
    public static final int NONE = 0;
    public static final int HAS_POSITION = 1;
    public static final int HAS_SIZE = 1 << 1;
    public static final int HAS_CONTENT_SIZE = 1 << 2;
    public static final int HAS_COLLAPSED = 1 << 3;
    public static final int HAS_SIZE_CONSTRAINT = 1 << 4;
    public static final int HAS_FOCUS = 1 << 5;
    public static final int HAS_BACKGROUND_ALPHA = 1 << 6;
    public static final int HAS_SCROLL = 1 << 7;
    public static final int HAS_WINDOW_FLAGS = 1 << 8;
    public static final int HAS_CHILD_FLAGS = 1 << 9;
    public static final int HAS_REFRESH_POLICY = 1 << 10;
}
