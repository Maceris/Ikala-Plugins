package com.ikalagaming.graphics.gui.flags;

/** Flags for shortcut(), setNextItemShortcut(), and the key ownership aware key queries. */
public class InputFlags {
    public static final int NONE = 0;

    /**
     * Enable repeat. Return true on successive repeats. Default for isKeyPressed(), not default for
     * isMouseClicked().
     */
    public static final int REPEAT = 1;

    // Routing policies for shortcut() and setNextItemShortcut(). The default is ROUTE_FOCUSED. Only
    // one policy can be selected.

    /** Route to the active item only. */
    public static final int ROUTE_ACTIVE = 1 << 10;

    /**
     * Route to windows in the focus stack (default). The deepest focused window takes inputs, the
     * active item takes inputs over the deepest focused window.
     */
    public static final int ROUTE_FOCUSED = 1 << 11;

    /** Global route (unless a focused window or active item registered the route). */
    public static final int ROUTE_GLOBAL = 1 << 12;

    /** Do not register a route, poll keys directly. */
    public static final int ROUTE_ALWAYS = 1 << 13;

    // Routing options

    /**
     * Option for global routes: higher priority than focused routes (unless the active item is in
     * the focused route).
     */
    public static final int ROUTE_OVER_FOCUSED = 1 << 14;

    /**
     * Option for global routes: higher priority than the active item. Unlikely you need this, it
     * will interfere with every active item, e.g. Ctrl+A registered by inputText.
     */
    public static final int ROUTE_OVER_ACTIVE = 1 << 15;

    /**
     * Option for global routes: will not be applied if the background is focused (no IkGui windows
     * are focused). Useful for overlay applications.
     */
    public static final int ROUTE_UNLESS_BG_FOCUSED = 1 << 16;

    /** Option: the route is evaluated from the point of view of the root window. */
    public static final int ROUTE_FROM_ROOT_WINDOW = 1 << 17;

    /** For setNextItemShortcut(): automatically display a tooltip when hovering the item. */
    public static final int TOOLTIP = 1 << 18;

    /** Internal: repeat rate, regular (default). */
    public static final int INTERNAL_REPEAT_RATE_DEFAULT = 1 << 1;

    /** Internal: repeat rate, fast. */
    public static final int INTERNAL_REPEAT_RATE_NAV_MOVE = 1 << 2;

    /** Internal: repeat rate, faster. */
    public static final int INTERNAL_REPEAT_RATE_NAV_TWEAK = 1 << 3;

    /** Internal: stop repeating when released (default for everything except shortcut()). */
    public static final int INTERNAL_REPEAT_UNTIL_RELEASE = 1 << 4;

    /** Internal: stop repeating when released or when key mods change (default for shortcut()). */
    public static final int INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE = 1 << 5;

    /** Internal: stop repeating when released or when key mods leave the none state. */
    public static final int INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE_FROM_NONE = 1 << 6;

    /** Internal: stop repeating when released or when any other key is pressed. */
    public static final int INTERNAL_REPEAT_UNTIL_OTHER_KEY_PRESS = 1 << 7;

    /**
     * Internal, for setKeyOwner(): further accesses to the key require an explicit owner ID (any
     * owner is not accepted). Cleared at the end of the frame.
     */
    public static final int INTERNAL_LOCK_THIS_FRAME = 1 << 20;

    /**
     * Internal, for setKeyOwner(): further accesses to the key require an explicit owner ID (any
     * owner is not accepted). Cleared when the key is released.
     */
    public static final int INTERNAL_LOCK_UNTIL_RELEASE = 1 << 21;

    /** Internal, for setItemKeyOwner(): only set if the item is hovered. */
    public static final int INTERNAL_COND_HOVERED = 1 << 22;

    /** Internal, for setItemKeyOwner(): only set if the item is active. */
    public static final int INTERNAL_COND_ACTIVE = 1 << 23;

    public static final int INTERNAL_COND_DEFAULT = INTERNAL_COND_HOVERED | INTERNAL_COND_ACTIVE;

    public static final int INTERNAL_REPEAT_RATE_MASK =
            INTERNAL_REPEAT_RATE_DEFAULT
                    | INTERNAL_REPEAT_RATE_NAV_MOVE
                    | INTERNAL_REPEAT_RATE_NAV_TWEAK;
    public static final int INTERNAL_REPEAT_UNTIL_MASK =
            INTERNAL_REPEAT_UNTIL_RELEASE
                    | INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE
                    | INTERNAL_REPEAT_UNTIL_KEY_MODS_CHANGE_FROM_NONE
                    | INTERNAL_REPEAT_UNTIL_OTHER_KEY_PRESS;
    public static final int INTERNAL_REPEAT_MASK =
            REPEAT | INTERNAL_REPEAT_RATE_MASK | INTERNAL_REPEAT_UNTIL_MASK;
    public static final int INTERNAL_COND_MASK = INTERNAL_COND_HOVERED | INTERNAL_COND_ACTIVE;
    public static final int INTERNAL_ROUTE_TYPE_MASK =
            ROUTE_ACTIVE | ROUTE_FOCUSED | ROUTE_GLOBAL | ROUTE_ALWAYS;
    public static final int INTERNAL_ROUTE_OPTIONS_MASK =
            ROUTE_OVER_FOCUSED
                    | ROUTE_OVER_ACTIVE
                    | ROUTE_UNLESS_BG_FOCUSED
                    | ROUTE_FROM_ROOT_WINDOW;
    public static final int INTERNAL_SUPPORTED_BY_IS_KEY_PRESSED = INTERNAL_REPEAT_MASK;
    public static final int INTERNAL_SUPPORTED_BY_SHORTCUT =
            INTERNAL_REPEAT_MASK | INTERNAL_ROUTE_TYPE_MASK | INTERNAL_ROUTE_OPTIONS_MASK;
    public static final int INTERNAL_SUPPORTED_BY_SET_NEXT_ITEM_SHORTCUT =
            INTERNAL_SUPPORTED_BY_SHORTCUT | TOOLTIP;
    public static final int INTERNAL_SUPPORTED_BY_SET_KEY_OWNER =
            INTERNAL_LOCK_THIS_FRAME | INTERNAL_LOCK_UNTIL_RELEASE;
    public static final int INTERNAL_SUPPORTED_BY_SET_ITEM_KEY_OWNER =
            INTERNAL_SUPPORTED_BY_SET_KEY_OWNER | INTERNAL_COND_MASK;

    /** Private constructor so this is not instantiated. */
    private InputFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
