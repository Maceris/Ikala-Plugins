package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.LayoutType;
import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2f;

public class ComboPreviewData {
    private Rect previewRect;
    private Vector2f backupCursorPos;
    private Vector2f backupCursorMaxPos;
    private Vector2f backupCursorPosPreviousLine;
    private float backupPreviousLineTextBaseOffset;
    private LayoutType backupLayout;

    public ComboPreviewData() {
        this.previewRect = new Rect(0, 0, 0, 0);
        this.backupCursorPos = new Vector2f(0, 0);
        this.backupCursorMaxPos = new Vector2f(0, 0);
        this.backupCursorPosPreviousLine = new Vector2f(0, 0);
        this.backupPreviousLineTextBaseOffset = 0;
        this.backupLayout = LayoutType.HORIZONTAL;
    }
}
