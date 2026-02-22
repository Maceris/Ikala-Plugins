package com.ikalagaming.graphics.frontend.gui.flags;

public class SelectableFlags {
    public static final int NONE = 0;
    public static final int DONT_CLOSE_POPUPS = 1;
    public static final int SPAN_ALL_COLUMNS = 1 << 1;
    public static final int ALLOW_DOUBLE_CLICK = 1 << 2;
    public static final int DISABLED = 1 << 3;
    public static final int ALLOW_ITEM_OVERLAP = 1 << 4;

    /** Private constructor so this is not instantiated. */
    private SelectableFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
