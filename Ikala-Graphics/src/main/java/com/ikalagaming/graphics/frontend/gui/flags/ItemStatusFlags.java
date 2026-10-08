package com.ikalagaming.graphics.frontend.gui.flags;

public class ItemStatusFlags {
    public static final int NONE = 0;
    public static final int HOVERED_RECT = 1;
    public static final int HAS_DISPLAY_RECT = 1 << 1;
    public static final int EDITED = 1 << 2;
    public static final int TOGGLED_SELECTION = 1 << 3;
    public static final int TOGGLED_OPEN = 1 << 4;
    public static final int HAS_DEACTIVATED = 1 << 5;
    public static final int DEACTIVATED = 1 << 6;
    public static final int HOVERED_WINDOW = 1 << 7;
    public static final int VISIBLE = 1 << 8;
    public static final int HAS_CLIP_RECT = 1 << 9;
    public static final int HAS_SHORTCUT = 1 << 10;

    /** Similar to EDITED, but bypassing ItemFlags.INTERNAL_NO_MARK_EDITED. */
    public static final int EDITED_INTERNAL = 1 << 11;

    // The following flags are only reported to the test engine hooks

    /** Item is openable (e.g. tree nodes). */
    public static final int OPENABLE = 1 << 20;

    /** The item is open. */
    public static final int OPENED = 1 << 21;

    /** Item is checkable (e.g. checkboxes, menu items). */
    public static final int CHECKABLE = 1 << 22;

    /** The item is checked. */
    public static final int CHECKED = 1 << 23;

    /** Item is a text-inputable (e.g. inputText, sliders, drags). */
    public static final int INPUTABLE = 1 << 24;

    /** Private constructor so this is not instantiated. */
    private ItemStatusFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
