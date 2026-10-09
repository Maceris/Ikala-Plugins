package com.ikalagaming.graphics.gui.data;

/** Table settings, saved to and loaded from the .ini file. */
public class TableSettings {
    /** The table ID, set to 0 to invalidate/delete the settings. */
    public int id;

    /**
     * Which data we want to save, using the RESIZABLE/REORDERABLE/SORTABLE/HIDEABLE table flags.
     *
     * @see com.ikalagaming.graphics.gui.flags.TableFlags
     */
    public int saveFlags;

    /** Reference scale to be able to rescale columns on font/dpi changes. */
    public float refScale;

    public int columnsCount;

    /** The number of columns this settings instance can store. */
    public int columnsCountMax;

    /** The last used date as YYYYMMDD, or 0 if unknown. */
    public int lastUsedDate;

    /** Set when loaded from .ini data, to enable merging .ini data into a running context. */
    public boolean wantApply;

    /** Settings for each column, with at least columnsCountMax entries. */
    public TableColumnSettings[] columns;

    /**
     * Create table settings.
     *
     * @param id The table ID.
     * @param columnsCount The number of columns.
     */
    public TableSettings(int id, int columnsCount) {
        columns = new TableColumnSettings[0];
        init(id, columnsCount, columnsCount);
    }

    /**
     * Clear and initialize settings.
     *
     * @param id The table ID.
     * @param columnsCount The number of columns.
     * @param columnsCountMax The maximum number of columns to store.
     */
    public void init(int id, int columnsCount, int columnsCountMax) {
        if (columns.length < columnsCountMax) {
            columns = new TableColumnSettings[columnsCountMax];
            for (int i = 0; i < columnsCountMax; ++i) {
                columns[i] = new TableColumnSettings();
            }
        } else {
            for (TableColumnSettings column : columns) {
                column.reset();
            }
        }
        this.id = id;
        saveFlags = 0;
        refScale = 0;
        this.columnsCount = columnsCount;
        this.columnsCountMax = columnsCountMax;
        lastUsedDate = 0;
        wantApply = true;
    }
}
