package com.ikalagaming.graphics.frontend.gui.flags;

public class BackendFlags {
    public static final int NONE = 0;
    public static final int HAS_GAMEPAD = 1;
    public static final int HAS_MOUSE_CURSORS = 1 << 1;
    public static final int HAS_SET_MOUSE_POS = 1 << 2;
    public static final int RENDER_HAS_VERTEX_OFFSET = 1 << 3;
    public static final int PLATFORM_HAS_VIEWPORTS = 1 << 10;
    public static final int HAS_MOUSE_HOVERED_VIEWPORT = 1 << 11;
    public static final int RENDERER_HAS_VIEWPORTS = 1 << 12;

    /** Private constructor so this is not instantiated. */
    private BackendFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
