package com.ikalagaming.graphics.frontend.gui.enums;

/** The type of selection request. */
public enum SelectionRequestType {
    /** No request. */
    NONE,
    /**
     * Clear the selection (if selected is false) or select all items (if selected is true). The
     * range items are not set, since what "all" means is entirely up to the application.
     */
    SET_ALL,
    /**
     * Select or unselect the items from the first to the last range item (inclusive), based on the
     * selected value.
     */
    SET_RANGE,
}
