package com.ikalagaming.graphics.frontend.gui.flags;

public class ItemStatusFlags {
    public static final int NONE = 0;
    public static final int HOVERED_RECT = 1;
    public static final int HAS_DISPLAY_RECT = 1 << 1;
    public static final int EDITED = 1 << 2;
    public static final int TOGGLED_SELECTION = 1 << 3;
    public static final int TOGGLED_OPEN = 1 << 4;
    public static final int HAS_DEACTIVATED = 1 << 5;
    public static final int DEACTIVATED = 1 << 6;
    public static final int HOVERED_WINDOW = 1 << 7;
    public static final int VISIBLE = 1 << 8;
    public static final int HAS_CLIP_RECT = 1 << 9;
    public static final int HAS_SHORTCUT = 1 << 10;

    /** Private constructor so this is not instantiated. */
    private ItemStatusFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
