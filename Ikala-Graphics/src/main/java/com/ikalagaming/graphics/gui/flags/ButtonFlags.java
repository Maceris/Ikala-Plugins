package com.ikalagaming.graphics.gui.flags;

public class ButtonFlags {
    public static final int NONE = 0;

    /** React on left mouse button (default). */
    public static final int MOUSE_BUTTON_LEFT = 1;

    /** React on right mouse button. */
    public static final int MOUSE_BUTTON_RIGHT = 1 << 1;

    /** React on center mouse button. */
    public static final int MOUSE_BUTTON_MIDDLE = 1 << 2;

    /** All the mouse button flags. */
    public static final int MOUSE_BUTTON_MASK =
            MOUSE_BUTTON_LEFT | MOUSE_BUTTON_RIGHT | MOUSE_BUTTON_MIDDLE;

    /** InvisibleButton(): do not disable navigation/tabbing. Otherwise disabled by default. */
    public static final int ENABLE_NAV = 1 << 3;

    /** Hit testing is allowed to overlap other items, such as when used as a background. */
    public static final int ALLOW_OVERLAP = 1 << 12;

    /** Internal: return true on click (mouse down event). */
    public static final int INTERNAL_PRESSED_ON_CLICK = 1 << 4;

    /** Internal: [Default] return true on click + release on same item. */
    public static final int INTERNAL_PRESSED_ON_CLICK_RELEASE = 1 << 5;

    /**
     * Internal: return true on click + release even if the release event is not done while hovering
     * the item.
     */
    public static final int INTERNAL_PRESSED_ON_CLICK_RELEASE_ANYWHERE = 1 << 6;

    /** Internal: return true on release (default requires click+release). */
    public static final int INTERNAL_PRESSED_ON_RELEASE = 1 << 7;

    /** Internal: return true on double-click (default requires click+release). */
    public static final int INTERNAL_PRESSED_ON_DOUBLE_CLICK = 1 << 8;

    /**
     * Internal: return true when held into while we are drag and dropping another item (used by
     * e.g. tree nodes, collapsing headers).
     */
    public static final int INTERNAL_PRESSED_ON_DRAG_DROP_HOLD = 1 << 9;

    /** Internal: all the flags dealing with when a button is considered pressed. */
    public static final int INTERNAL_PRESSED_ON_MASK =
            INTERNAL_PRESSED_ON_CLICK
                    | INTERNAL_PRESSED_ON_CLICK_RELEASE
                    | INTERNAL_PRESSED_ON_CLICK_RELEASE_ANYWHERE
                    | INTERNAL_PRESSED_ON_RELEASE
                    | INTERNAL_PRESSED_ON_DOUBLE_CLICK
                    | INTERNAL_PRESSED_ON_DRAG_DROP_HOLD;

    /** Internal: the default for when a button is considered pressed. */
    public static final int INTERNAL_PRESSED_ON_DEFAULT = INTERNAL_PRESSED_ON_CLICK_RELEASE;

    /** Internal: allow interactions even if a child window is overlapping. */
    public static final int INTERNAL_FLATTEN_CHILDREN = 1 << 13;

    /**
     * Internal: don't set active ID while holding the mouse (only usable with
     * INTERNAL_PRESSED_ON_CLICK).
     */
    public static final int INTERNAL_NO_HOLDING_ACTIVE_ID = 1 << 14;

    /** Internal: disable interaction if a key modifier is held. */
    public static final int INTERNAL_NO_KEY_MODS_ALLOWED = 1 << 15;

    /** Internal: don't override navigation focus when activated. */
    public static final int INTERNAL_NO_NAV_FOCUS = 1 << 16;

    /** Internal: don't report as hovered when nav focus is on this item. */
    public static final int INTERNAL_NO_HOVERED_ON_FOCUS = 1 << 17;

    /** Internal: don't focus the window when clicking on the button. */
    public static final int INTERNAL_NO_FOCUS = 1 << 18;

    /**
     * Internal: vertically align button to match text baseline, used by smallButton() for example.
     */
    public static final int INTERNAL_ALIGN_TEXT_BASELINE = 1 << 19;

    /**
     * Internal: don't set the key owner on the initial click. Mouse buttons are keys, so the key in
     * question is often Key.MOUSE_LEFT.
     */
    public static final int INTERNAL_NO_SET_KEY_OWNER = 1 << 20;

    /**
     * Internal: don't test the key owner when polling the key. Mouse buttons are keys, so the key
     * in question is often Key.MOUSE_LEFT.
     */
    public static final int INTERNAL_NO_TEST_KEY_OWNER = 1 << 21;

    /** Private constructor so this is not instantiated. */
    private ButtonFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
