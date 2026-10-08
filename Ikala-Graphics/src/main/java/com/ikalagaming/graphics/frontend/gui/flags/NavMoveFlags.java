package com.ikalagaming.graphics.frontend.gui.flags;

/** Flags for navigation move requests. */
public class NavMoveFlags {
    public static final int NONE = 0;

    /** On a failed request, restart from the opposite side. */
    public static final int LOOP_X = 1;

    /** On a failed request, restart from the opposite side. */
    public static final int LOOP_Y = 1 << 1;

    /**
     * On a failed request, request from the opposite side one line down (when moving right) or one
     * line up (when moving left).
     */
    public static final int WRAP_X = 1 << 2;

    /** Like WRAP_X, but for columns. */
    public static final int WRAP_Y = 1 << 3;

    /** All the wrap and loop flags. */
    public static final int WRAP_MASK = LOOP_X | LOOP_Y | WRAP_X | WRAP_Y;

    /**
     * Allow scoring and considering the current navigation ID as a move target candidate. This is
     * used when the move source is offset (e.g. page down from the bottom item should stay there).
     */
    public static final int ALLOW_CURRENT_NAV_ID = 1 << 4;

    /** Store an alternate result with only the visible items, used by page up/page down. */
    public static final int ALSO_SCORE_VISIBLE_SET = 1 << 5;

    /** Force scrolling to the min/max, used by home/end. */
    public static final int SCROLL_TO_EDGE_Y = 1 << 6;

    /** The request was forwarded from the previous frame. */
    public static final int FORWARDED = 1 << 7;

    /** Dummy scoring for debugging, don't apply the result. */
    public static final int DEBUG_NO_RESULT = 1 << 8;

    /**
     * Requests from the focus API can land on/focus/activate items even if they are marked with
     * NO_TAB_STOP.
     */
    public static final int FOCUS_API = 1 << 9;

    /** Focus, and activate if the item is inputable. */
    public static final int IS_TABBING = 1 << 10;

    /** A page up/page down request. */
    public static final int IS_PAGE_MOVE = 1 << 11;

    /** Activate/select the target item. */
    public static final int ACTIVATE = 1 << 12;

    /** Don't trigger selection by not setting the just moved to ID. */
    public static final int NO_SELECT = 1 << 13;

    /** Do not alter the navigation cursor visible state. */
    public static final int NO_SET_NAV_CURSOR_VISIBLE = 1 << 14;

    /** Do not clear the active ID when applying the move result. */
    public static final int NO_CLEAR_ACTIVE_ID = 1 << 15;

    /** Private constructor so this is not instantiated. */
    private NavMoveFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
