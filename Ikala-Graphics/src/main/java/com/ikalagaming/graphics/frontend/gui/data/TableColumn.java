package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;

/**
 * Per-column table data. We use the terminology "enabled" to refer to a column that is not hidden
 * by the user or api, and "clipped" to refer to a column that is out of sight because of scrolling
 * or clipping. This is in contrast with some user-facing api such as isItemVisible() /
 * isRectVisible() which use "visible" to mean "not clipped".
 */
public class TableColumn {
    /**
     * Flags after some patching (not directly the same as provided by the user).
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags
     */
    public int flags;

    /**
     * Final/actual width visible == (maxX - minX), locked in tableUpdateLayout(). May be greater
     * than widthRequest to honor the minimum width, or less to honor shrinking columns down in
     * tight space.
     */
    public float widthGiven;

    /** Absolute position of the left of the column. */
    public float minX;

    /** Absolute position of the right of the column. */
    public float maxX;

    /**
     * Master width absolute value when not stretching. When stretching this is derived every frame
     * from stretchWeight in tableUpdateLayout().
     */
    public float widthRequest;

    /** Automatic width. */
    public float widthAuto;

    /** Maximum width. */
    public float widthMax;

    /** Master width weight when stretching. Often around 1.0 initially. */
    public float stretchWeight;

    /** Value passed to tableSetupColumn(). For widths it is a content width (without padding). */
    public float initStretchWeightOrWidth;

    /** Clipping rectangle for the column. */
    public final RectFloat clipRect;

    /** Hash of the column name (ignoring the ID stack), used for .ini persistence. */
    public int id;

    /** Optional user data value passed to tableSetupColumn(). */
    public int userData;

    /** Contents region min, the cursor start position when entering the column. */
    public float workMinX;

    /** Contents region max. */
    public float workMaxX;

    /** Current item width for the column, preserved across rows. */
    public float itemWidth;

    /** Contents maximum position for frozen rows (apart from headers). */
    public float contentMaxXFrozen;

    /** Contents maximum position for unfrozen rows. */
    public float contentMaxXUnfrozen;

    /** Contents maximum position for header rows (regardless of freezing). */
    public float contentMaxXHeadersUsed;

    /** Ideal contents maximum position for header rows. */
    public float contentMaxXHeadersIdeal;

    /** The column name, or null if it doesn't have one. */
    public String name;

    /** Index within the table's display order (columns may be reordered by users). */
    public int displayOrder;

    /** Index within the enabled/visible set. */
    public int indexWithinEnabledSet;

    /** Index of the previous enabled/visible column, -1 if this is the first one. */
    public int prevEnabledColumn;

    /** Index of the next enabled/visible column, -1 if this is the last one. */
    public int nextEnabledColumn;

    /**
     * Index of this column within the sort specs, -1 if not sorting on this column, 0 for single
     * sort, may be greater than 0 with multiple sort.
     */
    public int sortOrder;

    /** Index of the current draw channel. */
    public int drawChannelCurrent;

    /** Draw channel for frozen rows (often headers). */
    public int drawChannelFrozen;

    /** Draw channel for unfrozen rows. */
    public int drawChannelUnfrozen;

    /** isUserEnabled and not disabled by flags. */
    public boolean isEnabled;

    /** Whether the column is not hidden by the user (unrelated to being clipped). */
    public boolean isUserEnabled;

    public boolean isUserEnabledNextFrame;

    /** Whether the column is in view horizontally (not scrolled away or clipped). */
    public boolean isVisibleX;

    public boolean isVisibleY;

    /**
     * Return value for tableSetColumnIndex() / tableNextColumn(): whether we request the user to
     * output contents or not.
     */
    public boolean isRequestOutput;

    /** Whether item submissions to this column are completely ignored (no layout will happen). */
    public boolean isSkipItems;

    public boolean isPreserveWidthAuto;
    public boolean isJustCreated;
    public boolean isLoadedSettings;
    public boolean isNeedReconcileSrc;
    public boolean isNeedReconcileDst;

    /** The navigation layer. */
    public int navLayerCurrent;

    /** Queue of 4 bits for the next 4 frames to request auto-fit. */
    public int autoFitQueue;

    /** Queue of 4 bits for the next 4 frames to disable clipping/skipping items. */
    public int cannotSkipItemsQueue;

    /** The sort direction, ascending or descending. */
    public @NonNull SortDirection sortDirection;

    /** Number of available sort directions (0 to 3). */
    public int sortDirectionsAvailableCount;

    /** Mask of available sort directions (1 bit each, by sort direction ordinal). */
    public int sortDirectionsAvailableMask;

