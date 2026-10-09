package com.ikalagaming.graphics.ui;

/** How leftover space along a row or column is used, such as horizontally within a row. */
public enum Justify {
    /** Children are packed at the left of a row, or the top of a column. */
    START,
    /** Children are packed in the middle. */
    CENTER,
    /** Children are packed at the right of a row, or the bottom of a column. */
    END,
    /** The first and last children are at the ends, with the leftover space between children. */
    SPACE_BETWEEN
}
