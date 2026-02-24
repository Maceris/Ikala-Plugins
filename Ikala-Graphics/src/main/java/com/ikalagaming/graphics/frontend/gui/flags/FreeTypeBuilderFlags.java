package com.ikalagaming.graphics.frontend.gui.flags;

public class FreeTypeBuilderFlags {
    public static final int NONE = 0;
    public static final int NO_HINTING = 1;
    public static final int NO_AUTO_HINT = 1 << 1;
    public static final int FORCE_AUTO_HINT = 1 << 2;
    public static final int LIGHT_HINTING = 1 << 3;
    public static final int MONO_HINTING = 1 << 4;
    public static final int BOLD = 1 << 5;
    public static final int ITALIC = 1 << 6;
    public static final int MONOCHROME = 1 << 7;
    public static final int LOAD_COLOR = 1 << 8;
    public static final int BITMAP = 1 << 9;

    /** Private constructor so this is not instantiated. */
    private FreeTypeBuilderFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
