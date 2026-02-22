package com.ikalagaming.graphics.frontend.gui.flags;

public class ColorEditFlags {
    public static final int NONE = 0;
    public static final int NO_ALPHA = 1;
    public static final int NO_PICKER = 1 << 1;
    public static final int NO_OPTIONS = 1 << 2;
    public static final int NO_SMALL_PREVIEW = 1 << 3;
    public static final int NO_INPUTS = 1 << 4;
    public static final int NO_TOOLTIP = 1 << 5;
    public static final int NO_LABEL = 1 << 6;
    public static final int NO_SIDE_PREVIEW = 1 << 7;
    public static final int NO_DRAG_DROP = 1 << 8;
    public static final int NO_BORDER = 1 << 9;
    public static final int ALPHA_BAR = 1 << 10;
    public static final int ALPHA_PREVIEW = 1 << 11;
    public static final int ALPHA_PREVIEW_HALF = 1 << 12;
    public static final int HDR = 1 << 13;
    public static final int DISPLAY_RGB = 1 << 14;
    public static final int DISPLAY_HSV = 1 << 15;
    public static final int DISPLAY_HEX = 1 << 16;
    public static final int UINT8 = 1 << 17;
    public static final int FLOAT = 1 << 18;
    public static final int PICKER_HUE_BAR = 1 << 19;
    public static final int PICKER_HUE_WHEEL = 1 << 20;
    public static final int INPUT_RGB = 1 << 21;
    public static final int INPUT_HSV = 1 << 22;

    // Combined flags
    public static final int OPTIONS_DEFAULT = INPUT_RGB | PICKER_HUE_BAR | UINT8 | DISPLAY_RGB;

    /** Private constructor so this is not instantiated. */
    private ColorEditFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
