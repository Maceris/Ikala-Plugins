package com.ikalagaming.graphics.gui.flags;

/** Flags for beginCombo(). */
public class ComboFlags {
    public static final int NONE = 0;

    /** Align the popup toward the left by default. */
    public static final int POPUP_ALIGN_LEFT = 1;

    /** Max ~4 items visible. */
    public static final int HEIGHT_SMALL = 1 << 1;

    /** Max ~8 items visible (default). */
    public static final int HEIGHT_REGULAR = 1 << 2;

    /** Max ~20 items visible. */
    public static final int HEIGHT_LARGE = 1 << 3;

    /** As many fitting items as possible. */
    public static final int HEIGHT_LARGEST = 1 << 4;

    /** Display the preview box without the square arrow button. */
    public static final int NO_ARROW_BUTTON = 1 << 5;

    /** Display only a square arrow button. */
    public static final int NO_PREVIEW = 1 << 6;

    /** Width dynamically calculated from the preview contents. */
    public static final int WIDTH_FIT_PREVIEW = 1 << 7;

    // Combined flags
    public static final int HEIGHT_MASK =
            HEIGHT_SMALL | HEIGHT_REGULAR | HEIGHT_LARGE | HEIGHT_LARGEST;

    /** Private constructor so this is not instantiated. */
    private ComboFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
