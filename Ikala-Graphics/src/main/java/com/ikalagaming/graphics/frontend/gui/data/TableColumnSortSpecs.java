package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;

import lombok.NonNull;

/** Sorting specification for one column of a table. */
public class TableColumnSortSpecs {
    /** User data for the column, if specified in tableSetupColumn(). */
    public int columnUserID;

    /** Index of the column. */
    public int columnIndex;

    /**
     * Index within the parent sort specs, always stored in order starting from 0. Tables sorted on
     * a single criteria will always have a 0 here.
     */
    public int sortOrder;

    /** Ascending or descending. */
    public @NonNull SortDirection sortDirection = SortDirection.NONE;
}
