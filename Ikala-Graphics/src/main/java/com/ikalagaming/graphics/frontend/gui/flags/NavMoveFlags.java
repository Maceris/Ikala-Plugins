package com.ikalagaming.graphics.frontend.gui.flags;

public class NavMoveFlags {
    public static final int NONE = 0;

    /** On a failed request, restart from the opposite side. */
    public static final int LOOP_X = 1;

    public static final int LOOP_Y = 1 << 1;

    /**
     * On a failed request, request on the opposite side one line up or down (depending on nav
     * direction)
     */
    public static final int WRAP_X = 1 << 2;

    public static final int WRAP_Y = 1 << 3;

    /** Allow considering the current Nav ID as a target. */
    public static final int ALLOW_CURRENT_NAV_ID = 1 << 4;

    /**
     * Stores a second set of components, just the visible ones, for use with page up / page down.
     */
    public static final int ALSO_SCORE_VISIBLE_SET = 1 << 5;

    public static final int FORWARDED = 1 << 6;

    /** Dummy score for debugging, don't actually apply the result. */
    public static final int DEBUG_NO_RESULT = 1 << 7;

    /**
     * The focus API can navigate to items even if they normally wouldn't be able to be selected.
     */
    public static final int FOCUS_API = 1 << 8;

    /** We are using Tab navigation. */
    public static final int IS_TABBING = 1 << 9;

    /** We are using page up / page down. */
    public static final int IS_PAGE_MOVE = 1 << 10;

    public static final int ACTIVATE = 1 << 11;
    public static final int NO_SELECT = 1 << 12;
    public static final int NO_SET_NAV_CURSOR_VISIBLE = 1 << 13;
    public static final int NO_CLEAR_ACTIVE_ID = 1 << 14;

    // Combined flags
    public static final int WRAP_MASK = LOOP_X | LOOP_Y | WRAP_X | WRAP_Y;
}
