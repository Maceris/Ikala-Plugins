package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;

public class GroupData {
    private int windowID;
    private Vector2f backupCursorPos;
    private Vector2f backupCursorMaxPos;
    private Vector2f backupCursorPosPreviousLine;
    private float backupIndent;
    private float backupGroupOffset;
    private Vector2f backupCurrentLineSize;
    private float backupCurrentLineTextBaseOffset;
    private int backupActiveIDAlive;
    private boolean backupDeactivatedIDAlive;
    private boolean backupHoveredIDAlive;
    private boolean backupSameLine;
    private boolean emitItem;
}
