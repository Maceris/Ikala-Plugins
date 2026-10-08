package com.ikalagaming.graphics.frontend.gui.flags;

/**
 * Flags for beginMultiSelect().
 *
 * @see com.ikalagaming.graphics.frontend.gui.IkGui#beginMultiSelect(int, int, int)
 */
public class MultiSelectFlags {
    public static final int NONE = 0;

    /**
     * Disable selecting more than one item. This allows single-selection code to share the same
     * logic, but disables the main purpose of beginMultiSelect().
     */
    public static final int SINGLE_SELECT = 1;

    /** Disable the Ctrl+A shortcut to select all. */
    public static final int NO_SELECT_ALL = 1 << 1;

    /**
     * Disable Shift+selection mouse/keyboard support, which is useful for unordered 2D selection.
     * This guarantees you never have to interpolate between two selection user data values.
     */
    public static final int NO_RANGE_SELECT = 1 << 2;

    /**
     * Disable selecting items when navigating, useful for e.g. range-select in a list of
     * checkboxes.
     */
    public static final int NO_AUTO_SELECT = 1 << 3;

    /**
     * Disable clearing the selection when navigating or selecting another item. Generally used with
     * {@link #NO_AUTO_SELECT}, useful for e.g. range-select in a list of checkboxes.
     */
    public static final int NO_AUTO_CLEAR = 1 << 4;

    /** Disable clearing the selection when clicking/selecting an already selected item. */
    public static final int NO_AUTO_CLEAR_ON_RESELECT = 1 << 5;

    /**
     * Enable box-selection with items of the same width and x position (e.g. full row selectables).
     * Box-selection works better with a little spacing between item hit boxes, so empty space can
     * be clicked.
     */
    public static final int BOX_SELECT_1D = 1 << 6;

    /**
     * Enable box-selection with items of varying width or x position (e.g. different width labels,
     * or a 2D grid). This is slower, since it alters clipping so that horizontal movements will
     * update the selection of normally clipped items.
     */
    public static final int BOX_SELECT_2D = 1 << 7;

    /** Disable scrolling when box-selecting and moving the mouse near the edges of the scope. */
    public static final int BOX_SELECT_NO_SCROLL = 1 << 8;

    /** Clear the selection when pressing Escape while the scope is focused. */
    public static final int CLEAR_ON_ESCAPE = 1 << 9;

    /** Clear the selection when clicking on an empty location within the scope. */
    public static final int CLEAR_ON_CLICK_VOID = 1 << 10;

    /**
     * The scope for box-select and {@link #CLEAR_ON_CLICK_VOID} is the whole window (default). Use
     * this if the multi-select covers a whole window, or is only used once in the same window.
     */
    public static final int SCOPE_WINDOW = 1 << 11;

    /**
     * The scope for box-select and {@link #CLEAR_ON_CLICK_VOID} is the rectangle encompassing
     * beginMultiSelect()/endMultiSelect(). Use this if beginMultiSelect() is called multiple times
     * in the same window.
     */
    public static final int SCOPE_RECT = 1 << 12;

    /**
     * Apply the selection on mouse down when clicking an unselected item, and on mouse up when
     * clicking a selected item (default). This allows dragging multiple selected items.
     */
    public static final int SELECT_ON_AUTO = 1 << 13;

    /**
     * Apply the selection on mouse down when clicking any item. This prevents drag and drop of a
     * multiple selection, but allows e.g. box-select to always reselect even when clicking inside
     * an existing selection (Excel style behavior).
     */
    public static final int SELECT_ON_CLICK_ALWAYS = 1 << 14;

    /**
     * Apply the selection on mouse release when clicking an unselected item. This allows dragging
     * an unselected item without altering the selection.
     */
    public static final int SELECT_ON_CLICK_RELEASE = 1 << 15;

    /**
     * Temporary: enable navigation wrapping on the X axis. Provided as a convenience until there's
     * a general navigation API for this.
     */
    public static final int NAV_WRAP_X = 1 << 16;

    /**
     * Disable the default right-click processing, which selects the item on mouse down for context
     * menus.
     */
    public static final int NO_SELECT_ON_RIGHT_CLICK = 1 << 17;

    /** All the select-on flags. */
    public static final int SELECT_ON_MASK =
            SELECT_ON_AUTO | SELECT_ON_CLICK_ALWAYS | SELECT_ON_CLICK_RELEASE;

    /** Internal: used by checkboxes, which toggle instead of select. */
    public static final int INTERNAL_CHECKBOX_MODE = 1 << 20;

    /** Private constructor so this is not instantiated. */
    private MultiSelectFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
