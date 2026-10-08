package com.ikalagaming.graphics.frontend.gui.flags;

public class ItemFlags {
    public static final int NONE = 0;

    /** Disable keyboard tabbing. */
    public static final int NO_TAB_STOP = 1;

    /**
     * Any button-like behavior will have repeat mode enabled (based on io.keyRepeatDelay and
     * io.keyRepeatRate values).
     */
    public static final int BUTTON_REPEAT = 1 << 1;

    /** Disable interactions, but doesn't affect visuals. */
    public static final int DISABLED = 1 << 2;

    /** Disable any form of focusing (keyboard/gamepad directional navigation and setting focus). */
    public static final int NO_NAV = 1 << 3;

    /** Disable item being a candidate for default focus (e.g. used by title bar items). */
    public static final int NO_NAV_DEFAULT_FOCUS = 1 << 4;

    /** menuItem()/selectable() automatically close their parent popup window. On by default. */
    public static final int AUTO_CLOSE_POPUPS = 1 << 5;

    /** Represent a mixed/indeterminate value, generally multi-selection where values differ. */
    public static final int MIXED_VALUE = 1 << 6;

    /** Read-only, for API that supports it. */
    public static final int READ_ONLY = 1 << 7;

    /** Allow the next item to be overlapped by a subsequent item. */
    public static final int ALLOW_OVERLAP = 1 << 8;

    /** Disable the item being focusable or being selected as a focus target by the mouse. */
    public static final int NO_FOCUS = 1 << 9;

    /**
     * InputText: apply keyboard edits to the backing value while typing. Otherwise, edits are
     * applied when validating, tabbing out or deactivating. On by default.
     */
    public static final int LIVE_EDIT_ON_INPUT_TEXT = 1 << 10;

    /**
     * Drags, sliders, inputScalar: apply keyboard edits to the backing value while typing.
     * Otherwise, edits are applied when validating, tabbing out or deactivating.
     */
    public static final int LIVE_EDIT_ON_INPUT_SCALAR = 1 << 11;

    /** Apply keyboard edits to backing values while typing, for both text and scalars. */
    public static final int LIVE_EDIT_ON_INPUT =
            LIVE_EDIT_ON_INPUT_TEXT | LIVE_EDIT_ON_INPUT_SCALAR;

    /** Allow submitting an item with the same ID as an item already submitted this frame. */
    public static final int ALLOW_DUPLICATE_ID = 1 << 12;

    /** Internal: auto-activate input mode when tab focused. */
    public static final int INTERNAL_INPUTABLE = 1 << 19;

    /** Internal: set by setNextItemSelectionUserData(). */
    public static final int INTERNAL_HAS_SELECTION_USER_DATA = 1 << 23;

    /** Internal: set by setNextItemSelectionUserData(). */
    public static final int INTERNAL_IS_MULTI_SELECT = 1 << 24;

    /** Internal: skip the test for whether the window contents are hoverable. */
    public static final int INTERNAL_NO_WINDOW_HOVERABLE_CHECK = 1 << 20;

    /** Internal: don't mark the item as edited. */
    public static final int INTERNAL_NO_MARK_EDITED = 1 << 21;

    /** Internal: allow hovering interactions even when navigation highlighting is active. */
    public static final int INTERNAL_NO_NAV_DISABLE_MOUSE_HOVER = 1 << 22;

    /** The default flags at the start of each frame. */
    public static final int DEFAULT = AUTO_CLOSE_POPUPS | LIVE_EDIT_ON_INPUT_TEXT;

    /** Private constructor so this is not instantiated. */
    private ItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
