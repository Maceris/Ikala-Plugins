package com.ikalagaming.graphics.gui.flags;

/** Flags for tableSetupColumn(). */
public class TableColumnFlags {
    // Input configuration flags
    public static final int NONE = 0;

    /**
     * Overriding/master disable flag: hide the column, it won't show in the context menu (unlike
     * calling tableSetColumnEnabled() which manipulates the user accessible state).
     */
    public static final int DISABLED = 1;

    /** Default as a hidden/disabled column. */
    public static final int DEFAULT_HIDE = 1 << 1;

    /** Default as a sorting column. */
    public static final int DEFAULT_SORT = 1 << 2;

    /**
     * Column will stretch. Preferable with horizontal scrolling disabled (default if the table
     * sizing policy is SIZING_STRETCH_SAME or SIZING_STRETCH_PROP).
     */
    public static final int WIDTH_STRETCH = 1 << 3;

    /**
     * Column will not stretch. Preferable with horizontal scrolling enabled (default if the table
     * sizing policy is SIZING_FIXED_FIT and the table is resizable).
     */
    public static final int WIDTH_FIXED = 1 << 4;

    /** Disable manual resizing. */
    public static final int NO_RESIZE = 1 << 5;

    /**
     * Disable manually reordering this column, this will also prevent other columns from crossing
     * over this column.
     */
    public static final int NO_REORDER = 1 << 6;

    /** Disable the ability to hide/disable this column. */
    public static final int NO_HIDE = 1 << 7;

    /** Disable clipping for this column. */
    public static final int NO_CLIP = 1 << 8;

    /** Disable the ability to sort on this field (even if the table is sortable). */
    public static final int NO_SORT = 1 << 9;

    /** Disable the ability to sort in the ascending direction. */
    public static final int NO_SORT_ASCENDING = 1 << 10;

    /** Disable the ability to sort in the descending direction. */
    public static final int NO_SORT_DESCENDING = 1 << 11;

    /**
     * tableHeadersRow() will submit an empty label for this column. Convenient for some small
     * columns. The name will still appear in the context menu or in angled headers. You may append
     * into this cell by calling tableSetColumnIndex() right after the tableHeadersRow() call.
     */
    public static final int NO_HEADER_LABEL = 1 << 12;

    /** Disable the header text width contribution to the automatic column width. */
    public static final int NO_HEADER_WIDTH = 1 << 13;

    /** Make the initial sort direction ascending when first sorting on this column (default). */
    public static final int PREFER_SORT_ASCENDING = 1 << 14;

    /** Make the initial sort direction descending when first sorting on this column. */
    public static final int PREFER_SORT_DESCENDING = 1 << 15;

    /** Use the current indent value when entering the cell (default for column 0). */
    public static final int INDENT_ENABLE = 1 << 16;

    /**
     * Ignore the current indent value when entering the cell (default for columns after 0).
     * Indentation changes within the cell will still be honored.
     */
    public static final int INDENT_DISABLE = 1 << 17;

    /**
     * tableHeadersRow() will submit an angled header row for this column. Note this will add an
     * extra row.
     */
    public static final int ANGLED_HEADER = 1 << 18;

    // Output status flags, read-only via tableGetColumnFlags()
    /** Status: is enabled == not hidden by the user/api (DEFAULT_HIDE and NO_HIDE). */
    public static final int IS_ENABLED = 1 << 24;

    /** Status: is visible == is enabled and not clipped by scrolling. */
    public static final int IS_VISIBLE = 1 << 25;

    /** Status: is currently part of the sort specs. */
    public static final int IS_SORTED = 1 << 26;

    /** Status: is hovered by the mouse. */
    public static final int IS_HOVERED = 1 << 27;

    // Internal combinations and masks
    public static final int INTERNAL_WIDTH_MASK = WIDTH_STRETCH | WIDTH_FIXED;
    public static final int INTERNAL_INDENT_MASK = INDENT_ENABLE | INDENT_DISABLE;
    public static final int INTERNAL_STATUS_MASK = IS_ENABLED | IS_VISIBLE | IS_SORTED | IS_HOVERED;

    /**
     * Disable the user resizing this column directly (it may however be resized indirectly from its
     * left edge).
     */
    public static final int INTERNAL_NO_DIRECT_RESIZE = 1 << 30;

    /** Private constructor so this is not instantiated. */
    private TableColumnFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
