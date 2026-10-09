package com.ikalagaming.graphics.gui.data;

/** Per-instance table data that needs preserving across frames. */
public class TableInstanceData {
    public int tableInstanceID;

    /** Outer height from last frame. */
    public float lastOuterHeight;

    /** Height of the first consecutive header rows from last frame. */
    public float lastTopHeadersRowHeight;

    /** Height of the frozen section from last frame. */
    public float lastFrozenHeight;

    /** Index of the row which was hovered last frame. */
    public int hoveredRowLast;

    /** Index of the row hovered this frame, set after encountering it. */
    public int hoveredRowNext;

    public TableInstanceData() {
        tableInstanceID = 0;
        lastOuterHeight = 0;
        lastTopHeadersRowHeight = 0;
        lastFrozenHeight = 0;
        hoveredRowLast = -1;
        hoveredRowNext = -1;
    }
}
