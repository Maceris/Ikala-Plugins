package com.ikalagaming.graphics.gui.flags;

/** Flags for openPopup(), beginPopupContext*() and isPopupOpen(). */
public class PopupFlags {
    public static final int NONE = 0;

    /** For beginPopupContext*(): open on left mouse release. Only one button allowed! */
    public static final int MOUSE_BUTTON_LEFT = 1;

    /** For beginPopupContext*(): open on right mouse release. Only one button allowed! */
    public static final int MOUSE_BUTTON_RIGHT = 1 << 1;

    /** For beginPopupContext*(): open on middle mouse release. Only one button allowed! */
    public static final int MOUSE_BUTTON_MIDDLE = 1 << 2;

    /**
     * For openPopup*(), beginPopupContext*(): don't reopen the same popup if already open (won't
     * reposition, won't reinitialize navigation).
     */
    public static final int NO_REOPEN = 1 << 5;

    /**
     * For openPopup*(), beginPopupContext*(): don't open if there's already a popup at the same
     * level of the popup stack.
     */
    public static final int NO_OPEN_OVER_EXISTING_POPUP = 1 << 7;

    /**
     * For beginPopupContextWindow(): don't return true when hovering items, only when hovering
     * empty space.
     */
    public static final int NO_OPEN_OVER_ITEMS = 1 << 8;

    /** For isPopupOpen(): ignore the ID parameter and test for any popup. */
    public static final int ANY_POPUP_ID = 1 << 10;

    /**
     * For isPopupOpen(): search/test at any level of the popup stack (default tests the current
     * level).
     */
    public static final int ANY_POPUP_LEVEL = 1 << 11;

    // Combined flags
    public static final int MOUSE_BUTTON_DEFAULT = MOUSE_BUTTON_RIGHT;
    public static final int MOUSE_BUTTON_MASK =
            MOUSE_BUTTON_LEFT | MOUSE_BUTTON_RIGHT | MOUSE_BUTTON_MIDDLE;
    public static final int ANY_POPUP = ANY_POPUP_ID | ANY_POPUP_LEVEL;

    /** Private constructor so this is not instantiated. */
    private PopupFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
