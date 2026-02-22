package com.ikalagaming.graphics.frontend.gui.flags;

public class SliderFlags {
    public static final int NONE = 0;
    public static final int ALWAYS_CLAMP = 1;
    public static final int LOGARITHMIC = 1 << 1;
    public static final int NO_ROUND_TO_FORMAT = 1 << 2;
    public static final int NO_INPUT = 1 << 3;

    /** Private constructor so this is not instantiated. */
    private SliderFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
