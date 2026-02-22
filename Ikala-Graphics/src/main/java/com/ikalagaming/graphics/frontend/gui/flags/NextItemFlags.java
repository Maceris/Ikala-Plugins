package com.ikalagaming.graphics.frontend.gui.flags;

public class NextItemFlags {
    private static final int NONE = 0;
    private static final int HAS_WIDTH = 1;
    private static final int HAS_OPEN = 1 << 1;
    private static final int HAS_SHORTCUT = 1 << 2;
    private static final int HAS_REFERENCE_VALUE = 1 << 3;
    private static final int HAS_STORAGE_ID = 1 << 4;

    /** Private constructor so this is not instantiated. */
    private NextItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
