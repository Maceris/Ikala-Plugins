package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags;

public class TabItem {

    public int id;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags
     */
    public int tabItemFlags;

    public int lastFrameVisible;
    public int lastFrameSelected;
    public float offset;
    public float width;
    public float labelWidth;
    public float requestedWidth;
    public IkInt nameOffset;
    public IkShort beginOrder;
    public IkShort indexDuringLayout;
    public boolean closeRequested;

    public TabItem() {
        id = 0;
        tabItemFlags = TabItemFlags.NONE;
        lastFrameVisible = 0;
        lastFrameSelected = 0;
        offset = 0.0f;
        width = 0.0f;
        labelWidth = 0.0f;
        requestedWidth = 0.0f;
        nameOffset = new IkInt();
        beginOrder = new IkShort();
        indexDuringLayout = new IkShort();
        closeRequested = false;
    }
}
