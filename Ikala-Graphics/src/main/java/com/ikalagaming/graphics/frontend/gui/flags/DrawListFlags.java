package com.ikalagaming.graphics.frontend.gui.flags;

public class DrawListFlags {
    public static final int NONE = 0;
    public static final int ANTI_ALIASED_LINES = 1;
    public static final int ANTI_ALIASED_FILL = 1 << 1;
    public static final int ALLOW_VERTEX_OFFSET = 1 << 2;

    /** Private constructor so this is not instantiated. */
    private DrawListFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
