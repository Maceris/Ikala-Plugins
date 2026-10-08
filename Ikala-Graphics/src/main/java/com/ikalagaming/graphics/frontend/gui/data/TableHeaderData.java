package com.ikalagaming.graphics.frontend.gui.data;

/** Parameters for an angled header, see tableAngledHeadersRowEx(). */
public class TableHeaderData {
    /** The column index. */
    public int index;

    /** The text color. */
    public int textColor;

    /** The background color. */
    public int backgroundColor0;

    /** An optional highlight color drawn over the background, 0 for none. */
    public int backgroundColor1;

    public TableHeaderData() {}

    /**
     * Create header data.
     *
     * @param index The column index.
     * @param textColor The text color.
     * @param backgroundColor0 The background color.
     * @param backgroundColor1 An optional highlight color drawn over the background, 0 for none.
     */
    public TableHeaderData(int index, int textColor, int backgroundColor0, int backgroundColor1) {
        this.index = index;
        this.textColor = textColor;
        this.backgroundColor0 = backgroundColor0;
        this.backgroundColor1 = backgroundColor1;
    }
}
