package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;

import lombok.NonNull;

/** Settings for one table column, saved to the .ini file. */
public class TableColumnSettings {
    public float widthOrWeight;
    public int id;
    public int index;
    public int displayOrder;
    public int sortOrder;
    public @NonNull SortDirection sortDirection;

    /** 1 if enabled, 0 if hidden, -1 if not specified. Stored as "Visible" in the .ini file. */
    public int isEnabled;

    public boolean isStretch;

    /** Used during loading to mark finding a matching column. */
    public boolean isLoaded;

    public TableColumnSettings() {
        sortDirection = SortDirection.NONE;
        reset();
    }

    /** Reset to default values. */
    public void reset() {
        widthOrWeight = 0;
        id = 0;
        index = -1;
        displayOrder = -1;
        sortOrder = -1;
        sortDirection = SortDirection.NONE;
        isEnabled = -1;
        isStretch = false;
        isLoaded = false;
    }
}
