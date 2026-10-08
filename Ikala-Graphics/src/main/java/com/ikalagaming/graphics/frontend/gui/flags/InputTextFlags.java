package com.ikalagaming.graphics.frontend.gui.flags;

public class InputTextFlags {
    public static final int NONE = 0;

    // Basic filters (also see CALLBACK_CHAR_FILTER)

    /** Allow 0123456789.+-*&#47; */
    public static final int CHARS_DECIMAL = 1;

    /** Allow 0123456789ABCDEFabcdef. */
    public static final int CHARS_HEXADECIMAL = 1 << 1;

    /** Allow 0123456789.+-*&#47;eE (scientific notation input). */
    public static final int CHARS_SCIENTIFIC = 1 << 2;

    /** Turn a..z into A..Z. */
    public static final int CHARS_UPPERCASE = 1 << 3;

    /** Filter out spaces and tabs. */
    public static final int CHARS_NO_BLANK = 1 << 4;

    // Inputs

    /** Pressing TAB inputs a '\t' character into the text field. */
    public static final int ALLOW_TAB_INPUT = 1 << 5;

    /**
     * Return true when Enter is pressed (as opposed to every time the value was modified). Consider
     * using isItemDeactivatedAfterEdit() instead.
     */
    public static final int ENTER_RETURNS_TRUE = 1 << 6;

    /**
     * Escape key clears content if not empty, and deactivates otherwise (contrast to the default
     * behavior of Escape to revert).
     */
    public static final int ESCAPE_CLEARS_ALL = 1 << 7;

    /**
     * In multi-line mode: validate with Enter, add new line with Ctrl+Enter (default is the
     * opposite). Shift+Enter always enters a new line either way.
     */
    public static final int CTRL_ENTER_FOR_NEW_LINE = 1 << 8;

    // Other options

    /** Read-only mode. */
    public static final int READ_ONLY = 1 << 9;

    /** Password mode, display all characters as '*', disable copy. */
    public static final int PASSWORD = 1 << 10;

    /** Overwrite mode. */
    public static final int ALWAYS_OVERWRITE = 1 << 11;

    /** Select the entire text when first taking mouse focus. */
    public static final int AUTO_SELECT_ALL = 1 << 12;

    /** inputFloat(), inputInt(), inputScalar() etc. only: parse an empty string as zero. */
    public static final int PARSE_EMPTY_REF_VAL = 1 << 13;

    /**
     * inputFloat(), inputInt(), inputScalar() etc. only: when the value is zero, do not display it.
     * Generally used with PARSE_EMPTY_REF_VAL.
     */
    public static final int DISPLAY_EMPTY_REF_VAL = 1 << 14;

    /** Disable following the cursor horizontally. */
    public static final int NO_HORIZONTAL_SCROLL = 1 << 15;

    /** Disable undo/redo. */
    public static final int NO_UNDO_REDO = 1 << 16;

    // Elide display / alignment

    /**
     * When text doesn't fit, elide the left side to ensure the right side stays visible. Useful for
     * paths/filenames. Single-line only!
     */
    public static final int ELIDE_LEFT = 1 << 17;

    // Callback features

    /** Callback on pressing TAB (for completion handling). */
    public static final int CALLBACK_COMPLETION = 1 << 18;

    /** Callback on pressing Up/Down arrows (for history handling). */
    public static final int CALLBACK_HISTORY = 1 << 19;

    /**
     * Callback on each iteration. User code may query the cursor position, modify the text buffer.
     */
    public static final int CALLBACK_ALWAYS = 1 << 20;

    /**
     * Callback on character inputs to replace or discard them. Modify the event char to replace or
     * discard (set it to 0).
     */
    public static final int CALLBACK_CHAR_FILTER = 1 << 21;

    /**
     * Callback when the text grows beyond the capacity of the string. The string is resized to fit
     * automatically, this just notifies the callback.
     */
    public static final int CALLBACK_RESIZE = 1 << 22;

    /** Callback on any edit. */
    public static final int CALLBACK_EDIT = 1 << 23;

    // Multi-line word wrapping

    /** inputTextMultiline(): word-wrap lines that are too long. */
    public static final int WORD_WRAP = 1 << 24;

    /** Internal: for use by inputTextMultiline(). */
    public static final int INTERNAL_MULTILINE = 1 << 26;

    /**
     * Internal: for use by tempInputText(), will skip calling itemAdd(). Requires the bounding box
     * to strictly match.
     */
    public static final int INTERNAL_TEMP_INPUT = 1 << 27;

    /** Internal: for use by inputScalar() and tempInputScalar(). */
    public static final int INTERNAL_LOCALIZE_DECIMAL_POINT = 1 << 28;

    /** Private constructor so this is not instantiated. */
    private InputTextFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
