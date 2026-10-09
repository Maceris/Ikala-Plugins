package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.LayoutType;
import com.ikalagaming.graphics.gui.util.RectFloat;

import org.joml.Vector2f;

/** Storage for beginComboPreview() and endComboPreview(). */
public class ComboPreviewData {
    /** Where the preview of the last combo goes, empty if it had no preview. */
    public final RectFloat previewRect;

    /** The window cursor position from before the preview. */
    public final Vector2f backupCursorPos;

    /** The maximum window cursor position from before the preview. */
    public final Vector2f backupCursorMaxPos;

    /** The window's previous line cursor position from before the preview. */
    public final Vector2f backupCursorPosPreviousLine;

    /** The window's previous line text baseline offset from before the preview. */
    public float backupPreviousLineTextBaseOffset;

    /** The right edge of the window's work rect from before the preview. */
    public float backupWorkRectMaxX;

    /** The right edge of the window's content region from before the preview. */
    public float backupContentRectMaxX;

    /** The window layout type from before the preview. */
    public LayoutType backupLayout;

    /** Whether we are between beginComboPreview() and endComboPreview(). */
    public boolean withinPreview;

    public ComboPreviewData() {
        this.previewRect = new RectFloat(0, 0, 0, 0);
        this.backupCursorPos = new Vector2f(0, 0);
        this.backupCursorMaxPos = new Vector2f(0, 0);
        this.backupCursorPosPreviousLine = new Vector2f(0, 0);
        this.backupPreviousLineTextBaseOffset = 0;
        this.backupWorkRectMaxX = 0;
        this.backupContentRectMaxX = 0;
        this.backupLayout = LayoutType.HORIZONTAL;
        this.withinPreview = false;
    }
}
