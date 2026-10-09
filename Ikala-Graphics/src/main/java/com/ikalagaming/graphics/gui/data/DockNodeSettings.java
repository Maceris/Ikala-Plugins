package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.Axis;

import lombok.NonNull;

/** Persistent settings data for a dock node, which is saved to and loaded from the ini file. */
public class DockNodeSettings {
    public int id;
    public int parentNodeID;
    public int parentWindowID;
    public int selectedTabID;
    public @NonNull Axis splitAxis;
    public int depth;

    /**
     * Only flags in {@link com.ikalagaming.graphics.gui.flags.DockNodeFlags#SAVED_FLAGS_MASK} are
     * saved.
     *
     * @see com.ikalagaming.graphics.gui.flags.DockNodeFlags
     */
    public int flags;

    public int positionX;
    public int positionY;
    public int sizeX;
    public int sizeY;
    public int sizeRefX;
    public int sizeRefY;

    public DockNodeSettings() {
        id = 0;
        parentNodeID = 0;
        parentWindowID = 0;
        selectedTabID = 0;
        splitAxis = Axis.NONE;
        depth = 0;
        flags = 0;
        positionX = 0;
        positionY = 0;
        sizeX = 0;
        sizeY = 0;
        sizeRefX = 0;
        sizeRefY = 0;
    }
}
