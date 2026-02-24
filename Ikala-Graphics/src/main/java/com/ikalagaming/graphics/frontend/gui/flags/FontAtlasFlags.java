package com.ikalagaming.graphics.frontend.gui.flags;

public class FontAtlasFlags {
    public static final int NONE = 0;
    public static final int NO_POWER_OF_TWO_HEIGHT = 1;
    public static final int NO_MOUSE_CURSORS = 1 << 1;
    public static final int NO_BAKED_LINES = 1 << 2;

    /** Private constructor so this is not instantiated. */
    private FontAtlasFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