    /** Ordered list of available sort directions (2 bits each, by sort direction ordinal). */
    public int sortDirectionsAvailableList;

    public TableColumn() {
        clipRect = new RectFloat(0, 0, 0, 0);
        sortDirection = SortDirection.NONE;
        reset();
    }

    /** Reset to the default state of a newly created column. */
    public void reset() {
        flags = 0;
        widthGiven = 0;
        minX = 0;
        maxX = 0;
        widthRequest = -1.0f;
        widthAuto = 0;
        widthMax = 0;
        stretchWeight = -1.0f;
        initStretchWeightOrWidth = 0;
        clipRect.set(0, 0, 0, 0);
        id = 0;
        userData = 0;
        workMinX = 0;
        workMaxX = 0;
        itemWidth = 0;
        contentMaxXFrozen = 0;
        contentMaxXUnfrozen = 0;
        contentMaxXHeadersUsed = 0;
        contentMaxXHeadersIdeal = 0;
        name = null;
        displayOrder = -1;
        indexWithinEnabledSet = -1;
        prevEnabledColumn = -1;
        nextEnabledColumn = -1;
        sortOrder = -1;
        drawChannelCurrent = -1;
        drawChannelFrozen = -1;
        drawChannelUnfrozen = -1;
        isEnabled = false;
        isUserEnabled = false;
        isUserEnabledNextFrame = false;
        isVisibleX = false;
        isVisibleY = false;
        isRequestOutput = false;
        isSkipItems = false;
        isPreserveWidthAuto = false;
        isJustCreated = true;
        isLoadedSettings = false;
        isNeedReconcileSrc = false;
        isNeedReconcileDst = false;
        navLayerCurrent = 0;
        autoFitQueue = 0;
        cannotSkipItemsQueue = 0;
        sortDirection = SortDirection.NONE;
        sortDirectionsAvailableCount = 0;
        sortDirectionsAvailableMask = 0;
        sortDirectionsAvailableList = 0;
    }

    /**
     * Copy all values from another column.
     *
     * @param other The column to copy.
     */
    public void set(@NonNull TableColumn other) {
        flags = other.flags;
        widthGiven = other.widthGiven;
        minX = other.minX;
        maxX = other.maxX;
        widthRequest = other.widthRequest;
        widthAuto = other.widthAuto;
        widthMax = other.widthMax;
        stretchWeight = other.stretchWeight;
        initStretchWeightOrWidth = other.initStretchWeightOrWidth;
        clipRect.set(other.clipRect);
        id = other.id;
        userData = other.userData;
        workMinX = other.workMinX;
        workMaxX = other.workMaxX;
        itemWidth = other.itemWidth;
        contentMaxXFrozen = other.contentMaxXFrozen;
        contentMaxXUnfrozen = other.contentMaxXUnfrozen;
        contentMaxXHeadersUsed = other.contentMaxXHeadersUsed;
        contentMaxXHeadersIdeal = other.contentMaxXHeadersIdeal;
        name = other.name;
        displayOrder = other.displayOrder;
        indexWithinEnabledSet = other.indexWithinEnabledSet;
        prevEnabledColumn = other.prevEnabledColumn;
        nextEnabledColumn = other.nextEnabledColumn;
        sortOrder = other.sortOrder;
        drawChannelCurrent = other.drawChannelCurrent;
        drawChannelFrozen = other.drawChannelFrozen;
        drawChannelUnfrozen = other.drawChannelUnfrozen;
        isEnabled = other.isEnabled;
        isUserEnabled = other.isUserEnabled;
        isUserEnabledNextFrame = other.isUserEnabledNextFrame;
        isVisibleX = other.isVisibleX;
        isVisibleY = other.isVisibleY;
        isRequestOutput = other.isRequestOutput;
        isSkipItems = other.isSkipItems;
        isPreserveWidthAuto = other.isPreserveWidthAuto;
        isJustCreated = other.isJustCreated;
        isLoadedSettings = other.isLoadedSettings;
        isNeedReconcileSrc = other.isNeedReconcileSrc;
        isNeedReconcileDst = other.isNeedReconcileDst;
        navLayerCurrent = other.navLayerCurrent;
        autoFitQueue = other.autoFitQueue;
        cannotSkipItemsQueue = other.cannotSkipItemsQueue;
        sortDirection = other.sortDirection;
        sortDirectionsAvailableCount = other.sortDirectionsAvailableCount;
        sortDirectionsAvailableMask = other.sortDirectionsAvailableMask;
        sortDirectionsAvailableList = other.sortDirectionsAvailableList;
    }
}
