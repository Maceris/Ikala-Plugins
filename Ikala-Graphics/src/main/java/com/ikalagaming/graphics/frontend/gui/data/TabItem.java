package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags;

public class TabItem {

    /** beginTabItem() order, used to re-order tabs after toggling TabBarFlags.REORDERABLE. */
    public IkShort beginOrder;

    public boolean closeRequested;

    /**
     * Width of label + padding, stored during beginTabItem() call (misnamed as "Content" would
     * normally imply width of label only).
     */
    public float contentWidth;

    /**
     * @see TabItemFlags
     */
    public int flags;

    public int id;

    /**
     * Index only used during tabBarLayout(). Tabs gets reordered so 'tabs.get(n).indexDuringLayout
     * == n' but may mismatch during additions.
     */
    public IkShort indexDuringLayout;

    public float labelWidth;

    /**
     * This allows us to infer an ordered list of the last activated tabs with little maintenance.
     */
    public int lastFrameSelected;

    public int lastFrameVisible;

    /** When Window==NULL, offset to name within parent ImGuiTabBar::Tab. */
    public IkInt nameOffset;

    /** Position relative to beginning of tab bar. */
    public float offset;

    /** Width optionally requested by caller, -1.0f is unused. */
    public float requestedWidth;

    /** Marked as closed by setTabItemClosed(). */
    public boolean wantClose;

    /** Width currently displayed. */
    public float width;

    /** When TabItem is part of a DockNode's TabBar, we hold on to a window. */
    public Window window;

    public TabItem() {
        beginOrder = new IkShort();
        closeRequested = false;
        contentWidth = 0.0f;
        flags = TabItemFlags.NONE;
        id = 0;
        indexDuringLayout = new IkShort();
        labelWidth = 0.0f;
        lastFrameSelected = 0;
        lastFrameVisible = 0;
        nameOffset = new IkInt();
        offset = 0.0f;
        requestedWidth = -1.0f;
        wantClose = false;
        width = 0.0f;
        window = null;
    }
}
