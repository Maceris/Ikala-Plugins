package com.ikalagaming.graphics.gui.flags;

/** Flags for selectable(). */
public class SelectableFlags {
    public static final int NONE = 0;

    /** Clicking this doesn't close the parent popup window (overrides AUTO_CLOSE_POPUPS). */
    public static final int NO_AUTO_CLOSE_POPUPS = 1;

    /** The frame will span all columns of its container table (text will still fit the column). */
    public static final int SPAN_ALL_COLUMNS = 1 << 1;

    /** Generate press events on double clicks too. */
    public static final int ALLOW_DOUBLE_CLICK = 1 << 2;

    /** Cannot be selected, display grayed out text. */
    public static final int DISABLED = 1 << 3;

    /**
     * Hit testing will allow subsequent widgets to overlap this one. Requires the previous frame
     * hovered ID to match before being usable.
     */
    public static final int ALLOW_OVERLAP = 1 << 4;

    /** Make the item be displayed as if it is hovered. */
    public static final int HIGHLIGHT = 1 << 5;

    /** Auto-select when moved into with navigation, unless ctrl is held. */
    public static final int SELECT_ON_NAV = 1 << 6;

    /** Don't hold the active ID, used for menus so users can click and drag to browse entries. */
    public static final int INTERNAL_NO_HOLDING_ACTIVE_ID = 1 << 20;

    /** Override button behavior to react on click (the default is click + release). */
    public static final int INTERNAL_SELECT_ON_CLICK = 1 << 22;

    /** Override button behavior to react on release (the default is click + release). */
    public static final int INTERNAL_SELECT_ON_RELEASE = 1 << 23;

    /** Span all available width even if we declared less for layout purposes. */
    public static final int INTERNAL_SPAN_AVAILABLE_WIDTH = 1 << 24;

    /** Set the navigation ID on mouse hover (used by menu items). */
    public static final int INTERNAL_SET_NAV_ID_ON_HOVER = 1 << 25;

    /** Disable padding each side with half the item spacing. */
    public static final int INTERNAL_NO_PAD_WITH_HALF_SPACING = 1 << 26;

    /**
     * Don't set the key owner on the initial click. Mouse buttons are keys, so the key in question
     * is often Key.MOUSE_LEFT.
     */
    public static final int INTERNAL_NO_SET_KEY_OWNER = 1 << 27;

    /** Private constructor so this is not instantiated. */
    private SelectableFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
