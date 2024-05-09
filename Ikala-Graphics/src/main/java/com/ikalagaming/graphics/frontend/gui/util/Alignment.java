package com.ikalagaming.graphics.frontend.gui.util;

/**
 * The area of a parent window or screen that a window should call the origin with respect to
 * displacement. Centered alignments ignore displacement in whatever axis/axes they are centered on.
 *
 * @author Ches Burks
 */
public enum Alignment {
    /** Aligned to the north of the parent, centered horizontally. */
    NORTH,
    /** Aligned to the east of the parent, centered vertically. */
    EAST,
    /** Aligned to the south of the parent, centered horizontally. */
    SOUTH,
    /** Aligned to the west of the parent, centered vertically. */
    WEST,
    /** Aligned to the north-east corner of the parent. */
    NORTH_EAST,
    /** Aligned to the north-west corner of the parent. */
    NORTH_WEST,
    /** Aligned to the south-east corner of the parent. */
    SOUTH_EAST,
    /** Aligned to the south-west corner of the parent. */
    SOUTH_WEST,
    /** Aligned in the center of the parent. */
    CENTER;
}
