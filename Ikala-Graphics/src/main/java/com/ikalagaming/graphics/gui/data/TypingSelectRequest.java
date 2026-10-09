package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.TypingSelectFlags;

/** A typing-select search request, returned by getTypingSelectRequest(). */
public class TypingSelectRequest {
    /**
     * The flags passed to getTypingSelectRequest().
     *
     * @see TypingSelectFlags
     */
    public int flags;

    /**
     * The text typed so far. Use the full string, unless {@link #singleCharMode} is set, in which
     * case only the first {@link #singleCharSize} chars matter.
     */
    public String searchBuffer;

    /** Set when the buffer was modified this frame, requesting a selection. */
    public boolean selectRequest;

    /**
     * Set when the buffer contains the same character repeated, to implement a special mode where
     * each press goes to the next item starting with that character. It's preferable not to show an
     * on-screen search indicator in this mode.
     */
    public boolean singleCharMode;

    /**
     * The length of the first code point in chars, 1 for most characters and 2 for surrogate pairs.
     * If the search buffer has the same length, only one letter has been typed.
     */
    public int singleCharSize;

    /** Create an empty request. */
    public TypingSelectRequest() {
        flags = TypingSelectFlags.NONE;
        searchBuffer = "";
        selectRequest = false;
        singleCharMode = false;
        singleCharSize = 0;
    }
}
