package com.ikalagaming.graphics.gui.data;

/** A range of items for the list clipper. */
public class ListClipperRange {
    /** First position, inclusive. */
    public int min;

    /** Last position, exclusive. */
    public int max;

    /** True if min/max are absolute positions that still need converting to indices. */
    public boolean positionToIndexConvert;

    /** Add to min after converting to indices. */
    public int positionToIndexOffsetMin;

    /** Add to max after converting to indices. */
    public int positionToIndexOffsetMax;

    /**
     * Create a range from item indices.
     *
     * @param min The first index, inclusive.
     * @param max The last index, exclusive.
     * @return The range.
     */
    public static ListClipperRange fromIndices(int min, int max) {
        final ListClipperRange range = new ListClipperRange();
        range.min = min;
        range.max = max;
        return range;
    }

    /**
     * Create a range from y positions, which will be converted to indices later.
     *
     * @param y1 The top of the range.
     * @param y2 The bottom of the range.
     * @param offsetMin Added to the min index after converting.
     * @param offsetMax Added to the max index after converting.
     * @return The range.
     */
    public static ListClipperRange fromPositions(float y1, float y2, int offsetMin, int offsetMax) {
        final ListClipperRange range = new ListClipperRange();
        range.min = (int) y1;
        range.max = (int) y2;
        range.positionToIndexConvert = true;
        range.positionToIndexOffsetMin = offsetMin;
        range.positionToIndexOffsetMax = offsetMax;
        return range;
    }
}
