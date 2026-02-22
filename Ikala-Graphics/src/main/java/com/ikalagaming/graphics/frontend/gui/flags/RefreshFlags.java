package com.ikalagaming.graphics.frontend.gui.flags;

public class RefreshFlags {
    public static final int NONE = 0;
    public static final int TRY_AVOIDING_REFRESH = 1;
    public static final int REFRESH_ON_HOVER = 1 << 1;
    public static final int REFRESH_ON_FOCUS = 1 << 2;

    /** Private constructor so this is not instantiated. */
    private RefreshFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
