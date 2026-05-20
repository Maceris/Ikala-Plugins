package com.ikalagaming.graphics.frontend.gui.flags;

public class ChildFlags {
    public static final int NONE = 0;

    /** Show an outer border and enable WindowPadding. */
    public static final int BORDER = 1;

    /**
     * Pad with style window padding even if no border are drawn (no padding by default for
     * non-bordered child windows because it makes more sense)
     */
    public static final int ALWAYS_USE_WINDOW_PADDING = 1 << 1;

    /**
     * Allow resize from right border (layout direction). Enable .ini saving (unless {@link
     * WindowFlags#NO_SAVED_SETTINGS} passed to window flags)
     */
    public static final int RESIZE_X = 1 << 2;

    /**
     * Allow resize from bottom border (layout direction). Enable .ini saving (unless {@link
     * WindowFlags#NO_SAVED_SETTINGS} passed to window flags)
     */
    public static final int RESIZE_Y = 1 << 3;

    /** Enable auto-resizing width. */
    public static final int AUTO_RESIZE_X = 1 << 4;

    /** Enable auto-resizing height. */
    public static final int AUTO_RESIZE_Y = 1 << 5;

    /**
     * Combined with AUTO_RESIZE_X/AUTO_RESIZE_Y. Always measure size even when child is hidden,
     * always return true, always disable clipping optimization! NOT RECOMMENDED.
     */
    public static final int ALWAYS_AUTO_RESIZE = 1 << 6;

    /**
     * Style the child window like a framed item: use FrameBg, FrameRounding, FrameBorderSize,
     * FramePadding instead of ChildBg, ChildRounding, ChildBorderSize, WindowPadding.
     */
    public static final int FRAME_STYLE = 1 << 7;

    /**
     * Share focus scope, allow keyboard/gamepad navigation to cross over parent border to this
     * child or between sibling child windows.
     */
    public static final int NAV_FLATTENED = 1 << 8;

    /** Private constructor so this is not instantiated. */
    private ChildFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
