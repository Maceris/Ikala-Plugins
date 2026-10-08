package com.ikalagaming.graphics.frontend.gui.data;

/**
 * Data passed to tableSetupColumn() when the column topology changed, used to match old columns to
 * new ones.
 */
public class TableReconcileColumnData {
    // Setup data
    public int id;
    public String name;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags
     */
    public int flags;

    public float initWidthOrWeight;
    public int userData;

    // Reconcile data
    /** Index in the current table. */
    public int columnNewIndex;

    /** Index in the previous frame table. */
    public int columnOldIndex;

    /** Full backup of the old column, or a default column if none was found. */
    public final TableColumn columnOldData;

    public TableReconcileColumnData() {
        columnNewIndex = -1;
        columnOldIndex = -1;
        columnOldData = new TableColumn();
    }
}
