package com.ikalagaming.graphics.gui.data;

/** State for the ID stack tool window. */
public class IDStackTool {
    /** Encode non-ASCII characters in the path as hex escapes, so it can be copied around. */
    public final IkBoolean optHexEncodeNonAsciiChars = new IkBoolean(true);

    /** Copy the path to the clipboard when pressing Ctrl+C. */
    public final IkBoolean optCopyToClipboardOnCtrlC = new IkBoolean(false);

    /** The last frame the tool window was visible, -1 if never. */
    public int lastActiveFrame = -1;

    /** When we last copied the path to the clipboard, in milliseconds of context time. */
    public long copyToClipboardLastTime = Long.MIN_VALUE / 2;
}
