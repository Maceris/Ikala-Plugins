package com.ikalagaming.graphics.frontend.gui.flags;

public class ComboFlags {
    public static final int NONE = 0;
    public static final int POPUP_ALIGN_LEFT = 1;
    public static final int HEIGHT_SMALL = 1 << 1;
    public static final int HEIGHT_REGULAR = 1 << 2;
    public static final int HEIGHT_LARGE = 1 << 3;
    public static final int HEIGHT_LARGEST = 1 << 4;
    public static final int NO_ARROW_BUTTON = 1 << 5;
    public static final int NO_PREVIEW = 1 << 6;

    // Combined flags
    public static final int HEIGHT_MASK =
            HEIGHT_SMALL | HEIGHT_REGULAR | HEIGHT_LARGE | HEIGHT_LARGEST;

    /** Private constructor so this is not instantiated. */
    private ComboFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
