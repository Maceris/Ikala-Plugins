package com.ikalagaming.graphics.frontend.gui.flags;

public class ViewportFlags {
    public static final int NONE = 0;
    public static final int IS_PLATFORM_WINDOW = 1;
    public static final int IS_PLATFORM_MONITOR = 1 << 1;
    public static final int OWNED_BY_APP = 1 << 2;
    public static final int NO_DECORATION = 1 << 3;
    public static final int NO_TASK_BAR_ICON = 1 << 4;
    public static final int NO_FOCUS_ON_APPEARING = 1 << 5;
    public static final int NO_FOCUS_ON_CLICK = 1 << 6;
    public static final int NO_INPUTS = 1 << 7;
    public static final int NO_RENDERER_CLEAR = 1 << 8;
    public static final int NO_AUTO_MERGE = 1 << 9;
    public static final int TOP_MOST = 1 << 10;
    public static final int CAN_HOST_OTHER_WINDOWS = 1 << 11;
    public static final int IS_MINIMIZED = 1 << 12;
    public static final int IS_FOCUSED = 1 << 13;

    /** Private constructor so this is not instantiated. */
    private ViewportFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
