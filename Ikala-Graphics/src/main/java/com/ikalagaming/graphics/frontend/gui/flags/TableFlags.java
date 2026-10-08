package com.ikalagaming.graphics.frontend.gui.flags;

/** Flags for beginTable(). */
public class TableFlags {
    public static final int NONE = 0;

    // Features
    /** Enable resizing columns. */
    public static final int RESIZABLE = 1;

    /**
     * Enable reordering columns in the header row. Needs tableSetupColumn() + tableHeadersRow() to
     * display headers, or CONTEXT_MENU_IN_BODY to access the context menu without headers.
     */
    public static final int REORDERABLE = 1 << 1;

    /** Enable hiding/disabling columns in the context menu. */
    public static final int HIDEABLE = 1 << 2;

    /**
     * Enable sorting. Call tableGetSortSpecs() to obtain sort specs. Also see SORT_MULTI and
     * SORT_TRISTATE.
     */
    public static final int SORTABLE = 1 << 3;

    /** Disable persisting column order, width, visibility, and sort settings in the .ini file. */
    public static final int NO_SAVED_SETTINGS = 1 << 4;

    /**
     * Right-clicking on column body/contents will also display the table context menu. By default
     * it is available in tableHeadersRow().
     */
    public static final int CONTEXT_MENU_IN_BODY = 1 << 5;

    // Decorations
    /**
     * Set each row background color with ColorType.TABLE_ROW_BACKGROUND or
     * TABLE_ROW_BACKGROUND_ALT, equivalent to calling tableSetBackgroundColor() with
     * ROW_BACKGROUND_0 on each row manually.
     */
    public static final int ROW_BACKGROUND = 1 << 6;

    /** Draw horizontal borders between rows. */
    public static final int BORDERS_INNER_H = 1 << 7;

    /** Draw horizontal borders at the top and bottom. */
    public static final int BORDERS_OUTER_H = 1 << 8;

    /** Draw vertical borders between columns. */
    public static final int BORDERS_INNER_V = 1 << 9;

    /** Draw vertical borders on the left and right sides. */
    public static final int BORDERS_OUTER_V = 1 << 10;

    /** Draw horizontal borders. */
    public static final int BORDERS_H = BORDERS_INNER_H | BORDERS_OUTER_H;

    /** Draw vertical borders. */
    public static final int BORDERS_V = BORDERS_INNER_V | BORDERS_OUTER_V;

    /** Draw inner borders. */
    public static final int BORDERS_INNER = BORDERS_INNER_V | BORDERS_INNER_H;

    /** Draw outer borders. */
    public static final int BORDERS_OUTER = BORDERS_OUTER_V | BORDERS_OUTER_H;

    /** Draw all borders. */
    public static final int BORDERS = BORDERS_INNER | BORDERS_OUTER;

    /**
     * [ALPHA] Disable vertical borders in the column body (borders will always appear in headers).
     */
    public static final int NO_BORDERS_IN_BODY = 1 << 11;

    /**
     * [ALPHA] Disable vertical borders in the column body until hovered for resizing (borders will
     * always appear in headers).
     */
    public static final int NO_BORDERS_IN_BODY_UNTIL_RESIZE = 1 << 12;

    // Sizing policy
    /**
     * Columns default to fixed width or auto width (if resizable or not resizable), matching
     * contents width.
     */
    public static final int SIZING_FIXED_FIT = 1 << 13;

    /**
     * Columns default to fixed width or auto width (if resizable or not resizable), matching the
     * maximum contents width of all columns. Implicitly enables NO_KEEP_COLUMNS_VISIBLE.
     */
    public static final int SIZING_FIXED_SAME = 2 << 13;

    /** Columns default to stretching, with default weights proportional to their content widths. */
    public static final int SIZING_STRETCH_PROP = 3 << 13;

    /**
     * Columns default to stretching, with default weights all equal unless overridden by
     * tableSetupColumn().
     */
    public static final int SIZING_STRETCH_SAME = 4 << 13;

    // Sizing extra options
    /**
     * Make the outer width auto-fit to the columns, overriding the outer width. Only available when
     * SCROLL_X/SCROLL_Y are disabled and stretch columns are not used.
     */
    public static final int NO_HOST_EXTEND_X = 1 << 16;

    /**
     * Make the outer height stop exactly at the outer height (prevent auto-extending the table past
     * the limit). Only available when SCROLL_X/SCROLL_Y are disabled. Data below the limit will be
     * clipped and not visible.
     */
    public static final int NO_HOST_EXTEND_Y = 1 << 17;

    /**
     * Disable keeping columns always minimally visible when SCROLL_X is off and the table gets too
     * small. Not recommended if columns are resizable.
     */
    public static final int NO_KEEP_COLUMNS_VISIBLE = 1 << 18;

    /**
     * Disable distributing the remainder width to stretched columns (width allocation on a 100 wide
     * table with 3 columns: Without this flag: 33,33,34. With this flag: 33,33,33). With larger
     * numbers of columns, resizing will appear to be less smooth.
     */
    public static final int PRECISE_WIDTHS = 1 << 19;

    // Clipping
    /**
     * Disable the clipping rectangle for every individual column (items will be able to overflow
     * into other columns). Generally incompatible with tableSetupScrollFreeze().
     */
    public static final int NO_CLIP = 1 << 20;

    // Padding
    /**
     * Default if BORDERS_OUTER_V is on. Enable outermost padding. Generally desirable if you have
     * headers.
     */
    public static final int PAD_OUTER_X = 1 << 21;

    /** Default if BORDERS_OUTER_V is off. Disable outermost padding. */
    public static final int NO_PAD_OUTER_X = 1 << 22;

    /**
     * Disable inner padding between columns (double inner padding if BORDERS_OUTER_V is on, single
     * inner padding if BORDERS_OUTER_V is off).
     */
    public static final int NO_PAD_INNER_X = 1 << 23;

    // Scrolling
    /**
     * Enable horizontal scrolling. Requires the outer size parameter of beginTable() to specify the
     * container size. Changes the default sizing policy. Because this creates a child window,
     * SCROLL_Y is currently generally recommended when using SCROLL_X.
     */
    public static final int SCROLL_X = 1 << 24;

    /**
     * Enable vertical scrolling. Requires the outer size parameter of beginTable() to specify the
     * container size.
     */
    public static final int SCROLL_Y = 1 << 25;

    // Sorting
    /**
     * Hold shift when clicking headers to sort on multiple columns. tableGetSortSpecs() may return
     * specs where specsCount is greater than 1.
     */
    public static final int SORT_MULTI = 1 << 26;

    /**
     * Allow no sorting, disable default sorting. tableGetSortSpecs() may return specs where
     * specsCount is 0.
     */
    public static final int SORT_TRISTATE = 1 << 27;

    // Miscellaneous
    /** Highlight column headers when hovered (may evolve into a fuller highlight). */
    public static final int HIGHLIGHT_HOVERED_COLUMN = 1 << 28;

    // Internal combinations and masks
    /** All of the sizing policy values. */
    public static final int INTERNAL_SIZING_MASK =
            SIZING_FIXED_FIT | SIZING_FIXED_SAME | SIZING_STRETCH_PROP | SIZING_STRETCH_SAME;

    /** Private constructor so this is not instantiated. */
    private TableFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
