package com.ikalagaming.graphics.frontend.gui.flags;

public class ActivateFlags {
    public static final int NONE = 0;
    public static final int PREFER_INPUT = 1;
    public static final int PREFER_NUDGE = 1 << 1;
    public static final int TRY_TO_PRESERVE_STATE = 1 << 2;
    public static final int FROM_TABBING = 1 << 3;
    public static final int FROM_SHORTCUT = 1 << 4;

    /** Private constructor so this is not instantiated. */
    private ActivateFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
