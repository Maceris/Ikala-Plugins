package com.ikalagaming.graphics.gui.flags;

/** Flags for item activation through navigation, tabbing, shortcuts, or the focus API. */
public class ActivateFlags {
    public static final int NONE = 0;

    /**
     * Favor activation that requires keyboard text input (e.g. for slider/drag). Default for Enter
     * key.
     */
    public static final int PREFER_INPUT = 1;

    /**
     * Favor activation for tweaking with arrows or gamepad (e.g. for slider/drag). Default for
     * Space key and if keyboard is not used.
     */
    public static final int PREFER_TWEAK = 1 << 1;

    /**
     * Request the widget to preserve state if it can (e.g. input text will try to preserve the
     * cursor/selection).
     */
    public static final int TRY_TO_PRESERVE_STATE = 1 << 2;

    /** Activation requested by a tabbing request. */
    public static final int FROM_TABBING = 1 << 3;

    /** Activation requested by an item shortcut via setNextItemShortcut(). */
    public static final int FROM_SHORTCUT = 1 << 4;

    /** Activation requested by an API request (activateItemByID, setKeyboardFocusHere). */
    public static final int FROM_FOCUS_API = 1 << 5;

    /** Private constructor so this is not instantiated. */
    private ActivateFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
