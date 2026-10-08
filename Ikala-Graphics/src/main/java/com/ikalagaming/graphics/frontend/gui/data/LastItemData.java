package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

/** Information about the last item that was submitted, used for querying items after the fact. */
public class LastItemData {
    public int id;

    /**
     * @see ItemFlags
     */
    public int itemFlags;

    /**
     * @see ItemStatusFlags
     */
    public int statusFlags;

    /** The full rectangle of the item. */
    public final RectFloat rect;

    /** The rectangle used for navigation, which is usually the same as the rect. */
    public final RectFloat navRect;

    /** The display rect, only valid if {@link ItemStatusFlags#HAS_DISPLAY_RECT} is set. */
    public final RectFloat displayRect;

    /** The clip rect, only valid if {@link ItemStatusFlags#HAS_CLIP_RECT} is set. */
    public final RectFloat clipRect;

    /**
     * The shortcut of the item, as a key chord. Only valid if {@link ItemStatusFlags#HAS_SHORTCUT}
     * is set.
     *
     * @see com.ikalagaming.graphics.frontend.gui.util.KeyChord
     */
    public int shortcut;

    public LastItemData() {
        id = 0;
        itemFlags = ItemFlags.NONE;
        statusFlags = ItemStatusFlags.NONE;
        rect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        navRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        displayRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        clipRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        shortcut = 0;
    }

    /**
     * Copy all the values from another item.
     *
     * @param other The data to copy.
     */
    public void set(LastItemData other) {
        id = other.id;
        itemFlags = other.itemFlags;
        statusFlags = other.statusFlags;
        rect.set(other.rect);
        navRect.set(other.navRect);
        displayRect.set(other.displayRect);
        clipRect.set(other.clipRect);
        shortcut = other.shortcut;
    }
}
