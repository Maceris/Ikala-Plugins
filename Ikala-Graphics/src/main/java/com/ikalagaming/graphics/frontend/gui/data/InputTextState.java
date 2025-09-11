package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;

public class InputTextState {
    private Context context;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags
     */
    private int inputTextFlags;

    private int ID;
    private int textLength;
    private StringBuilder text;
    private StringBuilder textToRevertTo;
    private StringBuilder callbackTextBackup;
    private Vector2f scroll;
    private float cursorTimer;
    private boolean cursorFollow;
    private boolean selectAllMouseLock;
    private boolean edited;
    private int reloadSelectionBegin;
    private int reloadSelectionEnd;
}
