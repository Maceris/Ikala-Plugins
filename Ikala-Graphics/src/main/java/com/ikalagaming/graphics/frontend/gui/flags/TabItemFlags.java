package com.ikalagaming.graphics.frontend.gui.flags;

public class TabItemFlags {
    public static final int NONE = 0;

    /** Display a dot next to the title + set NO_ASSUMED_CLOSURE. */
    public static final int UNSAVED_DOCUMENT = 1;

    /** Trigger flag to programmatically make the tab selected when calling beginTabItem(). */
    public static final int SET_SELECTED = 1 << 1;

    /**
     * Disable behavior of closing tabs (that are submitted with open != null) with middle mouse
     * button. You may handle this behavior manually on user's side with if (isItemHovered()
     * &amp;&amp; isMouseClicked(MouseButton.MIDDLE)) open = false.
     */
    public static final int NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON = 1 << 2;

    /** Don't call pushID()/popID() on beginTabItem()/endTabItem(). */
    public static final int NO_PUSH_ID = 1 << 3;

    /** Disable tooltip for the given tab. */
    public static final int NO_TOOLTIP = 1 << 4;

    /** Disable reordering this tab or having another tab cross over this tab. */
    public static final int NO_REORDER = 1 << 5;

    /** Enforce the tab position to the left of the tab bar (after the tab list popup button). */
    public static final int LEADING = 1 << 6;

    /** Enforce the tab position to the right of the tab bar (before the scrolling buttons). */
    public static final int TRAILING = 1 << 7;

    /**
     * Tab is selected when trying to close + closure is not immediately assumed (will wait for user
     * to stop submitting the tab). Otherwise, closure is assumed when pressing the X, so if you
     * keep submitting the tab may reappear at end of tab bar.
     */
    public static final int NO_ASSUMED_CLOSURE = 1 << 8;

    /**
     * Track whether open was set or not (we'll need this info on the next frame to recompute
     * contentWidth during layout)
     */
    public static final int INTERNAL_NO_CLOSE_BUTTON = 1 << 20;

    /** Used by tabItemButton, change the tab item behavior to mimic a button */
    public static final int INTERNAL_BUTTON = 1 << 21;

    /** To reserve space, like with LEADING. */
    public static final int INTERNAL_INVISIBLE = 1 << 22;

    /**
     * (Docking) Trailing tabs with the UNSORTED flag will be sorted based on the dockOrder of their
     * Window.
     */
    public static final int INTERNAL_UNSORTED = 1 << 23;

    // Combined flags
    public static final int INTERNAL_SECTION_MASK = LEADING | TRAILING;

    /** Private constructor so this is not instantiated. */
    private TabItemFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
