package com.ikalagaming.graphics.gui.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The state of the docking system. */
public class DockContext {
    /**
     * All dock nodes, by ID. Sorted by ID so iteration order is stable (e.g. when saving settings).
     */
    public final Map<Integer, DockNode> nodes;

    /** Requests queued to be processed at the start of the next frame. */
    public final List<DockNodeRequest> requests;

    /** Settings for nodes, as loaded from or saved to the ini file. */
    public final List<DockNodeSettings> nodeSettings;

    public boolean wantFullRebuild;

    public DockContext() {
        nodes = new TreeMap<>(Integer::compareUnsigned);
        requests = new ArrayList<>();
        nodeSettings = new ArrayList<>();
        wantFullRebuild = false;
    }
}
