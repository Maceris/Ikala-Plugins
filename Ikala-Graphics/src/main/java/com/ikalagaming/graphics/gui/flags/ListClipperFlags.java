package com.ikalagaming.graphics.gui.flags;

/** Flags for the list clipper. */
public class ListClipperFlags {
    public static final int NONE = 0;

    /**
     * Internal: disable modifying table row counters. Avoids the assumption that 1 clipper item ==
     * 1 table row.
     */
    public static final int NO_SET_TABLE_ROW_COUNTERS = 1;

    /** Private constructor so this is not instantiated. */
    private ListClipperFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
