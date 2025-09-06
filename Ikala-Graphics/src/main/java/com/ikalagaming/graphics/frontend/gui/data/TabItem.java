package com.ikalagaming.graphics.frontend.gui.data;

public class TabItem {

    private int ID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags
     */
    private int tabItemFlags;

    private int lastFrameVisible;
    private int lastFrameSelected;
    private float offset;
    private float width;
    private float labelWidth;
    private float requestedWidth;
    private IkInt nameOffset;
    private IkShort beginOrder;
    private IkShort indexDuringLayout;
    private boolean closeRequested;
}
