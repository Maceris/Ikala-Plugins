package com.ikalagaming.graphics.ui;

/** Where children sit across a row or column, such as vertically within a row. */
public enum Align {
    /** At the top of a row, or the left of a column. */
    START,
    /** In the middle. */
    CENTER,
    /** At the bottom of a row, or the right of a column. */
    END,
    /** Children that aren't a fixed size fill the row's height or the column's width. */
    STRETCH
}
