package com.ikalagaming.graphics.frontend.gui.flags;

public class WindowFlags {
    public static final int NONE = 0;

    /** Disable the title bar. */
    public static final int NO_TITLE_BAR = 1;

    /** Disable resizing with the lower-right grip. */
    public static final int NO_RESIZE = 1 << 1;

    /** Disable moving the window. */
    public static final int NO_MOVE = 1 << 2;

    /**
     * Disable the scrollbars visibility. The contents can still be scrolled with the mouse or code.
     */
    public static final int NO_SCROLLBAR = 1 << 3;

    /**
     * Disables scrolling with the mouse wheel. When used on a child window, mouse wheel input is
     * forwarded to the parent window unless that also sets this flag. Does not affect visibility of
     * the scrollbar.
     */
    public static final int NO_SCROLL_WITH_MOUSE = 1 << 4;

    /** Disable collapsing the window by double-clicking it. */
    public static final int NO_COLLAPSE = 1 << 5;

    /** Resize the window to it's contents every frame. */
    public static final int ALWAYS_AUTO_RESIZE = 1 << 6;

    /** Disable drawing background color and outside border. */
    public static final int NO_BACKGROUND = 1 << 7;

    /** Never save or load settings in the .ini file. */
    public static final int NO_SAVED_SETTINGS = 1 << 8;

    /** Disable catching mouse, mouse input and hovering passes through. */
    public static final int NO_MOUSE_INPUTS = 1 << 9;

    /** Has a menu bar. */
    public static final int MENU_BAR = 1 << 10;

    /** Allow the horizontal scrollbar to appear. */
    public static final int HORIZONTAL_SCROLLBAR = 1 << 11;

    /** Don't grab focus when appearing. */
    public static final int NO_FOCUS_ON_APPEARING = 1 << 12;

    /** Don't bring the window to the front when taking focus (e.g. when clicked). */
    public static final int NO_BRING_TO_FRONT_ON_FOCUS = 1 << 13;

    /** Always show the vertical scrollbar, even when not required. */
    public static final int ALWAYS_VERTICAL_SCROLLBAR = 1 << 14;

    /** Always show the horizontal scrollbar, even when not required. */
    public static final int ALWAYS_HORIZONTAL_SCROLLBAR = 1 << 15;

    /** No keyboard/gamepad navigation within the window. */
    public static final int NO_NAV_INPUTS = 1 << 16;

    /**
     * No focusing on the window with keyboard/gamepad navigation, e.g. it's skipped when tabbing
     * through windows.
     */
    public static final int NO_NAV_FOCUS = 1 << 17;

    /**
     * Display a dot next to the title. Also used to indicate the close button should select the tab
     * instead of immediately closing, to prevent losing unsaved work.
     */
    public static final int UNSAVED_DOCUMENT = 1 << 18;

    /** Disable docking of the window. */
    public static final int NO_DOCKING = 1 << 19;

    /** Internal, don't use this. Indicates a window is hosting a docking node. */
    public static final int INTERNAL_DOCK_NODE_HOST = 1 << 23;

    /** Internal, don't use this. Indicates this is a child window. */
    public static final int INTERNAL_CHILD_WINDOW = 1 << 24;

    /** Internal, don't use this. Indicates this is a tooltip window. */
    public static final int INTERNAL_TOOLTIP = 1 << 25;

    /** Internal, don't use this. Indicates this is a popup window. */
    public static final int INTERNAL_POPUP = 1 << 26;

    /** Internal, don't use this. Indicates this is a popup modal window. */
    public static final int INTERNAL_MODAL = 1 << 27;

    /** Internal, don't use this. Indicates this is a menu. */
    public static final int INTERNAL_CHILD_MENU = 1 << 28;

    // Combined flags
    public static final int NO_NAV = NO_NAV_INPUTS | NO_NAV_FOCUS;
    public static final int NO_DECORATION = NO_TITLE_BAR | NO_RESIZE | NO_SCROLLBAR | NO_COLLAPSE;
    public static final int NO_INPUTS = NO_MOUSE_INPUTS | NO_NAV_INPUTS | NO_NAV_FOCUS;

    /** Private constructor so this is not instantiated. */
    private WindowFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
