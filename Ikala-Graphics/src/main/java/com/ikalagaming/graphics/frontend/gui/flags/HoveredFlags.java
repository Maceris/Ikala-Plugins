package com.ikalagaming.graphics.frontend.gui.flags;

public class HoveredFlags {
    /**
     * Return true if directly over the item/window, not obstructed by another window, not
     * obstructed by an active popup or modal blocking inputs under them.
     */
    public static final int NONE = 0;

    /** isWindowHovered() only: Return true if any children of the window is hovered. */
    public static final int CHILD_WINDOWS = 1;

    /** isWindowHovered() only: Test from root window (top most parent of the current hierarchy). */
    public static final int ROOT_WINDOW = 1 << 1;

    /** isWindowHovered() only: Return true if any window is hovered. */
    public static final int ANY_WINDOW = 1 << 2;

    /**
     * isWindowHovered() only: Do not consider popup hierarchy (do not treat popup emitter as parent
     * of popup).
     */
    public static final int NO_POPUP_HIERARCHY = 1 << 3;

    /**
     * isWindowHovered() only: Consider docking hierarchy (treat dockspace host as parent of docked
     * window).
     */
    public static final int DOCK_HIERARCHY = 1 << 4;

    /** Return true even if a popup window is normally blocking access to this item/window. */
    public static final int ALLOW_WHEN_BLOCKED_BY_POPUP = 1 << 5;

    /** Return true even if a modal popup window is normally blocking access to this item/window. */
    public static final int ALLOW_WHEN_BLOCKED_BY_MODAL = 1 << 6;

    /**
     * Return true even if an active item is blocking access to this item/window. Useful for Drag
     * and Drop patterns.
     */
    public static final int ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM = 1 << 7;

    /**
     * isItemHovered() only: Return true even if the item uses AllowOverlap mode and is overlapped
     * by another hoverable item.
     */
    public static final int ALLOW_WHEN_OVERLAPPED_BY_ITEM = 1 << 8;

    /**
     * isItemHovered() only: Return true even if the position is obstructed or overlapped by another
     * window.
     */
    public static final int ALLOW_WHEN_OVERLAPPED_BY_WINDOW = 1 << 9;

    /** isItemHovered() only: Return true even if the item is disabled. */
    public static final int ALLOW_WHEN_DISABLED = 1 << 10;

    /**
     * isItemHovered() only: Disable using keyboard/gamepad navigation state when active, always
     * query mouse.
     */
    public static final int NO_NAV_OVERRIDE = 1 << 11;

    /**
     * Shortcut for standard flags when using isItemHovered() + setTooltip() sequence. Uses the
     * style hoverFlagsForTooltipMouse/hoverFlagsForTooltipNav flags.
     */
    public static final int FOR_TOOLTIP = 1 << 12;

    /**
     * Require mouse to be stationary for style.hoverStationaryDelay (~0.15 sec) at least one time.
     * After this, can move on same item/window.
     */
    public static final int STATIONARY = 1 << 13;

    /** isItemHovered() only: Return true immediately (default). */
    public static final int DELAY_NONE = 1 << 14;

    /** isItemHovered() only: Return true after style.hoverDelayShort elapsed (~0.15 sec). */
    public static final int DELAY_SHORT = 1 << 15;

    /** isItemHovered() only: Return true after style.hoverDelayNormal elapsed (~0.40 sec). */
    public static final int DELAY_NORMAL = 1 << 16;

    /**
     * isItemHovered() only: Disable shared delay system where moving from one item to the next
     * keeps the previous timer for a short time (standard for tooltips with long delays).
     */
    public static final int NO_SHARED_DELAY = 1 << 17;

    // Combined flags
    public static final int ALLOW_WHEN_OVERLAPPED =
            ALLOW_WHEN_OVERLAPPED_BY_ITEM | ALLOW_WHEN_OVERLAPPED_BY_WINDOW;
    public static final int RECT_ONLY =
            ALLOW_WHEN_BLOCKED_BY_POPUP | ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM | ALLOW_WHEN_OVERLAPPED;
    public static final int ROOT_AND_CHILD_WINDOWS = ROOT_WINDOW | CHILD_WINDOWS;

    /** Private constructor so this is not instantiated. */
    private HoveredFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
