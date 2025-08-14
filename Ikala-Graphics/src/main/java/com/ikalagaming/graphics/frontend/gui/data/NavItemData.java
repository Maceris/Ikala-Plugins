package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;

public class NavItemData {

    private Window window;
    private int ID;
    private int focusScopeID;
    private Rect rect;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ItemFlags
     */
    private int itemFlags;

    private float distanceToBox;
    private float distanceToCenter;
    private float distanceAxial;
    private int selectionUserData;

    public NavItemData() {
        clear();
    }

    public void clear() {
        window = null;
        ID = 0;
        focusScopeID = 0;
        itemFlags = ItemFlags.NONE;
        selectionUserData = -1;
        distanceToBox = Float.MAX_VALUE;
        distanceToCenter = Float.MAX_VALUE;
        distanceAxial = Float.MAX_VALUE;
    }
}
