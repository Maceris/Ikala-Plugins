package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.util.RectFloat;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Transient table data that is only needed between beginTable() and endTable(). These are shared,
 * with one per level of nested tables.
 */
public class TableTempData {
    /** Shortcut to the inner window ID of the table. */
    public int windowID;

    /** Index of the table in the context table list. */
    public int tableIndex;

    /** The last time this was used, in milliseconds, or -1. */
    public long lastTimeActive;

    /** Used in endTable(). */
    public float angledHeadersExtraWidth;

    /** Used in tableAngledHeadersRow(). */
    public final List<TableHeaderData> angledHeadersRequests;

    /** Used in tableSetupColumn(), tableUpdateLayout(). Cleared every frame. */
    public final List<TableReconcileColumnData> reconcileColumnsRequests;

    /** The columns from before a column count change, used in beginTable() and layout. */
    public TableColumn[] oldColumnsData;

    /** The outer size passed to beginTable(). */
    public final Vector2f userOuterSize;

    public final DrawListSplitter drawSplitter;

    /** Backup of the inner window work rect at the end of beginTable(). */
    public final RectFloat hostBackupWorkRect;

    /** Backup of the inner window parent work rect at the end of beginTable(). */
    public final RectFloat hostBackupParentWorkRect;

    /** Backup of the inner window previous line size at the end of beginTable(). */
    public final Vector2f hostBackupPrevLineSize;

    /** Backup of the inner window current line size at the end of beginTable(). */
    public final Vector2f hostBackupCurrLineSize;

    /** Backup of the inner window cursor max position at the end of beginTable(). */
    public final Vector2f hostBackupCursorMaxPos;

    /** Backup of the outer window item width at the end of beginTable(). */
    public float hostBackupItemWidth;

    /** Backup of the outer window's columns offset at the end of beginTable(). */
    public float hostBackupColumnsOffset;

    /** Backup of the outer window item width stack size at the end of beginTable(). */
    public int hostBackupItemWidthStackSize;

    public TableTempData() {
        windowID = 0;
        tableIndex = 0;
        lastTimeActive = -1;
        angledHeadersExtraWidth = 0;
        angledHeadersRequests = new ArrayList<>();
        reconcileColumnsRequests = new ArrayList<>();
        oldColumnsData = null;
        userOuterSize = new Vector2f();
        drawSplitter = new DrawListSplitter();
        hostBackupWorkRect = new RectFloat(0, 0, 0, 0);
        hostBackupParentWorkRect = new RectFloat(0, 0, 0, 0);
        hostBackupPrevLineSize = new Vector2f();
        hostBackupCurrLineSize = new Vector2f();
        hostBackupCursorMaxPos = new Vector2f();
        hostBackupItemWidth = 0;
        hostBackupColumnsOffset = 0;
        hostBackupItemWidthStackSize = 0;
    }
}
