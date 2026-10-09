package com.ikalagaming.graphics.gui.flags;

/**
 * Flags for getTypingSelectRequest().
 *
 * @see com.ikalagaming.graphics.gui.IkGuiInternal#getTypingSelectRequest(int)
 */
public class TypingSelectFlags {
    public static final int NONE = 0;

    /**
     * Backspace deletes character inputs. If using this, make sure getTypingSelectRequest() is not
     * called more than once per frame (filter by e.g. the focus state).
     */
    public static final int ALLOW_BACKSPACE = 1;

    /**
     * Allow the "single char" search mode, which is activated when pressing the same character
     * multiple times, and cycles through items starting with that character.
     */
    public static final int ALLOW_SINGLE_CHAR_MODE = 1 << 1;

    /** Private constructor so this is not instantiated. */
    private TypingSelectFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
