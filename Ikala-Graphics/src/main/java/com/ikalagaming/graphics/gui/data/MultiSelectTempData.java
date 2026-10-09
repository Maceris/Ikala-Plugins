package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.MultiSelectFlags;

import org.joml.Vector2f;

/** Temporary storage for a multi-select scope, while its items are being submitted. */
public class MultiSelectTempData {
    /** Requests set and returned by beginMultiSelect()/endMultiSelect(). */
    public final MultiSelectIO io;

    /** The persistent storage for the scope. */
    public MultiSelectState storage;

    /** The focus scope of the selection, copied from the current focus scope. */
    public int focusScopeID;

    /**
     * @see MultiSelectFlags
     */
    public int flags;

    /** The top left of the scope, when using {@link MultiSelectFlags#SCOPE_RECT}. */
    public final Vector2f scopeRectMin;

    /** The window's cursor max position before the scope started. */
    public final Vector2f backupCursorMaxPos;

    /** The ID of the box-select, or 0 when box-select is not enabled. */
    public int boxSelectID;

    /** Copy of the key modifiers at the time of the request. */
    public int keyMods;

    /** -1 is no operation, 0 is clear all, 1 is select all. */
    public int loopRequestSetAll;

    /** Set when switching the IO from the beginMultiSelect() to endMultiSelect() state. */
    public boolean isEndIO;

    /**
     * Set if the selection scope (any item of the selection) is currently focused. May be used for
     * custom shortcuts associated with the selection.
     */
    public boolean isFocused;

    /**
     * Set by beginMultiSelect() when using Shift+Navigation. Since scrolling may be affected, we
     * can't afford a frame of lag with Shift+Navigation.
     */
    public boolean isKeyboardSetRange;

    /** Set when the focused item was submitted. */
    public boolean navIDPassedBy;

    /** Set by the item that matches the range source item. */
    public boolean rangeSourcePassedBy;

    /** Set by the item that was just navigated to, when keyboard setting a range. */
    public boolean rangeDestinationPassedBy;

    /** If the selection size is 1, or not known. */
    public boolean isSoleOrUnknownSelectionSize;

    /** Create new, empty temporary data. */
    public MultiSelectTempData() {
        io = new MultiSelectIO();
        scopeRectMin = new Vector2f();
        backupCursorMaxPos = new Vector2f();
        clear();
    }

    /** Clear everything. */
    public void clear() {
        clearIO();
        storage = null;
        focusScopeID = 0;
        flags = MultiSelectFlags.NONE;
        scopeRectMin.set(0, 0);
        backupCursorMaxPos.set(0, 0);
        boxSelectID = 0;
        keyMods = 0;
        loopRequestSetAll = 0;
        isEndIO = false;
        isFocused = false;
        isKeyboardSetRange = false;
        navIDPassedBy = false;
        rangeSourcePassedBy = false;
        rangeDestinationPassedBy = false;
        isSoleOrUnknownSelectionSize = false;
    }

    /** Clear the IO data. */
    public void clearIO() {
        io.clear();
    }
}
