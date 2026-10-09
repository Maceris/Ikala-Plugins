package com.ikalagaming.graphics.gui.data;

/** Storage for getTypingSelectRequest(). */
public class TypingSelectState {
    /** The maximum number of chars in the search buffer, since the search is very transient. */
    public static final int MAX_SEARCH_LENGTH = 63;

    /** The user facing request data. */
    public final TypingSelectRequest request;

    /** The text typed so far. */
    public final StringBuilder searchBuffer;

    /** The focus scope that the search was typed in. */
    public int focusScope;

    /** The frame of the last request. */
    public int lastRequestFrame;

    /** The time of the last request, in milliseconds. */
    public long lastRequestTime;

    /**
     * After the same character is repeated enough times, we lock into single char mode. This means
     * the buffer never fills, and the mode applies immediately without waiting for the timer.
     */
    public boolean singleCharModeLock;

    /** Create an empty state. */
    public TypingSelectState() {
        request = new TypingSelectRequest();
        searchBuffer = new StringBuilder(MAX_SEARCH_LENGTH);
        focusScope = 0;
        lastRequestFrame = 0;
        lastRequestTime = 0;
        singleCharModeLock = false;
    }

    /** Clear the search. The remaining data is kept for easier debugging. */
    public void clear() {
        searchBuffer.setLength(0);
        singleCharModeLock = false;
    }
}
