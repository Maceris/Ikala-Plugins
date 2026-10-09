package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.enums.DockRequestType;

import lombok.NonNull;

/** A queued request to dock, undock, or split windows and dock nodes. */
public class DockNodeRequest {
    public @NonNull DockRequestType type;

    /**
     * Destination/target window to dock into (may be a loose window or a dock node host). May be
     * null, in which case {@link #dockTargetNode} cannot be null.
     */
    public Window dockTargetWindow;

    /** Destination/target node to dock into. */
    public DockNode dockTargetNode;

    /** Source/payload window to dock (may be a loose window or a dock node host), optional. */
    public Window dockPayload;

    public @NonNull Direction dockSplitDirection;
    public float dockSplitRatio;
    public boolean dockSplitOuter;
    public Window undockTargetWindow;
    public DockNode undockTargetNode;

    public DockNodeRequest() {
        type = DockRequestType.NONE;
        dockTargetWindow = null;
        dockTargetNode = null;
        dockPayload = null;
        dockSplitDirection = Direction.NONE;
        dockSplitRatio = 0.5f;
        dockSplitOuter = false;
        undockTargetWindow = null;
        undockTargetNode = null;
    }
}
