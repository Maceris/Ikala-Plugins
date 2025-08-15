package com.ikalagaming.graphics.frontend.gui.data;

public class ListClipperRange {
    /** First position, inclusive. */
    private int begin;

    /** Last position, exclusive. */
    private int end;

    /** True if we have converted to indices, false if still absolute position. */
    private boolean positionToIndexConverted;

    /** Add to min after converting to indices. */
    private IkByte positionToIndexOffsetBegin;

    /** Add to max after converting to indices. */
    private IkByte positionToIndexOffsetEnd;
}
