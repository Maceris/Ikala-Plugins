package com.ikalagaming.graphics.frontend.gui.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockContext {
    public final Map<Integer, DockNode> nodes;
    public final List<DockNodeRequest> requests;
    public final List<DockNodeSettings> nodeSettings;
    public boolean wantFullRebuild;

    public DockContext() {
        nodes = new HashMap<>();
        requests = new ArrayList<>();
        nodeSettings = new ArrayList<>();
        wantFullRebuild = false;
    }
}
