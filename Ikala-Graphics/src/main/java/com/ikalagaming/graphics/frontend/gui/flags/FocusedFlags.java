package com.ikalagaming.graphics.frontend.gui.flags;

public class FocusedFlags {
    public static final int NONE = 0;
    public static final int CHILD_WINDOWS = 1;
    public static final int ROOT_WINDOW = 1 << 1;
    public static final int ANY_WINDOW = 1 << 2;
    public static final int NO_POPUP_HIERARCHY = 1 << 3;
    public static final int DOCK_HIERARCHY = 1 << 4;

    // Combined flags
    public static final int ROOT_AND_CHILD_WINDOWS = ROOT_WINDOW | CHILD_WINDOWS;
}
