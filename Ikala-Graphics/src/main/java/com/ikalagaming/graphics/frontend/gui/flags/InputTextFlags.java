package com.ikalagaming.graphics.frontend.gui.flags;

public class InputTextFlags {
    public static final int NONE = 0;
    public static final int CHARS_DECIMAL = 1;
    public static final int CHARS_HEXADECIMAL = 1 << 1;
    public static final int CHARS_UPPERCASE = 1 << 2;
    public static final int CHARS_NO_BLANK = 1 << 3;
    public static final int AUTO_SELECT_ALL = 1 << 4;
    public static final int ENTER_RETURNS_TRUE = 1 << 5;
    public static final int CALLBACK_COMPLETION = 1 << 6;
    public static final int CALLBACK_HISTORY = 1 << 7;
    public static final int CALLBACK_ALWAYS = 1 << 8;
    public static final int CALLBACK_CHAR_FILTER = 1 << 9;
    public static final int ALLOW_TAB_INPUT = 1 << 10;
    public static final int CTRL_ENTER_FOR_NEWLINE = 1 << 11;
    public static final int NO_HORIZONTAL_SCROLL = 1 << 12;
    public static final int ALWAYS_OVERWRITE = 1 << 13;
    public static final int READ_ONLY = 1 << 14;
    public static final int PASSWORD = 1 << 15;
    public static final int NO_UNDO_REDO = 1 << 16;
    public static final int CHARS_SCIENTIFIC = 1 << 17;
    public static final int CALLBACK_RESIZE = 1 << 18;
    public static final int CALLBACK_EDIT = 1 << 19;
}
