package com.ikalagaming.graphics.frontend.gui.data;

/** Persistent storage for a multi-select scope, kept for as long as the selection is alive. */
public class MultiSelectState {
    /** The window the scope was last submitted in. */
    public Window window;

    /** The ID of the scope. */
    public int id;

    /** The last frame the scope was submitted, for garbage collection. */
    public int lastFrameActive;

    /**
     * The selection size provided by the user to beginMultiSelect(), which may be -1 if unknown.
     */
    public int lastSelectionSize;

    /** The selection state of the range source item, -1 if we don't have one, or 0/1. */
    public int rangeSelected;

    /** The selection state of the focused item, -1 if we don't have one, or 0/1. */
    public int navIDSelected;

    /** The selection user data of the range source item. */
    public long rangeSourceItem;

    /** The selection user data of the focused item, if it was submitted. */
    public long navIDItem;

    /** Create a new, empty state. */
    public MultiSelectState() {
        window = null;
        id = 0;
        lastFrameActive = 0;
        lastSelectionSize = 0;
        rangeSelected = -1;
        navIDSelected = -1;
        rangeSourceItem = SelectionUserData.INVALID;
        navIDItem = SelectionUserData.INVALID;
    }
}
