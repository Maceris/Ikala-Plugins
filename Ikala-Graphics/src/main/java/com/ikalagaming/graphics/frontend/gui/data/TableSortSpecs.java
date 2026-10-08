package com.ikalagaming.graphics.frontend.gui.data;

/**
 * Sorting specifications for a table, often handling sort specs for a single column, occasionally
 * more. Obtained by calling tableGetSortSpecs(). When specsDirty is true you should sort your data.
 * It will be true when the sorting specs have changed since the last call, or the first time. Make
 * sure to set specsDirty to false after sorting, otherwise you may wastefully sort your data every
 * frame. Don't hold on to this over multiple frames, or past a subsequent call to beginTable().
 */
public class TableSortSpecs {
    /**
     * The sort specs, in sort order. Most often 1. May be more with TableFlags.SORT_MULTI, or empty
     * with TableFlags.SORT_TRISTATE.
     */
    public TableColumnSortSpecs[] specs = new TableColumnSortSpecs[0];

    /** The number of sort specs, the same as specs.length. */
    public int specsCount;

    /** Set to true when the specs have changed since last time. Sort again, then clear this. */
    public boolean specsDirty;
}
