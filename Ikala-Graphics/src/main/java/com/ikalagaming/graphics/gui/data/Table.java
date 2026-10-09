package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.util.RectFloat;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Table data that persists across frames. */
public class Table {
    public int id;

    /**
     * @see com.ikalagaming.graphics.gui.flags.TableFlags
     */
    public int flags;

    /** Transient data while the table is active, an entry in the context's tablesTempData. */
    public TableTempData tempData;

    /** The columns, by index. */
    public TableColumn[] columns;

    /**
     * The display order of columns, mapping display order to column index. When not reordered, the
     * values are 0 to count - 1.
     */
    public int[] displayOrderToIndex;

    /** Cell background requests for the current row. */
    public TableCellData[] rowCellData;

    /** Column display order to whether it is enabled. */
    public final BitSet enabledMaskByDisplayOrder;

    /** Column index to whether it is enabled (not hidden by the user or api). */
    public final BitSet enabledMaskByIndex;

    /**
     * Column index to whether it is visible (enabled, and not hidden by scrolling or clip rect).
     */
    public final BitSet visibleMaskByIndex;

    /**
     * Which data was loaded from the .ini file.
     *
     * @see com.ikalagaming.graphics.gui.flags.TableFlags
     */
    public int settingsLoadedFlags;

    /** The settings bound to this table, or null if there are none. */
    public TableSettings settings;

    public int lastFrameActive;

    /** Number of columns declared in beginTable(). */
    public int columnsCount;

    public int currentRow;
    public int currentColumn;

    /**
     * Count of beginTable() calls with the same ID in the same frame (generally 0). Multiple tables
     * with the same ID are separate tables, they are just synced.
     */
    public int instanceCurrent;

    /** Which instance (generally 0) of the same ID is being interacted with. */
    public int instanceInteracted;

    public float rowPosY1;
    public float rowPosY2;

    /** Height submitted to tableNextRow(). */
    public float rowMinHeight;

    /** Top and bottom padding. Reloaded during row change. */
    public float rowCellPaddingY;

    public float rowTextBaseline;
    public float rowIndentOffsetX;

    /**
     * The current row flags.
     *
     * @see com.ikalagaming.graphics.gui.flags.TableRowFlags
     */
    public int rowFlags;

    /**
     * The previous row flags.
     *
     * @see com.ikalagaming.graphics.gui.flags.TableRowFlags
     */
    public int lastRowFlags;

    /**
     * Counter for alternating background colors (can be fast-forwarded by the clipper), not the
     * same as currentRow because header rows typically don't increase this.
     */
    public int rowBackgroundColorCounter;

    /** Background color overrides for the current row. */
    public final int[] rowBackgroundColor;

    public int borderColorStrong;
    public int borderColorLight;
    public float borderX1;
    public float borderX2;
    public float hostIndentX;
    public float minColumnWidth;
    public float outerPaddingX;

    /** Padding from each border. Locked in beginTable()/layout. */
    public float cellPaddingX;

    /** Spacing between non-bordered cells. Locked in beginTable()/layout. */
    public float cellSpacingX1;

    public float cellSpacingX2;

    /** The value passed to beginTable(). */
    public float innerWidth;

    /** Sum of current column widths. */
    public float columnsGivenWidth;

    /** Sum of ideal column widths so nothing is clipped, used for auto-fitting. */
    public float columnsAutoFitWidth;

    /** Sum of weights of all enabled stretching columns. */
    public float columnsStretchSumWeights;

    public float resizedColumnNextWidth;

    /**
     * Lock the minimum contents width while resizing down, in order to not create feedback loops.
     */
    public float resizeLockMinContentsX2;

    /** Reference scale to be able to rescale columns on font/dpi changes. */
    public float refScale;

    /** Set by tableAngledHeadersRow(), used in tableUpdateLayout(). */
    public float angledHeadersHeight;

    /** Set by tableAngledHeadersRow(), used in tableUpdateLayout(). */
    public float angledHeadersSlope;

    /**
     * For non-scrolling tables, outerRect bottom is often Float.MAX_VALUE until endTable(), unless
     * a height has been specified in beginTable().
     */
    public final RectFloat outerRect;

    /** The inner rect, without decoration. */
    public final RectFloat innerRect;

    public final RectFloat workRect;
    public final RectFloat innerClipRect;

    /** Used to clip cell background fills, which changes as we cross frozen rows. */
    public final RectFloat backgroundClipRect;

    /** The clip rect for the background 0/1 channel. */
    public final RectFloat background0ClipRectForDrawCommand;

    /** The clip rect for the background 2 channel. */
    public final RectFloat background2ClipRectForDrawCommand;

    /** The clip rect of the host window. */
    public final RectFloat hostClipRect;

    /** Backup of the inner window clip rect during push/pop of the background channel. */
    public final RectFloat hostBackupInnerClipRect;

    /** Parent window for the table. */
    public Window outerWindow;

    /** Window holding the table data, the outer window or a child window. */
    public Window innerWindow;

    /** Shortcut to tempData.drawSplitter while in the table. */
    public DrawListSplitter drawSplitter;

    public final TableInstanceData instanceDataFirst;
    public final List<TableInstanceData> instanceDataExtra;

    /** Public facing sort specs, returned by tableGetSortSpecs(). */
    public final TableSortSpecs sortSpecs;

    public int sortSpecsCount;

    /** Number of enabled columns, at most columnsCount. */
    public int columnsEnabledCount;

    /** Number of enabled columns using fixed widths, at most columnsCount. */
    public int columnsEnabledFixedCount;

    /** Count of calls to tableSetupColumn(). */
    public int declColumnsCount;

    /** Count of columns with angled headers. */
    public int angledHeadersCount;

