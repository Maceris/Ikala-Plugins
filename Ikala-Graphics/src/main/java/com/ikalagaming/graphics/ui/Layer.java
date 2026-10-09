package com.ikalagaming.graphics.ui;

/**
 * How a surface stacks with other surfaces and with plain IkGui windows, like debug windows. IkGui
 * orders windows by focus, not by when they were submitted, so this is how a surface stays behind
 * or in front.
 */
public enum Layer {
    /**
     * Always behind every other window, even while it has focus. For HUDs and full-screen menus, so
     * tool windows float above them.
     */
    BACKGROUND,
    /** Ordered by focus like any IkGui window. For inventories, forms and the like. */
    NORMAL,
    /** Always in front of every other window. For modal dialogs and notifications. */
    OVERLAY
}
