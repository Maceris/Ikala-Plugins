package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2i;

/** Window data that is saved to and loaded from the .ini file. */
public class WindowSettings {
    /** ID of window class if specified. */
    public int classID;

    public boolean collapsed;

    /**
     * ID of last known DockNode (even if the DockNode is invisible because it has only 1 active
     * window), or 0 if none.
     */
    public int dockID;

    /**
     * Order of the last time the window was visible within its DockNode. This is used to reorder
     * windows that are reappearing on the same frame. The same value between windows that were
     * active, and windows that were none, are possible.
     */
    public int dockOrder;

    public int id;

    public boolean isChild;

    /** The last date the window was used, as YYYYMMDD, or 0 if unknown. */
    public int lastUsedDate;

    /** The name of the window, as stored in the .ini file. */
    public final String name;

    /** Stored relative to the viewport, as opposed to the runtime absolute positions. */
    public final Vector2i position;

    public final Vector2i size;
    public int viewportID;
    public final Vector2i viewportPosition;

    /**
     * Set when loaded from .ini data (to enable merging/loading .ini data into an already running
     * context)
     */
    public boolean wantApply;

    /** Set to invalidate/delete the settings entry. */
    public boolean wantDelete;

    /**
     * Create new settings.
     *
     * @param name The name of the window.
     */
    public WindowSettings(String name) {
        this.name = name;
        position = new Vector2i();
        size = new Vector2i();
        viewportPosition = new Vector2i();
        clear();
    }

    /** Reset everything other than the name to defaults. */
    public void clear() {
        classID = 0;
        collapsed = false;
        dockID = 0;
        dockOrder = -1;
        id = 0;
        isChild = false;
        lastUsedDate = 0;
        position.set(0, 0);
        size.set(0, 0);
        viewportID = 0;
        viewportPosition.set(0, 0);
        wantApply = false;
        wantDelete = false;
    }
}