    /**
     * Index of the column whose visible region is being hovered. Equal to columnsCount when
     * hovering the empty region after the right-most column.
     */
    public int hoveredColumnBody;

    /** Index of the column whose right border is being hovered (for resizing). */
    public int hoveredColumnBorder;

    /** Index of the column which should be highlighted. */
    public int highlightColumnHeader;

    /** Index of the single column requesting auto-fit. */
    public int autoFitSingleColumn;

    /** Index of the column being resized. Reset when instanceCurrent is 0. */
    public int resizedColumn;

    /** Index of the column being resized from the previous frame. */
    public int lastResizedColumn;

    /** Index of the column header being held. */
    public int heldHeaderColumn;

    /** Index of the column header being held from the previous frame. */
    public int lastHeldHeaderColumn;

    /** Index of the column being reordered (not cleared). */
    public int reorderColumn;

    /** Requested display order of the column being reordered. */
    public int reorderColumnDstOrder;

    public int leftMostEnabledColumn;
    public int rightMostEnabledColumn;
    public int leftMostStretchedColumn;
    public int rightMostStretchedColumn;

    /** Column right-clicked on, or -1 if opening the context menu from a neutral spot. */
    public int contextPopupColumn;

    public int freezeRowsRequest;

    /** Actual frozen row count (freezeRowsRequest, or 0 when there is no scrolling offset). */
    public int freezeRowsCount;

    public int freezeColumnsRequest;

    /** Actual frozen column count (freezeColumnsRequest, or 0 when there is no scrolling). */
    public int freezeColumnsCount;

    /** Index of the current rowCellData entry in the current row. */
    public int rowCellDataCurrent;

    /** Non-visible columns are redirected to this draw channel. */
    public int dummyDrawChannel;

    /** For selectable() and other widgets drawing across columns after the freezing line. */
    public int background2DrawChannelCurrent;

    public int background2DrawChannelUnfrozen;

    /** The navigation layer at the time of beginTable(). */
    public int navLayer;

    /** Set by tableUpdateLayout(), which is called when beginning the first row. */
    public boolean isLayoutLocked;

    /** Set when inside tableBeginRow()/tableEndRow(). */
    public boolean isInsideRow;

    public boolean isNewTable;
    public boolean isInitializing;
    public boolean isReconcileMode;
    public boolean isSortSpecsDirty;

    /** Set when the first row had the TableRowFlags.HEADERS flag. */
    public boolean isUsingHeaders;

    /** Set when the default context menu is open. */
    public boolean isContextPopupOpen;

    /**
     * Disable the default context menu. You may submit your own using
     * tableBeginContextMenuPopup()/endPopup().
     */
    public boolean disableDefaultContextMenu;

    public boolean isSettingsRequestLoad;

    /** Set when table settings have changed and need to be reported into the settings data. */
    public boolean isSettingsDirty;

    /** Set when the display order is unchanged from the default. */
    public boolean isDefaultDisplayOrder;

    /** Set when visibility is unchanged from the default. */
    public boolean isDefaultVisibility;

    /** Set to queue a call to tableResetSettings() in beginTable(). */
    public boolean isResetAllRequest;

    public boolean isResetDisplayOrderRequest;
    public boolean isResetVisibilityRequest;

    /** Set when we got past the frozen rows. */
    public boolean isUnfrozenRows;

    /** Set if the user didn't explicitly set a sizing policy in beginTable(). */
    public boolean isDefaultSizingPolicy;

    public boolean isActiveIDAliveBeforeTable;
    public boolean isActiveIDInTable;

    /** Whether any instance of this table had a vertical scrollbar during the current frame. */
    public boolean hasScrollbarYCurrent;

    /** Whether any instance of this table had a vertical scrollbar during the previous frame. */
    public boolean hasScrollbarYPrevious;

    /**
     * Backup of the inner window skipItems at the end of beginTable(), because we overwrite it per
     * column.
     */
    public boolean hostSkipItems;

    public Table() {
        columns = new TableColumn[0];
        displayOrderToIndex = new int[0];
        rowCellData = new TableCellData[0];
        enabledMaskByDisplayOrder = new BitSet();
        enabledMaskByIndex = new BitSet();
        visibleMaskByIndex = new BitSet();
        lastFrameActive = -1;
        rowBackgroundColor = new int[2];
        outerRect = new RectFloat(0, 0, 0, 0);
        innerRect = new RectFloat(0, 0, 0, 0);
        workRect = new RectFloat(0, 0, 0, 0);
        innerClipRect = new RectFloat(0, 0, 0, 0);
        backgroundClipRect = new RectFloat(0, 0, 0, 0);
        background0ClipRectForDrawCommand = new RectFloat(0, 0, 0, 0);
        background2ClipRectForDrawCommand = new RectFloat(0, 0, 0, 0);
        hostClipRect = new RectFloat(0, 0, 0, 0);
        hostBackupInnerClipRect = new RectFloat(0, 0, 0, 0);
        instanceDataFirst = new TableInstanceData();
        instanceDataExtra = new ArrayList<>();
        sortSpecs = new TableSortSpecs();
        resizedColumnNextWidth = Float.MAX_VALUE;
    }

    /**
     * Fetch the data for an instance of this table.
     *
     * @param instanceNumber The instance number, 0 for the first instance.
     * @return The instance data.
     */
    public TableInstanceData getInstanceData(int instanceNumber) {
        if (instanceNumber == 0) {
            return instanceDataFirst;
        }
        return instanceDataExtra.get(instanceNumber - 1);
    }

    /**
     * Fetch the ID for an instance of this table.
     *
     * @param instanceNumber The instance number, 0 for the first instance.
     * @return The instance ID.
     */
    public int getInstanceID(int instanceNumber) {
        return getInstanceData(instanceNumber).tableInstanceID;
    }
}
