package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextItemFlags;

/** Storage for setNextItem*() calls, which are consumed by the next item that is submitted. */
public class NextItemData {
    /**
     * Item flags to apply to the next item.
     *
     * @see ItemFlags
     */
    public int itemFlags;

    /**
     * Which fields have been set.
     *
     * @see NextItemFlags
     */
    public int fieldFlags;

    public int focusScopeID;
    public long selectionUserData;

    /** The width of the next item, set by setNextItemWidth(). */
    public float width;

    public int shortcut;
    public int shortcutFlags;

    /** The open state of the next tree node/collapsing header, set by setNextItemOpen(). */
    public boolean openValue;

    /** The condition for setting the open state. */
    public Condition openCondition;

    public int storageID;

    /** The color of the marker drawn on the next drag or slider, in RGBA format. */
    public int colorMarker;

    /**
     * The reference value for the next input scalar, used for InputTextFlags.PARSE_EMPTY_REF_VAL
     * and DISPLAY_EMPTY_REF_VAL. A Long for integer types and a Double for floating point types.
     */
    public Number referenceValue;

    public NextItemData() {
        itemFlags = ItemFlags.NONE;
        fieldFlags = NextItemFlags.NONE;
        focusScopeID = 0;
        selectionUserData = -1;
        width = 0.0f;
        shortcut = 0;
        shortcutFlags = 0;
        openValue = false;
        openCondition = Condition.NONE;
        storageID = 0;
        colorMarker = 0;
    }

    /** Clear the flags, without resetting the actual values. */
    public void clearFlags() {
        fieldFlags = NextItemFlags.NONE;
        itemFlags = ItemFlags.NONE;
    }
}
