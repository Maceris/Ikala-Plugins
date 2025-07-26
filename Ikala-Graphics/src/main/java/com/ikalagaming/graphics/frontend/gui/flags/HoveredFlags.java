package com.ikalagaming.graphics.frontend.gui.flags;

public class HoveredFlags {
    public static final int NONE = 0;
    public static final int CHILD_WINDOWS = 1;
    public static final int ROOT_WINDOW = 1 << 1;
    public static final int ANY_WINDOW = 1 << 2;
    public static final int NO_POPUP_HIERARCHY = 1 << 3;
    public static final int DOCK_HIERARCHY = 1 << 4;
    public static final int ALLOW_WHEN_BLOCKED_BY_POPUP = 1 << 5;
    public static final int ALLOW_WHEN_BLOCKED_BY_MODAL = 1 << 6;
    public static final int ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM = 1 << 7;
    public static final int ALLOW_WHEN_OVERLAPPED_BY_ITEM = 1 << 8;
    public static final int ALLOW_WHEN_OVERLAPPED_BY_WINDOW = 1 << 9;
    public static final int ALLOW_WHEN_DISABLED = 1 << 10;

    // Combined flags
    public static final int ALLOW_WHEN_OVERLAPPED =
            ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM | ALLOW_WHEN_OVERLAPPED_BY_WINDOW;
    public static final int RECT_ONLY =
            ALLOW_WHEN_BLOCKED_BY_POPUP | ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM | ALLOW_WHEN_OVERLAPPED;
    public static final int ROOT_AND_CHILD_WINDOWS = ROOT_WINDOW | CHILD_WINDOWS;
}
