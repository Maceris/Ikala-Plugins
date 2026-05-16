package com.ikalagaming.graphics.frontend.gui.flags;

public class DockNodeFlags {
    public static final int NONE = 0;
    public static final int KEEP_ALIVE_ONLY = 1;
    public static final int NO_CENTRAL_NODE = 1 << 1;
    public static final int NO_DOCKING_OVER_CENTRAL_NODE = 1 << 2;
    public static final int PASSTHROUGH_CENTRAL_NODE = 1 << 3;
    public static final int NO_DOCKING_SPLIT = 1 << 4;
    public static final int NO_RESIZE = 1 << 5;
    public static final int AUTO_HIDE_TAB_BAR = 1 << 6;

    /**
     * A dockspace is a node that occupy space within an existing user window. Otherwise, the node
     * is floating and create its own window.
     */
    public static final int INTERNAL_DOCK_SPACE = 1 << 10;

    /**
     * The central node has 2 main properties: stay visible when empty, only use "remaining" spaces
     * from its neighbor.
     */
    public static final int INTERNAL_CENTRAL_NODE = 1 << 11;

    /** Tab bar is completely unavailable. No triangle in the corner to enable it back. */
    public static final int INTERNAL_NO_TAB_BAR = 1 << 12;

    /**
     * Tab bar is hidden, with a triangle in the corner to show it again. Note: actual tab-bar
     * instance may be destroyed as this is only used for single-window tab bar
     */
    public static final int INTERNAL_HIDDEN_TAB_BAR = 1 << 13;

    /** Disable window/docking menu (that one that appears instead of the collapse button). */
    public static final int INTERNAL_NO_WINDOW_MENU_BUTTON = 1 << 14;

    /** Disable close button. */
    public static final int INTERNAL_NO_CLOSE_BUTTON = 1 << 15;

    public static final int INTERNAL_NO_RESIZE_X = 1 << 16;
    public static final int INTERNAL_NO_RESIZE_Y = 1 << 17;

    /**
     * Any docked window will be automatically be focus-route chained
     * (window.parentWindowForFocusRoute set to this) so shortcut() in this window can run when any
     * docked window is focused.
     */
    public static final int INTERNAL_DOCKED_WINDOWS_IN_FOCUS_ROUTE = 1 << 18;

    /*
     * Disable docking/undocking actions in this dockspace or individual node (existing docked nodes will be preserved).
     * Those are not exposed in public because the desirable sharing/inheriting/copy-flag-on-split behaviors are quite difficult to design and understand.
     * The two public flags NO_DOCKING_IN_CENTRAL_NODE/NO_DOCKING_SPLIT don't have those issues.
     */

    /** Disable this node from splitting other windows/nodes. */
    public static final int INTERNAL_NO_DOCKING_SPLIT_OTHER = 1 << 19;

    /** Disable other windows/nodes from being docked over this node. */
    public static final int INTERNAL_NO_DOCKING_OVER_ME = 1 << 20;

    /** Disable this node from being docked over another window or non-empty node. */
    public static final int INTERNAL_NO_DOCKING_OVER_OTHER = 1 << 21;

    /**
     * Disable this node from being docked over an empty node (e.g. DockSpace with no other windows)
     */
    public static final int INTERNAL_NO_DOCKING_OVER_EMPTY = 1 << 22;

    // Combined flags

    public static final int INTERNAL_NO_DOCKING =
            INTERNAL_NO_DOCKING_OVER_ME
                    | INTERNAL_NO_DOCKING_OVER_OTHER
                    | INTERNAL_NO_DOCKING_OVER_EMPTY
                    | NO_DOCKING_SPLIT
                    | INTERNAL_NO_DOCKING_SPLIT_OTHER;

    // Masks
    public static final int SHARED_FLAGS_INHERIT_MASK = ~0;
    public static final int NO_RESIZE_FLAGS_MASKS =
            NO_RESIZE | INTERNAL_NO_RESIZE_X | INTERNAL_NO_RESIZE_Y;

    // When splitting, those local flags are moved to the inheriting child, never duplicated
    public static final int LOCAL_FLAGS_TRANSFER_MASK =
            NO_DOCKING_SPLIT
                    | NO_RESIZE_FLAGS_MASKS
                    | AUTO_HIDE_TAB_BAR
                    | INTERNAL_CENTRAL_NODE
                    | INTERNAL_NO_TAB_BAR
                    | INTERNAL_HIDDEN_TAB_BAR
                    | INTERNAL_NO_WINDOW_MENU_BUTTON
                    | INTERNAL_NO_CLOSE_BUTTON;
    public static final int SAVED_FLAGS_MASK =
            NO_RESIZE_FLAGS_MASKS
                    | INTERNAL_DOCK_SPACE
                    | INTERNAL_CENTRAL_NODE
                    | INTERNAL_NO_TAB_BAR
                    | INTERNAL_HIDDEN_TAB_BAR
                    | INTERNAL_NO_WINDOW_MENU_BUTTON
                    | INTERNAL_NO_CLOSE_BUTTON;

    /** Private constructor so this is not instantiated. */
    private DockNodeFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
