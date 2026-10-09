package com.ikalagaming.graphics.gui.flags;

public class KeyModFlags {
    public static final int NONE = 0;
    public static final int CTRL = 1;
    public static final int SHIFT = 1 << 1;
    public static final int ALT = 1 << 2;
    public static final int SUPER = 1 << 3;

    /** All the modifiers. */
    public static final int MASK = CTRL | SHIFT | ALT | SUPER;

    /** Private constructor so this is not instantiated. */
    private KeyModFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
