package com.ikalagaming.graphics.gui.flags;

/** Flags for beginTabBar(). */
public class TabBarFlags {
    public static final int NONE = 0;

    /** Allow manually dragging tabs to re-order them, and new tabs are appended at the end. */
    public static final int REORDERABLE = 1;

    /** Automatically select new tabs when they appear. */
    public static final int AUTO_SELECT_NEW_TABS = 1 << 1;

    /** Show a button that opens a popup listing all the tabs. */
    public static final int TAB_LIST_POPUP_BUTTON = 1 << 2;

    /**
     * Disable the behavior of closing tabs (that are submitted with open != null) with the middle
     * mouse button.
     */
    public static final int NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON = 1 << 3;

    /** Disable scrolling buttons (applies when the fitting policy allows scrolling). */
    public static final int NO_TAB_LIST_SCROLLING_BUTTONS = 1 << 4;

    /** Disable tooltips when hovering a tab. */
    public static final int NO_TOOLTIP = 1 << 5;

    /** Draw selected overline markers over the selected tab. */
    public static final int DRAW_SELECTED_OVERLINE = 1 << 6;

    /**
     * Shrink down tabs when they don't fit, until the width is style.tabMinWidthShrink, then enable
     * scrolling.
     */
    public static final int FITTING_POLICY_MIXED = 1 << 7;

    /** Shrink down tabs when they don't fit. */
    public static final int FITTING_POLICY_SHRINK = 1 << 8;

    /** Enable scrolling buttons when tabs don't fit. */
    public static final int FITTING_POLICY_SCROLL = 1 << 9;

    /** Internal: part of a dock node. */
    public static final int INTERNAL_DOCK_NODE = 1 << 20;

    /** Internal: the tab bar is focused, which affects the colors used. */
    public static final int INTERNAL_IS_FOCUSED = 1 << 21;

    /** Internal: mark settings dirty when reordering tabs. */
    public static final int INTERNAL_SAVE_SETTINGS = 1 << 22;

    // Combined flags
    public static final int FITTING_POLICY_MASK =
            FITTING_POLICY_MIXED | FITTING_POLICY_SHRINK | FITTING_POLICY_SCROLL;
    public static final int FITTING_POLICY_DEFAULT = FITTING_POLICY_MIXED;

    /** Private constructor so this is not instantiated. */
    private TabBarFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
