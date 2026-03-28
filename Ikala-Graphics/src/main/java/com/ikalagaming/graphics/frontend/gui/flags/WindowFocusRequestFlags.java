package com.ikalagaming.graphics.frontend.gui.flags;

/** Used internally for focusing windows. */
public class WindowFocusRequestFlags {
    public static final int NONE = 0;
    public static final int RESTORE_FOCUSED_CHILD = 1;
    public static final int UNLESS_BELOW_MODAL = 1 << 1;

    /** Private constructor so this is not instantiated. */
    private WindowFocusRequestFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
