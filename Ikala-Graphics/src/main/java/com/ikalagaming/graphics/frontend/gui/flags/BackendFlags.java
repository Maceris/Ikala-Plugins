package com.ikalagaming.graphics.frontend.gui.flags;

public class BackendFlags {
    public static final int NONE = 0;
    public static final int HAS_GAMEPAD = 1;
    public static final int HAS_MOUSE_CURSORS = 1 << 1;
    public static final int HAS_SET_MOUSE_POS = 1 << 2;
    public static final int RENDER_HAS_VERTEX_OFFSET = 1 << 3;

    /** The platform backend supports multiple viewports. */
    public static final int PLATFORM_HAS_VIEWPORTS = 1 << 10;

    /**
     * The platform backend supports calling addMouseViewportEvent() with the viewport under the
     * mouse. If possible, ignore viewports with {@link ViewportFlags#NO_INPUTS} set. If this cannot
     * be done, we use a flawed heuristic to find the viewport under the mouse.
     */
    public static final int HAS_MOUSE_HOVERED_VIEWPORT = 1 << 11;

    /** The renderer backend supports multiple viewports. */
    public static final int RENDERER_HAS_VIEWPORTS = 1 << 12;

    /**
     * The platform backend supports honoring the viewport parent viewport ID, by applying the
     * corresponding parent/child relationship at the platform level. Child windows always appear in
     * front of their parent window.
     */
    public static final int HAS_PARENT_VIEWPORT = 1 << 13;

    /** Private constructor so this is not instantiated. */
    private BackendFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
