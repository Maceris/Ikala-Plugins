package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

public class LastItemData {
    public int id;

    /**
     * @see ItemStatusFlags
     */
    public int statusFlags;

    public RectFloat rect;
    public RectFloat displayRect;
    public RectFloat clipRect;
    // TODO(ches) if we set up a different data type for shortcuts, update this
    public int shortcut;

    public LastItemData() {
        id = 0;
        statusFlags = ItemStatusFlags.NONE;
        rect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        displayRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        clipRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        shortcut = 0;
    }
}
