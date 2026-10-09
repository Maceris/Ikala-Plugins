package com.ikalagaming.graphics.gui.data;

/**
 * Constants for selection user data, the values passed to setNextItemSelectionUserData(). Most
 * applications store an item index, but any long value except {@link #INVALID} can be used, like an
 * ID. The multi-select system never assumes the values are indices, so it never interpolates
 * between them.
 */
public class SelectionUserData {
    /** Marks a missing or invalid value, which works for both indices and IDs. */
    public static final long INVALID = -1;

    /** Private constructor so this is not instantiated. */
    private SelectionUserData() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
