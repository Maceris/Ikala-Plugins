package com.ikalagaming.graphics.gui.flags;

/** Flags for tracking which fields of the next item data have been set. */
public class NextItemFlags {
    public static final int NONE = 0;
    public static final int HAS_WIDTH = 1;
    public static final int HAS_OPEN = 1 << 1;
    public static final int HAS_SHORTCUT = 1 << 2;
    public static final int HAS_REFERENCE_VALUE = 1 << 3;
    public static final int HAS_STORAGE_ID = 1 << 4;
    public static final int HAS_COLOR_MARKER = 1 << 5;

    /** Private constructor so this is not instantiated. */
    private NextItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
