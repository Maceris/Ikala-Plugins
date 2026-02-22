package com.ikalagaming.graphics.frontend.gui.flags;

public class TabItemFlags {
    public static final int NONE = 0;
    public static final int UNSAVED_DOCUMENT = 1;
    public static final int SET_SELECTED = 1 << 1;
    public static final int NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON = 1 << 2;
    public static final int NO_PUSH_ID = 1 << 3;
    public static final int NO_TOOLTIP = 1 << 4;
    public static final int NO_REORDER = 1 << 5;
    public static final int LEADING = 1 << 6;
    public static final int TRAILING = 1 << 7;

    /** Private constructor so this is not instantiated. */
    private TabItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
