package com.ikalagaming.graphics.frontend.gui.flags;

public class ItemFlags {
    public static final int NONE = 0;
    public static final int NO_TAB_STOP = 1;
    public static final int BUTTON_REPEAT = 1 << 1;
    public static final int DISABLED = 1 << 2;
    public static final int NO_NAV = 1 << 3;
    public static final int NO_NAV_DEFAULT_FOCUS = 1 << 4;
    public static final int SELECTABLE_DONT_CLOSE_POPUP = 1 << 5;
    public static final int MIXED_VALUE = 1 << 6;
    public static final int READ_ONLY = 1 << 7;

    /** Private constructor so this is not instantiated. */
    private ItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
