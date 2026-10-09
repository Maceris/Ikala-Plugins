package com.ikalagaming.graphics.gui.flags;

public class ConfigFlags {
    public static final int NONE = 0;
    public static final int NAV_ENABLE_KEYBOARD = 1;
    public static final int NAV_ENABLE_GAMEPAD = 1 << 1;
    public static final int NO_MOUSE = 1 << 4;
    public static final int NO_MOUSE_CURSOR_CHANGE = 1 << 5;
    public static final int NO_KEYBOARD = 1 << 6;
    public static final int DOCKING_ENABLE = 1 << 7;

    /**
     * Enable multiple viewports, which lets windows be moved outside the main application window.
     * Requires both {@link BackendFlags#PLATFORM_HAS_VIEWPORTS} and {@link
     * BackendFlags#RENDERER_HAS_VIEWPORTS} to be set by the respective backends, otherwise this is
     * ignored.
     */
    public static final int VIEWPORTS_ENABLE = 1 << 8;

    public static final int IS_SRGB = 1 << 11;
    public static final int IS_TOUCH_SCREEN = 1 << 12;

    /** Private constructor so this is not instantiated. */
    private ConfigFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
