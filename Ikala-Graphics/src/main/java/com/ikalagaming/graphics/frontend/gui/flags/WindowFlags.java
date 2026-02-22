package com.ikalagaming.graphics.frontend.gui.flags;

public class WindowFlags {
    public static final int NONE = 0;
    public static final int NO_TITLE_BAR = 1;
    public static final int NO_RESIZE = 1 << 1;
    public static final int NO_MOVE = 1 << 2;
    public static final int NO_COLLAPSE = 1 << 3;
    public static final int NO_BACKGROUND = 1 << 4;
    public static final int ALWAYS_USE_WINDOW_PADDING = 1 << 5;
    public static final int MENU_BAR = 1 << 6;
    public static final int NO_SCROLLBAR = 1 << 7;
    public static final int ALWAYS_VERTICAL_SCROLLBAR = 1 << 8;
    public static final int HORIZONTAL_SCROLLBAR = 1 << 9;
    public static final int ALWAYS_HORIZONTAL_SCROLLBAR = 1 << 10;
    public static final int NO_SCROLL_WITH_MOUSE = 1 << 11;
    public static final int NO_MOUSE_INPUTS = 1 << 12;
    public static final int NO_NAV_INPUTS = 1 << 13;
    public static final int NO_NAV_FOCUS = 1 << 14;
    public static final int NO_FOCUS_ON_APPEARING = 1 << 15;
    public static final int NO_BRING_TO_FRONT_ON_FOCUS = 1 << 16;
    public static final int NO_DOCKING = 1 << 17;

    // Combined flags
    public static final int NO_NAV = NO_NAV_INPUTS | NO_NAV_FOCUS;
    public static final int NO_DECORATION = NO_TITLE_BAR | NO_RESIZE | NO_SCROLLBAR | NO_COLLAPSE;
    public static final int NO_INPUTS = NO_MOUSE_INPUTS | NO_NAV_INPUTS | NO_NAV_FOCUS;

    /** Private constructor so this is not instantiated. */
    private WindowFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
