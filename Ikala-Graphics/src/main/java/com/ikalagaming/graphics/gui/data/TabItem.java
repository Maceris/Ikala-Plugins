package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.TabItemFlags;

/** Storage for one tab in a tab bar. */
public class TabItem {
    public int id;

    /**
     * @see TabItemFlags
     */
    public int flags;

    /** When the tab is part of a dock node's tab bar, we hold on to a window. */
    public Window window;

    /** The frame the tab was last submitted on. */
    public int lastFrameVisible;

    /**
     * The frame the tab was last selected on, which allows us to infer an ordered list of the last
     * activated tabs with little maintenance.
     */
    public int lastFrameSelected;

    /** Position relative to the beginning of the tab bar. */
    public float offset;

    /** Width currently displayed. */
    public float width;

    /**
     * Width of the label + padding, stored during the beginTabItem() call (misnamed as "content"
     * would normally imply the width of the label only).
     */
    public float contentWidth;

    /** Width optionally requested by the caller, -1 if unused. */
    public float requestedWidth;

    /**
     * The label of the tab, set when submitted. Null for docked window tabs, which use the window
     * name instead.
     */
    public String name;

    /** beginTabItem() order, used to re-order tabs after toggling TabBarFlags.REORDERABLE. */
    public int beginOrder;

    /**
     * Index only used during tab bar layout. Tabs get reordered so that tabs.get(n)
     * .indexDuringLayout == n, but may mismatch during additions.
     */
    public int indexDuringLayout;

    /** Marked as closed by setTabItemClosed(). */
    public boolean wantClose;

    public TabItem() {
        id = 0;
        flags = TabItemFlags.NONE;
        window = null;
        lastFrameVisible = -1;
        lastFrameSelected = -1;
        offset = 0.0f;
        width = 0.0f;
        contentWidth = 0.0f;
        requestedWidth = -1.0f;
        name = null;
        beginOrder = -1;
        indexDuringLayout = -1;
        wantClose = false;
    }
}
