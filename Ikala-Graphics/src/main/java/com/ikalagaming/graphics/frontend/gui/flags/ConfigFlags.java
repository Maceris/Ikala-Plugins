package com.ikalagaming.graphics.frontend.gui.flags;

public class ConfigFlags {
    public static final int NONE = 0;
    public static final int NAV_ENABLE_KEYBOARD = 1;
    public static final int NAV_ENABLE_GAMEPAD = 1 << 1;
    public static final int NAV_ENABLE_SET_MOUSE_POS = 1 << 2;
    public static final int NAV_NO_CAPTURE_KEYBOARD = 1 << 3;
    public static final int NO_MOUSE = 1 << 4;
    public static final int NO_MOUSE_CURSOR_CHANGE = 1 << 5;
    public static final int NO_KEYBOARD = 1 << 6;
    public static final int DOCKING_ENABLE = 1 << 7;
    public static final int VIEWPORTS_ENABLE = 1 << 8;
    public static final int DPI_ENABLE_SCALE_VIEWPORTS = 1 << 9;
    public static final int DPI_ENABLE_SCALE_FONTS = 1 << 10;
    public static final int IS_SRGB = 1 << 11;
    public static final int IS_TOUCH_SCREEN = 1 << 12;

    /** Private constructor so this is not instantiated. */
    private ConfigFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
