package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;

/** Backup of layout state when beginning a group, restored when the group ends. */
public class GroupData {
    public int windowID;
    public final Vector2f backupCursorPos;
    public final Vector2f backupCursorMaxPos;
    public final Vector2f backupCursorPosPreviousLine;
    public float backupIndent;
    public float backupGroupOffset;
    public final Vector2f backupCurrentLineSize;
    public float backupCurrentLineTextBaseOffset;
    public int backupActiveIDIsAlive;
    public boolean backupDeactivatedIDIsAlive;
    public boolean backupHoveredIDIsAlive;
    public boolean backupIsSameLine;
    public boolean backupAnyIDHasBeenEditedThisFrame;
    public boolean emitItem;

    public GroupData() {
        windowID = 0;
        backupCursorPos = new Vector2f();
        backupCursorMaxPos = new Vector2f();
        backupCursorPosPreviousLine = new Vector2f();
        backupIndent = 0;
        backupGroupOffset = 0;
        backupCurrentLineSize = new Vector2f();
        backupCurrentLineTextBaseOffset = 0;
        backupActiveIDIsAlive = 0;
        backupDeactivatedIDIsAlive = false;
        backupHoveredIDIsAlive = false;
        backupIsSameLine = false;
        backupAnyIDHasBeenEditedThisFrame = false;
        emitItem = true;
    }
}
