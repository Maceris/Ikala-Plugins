package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.ItemFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;

/** Storage for navigation query results. */
public class NavItemData {
    /** The window the item is in. */
    public Window window;

    /** The item ID. */
    public int id;

    /** The focus scope of the item. */
    public int focusScopeID;

    /** The navigation rectangle, relative to the window position. */
    public final RectFloat rectRelative;

    /**
     * The item flags.
     *
     * @see ItemFlags
     */
    public int itemFlags;

    /** Move: best candidate box distance to the current nav rect. */
    public float distanceBox;

    /** Move: best candidate center distance to the current nav rect. */
    public float distanceCenter;

    /** Move: best candidate axial distance to the current nav rect. */
    public float distanceAxial;

    /** The selection user data, -1 if invalid. */
    public long selectionUserData;

    public NavItemData() {
        rectRelative = new RectFloat(0, 0, 0, 0);
        clear();
    }

    /** Reset the result. */
    public void clear() {
        window = null;
        id = 0;
        focusScopeID = 0;
        itemFlags = ItemFlags.NONE;
        selectionUserData = -1;
        distanceBox = Float.MAX_VALUE;
        distanceCenter = Float.MAX_VALUE;
        distanceAxial = Float.MAX_VALUE;
    }
}
