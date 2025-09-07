package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.MultiSelectFlags;

import org.joml.Vector2f;

public class MultiSelectTempData {
    private MultiSelectIO IO;
    private MultiSelectState storage;
    private int forcusScopeID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.MultiSelectFlags
     */
    private int flags;

    private Vector2f scopeRectMin;
    private Vector2f backupCursorMaxPos;
    private Object lastSubmittedItem;
    private int boxSelectID;
    private int keyMods;

    /** -1 is no operation, 0 is clear all, 1 is select all. */
    private IkByte loopRequestSetAll;

    private boolean endingIO;
    private boolean focused;

    /** Set when starting multi-select when using shift navigation. */
    private boolean keyboardSetRange;

    private boolean navIDPassedBy;
    private boolean rangeSourcePassedBy;
    private boolean rangeDestinationPassedBy;

    public MultiSelectTempData() {
        scopeRectMin = new Vector2f();
        backupCursorMaxPos = new Vector2f();
        loopRequestSetAll = new IkByte();
        clear();
    }

    public void clear() {
        clearIO();
        forcusScopeID = 0;
        flags = MultiSelectFlags.NONE;
        scopeRectMin.set(0, 0);
        backupCursorMaxPos.set(0, 0);
        lastSubmittedItem = null;
        boxSelectID = 0;
        keyMods = 0;
        loopRequestSetAll.set((byte) 0);
        endingIO = false;
        focused = false;
        keyboardSetRange = false;
        navIDPassedBy = false;
        rangeSourcePassedBy = false;
        rangeDestinationPassedBy = false;
    }

    public void clearIO() {
        IO.requests.clear();
        IO.navIDItem = SelectionUserData.INVALID;
        IO.rangeSourceItem = SelectionUserData.INVALID;
        IO.navIDSelected = false;
        IO.rangeSourceReset = false;
    }
}
