package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Docking: dock nodes, the dock context, and the docking support used by begin()/end().
 *
 * <p>Typical docking call flow (the root level is generally the public API):
 *
 * <pre>
 * - newFrame()                               new frame
 *    | dockContextNewFrameUpdateUndocking()  - process queued undocking requests
 *    | - dockContextProcessUndockWindow()    - process one window undocking request
 *    | - dockContextProcessUndockNode()      - process one whole node undocking request
 *    | dockContextNewFrameUpdateDocking()    - process queued docking requests, create floating dock nodes
 *    | - update debugHoveredDockNode         - [debug] update node hovered by mouse
 *    | - dockContextProcessDock()            - process one docking request
 *    | - dockNodeUpdate()
 *    |   - dockNodeUpdateForRootNode()
 *    |     - dockNodeUpdateFlagsAndCollapse()
 *    |     - dockNodeFindInfo()
 *    |   - destroy unused node or tab bar
 *    |   - create dock node host window
 *    |      - begin() etc.
 *    |   - dockNodeStartMouseMovingWindow()
 *    |   - dockNodeTreeUpdatePosSize()
 *    |   - dockNodeTreeUpdateSplitter()
 *    |   - draw node background
 *    |   - dockNodeUpdateTabBar()            - create/update tab bar for a docking node
 *    |     - dockNodeAddTabBar()
 *    |     - dockNodeWindowMenuUpdate()
 *    |     - dockNodeCalcTabBarLayout()
 *    |     - beginTabBarEx()
 *    |     - tabItemEx() calls
 *    |     - endTabBar()
 *    |   - beginDockableDragDropTarget()
 *    |      - dockNodeUpdate()               - recurse into child nodes...
 * - dockSpace()                              user submits a dockspace into a window
 *    | begin(child)                          - create a child window
 *    | dockNodeUpdate()                      - call main dock node update function
 *    | end(child)
 *    | itemSize()
 * - begin()
 *    | beginDocked()
 *    | beginDockableDragDropSource()
 *    | beginDockableDragDropTarget()
 *    | - dockNodePreviewDockRender()
 * - endFrame()
 *    | dockContextEndFrame()
 * </pre>
 *
 * <p>The lifetime model is different from the one of regular windows: we always create a dock node
 * for each dock node settings entry, so we always hold the entire docking node tree. Nodes are
 * frequently hidden, e.g. if the windows or child nodes they host are not active. At boot time
 * only, we run a simple garbage collection pass to remove nodes that have no references. Because
 * dock node settings are always mirrored by their corresponding dock nodes, we can also recreate
 * the nodes from scratch given the settings data (this is what dockContextRebuildNodes() does).
 *
 * @see IkGuiImplDockBuilder
 * @see IkGuiImplDockSettings
 */
@Slf4j
class IkGuiImplDocking {
    /** Draw channel of dock host windows used for the backgrounds of nodes and docked windows. */
    static final int DOCKING_HOST_DRAW_CHANNEL_BG = 0;

    /** Draw channel of dock host windows used for everything else. */
    static final int DOCKING_HOST_DRAW_CHANNEL_FG = 1;

    /**
     * Alpha applied to the payload window when using {@link IkIO#configDockingTransparentPayload}.
     */
    static final float DOCKING_TRANSPARENT_PAYLOAD_ALPHA = 0.50f;

    /** The label of the menu item that hides the tab bar of a node with a single window. */
    private static final String HIDE_TAB_BAR_LABEL = "Hide tab bar###HideTabBar";

    /** Tooltip shown when docking requires holding shift. */
    private static final String HOLD_SHIFT_TO_DOCK = "Hold SHIFT to enable Docking window.";

    /** Tooltip for the window menu button of dock nodes. */
    private static final String DRAG_TO_UNDOCK_OR_MOVE_NODE =
            "Click and drag to move or undock whole node.";

    /** The popup ID of the window menu of a dock node. */
    private static final String WINDOW_MENU_POPUP = "#WindowMenu";

    static Context context;

    // ---------------------------------------------------------------------------------------------
    // Dock context
    // ---------------------------------------------------------------------------------------------

    /** Set up the docking system for a new context. */
    static void dockContextInitialize() {
        IkGuiImplDockSettings.dockSettingsAddSettingsHandler();
        context.dockNodeWindowMenuHandler = IkGuiImplDocking::dockNodeWindowMenuHandlerDefault;
    }

    /** Delete all the nodes when the context is destroyed. */
    static void dockContextShutdown() {
        for (DockNode node : new ArrayList<>(context.dockContext.nodes.values())) {
            dockContextDeleteNode(node);
        }
    }

    /**
     * Remove dock nodes, undocking their windows.
     *
     * @param rootID The ID of the root node to clear, or 0 to clear all nodes.
     * @param clearSettingsRefs Whether to also clear the references to the nodes in the window
     *     settings.
     */
    static void dockContextClearNodes(int rootID, boolean clearSettingsRefs) {
        IkGuiImplDockBuilder.dockBuilderRemoveNodeDockedWindows(rootID, clearSettingsRefs);
        IkGuiImplDockBuilder.dockBuilderRemoveNodeChildNodes(rootID);
    }

    /**
     * [DEBUG] Rebuild all the nodes from the current settings. This also acts as a test to make
     * sure we can rebuild from scratch without a glitch.
     */
    static void dockContextRebuildNodes() {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING, "[docking] DockContextRebuildNodes");
        log.trace("dockContextRebuildNodes");
        IkGuiImplConfig.saveIniSettingsToMemory();
        final int rootID = 0;
        dockContextClearNodes(rootID, false);
        dockContextBuildNodesFromSettings(context.dockContext.nodeSettings);
        dockContextBuildAddWindowsToNodes(rootID);
    }

    /**
     * Process queued undocking requests, called by newFrame() before moving windows so the window
     * is already following the mouse on the frame it is detached.
     */
    static void dockContextNewFrameUpdateUndocking() {
        final DockContext dc = context.dockContext;
        if ((context.io.configFlags & ConfigFlags.DOCKING_ENABLE) == 0) {
            if (!dc.nodes.isEmpty() || !dc.requests.isEmpty()) {
                dockContextClearNodes(0, true);
            }
            return;
        }

        // Setting NoSplit at runtime merges all nodes
        if (context.io.configDockingNoSplit) {
            for (DockNode node : new ArrayList<>(dc.nodes.values())) {
                if (isNodeAlive(node) && node.isRootNode() && node.isSplitNode()) {
                    IkGuiImplDockBuilder.dockBuilderRemoveNodeChildNodes(node.id);
                }
            }
        }

        // Process a full rebuild
        if (dc.wantFullRebuild) {
            dockContextRebuildNodes();
            dc.wantFullRebuild = false;
        }

        // Process undocking requests (we need to process them before
        // updateMouseMovingWindowNewFrame() in newFrame())
        for (DockNodeRequest request : new ArrayList<>(dc.requests)) {
            if (request.type == DockRequestType.UNDOCK && request.undockTargetWindow != null) {
                dockContextProcessUndockWindow(request.undockTargetWindow, true);
            } else if (request.type == DockRequestType.UNDOCK && request.undockTargetNode != null) {
                dockContextProcessUndockNode(request.undockTargetNode);
            }
        }
    }

    /** Process queued docking requests and update floating dock nodes, called by newFrame(). */
    static void dockContextNewFrameUpdateDocking() {
        final DockContext dc = context.dockContext;
        if ((context.io.configFlags & ConfigFlags.DOCKING_ENABLE) == 0) {
            return;
        }

        // [DEBUG] Store the hovered dock node. This is mostly a debug thing and isn't actually
        // used for docking targets, because docking involves more detailed filtering.
        context.debugHoveredDockNode = null;
        final Window hoveredWindow = context.windowHoveredUnderMovingWindow;
        if (hoveredWindow != null) {
            if (hoveredWindow.dockNodeAsHost != null) {
                context.debugHoveredDockNode =
                        dockNodeTreeFindVisibleNodeByPos(
                                hoveredWindow.dockNodeAsHost, context.io.mousePosition);
            } else if (hoveredWindow.rootWindow.dockNode != null) {
                context.debugHoveredDockNode = hoveredWindow.rootWindow.dockNode;
            }
        }

        // Process docking requests
        for (DockNodeRequest request : new ArrayList<>(dc.requests)) {
            if (request.type == DockRequestType.DOCK) {
                dockContextProcessDock(request);
            }
        }
        dc.requests.clear();

        // Create windows for each automatic docking node
        for (DockNode node : new ArrayList<>(dc.nodes.values())) {
            if (isNodeAlive(node) && node.isFloatingNode()) {
                dockNodeUpdate(node);
            }
        }
    }

    /** Draw the backgrounds of nodes that are missing their window, called by endFrame(). */
    static void dockContextEndFrame() {
        for (DockNode node : context.dockContext.nodes.values()) {
            if (node.lastFrameActive == context.frameCount
                    && node.isVisible
                    && node.hostWindow != null
                    && node.isLeafNode()
                    && !node.isBackgroundDrawnThisFrame) {
                final RectFloat backgroundRect =
                        new RectFloat(
                                node.position.x,
                                node.position.y + IkGuiImplLayout.getFrameHeight(),
                                node.position.x + node.size.x,
                                node.position.y + node.size.y);
                final int roundingFlags =
                        IkGuiInternal.calcRoundingFlagsForRectInRect(
                                backgroundRect,
                                node.hostWindow.getRect(),
                                context.style.variable.dockingSeparatorSize);
                final DrawList drawList = node.hostWindow.drawList;
                drawList.channelsSetCurrent(DOCKING_HOST_DRAW_CHANNEL_BG);
                drawList.addRectFilled(
                        backgroundRect.getLeft(),
                        backgroundRect.getTop(),
                        backgroundRect.getRight(),
                        backgroundRect.getBottom(),
                        node.lastBackgroundColor,
                        node.hostWindow.rounding,
                        roundingFlags);
            }
        }
    }

    /**
     * Look up a dock node by ID.
     *
     * @param id The node ID.
     * @return The node, or null if not found.
     */
    static DockNode dockContextFindNodeByID(int id) {
        return context.dockContext.nodes.get(id);
    }

    /**
     * Check if a node still exists in the dock context. Nodes can be deleted while iterating over a
     * copy of the node list.
     *
     * @param node The node.
     * @return True if the node is still in the context.
     */
    private static boolean isNodeAlive(@NonNull DockNode node) {
        return context.dockContext.nodes.get(node.id) == node;
    }

    /**
     * Generate an ID for a new node. The exact value doesn't matter as long as it is not already
     * used.
     *
     * @return An unused node ID.
     */
    static int dockContextGenerateNodeID() {
        int id = 1;
        while (dockContextFindNodeByID(id) != null) {
            ++id;
        }
        return id;
    }

    /**
     * Create a new node. We don't set lastFrameAlive on construction, as nodes are always created
     * to reflect the .ini settings.
     *
     * @param id The ID of the node, or 0 to generate one.
     * @return The new node.
     */
    static DockNode dockContextAddNode(int id) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING, "[docking] DockContextAddNode 0x%08X", id);
        if (id == 0) {
            id = dockContextGenerateNodeID();
        } else if (dockContextFindNodeByID(id) != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Adding dock node 0x{} which already exists", Integer.toHexString(id));
        }
        log.trace("dockContextAddNode 0x{}", Integer.toHexString(id));
        final DockNode node = new DockNode(id);
        context.dockContext.nodes.put(node.id, node);
        return node;
    }

    /**
     * Remove a node, which must not have any windows or child nodes.
     *
     * @param node The node to remove.
     * @param mergeSiblingIntoParentNode Whether to merge the sibling of the node into the parent.
     */
    static void dockContextRemoveNode(@NonNull DockNode node, boolean mergeSiblingIntoParentNode) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING, "[docking] DockContextRemoveNode 0x%08X", node.id);
        log.trace("dockContextRemoveNode 0x{}", Integer.toHexString(node.id));
        if (dockContextFindNodeByID(node.id) != node) {
            IkGuiImplDebugTools.reportError(
                    log, "Node out of sync with the context's list of nodes");
            return;
        }
        if (node.childNodes[0] != null || node.childNodes[1] != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't remove a dock node that still has child nodes");
            return;
        }
        if (!node.windows.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Can't remove a dock node that still has windows");
            return;
        }

        if (node.hostWindow != null) {
            node.hostWindow.dockNodeAsHost = null;
        }

        final DockNode parentNode = node.parentNode;
        final boolean merge = mergeSiblingIntoParentNode && parentNode != null;
        if (merge) {
            if (parentNode.childNodes[0] != node && parentNode.childNodes[1] != node) {
                IkGuiImplDebugTools.reportError(
                        log, "Parent node does not contain the node being removed");
                return;
            }
            final DockNode siblingNode =
                    parentNode.childNodes[0] == node
                            ? parentNode.childNodes[1]
                            : parentNode.childNodes[0];
            dockNodeTreeMerge(parentNode, siblingNode);
        } else {
            for (int n = 0; parentNode != null && n < parentNode.childNodes.length; ++n) {
                if (parentNode.childNodes[n] == node) {
                    parentNode.childNodes[n] = null;
                }
            }
            dockContextDeleteNode(node);
        }
    }

    /**
     * Raw-ish delete of a node.
     *
     * @param node The node to delete.
     */
    static void dockContextDeleteNode(@NonNull DockNode node) {
        node.tabBar = null;
        if (context.dockContext.nodes.get(node.id) == node) {
            context.dockContext.nodes.remove(node.id);
        }
    }

    /** Data used while pruning unused node settings. */
    private static class PruneNodeData {
        int countWindows;
        int countChildWindows;
        int countChildNodes;
        int rootID;
    }

    /** Garbage collect unused nodes from the settings, run once at init time. */
    static void dockContextPruneUnusedSettingsNodes() {
        final DockContext dc = context.dockContext;
        if (!context.windowByID.isEmpty()) {
            IkGuiImplDebugTools.reportError(
                    log, "Pruning dock settings nodes is only done before any windows are created");
            return;
        }

        final java.util.Map<Integer, PruneNodeData> pool = new java.util.HashMap<>();

        // Count child nodes and compute the root ID
        for (DockNodeSettings settings : dc.nodeSettings) {
            if (pool.containsKey(settings.id)) {
                // Duplicate
                settings.id = 0;
                continue;
            }
            final PruneNodeData parentData =
                    settings.parentNodeID != 0 ? pool.get(settings.parentNodeID) : null;
            pool.computeIfAbsent(settings.id, k -> new PruneNodeData()).rootID =
                    parentData != null ? parentData.rootID : settings.id;
            if (settings.parentNodeID != 0) {
                pool.computeIfAbsent(settings.parentNodeID, k -> new PruneNodeData())
                        .countChildNodes++;
            }
        }

        // Count references to dock IDs from dockspaces. We track the 'auto-DockNode <-
        // manual-Window <- manual-DockSpace' in order to avoid 'auto-DockNode' being ditched.
        for (DockNodeSettings settings : dc.nodeSettings) {
            if (settings.parentWindowID == 0) {
                continue;
            }
            final WindowSettings windowSettings =
                    IkGuiImplConfig.findWindowSettingsByID(settings.parentWindowID);
            if (windowSettings != null && windowSettings.dockID != 0) {
                final PruneNodeData data = pool.get(windowSettings.dockID);
                if (data != null) {
                    data.countChildNodes++;
                }
            }
        }

        // Count references to dock IDs from window settings. We guard against the possibility of
        // an invalid .ini file (the root ID may point to a missing node).
        for (WindowSettings settings : context.settingsWindows) {
            final int dockID = settings.dockID;
            if (dockID == 0) {
                continue;
            }
            final PruneNodeData data = pool.get(dockID);
            if (data == null) {
                continue;
            }
            data.countWindows++;
            final PruneNodeData dataRoot = data.rootID == dockID ? data : pool.get(data.rootID);
            if (dataRoot != null) {
                dataRoot.countChildWindows++;
            }
        }

        // Prune
        for (DockNodeSettings settings : dc.nodeSettings) {
            final PruneNodeData data = pool.get(settings.id);
            if (data == null || data.countWindows > 1) {
                continue;
            }
            final PruneNodeData dataRoot =
                    settings.id == data.rootID ? data : pool.get(data.rootID);
            final PruneNodeData dataParent =
                    settings.parentNodeID != 0 ? pool.get(settings.parentNodeID) : null;

            boolean remove = false;
            // Floating root node with only 1 window
            remove |=
                    data.countWindows == 1
                            && settings.parentNodeID == 0
                            && data.countChildNodes == 0
                            && (settings.flags & DockNodeFlags.INTERNAL_CENTRAL_NODE) == 0;
            // Leaf nodes with 0 windows
            remove |=
                    data.countWindows == 0
                            && settings.parentNodeID == 0
                            && data.countChildNodes == 0;
            remove |= dataRoot == null || dataRoot.countChildWindows == 0;
            if (remove) {
                log.trace(
                        "dockContextPruneUnusedSettingsNodes: prune 0x{}",
                        Integer.toHexString(settings.id));
                IkGuiImplDockSettings.dockSettingsRemoveNodeReferences(new int[] {settings.id});
                settings.id = 0;
            } else if (dataParent != null && dataParent.countChildNodes == 1) {
                log.trace(
                        "dockContextPruneUnusedSettingsNodes: merge 0x{} -> 0x{}",
                        Integer.toHexString(settings.id),
                        Integer.toHexString(settings.parentNodeID));
                IkGuiImplDockSettings.dockSettingsRenameNodeReferences(
                        settings.id, settings.parentNodeID);
                settings.id = 0;
            }
        }
    }

    /**
     * Create nodes for the given settings.
     *
     * @param nodeSettingsList The settings to build nodes from.
     */
    static void dockContextBuildNodesFromSettings(
            @NonNull List<DockNodeSettings> nodeSettingsList) {
        for (DockNodeSettings settings : nodeSettingsList) {
            if (settings.id == 0) {
                continue;
            }
            if (dockContextFindNodeByID(settings.id) != null) {
                log.trace(
                        "dockContextBuildNodesFromSettings: skip duplicate node 0x{}",
                        Integer.toHexString(settings.id));
                continue;
            }
            final DockNode node = dockContextAddNode(settings.id);
            node.parentNode =
                    settings.parentNodeID != 0
                            ? dockContextFindNodeByID(settings.parentNodeID)
                            : null;
            node.position.set(settings.positionX, settings.positionY);
            node.size.set(settings.sizeX, settings.sizeY);
            node.sizeRef.set(settings.sizeRefX, settings.sizeRefY);
            node.authorityForPosition = DataAuthority.DOCK_NODE;
            node.authorityForSize = DataAuthority.DOCK_NODE;
            node.authorityForViewport = DataAuthority.DOCK_NODE;
            if (node.parentNode != null && node.parentNode.childNodes[0] == null) {
                node.parentNode.childNodes[0] = node;
            } else if (node.parentNode != null && node.parentNode.childNodes[1] == null) {
                node.parentNode.childNodes[1] = node;
            }
            node.selectedTabID = settings.selectedTabID;
            node.splitAxis = settings.splitAxis;
            node.setLocalFlags(settings.flags & DockNodeFlags.SAVED_FLAGS_MASK);

            // Bind the host window immediately if it already exists (in case of a rebuild). This
            // is useful as the rootWindowForTitleBarHighlight links necessary to highlight the
            // currently focused node require node.hostWindow to be set.
            final DockNode rootNode = dockNodeGetRootNode(node);
            node.hostWindow = IkGuiInternal.findWindowByName(dockNodeGetHostWindowTitle(rootNode));
        }
    }

    /**
     * Rebind all windows to nodes. They can also lazily rebind, but we'll have a visible glitch
     * during the first frame.
     *
     * @param rootID The ID of the root node to add windows to, or 0 to add all windows.
     */
    static void dockContextBuildAddWindowsToNodes(int rootID) {
        for (Window window : new ArrayList<>(context.windowDisplayOrder)) {
            if (window.dockID == 0 || window.lastFrameActive < context.frameCount - 1) {
                continue;
            }
            if (window.dockNode != null) {
                continue;
            }

            final DockNode node = dockContextFindNodeByID(window.dockID);
            if (node == null) {
                // This should have been called after dockContextBuildNodesFromSettings()
                IkGuiImplDebugTools.reportError(
                        log, "Window {} refers to a missing dock node", window.name);
                continue;
            }
            if (rootID == 0 || dockNodeGetRootNode(node).id == rootID) {
                dockNodeAddWindow(node, window, true);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dock context docking/undocking
    // ---------------------------------------------------------------------------------------------

    /**
     * Queue a request to dock a window.
     *
     * @param target The target window, may be a loose window or a dock node host.
     * @param targetNode The target node, may be null.
     * @param payload The window being docked.
     * @param splitDirection The direction to split the target in, or NONE to dock over it.
     * @param splitRatio The ratio of the split.
     * @param splitOuter Whether this is an outer split (of the root node).
     */
    static void dockContextQueueDock(
            Window target,
            DockNode targetNode,
            @NonNull Window payload,
            @NonNull Direction splitDirection,
            float splitRatio,
            boolean splitOuter) {
        if (target == payload) {
            IkGuiImplDebugTools.reportError(log, "Trying to dock a window into itself");
            return;
        }
        final DockNodeRequest request = new DockNodeRequest();
        request.type = DockRequestType.DOCK;
        request.dockTargetWindow = target;
        request.dockTargetNode = targetNode;
        request.dockPayload = payload;
        request.dockSplitDirection = splitDirection;
        request.dockSplitRatio = splitRatio;
        request.dockSplitOuter = splitOuter;
        context.dockContext.requests.add(request);
    }

    /**
     * Queue a request to undock a window.
     *
     * @param window The window to undock.
     */
    static void dockContextQueueUndockWindow(@NonNull Window window) {
        final DockNodeRequest request = new DockNodeRequest();
        request.type = DockRequestType.UNDOCK;
        request.undockTargetWindow = window;
        context.dockContext.requests.add(request);
    }

    /**
     * Queue a request to undock a whole node.
     *
     * @param node The node to undock.
     */
    static void dockContextQueueUndockNode(@NonNull DockNode node) {
        final DockNodeRequest request = new DockNodeRequest();
        request.type = DockRequestType.UNDOCK;
        request.undockTargetNode = node;
        context.dockContext.requests.add(request);
    }

    /**
     * Cancel requests that target a node that is being removed.
     *
     * @param node The node being removed.
     */
    static void dockContextQueueNotifyRemovedNode(@NonNull DockNode node) {
        for (DockNodeRequest request : context.dockContext.requests) {
            if (request.dockTargetNode == node) {
                request.type = DockRequestType.NONE;
            }
        }
    }

    /**
     * Process a docking or split request.
     *
     * @param request The request.
     */
    static void dockContextProcessDock(@NonNull DockNodeRequest request) {
        if (IkGuiImplDebugTools.isDebugLogEnabled(DebugLogFlags.EVENT_DOCKING)) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_DOCKING,
                    "[docking] DockContextProcessDock node 0x%08X target '%s' dock window '%s',"
                            + " split_dir %s",
                    request.dockTargetNode != null ? request.dockTargetNode.id : 0,
                    IkGuiImplDebugTools.nameOf(request.dockTargetWindow),
                    IkGuiImplDebugTools.nameOf(request.dockPayload),
                    request.dockSplitDirection);
        }
        if (!((request.type == DockRequestType.DOCK && request.dockPayload != null)
                || (request.type == DockRequestType.SPLIT && request.dockPayload == null))) {
            IkGuiImplDebugTools.reportError(log, "Invalid dock request");
            return;
        }
        if (request.dockTargetWindow == null && request.dockTargetNode == null) {
            IkGuiImplDebugTools.reportError(log, "Dock requests need a target window or node");
            return;
        }

        final Window payloadWindow = request.dockPayload;
        final Window targetWindow = request.dockTargetWindow;
        DockNode node = request.dockTargetNode;
        log.trace(
                "dockContextProcessDock node 0x{} target '{}' dock window '{}', split direction {}",
                Integer.toHexString(node != null ? node.id : 0),
                targetWindow != null ? targetWindow.name : "null",
                payloadWindow != null ? payloadWindow.name : "null",
                request.dockSplitDirection);

        // Decide which tab will be selected at the end of the operation
        int nextSelectedID = 0;
        DockNode payloadNode = null;
        if (payloadWindow != null) {
            payloadNode = payloadWindow.dockNodeAsHost;
            // Important to clear this as the node will have its life as a child which might be
            // merged/deleted later
            payloadWindow.dockNodeAsHost = null;
            if (payloadNode != null && payloadNode.isLeafNode() && payloadNode.tabBar != null) {
                nextSelectedID =
                        payloadNode.tabBar.nextSelectedTabID != 0
                                ? payloadNode.tabBar.nextSelectedTabID
                                : payloadNode.tabBar.selectedTabID;
            }
            if (payloadNode == null) {
                nextSelectedID = payloadWindow.idTab;
            }
        }

        // When processing an interactive split, usually lastFrameAlive will be < frameCount. But
        // dock builder operations can make it ==.
        if (node != null && node.lastFrameAlive > context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "Dock target node has an invalid last frame alive");
        }
        if (node != null
                && targetWindow != null
                && node == targetWindow.dockNodeAsHost
                && node.windows.isEmpty()
                && !node.isSplitNode()
                && !node.isCentralNode()) {
            IkGuiImplDebugTools.reportError(
                    log, "Docking into an empty host node that should not exist");
        }

        // Create a new node and add the existing window to it
        if (node == null) {
            node = dockContextAddNode(0);
            node.position.set(targetWindow.position);
            node.size.set(targetWindow.size);
            if (targetWindow.dockNodeAsHost == null) {
                dockNodeAddWindow(node, targetWindow, true);
                node.tabBar.tabs.getFirst().flags &= ~TabItemFlags.INTERNAL_UNSORTED;
                targetWindow.dockIsActive = true;
            }
        }

        final Direction splitDirection = request.dockSplitDirection;
        if (splitDirection != Direction.NONE) {
            // Split into two, one side will be our payload node unless we are dropping a loose
            // window
            final Axis splitAxis =
                    (splitDirection == Direction.LEFT || splitDirection == Direction.RIGHT)
                            ? Axis.X
                            : Axis.Y;
            // Current contents will be moved to the opposite side
            final int splitInheritorChildIndex =
                    (splitDirection == Direction.LEFT || splitDirection == Direction.UP) ? 1 : 0;
            final float splitRatio = request.dockSplitRatio;
            // The payload node may be null here
            dockNodeTreeSplit(node, splitAxis, splitInheritorChildIndex, splitRatio, payloadNode);
            final DockNode newNode = node.childNodes[splitInheritorChildIndex ^ 1];
            newNode.hostWindow = node.hostWindow;
            node = newNode;
        }
        node.setLocalFlags(node.localFlags & ~DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR);

        if (node != payloadNode) {
            // Create the tab bar before we call dockNodeMoveWindows (which would attempt to move
            // the old tab bar, which would lead us to payload tabs wrongly appearing before
            // target tabs!)
            if (!node.windows.isEmpty() && node.tabBar == null) {
                dockNodeAddTabBar(node);
                for (Window window : node.windows) {
                    IkGuiInternal.tabBarAddTab(node.tabBar, TabItemFlags.NONE, window);
                }
            }

            if (payloadNode != null) {
                // Transfer the full payload node (with 1+ child windows or child nodes)
                if (payloadNode.isSplitNode()) {
                    if (!node.windows.isEmpty()) {
                        // We can dock a split payload into a node that already has windows only
                        // if our payload is a node tree with a single visible node. In this
                        // situation, we move the windows of the target node into the currently
                        // visible node of the payload. This allows us to preserve some of the
                        // underlying dock tree settings nicely.
                        final DockNode visibleNode = payloadNode.onlyNodeWithWindows;
                        if (visibleNode == null) {
                            // The docking should have been blocked by
                            // dockNodePreviewDockSetup() early on and never submitted
                            IkGuiImplDebugTools.reportError(
                                    log, "Cannot dock a split node into a node with windows");
                            return;
                        }
                        if (visibleNode.tabBar != null && visibleNode.tabBar.tabs.isEmpty()) {
                            IkGuiImplDebugTools.reportError(
                                    log, "The visible node of the payload has an empty tab bar");
                        }
                        dockNodeMoveWindows(node, visibleNode);
                        dockNodeMoveWindows(visibleNode, node);
                        IkGuiImplDockSettings.dockSettingsRenameNodeReferences(
                                node.id, visibleNode.id);
                    }
                    if (node.isCentralNode()) {
                        // The central node property needs to be moved to a leaf node, pick the
                        // last focused one
                        final DockNode lastFocusedNode =
                                dockContextFindNodeByID(payloadNode.lastFocusedNodeID);
                        if (lastFocusedNode == null) {
                            IkGuiImplDebugTools.reportError(
                                    log, "Payload node has no last focused node");
                            return;
                        }
                        final DockNode lastFocusedRootNode = dockNodeGetRootNode(lastFocusedNode);
                        if (lastFocusedRootNode != dockNodeGetRootNode(payloadNode)) {
                            IkGuiImplDebugTools.reportError(
                                    log, "Last focused node is not in the payload hierarchy");
                        }
                        lastFocusedNode.setLocalFlags(
                                lastFocusedNode.localFlags | DockNodeFlags.INTERNAL_CENTRAL_NODE);
                        node.setLocalFlags(node.localFlags & ~DockNodeFlags.INTERNAL_CENTRAL_NODE);
                        lastFocusedRootNode.centralNode = lastFocusedNode;
                    }

                    if (!node.windows.isEmpty()) {
                        IkGuiImplDebugTools.reportError(
                                log, "Target node still has windows after moving them");
                    }
                    dockNodeMoveChildNodes(node, payloadNode);
                } else {
                    final int payloadDockID = payloadNode.id;
                    dockNodeMoveWindows(node, payloadNode);
                    IkGuiImplDockSettings.dockSettingsRenameNodeReferences(payloadDockID, node.id);
                }
                dockContextRemoveNode(payloadNode, true);
            } else if (payloadWindow != null) {
                // Transfer a single window
                final int payloadDockID = payloadWindow.dockID;
                node.visibleWindow = payloadWindow;
                dockNodeAddWindow(node, payloadWindow, true);
                if (payloadDockID != 0) {
                    IkGuiImplDockSettings.dockSettingsRenameNodeReferences(payloadDockID, node.id);
                }
            }
        } else {
            // When docking a floating single window node we want to reevaluate auto-hiding of the
            // tab bar
            node.wantHiddenTabBarUpdate = true;
        }

        // Update the selection immediately
        if (node.tabBar != null) {
            node.tabBar.nextSelectedTabID = nextSelectedID;
        }
        IkGuiInternal.markIniSettingsDirty();
    }

    /**
     * Undocking a large (~full screen) window would leave it so large that the bottom-right sizing
     * corner would more than likely be off the screen and the window would be hard to resize to fit
     * on screen. This can be particularly problematic with configWindowsMoveFromTitleBarOnly and/or
     * with configWindowsResizeFromEdges disabled. When undocking a window we currently force its
     * maximum size to 90% of the host viewport.
     *
     * @param size The size of the window, which will be clamped.
     * @param referenceViewport The viewport, may be null.
     */
    static void fixLargeWindowsWhenUndocking(@NonNull Vector2f size, Viewport referenceViewport) {
        if (referenceViewport == null) {
            return;
        }
        final Vector2f maxSize = new Vector2f(referenceViewport.workSize).mul(0.90f);
        if (IkGuiImplViewports.viewportsEnabled()) {
            final PlatformMonitor monitor =
                    IkGuiImplViewports.getViewportPlatformMonitor(referenceViewport);
            maxSize.set(monitor.workSize).mul(0.90f);
        }
        IkGuiInternal.truncate(maxSize);
        size.min(maxSize);
    }

    /**
     * Undock a window from its node.
     *
     * @param window The window to undock.
     * @param clearPersistentDockingRef Whether to also forget the dock ID of the window.
     */
    static void dockContextProcessUndockWindow(
            @NonNull Window window, boolean clearPersistentDockingRef) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockContextProcessUndockWindow window '%s'",
                window.name);
        log.trace(
                "dockContextProcessUndockWindow window '{}', clearPersistentDockingRef = {}",
                window.name,
                clearPersistentDockingRef);
        if (window.dockNode != null) {
            dockNodeRemoveWindow(
                    window.dockNode, window, clearPersistentDockingRef ? 0 : window.dockID);
        } else {
            window.dockID = 0;
        }
        window.collapsed = false;
        window.dockIsActive = false;
        window.dockNodeIsVisible = false;
        window.dockTabIsVisible = false;
        fixLargeWindowsWhenUndocking(window.sizeFull, window.viewport);
        window.size.set(window.sizeFull);

        IkGuiInternal.markIniSettingsDirty();
    }

    /**
     * Undock a whole leaf node, so it becomes a floating node.
     *
     * @param node The node to undock.
     */
    static void dockContextProcessUndockNode(@NonNull DockNode node) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockContextProcessUndockNode node %08X",
                node.id);
        log.trace("dockContextProcessUndockNode node 0x{}", Integer.toHexString(node.id));
        if (!node.isLeafNode()) {
            IkGuiImplDebugTools.reportError(log, "Can only undock leaf nodes");
            return;
        }
        if (node.windows.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Can only undock nodes with windows");
            return;
        }

        if (node.isRootNode() || node.isCentralNode()) {
            // In the case of a root node or central node, the node will have to stay in place.
            // Create a new node to receive the payload.
            final DockNode newNode = dockContextAddNode(0);
            newNode.position.set(node.position);
            newNode.size.set(node.size);
            newNode.sizeRef.set(node.sizeRef);
            dockNodeMoveWindows(newNode, node);
            IkGuiImplDockSettings.dockSettingsRenameNodeReferences(node.id, newNode.id);
            node = newNode;
        } else {
            // Otherwise extract our node and merge our sibling back into the parent node
            final DockNode parentNode = node.parentNode;
            final int indexInParent = parentNode.childNodes[0] == node ? 0 : 1;
            parentNode.childNodes[indexInParent] = null;
            dockNodeTreeMerge(parentNode, parentNode.childNodes[indexInParent ^ 1]);
            // The node that stays in place keeps the viewport, so our newly dragged out node will
            // create a new viewport
            parentNode.authorityForViewport = DataAuthority.WINDOW;
            node.parentNode = null;
        }
        for (Window window : node.windows) {
            window.flags &= ~WindowFlags.INTERNAL_CHILD_WINDOW;
            if (window.parentWindow != null) {
                window.parentWindow.childWindows.remove(window);
            }
            IkGuiInternal.updateWindowParentAndRootLinks(window, window.flags, null);
        }
        node.authorityForPosition = DataAuthority.DOCK_NODE;
        node.authorityForSize = DataAuthority.DOCK_NODE;
        fixLargeWindowsWhenUndocking(node.size, node.windows.getFirst().viewport);
        node.wantMouseMove = true;
        IkGuiInternal.markIniSettingsDirty();
    }

    /**
     * Calculate where the mouse would need to be dropped to dock a window or node. This is mostly
     * used for automation.
     *
     * @param target The target window.
     * @param targetNode The target node, may be null to use the dock node of the target.
     * @param payloadWindow The payload window.
     * @param payloadNode The payload node, may be null.
     * @param splitDirection The direction to split.
     * @param splitOuter Whether to use the outer split.
     * @param outPosition Where to store the drop position.
     * @return True if the drop is possible, false if not.
     */
    static boolean dockContextCalcDropPosForDocking(
            @NonNull Window target,
            DockNode targetNode,
            @NonNull Window payloadWindow,
            DockNode payloadNode,
            @NonNull Direction splitDirection,
            boolean splitOuter,
            @NonNull Vector2f outPosition) {
        if (targetNode == null) {
            targetNode = target.dockNode;
        }

        // In dockNodePreviewDockSetup() for a root central node instead of showing both "inner"
        // and "outer" drop rects (which would be functionally identical) we only show the outer
        // one. Reflect this here.
        if (targetNode != null
                && targetNode.parentNode == null
                && targetNode.isCentralNode()
                && splitDirection != Direction.NONE) {
            splitOuter = true;
        }
        final DockPreviewData splitData = new DockPreviewData();
        dockNodePreviewDockSetup(
                target, targetNode, payloadWindow, payloadNode, splitData, false, splitOuter);
        final RectFloat rect = splitData.dropRectsDraw[directionIndex(splitDirection) + 1];
        if (rect.isInverted()) {
            return false;
        }
        outPosition.set(rect.getCenterX(), rect.getCenterY());
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Dock nodes
    // ---------------------------------------------------------------------------------------------

    /**
     * The index of a direction, matching ImGui's ImGuiDir values (NONE is -1).
     *
     * @param direction The direction.
     * @return The index.
     */
    static int directionIndex(@NonNull Direction direction) {
        return switch (direction) {
            case NONE -> -1;
            case LEFT -> 0;
            case RIGHT -> 1;
            case UP -> 2;
            case DOWN -> 3;
        };
    }

    /** The directions in index order, starting with NONE. */
    private static final Direction[] DIRECTIONS_WITH_NONE = {
        Direction.NONE, Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN
    };

    /**
     * The window title of the host window of a dock node.
     *
     * @param node The node.
     * @return The title.
     */
    static String dockNodeGetHostWindowTitle(@NonNull DockNode node) {
        return String.format("##DockNode_%02X", node.id);
    }

    /**
     * Find the order of the tab of a docked window in its node tab bar.
     *
     * @param window The window.
     * @return The tab order, or -1 if not found.
     */
    static int dockNodeGetTabOrder(@NonNull Window window) {
        final TabBar tabBar = window.dockNode.tabBar;
        if (tabBar == null) {
            return -1;
        }
        final TabItem tab = IkGuiInternal.tabBarFindTabByID(tabBar, window.idTab);
        return tab != null ? IkGuiImplTabs.tabBarGetTabOrder(tabBar, tab) : -1;
    }

    /**
     * Hide a window while the host window of its node is being created.
     *
     * @param window The window.
     */
    static void dockNodeHideWindowDuringHostWindowCreation(@NonNull Window window) {
        window.hidden = true;
        window.hiddenFramesCanSkipItems.set(window.active ? 1 : 2);
    }

    /**
     * Add a window to a dock node.
     *
     * @param node The node.
     * @param window The window.
     * @param addToTabBar Whether to add the window to the tab bar of the node.
     */
    static void dockNodeAddWindow(
            @NonNull DockNode node, @NonNull Window window, boolean addToTabBar) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockNodeAddWindow node 0x%08X window '%s'",
                node.id,
                window.name);
        if (window.dockNode != null) {
            // Can overwrite an existing window.dockNode (e.g. pointing to a disabled dockspace
            // node)
            if (window.dockNode.id == node.id) {
                IkGuiImplDebugTools.reportError(
                        log, "Adding window {} to a node it is already in", window.name);
                return;
            }
            dockNodeRemoveWindow(window.dockNode, window, 0);
        }
        if (window.dockNode != null && window.dockNodeAsHost != null) {
            IkGuiImplDebugTools.reportError(log, "A window cannot be both docked and a dock host");
            return;
        }
        log.trace(
                "dockNodeAddWindow node 0x{} window '{}'",
                Integer.toHexString(node.id),
                window.name);

        // If more than 2 windows appeared on the same frame leading to the creation of a new
        // hosting window, we'll hide windows until the host window is ready. Hide the first
        // window after it's been output (so it is not visible for one frame). We will call
        // dockNodeHideWindowDuringHostWindowCreation() on ourselves in begin().
        if (node.hostWindow == null
                && node.windows.size() == 1
                && !node.windows.getFirst().wasActive) {
            dockNodeHideWindowDuringHostWindowCreation(node.windows.getFirst());
        }

        node.windows.add(window);
        node.wantHiddenTabBarUpdate = true;
        window.dockNode = node;
        window.dockID = node.id;
        window.dockIsActive = node.windows.size() > 1;
        window.dockTabWantClose = false;

        // When reactivating a node with one or two loose windows, the window pos/size/viewport are
        // authoritative over the node storage. In particular, it is important we initialize the
        // viewport from the first window, so we don't create two viewports and drop one.
        if (node.hostWindow == null && node.isFloatingNode()) {
            if (node.authorityForPosition == DataAuthority.AUTO) {
                node.authorityForPosition = DataAuthority.WINDOW;
            }
            if (node.authorityForSize == DataAuthority.AUTO) {
                node.authorityForSize = DataAuthority.WINDOW;
            }
            if (node.authorityForViewport == DataAuthority.AUTO) {
                node.authorityForViewport = DataAuthority.WINDOW;
            }
        }

        // Add to the tab bar if requested
        if (addToTabBar) {
            if (node.tabBar == null) {
                dockNodeAddTabBar(node);
                node.tabBar.selectedTabID = node.selectedTabID;
                node.tabBar.nextSelectedTabID = node.selectedTabID;

                // Add existing windows
                for (int n = 0; n < node.windows.size() - 1; ++n) {
                    IkGuiInternal.tabBarAddTab(node.tabBar, TabItemFlags.NONE, node.windows.get(n));
                }
            }
            IkGuiInternal.tabBarAddTab(node.tabBar, TabItemFlags.INTERNAL_UNSORTED, window);
        }

        dockNodeUpdateVisibleFlag(node);

        // Update this without waiting for the next time we begin() in the window, so our host
        // window will have the proper title bar color on its first frame
        if (node.hostWindow != null) {
            IkGuiInternal.updateWindowParentAndRootLinks(
                    window, window.flags | WindowFlags.INTERNAL_CHILD_WINDOW, node.hostWindow);
        }
    }

    /**
     * Remove a window from a dock node. This may delete the node if it is automatic and becomes
     * empty.
     *
     * @param node The node.
     * @param window The window.
     * @param saveDockID The dock ID to keep in the window, 0 or the node ID.
     */
    static void dockNodeRemoveWindow(
            @NonNull DockNode node, @NonNull Window window, int saveDockID) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockNodeRemoveWindow node 0x%08X window '%s'",
                node.id,
                window.name);
        if (window.dockNode != node) {
            IkGuiImplDebugTools.reportError(
                    log, "Window {} is not in the dock node it is being removed from", window.name);
            return;
        }
        if (saveDockID != 0 && saveDockID != node.id) {
            IkGuiImplDebugTools.reportError(
                    log, "Trying to save an invalid dock node ID while removing a window");
            return;
        }
        log.trace(
                "dockNodeRemoveWindow node 0x{} window '{}'",
                Integer.toHexString(node.id),
                window.name);

        window.dockNode = null;
        window.dockIsActive = false;
        window.dockTabWantClose = false;
        window.dockID = saveDockID;
        window.flags &= ~WindowFlags.INTERNAL_CHILD_WINDOW;
        if (window.parentWindow != null) {
            window.parentWindow.childWindows.remove(window);
        }
        // Update immediately
        IkGuiInternal.updateWindowParentAndRootLinks(window, window.flags, null);

        if (node.hostWindow != null && node.hostWindow.viewportOwned) {
            // When undocking from a user interaction this will always run in newFrame() and have
            // not much effect. But mid-frame, if we clear the viewport we need to mark the window
            // as hidden as well.
            window.viewport = null;
            window.viewportID = 0;
            window.viewportOwned = false;
            window.hidden = true;
        }

        // Remove the window
        if (!node.windows.remove(window)) {
            IkGuiImplDebugTools.reportError(
                    log, "Could not find window {} in the dock node", window.name);
        }
        if (node.visibleWindow == window) {
            node.visibleWindow = null;
        }

        // Remove the tab and possibly the tab bar
        node.wantHiddenTabBarUpdate = true;
        if (node.tabBar != null) {
            IkGuiInternal.tabBarRemoveTab(node.tabBar, window.idTab);
            final int tabCountThresholdForTabBar = node.isCentralNode() ? 1 : 2;
            if (node.windows.size() < tabCountThresholdForTabBar) {
                dockNodeRemoveTabBar(node);
            }
        }

        if (node.windows.isEmpty()
                && !node.isCentralNode()
                && !node.isDockSpace()
                && window.dockID != node.id) {
            // Automatic dock nodes delete themselves if they are not holding at least one tab
            dockContextRemoveNode(node, true);
            return;
        }

        if (node.windows.size() == 1 && !node.isCentralNode() && node.hostWindow != null) {
            final Window remainingWindow = node.windows.getFirst();
            remainingWindow.collapsed = node.hostWindow.collapsed;
        }

        // Updating the visibility immediately is required so the dockNodeUpdateFlagsAndCollapse()
        // processing can reflect changes up the tree
        dockNodeUpdateVisibleFlag(node);
    }

    /**
     * Move the child nodes from one node to another.
     *
     * @param destinationNode The node to move children into, which must not have windows.
     * @param sourceNode The node to move children out of.
     */
    static void dockNodeMoveChildNodes(
            @NonNull DockNode destinationNode, @NonNull DockNode sourceNode) {
        if (!destinationNode.windows.isEmpty()) {
            IkGuiImplDebugTools.reportError(
                    log, "Destination node already has windows, cannot move child nodes");
            return;
        }
        destinationNode.childNodes[0] = sourceNode.childNodes[0];
        destinationNode.childNodes[1] = sourceNode.childNodes[1];
        if (destinationNode.childNodes[0] != null) {
            destinationNode.childNodes[0].parentNode = destinationNode;
        }
        if (destinationNode.childNodes[1] != null) {
            destinationNode.childNodes[1].parentNode = destinationNode;
        }
        destinationNode.splitAxis = sourceNode.splitAxis;
        destinationNode.sizeRef.set(sourceNode.sizeRef);
        sourceNode.childNodes[0] = null;
        sourceNode.childNodes[1] = null;
    }

    /**
     * Move the windows from one node to another.
     *
     * @param destinationNode The node to move windows into.
     * @param sourceNode The node to move windows out of.
     */
    static void dockNodeMoveWindows(
            @NonNull DockNode destinationNode, @NonNull DockNode sourceNode) {
        // Insert tabs in the same orders as currently ordered (node.windows isn't ordered)
        if (destinationNode == sourceNode) {
            IkGuiImplDebugTools.reportError(
                    log, "Trying to move windows but source and destination are the same");
            return;
        }
        final TabBar sourceTabBar = sourceNode.tabBar;
        if (sourceTabBar != null && sourceNode.windows.size() > sourceTabBar.tabs.size()) {
            IkGuiImplDebugTools.reportError(
                    log, "The source node has more windows than its tab bar has tabs");
        }

        // If the destination node is empty we can just move the entire tab bar (to preserve
        // selection, scrolling, etc.)
        final boolean moveTabBar = sourceTabBar != null && destinationNode.tabBar == null;
        if (moveTabBar) {
            destinationNode.tabBar = sourceNode.tabBar;
            sourceNode.tabBar = null;
        }

        // Tab order is not important here, it is preserved by sorting in dockNodeUpdateTabBar()
        for (Window window : new ArrayList<>(sourceNode.windows)) {
            window.dockNode = null;
            window.dockIsActive = false;
            dockNodeAddWindow(destinationNode, window, !moveTabBar);
        }
        sourceNode.windows.clear();

        if (!moveTabBar && sourceNode.tabBar != null) {
            if (destinationNode.tabBar != null) {
                destinationNode.tabBar.selectedTabID = sourceNode.tabBar.selectedTabID;
            }
            dockNodeRemoveTabBar(sourceNode);
        }
    }

    /**
     * Set the position and size of all the windows in a node to match the node.
     *
     * @param node The node.
     */
    static void dockNodeApplyPosSizeToWindows(@NonNull DockNode node) {
        for (Window window : node.windows) {
            // We don't assign directly to the position because it can break the calculation of the
            // content size on the next frame
            IkGuiImplWindows.setWindowPos(
                    window, node.position.x, node.position.y, Condition.ALWAYS);
            IkGuiImplWindows.setWindowSize(window, node.size.x, node.size.y, Condition.ALWAYS);
        }
    }

    /**
     * Unbind the host window of a node.
     *
     * @param node The node.
     */
    static void dockNodeHideHostWindow(@NonNull DockNode node) {
        if (node.hostWindow != null) {
            if (node.hostWindow.dockNodeAsHost == node) {
                node.hostWindow.dockNodeAsHost = null;
            }
            node.hostWindow = null;
        }

        if (node.windows.size() == 1) {
            node.visibleWindow = node.windows.getFirst();
            node.windows.getFirst().dockIsActive = false;
        }

        if (node.tabBar != null) {
            dockNodeRemoveTabBar(node);
        }
    }

    /** Results of searching a node tree, from dockNodeFindInfo(). */
    private static class DockNodeTreeInfo {
        DockNode centralNode;
        DockNode firstNodeWithWindows;
        int countNodesWithWindows;
    }

    /**
     * Search a node tree for the central node and nodes with windows. Called once by the root node
     * in dockNodeUpdate().
     *
     * @param node The node to search.
     * @param info The results.
     */
    private static void dockNodeFindInfo(@NonNull DockNode node, @NonNull DockNodeTreeInfo info) {
        if (!node.windows.isEmpty()) {
            if (info.firstNodeWithWindows == null) {
                info.firstNodeWithWindows = node;
            }
            info.countNodesWithWindows++;
        }
        if (node.isCentralNode()) {
            if (info.centralNode != null) {
                IkGuiImplDebugTools.reportError(
                        log, "There should only be one central node in a dock node tree");
            }
            if (!node.isLeafNode()) {
                IkGuiImplDebugTools.reportError(log, "Central nodes must be leaf nodes");
            }
            info.centralNode = node;
        }
        if (info.countNodesWithWindows > 1 && info.centralNode != null) {
            return;
        }
        if (node.childNodes[0] != null) {
            dockNodeFindInfo(node.childNodes[0], info);
        }
        if (node.childNodes[1] != null) {
            dockNodeFindInfo(node.childNodes[1], info);
        }
    }

    /**
     * Find a window in a node by ID.
     *
     * @param node The node.
     * @param id The window ID.
     * @return The window, or null if not in the node.
     */
    static Window dockNodeFindWindowByID(@NonNull DockNode node, int id) {
        if (id == 0) {
            IkGuiImplDebugTools.reportError(log, "Looking up a window with ID 0 in a dock node");
            return null;
        }
        for (Window window : node.windows) {
            if (window.id == id) {
                return window;
            }
        }
        return null;
    }

    /**
     * Remove inactive windows and nodes, and update the visibility flag.
     *
     * @param node The node.
     */
    static void dockNodeUpdateFlagsAndCollapse(@NonNull DockNode node) {
        if (node.parentNode != null
                && node.parentNode.childNodes[0] != node
                && node.parentNode.childNodes[1] != node) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Dock node 0x{} is not a child of its parent",
                    Integer.toHexString(node.id));
        }

        // Inherit most flags
        if (node.parentNode != null) {
            node.sharedFlags =
                    node.parentNode.sharedFlags & DockNodeFlags.SHARED_FLAGS_INHERIT_MASK;
        }

        // Recurse into children. There is the possibility that one of our children becoming empty
        // will delete itself and move its sibling contents into 'node'. If childNodes[0] deletes
        // itself, then childNodes[1].windows will be moved into 'node'. If childNodes[1] deletes
        // itself, then childNodes[0].windows will be moved into 'node' and the "remove inactive
        // windows" loop will have run twice on those windows (harmless).
        node.hasCentralNodeChild = false;
        if (node.childNodes[0] != null) {
            dockNodeUpdateFlagsAndCollapse(node.childNodes[0]);
        }
        if (node.childNodes[1] != null) {
            dockNodeUpdateFlagsAndCollapse(node.childNodes[1]);
        }

        // Remove inactive windows, collapse nodes. Merge node flag overrides stored in windows.
        node.localFlagsInWindows = DockNodeFlags.NONE;
        for (int windowIndex = 0; windowIndex < node.windows.size(); ++windowIndex) {
            final Window window = node.windows.get(windowIndex);
            if (window.dockNode != node) {
                IkGuiImplDebugTools.reportError(
                        log, "Window {} in dock node has a different dock node", window.name);
            }

            final boolean nodeWasActive = node.lastFrameActive + 1 == context.frameCount;
            boolean remove = nodeWasActive && !window.wasActive;
            // Submit all expected closures from the last frame
            remove |=
                    nodeWasActive
                            && (node.wantCloseAll || node.wantCloseTabID == window.idTab)
                            && window.hasCloseButton
                            && (window.flags & WindowFlags.UNSAVED_DOCUMENT) == 0;
            remove |= window.dockTabWantClose;
            if (remove) {
                window.dockTabWantClose = false;
                if (node.windows.size() == 1 && !node.isCentralNode()) {
                    dockNodeHideHostWindow(node);
                    node.state = DockNodeState.HOST_WINDOW_HIDDEN_BECAUSE_SINGLE_WINDOW;
                    // Will delete the node so it'll be invalid on return
                    dockNodeRemoveWindow(node, window, node.id);
                    return;
                }
                dockNodeRemoveWindow(node, window, node.id);
                --windowIndex;
                continue;
            }

            node.localFlagsInWindows |= window.windowClass.dockNodeFlagsOverrideSet;
        }
        node.updateMergedFlags();

        // Auto-hide tab bar option
        final int nodeFlags = node.mergedFlags;
        if (node.wantHiddenTabBarUpdate
                && node.windows.size() == 1
                && (nodeFlags & DockNodeFlags.AUTO_HIDE_TAB_BAR) != 0
                && !node.isHiddenTabBar()) {
            node.wantHiddenTabBarToggle = true;
        }
        node.wantHiddenTabBarUpdate = false;

        // Cancel toggling if we know our tab bar is enforced to be hidden at all times
        if (node.wantHiddenTabBarToggle
                && node.visibleWindow != null
                && (node.visibleWindow.windowClass.dockNodeFlagsOverrideSet
                                & DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR)
                        != 0) {
            node.wantHiddenTabBarToggle = false;
        }

        // Apply toggles at a single point of the frame (here!)
        final int previousLocalFlags = node.localFlags;
        if (node.windows.size() > 1) {
            node.setLocalFlags(node.localFlags & ~DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR);
        } else if (node.wantHiddenTabBarToggle) {
            node.setLocalFlags(node.localFlags ^ DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR);
        }
        if (((node.localFlags ^ previousLocalFlags) & DockNodeFlags.SAVED_FLAGS_MASK) != 0) {
            IkGuiInternal.markIniSettingsDirty();
        }
        node.wantHiddenTabBarToggle = false;

        dockNodeUpdateVisibleFlag(node);
    }

    /**
     * Update the flags marking which nodes have the central node in their hierarchy. This is rarely
     * called, as dockNodeUpdateForRootNode() generally does it most frames.
     *
     * @param node The node.
     */
    static void dockNodeUpdateHasCentralNodeChild(@NonNull DockNode node) {
        node.hasCentralNodeChild = false;
        if (node.childNodes[0] != null) {
            dockNodeUpdateHasCentralNodeChild(node.childNodes[0]);
        }
        if (node.childNodes[1] != null) {
            dockNodeUpdateHasCentralNodeChild(node.childNodes[1]);
        }
        if (node.isRootNode()) {
            DockNode markNode = node.centralNode;
            while (markNode != null) {
                markNode.hasCentralNodeChild = true;
                markNode = markNode.parentNode;
            }
        }
    }

    /**
     * Update the visibility flag of a node.
     *
     * @param node The node.
     */
    static void dockNodeUpdateVisibleFlag(@NonNull DockNode node) {
        boolean isVisible = node.parentNode == null ? node.isDockSpace() : node.isCentralNode();
        isVisible |= !node.windows.isEmpty();
        isVisible |= node.childNodes[0] != null && node.childNodes[0].isVisible;
        isVisible |= node.childNodes[1] != null && node.childNodes[1].isVisible;
        node.isVisible = isVisible;
    }

    /**
     * Start moving a window after a node was extracted.
     *
     * @param node The node.
     * @param window The window to move.
     */
    static void dockNodeStartMouseMovingWindow(@NonNull DockNode node, @NonNull Window window) {
        if (!node.wantMouseMove) {
            IkGuiImplDebugTools.reportError(
                    log, "Starting to move a node that did not want to move");
        }
        IkGuiInternal.startMouseMovingWindow(window);
        context.activeIDClickOffset
                .set(context.io.mouseClickedPosition[MouseButton.LEFT.index])
                .sub(node.position);
        // If we are docked into a non-movable root window, startMouseMovingWindow() won't set the
        // moving window. Override that decision.
        context.windowMoving = window;
        node.wantMouseMove = false;
    }

    /**
     * Update the central node, the only node with windows, and the last focused node. Copy the
     * window class.
     *
     * @param node The root node.
     */
    static void dockNodeUpdateForRootNode(@NonNull DockNode node) {
        dockNodeUpdateFlagsAndCollapse(node);

        // - Set up central node pointers
        // - Find if there's only a single visible window in the hierarchy (in which case we need
        //   to display a regular title bar)
        // Cannot merge this with dockNodeUpdateFlagsAndCollapse() because firstNodeWithWindows is
        // found after window removal and child collapsing
        final DockNodeTreeInfo info = new DockNodeTreeInfo();
        dockNodeFindInfo(node, info);
        node.centralNode = info.centralNode;
        node.onlyNodeWithWindows =
                info.countNodesWithWindows == 1 ? info.firstNodeWithWindows : null;
        node.countNodeWithWindows = info.countNodesWithWindows;
        if (node.lastFocusedNodeID == 0 && info.firstNodeWithWindows != null) {
            node.lastFocusedNodeID = info.firstNodeWithWindows.id;
        }

        // Copy the window class from our first window so it can be used for proper dock
        // filtering. When a node has mixed windows, prioritize the class with the most
        // constraints (dockingAllowUnclassed = false) as the reference to copy.
        final DockNode firstNodeWithWindows = info.firstNodeWithWindows;
        if (firstNodeWithWindows != null) {
            node.windowClass.set(firstNodeWithWindows.windows.getFirst().windowClass);
            for (int n = 1; n < firstNodeWithWindows.windows.size(); ++n) {
                final Window window = firstNodeWithWindows.windows.get(n);
                if (!window.windowClass.dockingAllowUnclassed) {
                    node.windowClass.set(window.windowClass);
                    break;
                }
            }
        }

        DockNode markNode = node.centralNode;
        while (markNode != null) {
            markNode.hasCentralNodeChild = true;
            markNode = markNode.parentNode;
        }
    }

    /**
     * Bind a host window to a node.
     *
     * @param node The node.
     * @param hostWindow The host window.
     */
    private static void dockNodeSetupHostWindow(
            @NonNull DockNode node, @NonNull Window hostWindow) {
        // Remove ourselves from any previous different host window. This can happen if a user
        // mistakenly adds a node without the dockspace flag, then a new frame creates a floating
        // host window for that node, then dockSpace() requalifies the node as a dockspace, moving
        // the host window.
        if (node.hostWindow != null
                && node.hostWindow != hostWindow
                && node.hostWindow.dockNodeAsHost == node) {
            node.hostWindow.dockNodeAsHost = null;
        }

        hostWindow.dockNodeAsHost = node;
        node.hostWindow = hostWindow;
    }

    /**
     * The main update function for a dock node, which creates the host window for floating nodes,
     * lays out the node tree, and submits the tab bars.
     *
     * @param node The node.
     */
    static void dockNodeUpdate(@NonNull DockNode node) {
        if (node.lastFrameActive == context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "Dock node 0x{} updated twice in one frame", Integer.toHexString(node.id));
            return;
        }
        node.lastFrameAlive = context.frameCount;
        node.isBackgroundDrawnThisFrame = false;

        node.centralNode = null;
        node.onlyNodeWithWindows = null;
        if (node.isRootNode()) {
            dockNodeUpdateForRootNode(node);
            // The root node may have been deleted if its last window was removed
            if (!isNodeAlive(node)) {
                return;
            }
        }

        // Remove the tab bar if not needed
        if (node.tabBar != null && node.isNoTabBar()) {
            dockNodeRemoveTabBar(node);
        }

        // Early out for hidden root dock nodes (when all dock ID references are in inactive
        // windows, or there is only 1 floating window holding on to the dock ID)
        boolean wantToHideHostWindow = false;
        if (node.isFloatingNode()) {
            if (node.windows.size() <= 1
                    && node.isLeafNode()
                    && !context.io.configDockingAlwaysTabBar
                    && (node.windows.isEmpty()
                            || !node.windows.getFirst().windowClass.dockingAlwaysTabBar)) {
                wantToHideHostWindow = true;
            }
            if (node.countNodeWithWindows == 0) {
                wantToHideHostWindow = true;
            }
        }
        if (wantToHideHostWindow) {
            if (node.windows.size() == 1) {
                // The floating window pos/size is authoritative
                final Window singleWindow = node.windows.getFirst();
                node.position.set(singleWindow.position);
                node.size.set(singleWindow.sizeFull);
                node.authorityForPosition = DataAuthority.WINDOW;
                node.authorityForSize = DataAuthority.WINDOW;
                node.authorityForViewport = DataAuthority.WINDOW;

                // Transfer focus immediately so when we revert to a regular window it is
                // immediately selected
                if (node.hostWindow != null && context.navFocusedWindow == node.hostWindow) {
                    IkGuiInternal.focusWindow(singleWindow, WindowFocusRequestFlags.NONE);
                }
                if (node.hostWindow != null) {
                    IkGuiImplDebugTools.debugLog(
                            DebugLogFlags.EVENT_VIEWPORT,
                            "[viewport] Node %08X transfer Viewport %08X->%08X to Window '%s'",
                            node.id,
                            node.hostWindow.viewportID,
                            singleWindow.id,
                            singleWindow.name);
                    singleWindow.viewport = node.hostWindow.viewport;
                    singleWindow.viewportID = node.hostWindow.viewportID;
                    if (node.hostWindow.viewportOwned) {
                        singleWindow.viewport.id = singleWindow.id;
                        singleWindow.viewport.window = singleWindow;
                        singleWindow.viewportOwned = true;
                    }
                }
                node.refViewportID = singleWindow.viewportID;
            }

            dockNodeHideHostWindow(node);
            node.state = DockNodeState.HOST_WINDOW_HIDDEN_BECAUSE_SINGLE_WINDOW;
            node.wantCloseAll = false;
            node.wantCloseTabID = 0;
            node.hasCloseButton = false;
            node.hasWindowMenuButton = false;
            node.lastFrameActive = context.frameCount;

            if (node.wantMouseMove && node.windows.size() == 1) {
                dockNodeStartMouseMovingWindow(node, node.windows.getFirst());
            }
            return;
        }

        // In some circumstances we will defer creating the host window (so everything will be
        // kept hidden), while the expected visible window is resizing itself. This is important
        // for first-time (no ini settings restored) single windows when configDockingAlwaysTabBar
        // is enabled, otherwise the node ends up using the minimum window size. Effectively those
        // windows will take an extra frame to show up:
        // N+0: begin(): window created (with no known size), node is created
        // N+1: dockNodeUpdate(): node skips creating host window / begin(): window size applied,
        //      not visible
        // N+2: dockNodeUpdate(): node can create host window / begin(): window becomes visible
        if (node.isVisible
                && node.hostWindow == null
                && node.isFloatingNode()
                && node.isLeafNode()) {
            if (node.windows.isEmpty()) {
                IkGuiImplDebugTools.reportError(
                        log, "Visible floating leaf dock node has no windows");
                return;
            }
            Window referenceWindow = null;
            // Note that we prune single-window-node settings on .ini loading, so this is generally
            // 0 for them!
            if (node.selectedTabID != 0) {
                referenceWindow = dockNodeFindWindowByID(node, node.selectedTabID);
            }
            if (referenceWindow == null) {
                referenceWindow = node.windows.getFirst();
            }
            if (referenceWindow.autoFitFramesX.get() > 0
                    || referenceWindow.autoFitFramesY.get() > 0) {
                node.state = DockNodeState.HOST_WINDOW_HIDDEN_BECAUSE_WINDOWS_ARE_RESIZING;
                return;
            }
        }

        final int nodeFlags = node.mergedFlags;

        // Decide if the node will have a close button and a window menu button
        node.hasWindowMenuButton =
                !node.windows.isEmpty()
                        && (nodeFlags & DockNodeFlags.INTERNAL_NO_WINDOW_MENU_BUTTON) == 0;
        node.hasCloseButton = false;
        for (Window window : node.windows) {
            // Setting dockIsActive here means that for a single active window in a leaf node,
            // dockIsActive will be cleared until the next begin() call
            node.hasCloseButton |= window.hasCloseButton;
            window.dockIsActive = node.windows.size() > 1;
        }
        if ((nodeFlags & DockNodeFlags.INTERNAL_NO_CLOSE_BUTTON) != 0
                || !context.style.variable.dockingNodeHasCloseButton) {
            node.hasCloseButton = false;
        }

        // Bind or create the host window
        Window hostWindow = null;
        boolean beganIntoHostWindow = false;
        if (node.isDockSpace()) {
            // [Explicit root dockspace node]
            if (node.hostWindow == null) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Dockspace node 0x{} has no host window",
                        Integer.toHexString(node.id));
                return;
            }
            hostWindow = node.hostWindow;
        } else {
            // [Automatic root or child nodes]
            if (node.isRootNode() && node.isVisible) {
                final Window referenceWindow =
                        !node.windows.isEmpty() ? node.windows.getFirst() : null;

                // Sync position
                if (node.authorityForPosition == DataAuthority.WINDOW && referenceWindow != null) {
                    IkGuiImplWindows.setNextWindowPos(
                            referenceWindow.position.x,
                            referenceWindow.position.y,
                            Condition.NONE,
                            0.0f,
                            0.0f);
                } else if (node.authorityForPosition == DataAuthority.DOCK_NODE) {
                    IkGuiImplWindows.setNextWindowPos(
                            node.position.x, node.position.y, Condition.NONE, 0.0f, 0.0f);
                }

                // Sync size
                if (node.authorityForSize == DataAuthority.WINDOW && referenceWindow != null) {
                    IkGuiImplWindows.setNextWindowSize(
                            referenceWindow.sizeFull.x, referenceWindow.sizeFull.y, Condition.NONE);
                } else if (node.authorityForSize == DataAuthority.DOCK_NODE) {
                    IkGuiImplWindows.setNextWindowSize(node.size.x, node.size.y, Condition.NONE);
                }

                // Sync collapsed
                if (node.authorityForSize == DataAuthority.WINDOW && referenceWindow != null) {
                    IkGuiImplWindows.setNextWindowCollapsed(
                            referenceWindow.collapsed, Condition.NONE);
                }

                // Sync viewport
                if (node.authorityForViewport == DataAuthority.WINDOW && referenceWindow != null) {
                    IkGuiImplViewports.setNextWindowViewport(referenceWindow.viewportID);
                } else if (node.authorityForViewport == DataAuthority.WINDOW
                        && node.refViewportID != 0) {
                    IkGuiImplViewports.setNextWindowViewport(node.refViewportID);
                }

                IkGuiImplWindows.setNextWindowClass(node.windowClass);

                // Begin into the host window
                final String windowLabel = dockNodeGetHostWindowTitle(node);
                final int windowFlags =
                        WindowFlags.NO_SCROLLBAR
                                | WindowFlags.NO_SCROLL_WITH_MOUSE
                                | WindowFlags.INTERNAL_DOCK_NODE_HOST
                                | WindowFlags.NO_FOCUS_ON_APPEARING
                                | WindowFlags.NO_SAVED_SETTINGS
                                | WindowFlags.NO_NAV_FOCUS
                                | WindowFlags.NO_COLLAPSE
                                | WindowFlags.NO_TITLE_BAR;

                // Don't set NO_BACKGROUND because it disables borders
                IkGuiImplWindows.setNextWindowBgAlpha(0.0f);
                IkGuiImplUtils.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
                IkGuiImplWindows.begin(windowLabel, null, windowFlags);
                IkGuiImplUtils.popStyleVar();
                beganIntoHostWindow = true;

                hostWindow = context.windowCurrent;
                dockNodeSetupHostWindow(node, hostWindow);
                hostWindow.cursorPosition.set(hostWindow.position);
                node.position.set(hostWindow.position);
                node.size.set(hostWindow.size);

                // We set NO_FOCUS_ON_APPEARING because we don't want the host window to take full
                // focus (e.g. steal the nav window), but we still bring it to the front of the
                // display. There's no way to choose this precise behavior via window flags.
                if (node.hostWindow.appearing) {
                    IkGuiInternal.bringWindowToDisplayFront(node.hostWindow);
                }

                node.authorityForPosition = DataAuthority.AUTO;
                node.authorityForSize = DataAuthority.AUTO;
                node.authorityForViewport = DataAuthority.AUTO;
            } else if (node.parentNode != null) {
                hostWindow = node.parentNode.hostWindow;
                node.hostWindow = hostWindow;
                node.authorityForPosition = DataAuthority.AUTO;
                node.authorityForSize = DataAuthority.AUTO;
                node.authorityForViewport = DataAuthority.AUTO;
            }
            if (node.wantMouseMove && node.hostWindow != null) {
                dockNodeStartMouseMovingWindow(node, node.hostWindow);
            }
        }
        // Clear when we have a host window
        node.refViewportID = 0;

        // Update the focused node (the one whose title bar is highlighted) within a node tree
        if (node.isSplitNode() && node.tabBar != null) {
            IkGuiImplDebugTools.reportError(log, "Split dock nodes should not have a tab bar");
        }
        if (node.isRootNode() && context.navFocusedWindow != null) {
            Window parentWindow = context.navFocusedWindow.rootWindow;
            while (parentWindow != null && parentWindow.dockNode != null) {
                final DockNode parentNode = dockNodeGetRootNode(parentWindow.dockNode);
                if (parentNode == node) {
                    // Note: not using the root node ID!
                    node.lastFocusedNodeID = parentWindow.dockNode.id;
                    break;
                }
                parentWindow =
                        parentNode.hostWindow != null ? parentNode.hostWindow.rootWindow : null;
            }
        }

        // Register a hit-test hole in the window unless we are currently dragging a window that is
        // compatible with our dockspace
        final DockNode centralNode = node.centralNode;
        final boolean centralNodeHole =
                node.isRootNode()
                        && hostWindow != null
                        && (nodeFlags & DockNodeFlags.PASSTHROUGH_CENTRAL_NODE) != 0
                        && centralNode != null
                        && centralNode.isEmpty();
        boolean centralNodeHoleRegisterHitTestHole = centralNodeHole;
        if (centralNodeHole && context.dragDropActive) {
            final Payload payload = context.dragDropPayload;
            if (payload.isDataType(IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW)
                    && payload.data instanceof Window payloadWindow
                    && dockNodeIsDropAllowed(hostWindow, payloadWindow)) {
                centralNodeHoleRegisterHitTestHole = false;
            }
        }
        if (centralNodeHoleRegisterHitTestHole) {
            // We add a little padding to match the "resize from edges" behavior and allow
            // grabbing the splitter easily. (But we only add it if there's something else on the
            // other side of the hole, otherwise for e.g. a fullscreen covering passthrough node
            // we'd have a gap on the edge not covered by the hole.)
            if (!node.isDockSpace()) {
                IkGuiImplDebugTools.reportError(
                        log, "Passthrough central nodes require the dockSpace() API");
            }
            final DockNode rootNode = dockNodeGetRootNode(centralNode);
            final RectFloat rootRect = rootNode.rect();
            final RectFloat holeRect = centralNode.rect();
            final float padding = context.windowBorderHoverPadding;
            if (holeRect.getLeft() > rootRect.getLeft()) {
                holeRect.setLeft(holeRect.getLeft() + padding);
            }
            if (holeRect.getRight() < rootRect.getRight()) {
                holeRect.setRight(holeRect.getRight() - padding);
            }
            if (holeRect.getTop() > rootRect.getTop()) {
                holeRect.setTop(holeRect.getTop() + padding);
            }
            if (holeRect.getBottom() < rootRect.getBottom()) {
                holeRect.setBottom(holeRect.getBottom() - padding);
            }
            if (!holeRect.isInverted()) {
                IkGuiImplWindows.setWindowHitTestHole(
                        hostWindow,
                        holeRect.getLeft(),
                        holeRect.getTop(),
                        holeRect.getWidth(),
                        holeRect.getHeight());
                if (hostWindow.parentWindow != null) {
                    IkGuiImplWindows.setWindowHitTestHole(
                            hostWindow.parentWindow,
                            holeRect.getLeft(),
                            holeRect.getTop(),
                            holeRect.getWidth(),
                            holeRect.getHeight());
                }
            }
        }

        // Update position/size, process and draw resizing splitters
        if (node.isRootNode() && hostWindow != null) {
            dockNodeTreeUpdatePosSize(node, hostWindow.position, hostWindow.size, null);
            IkGuiImplUtils.pushStyleColor(
                    ColorType.SEPARATOR, IkGuiImplUtils.getColor(ColorType.BORDER));
            IkGuiImplUtils.pushStyleColor(
                    ColorType.SEPARATOR_ACTIVE,
                    IkGuiImplUtils.getColor(ColorType.RESIZE_GRIP_ACTIVE));
            IkGuiImplUtils.pushStyleColor(
                    ColorType.SEPARATOR_HOVERED,
                    IkGuiImplUtils.getColor(ColorType.RESIZE_GRIP_HOVERED));
            dockNodeTreeUpdateSplitter(node);
            IkGuiImplUtils.popStyleColor(3);
        }

        // Draw the empty node background (currently can only be the central node)
        if (hostWindow != null && node.isEmpty() && node.isVisible) {
            hostWindow.drawList.channelsSetCurrent(DOCKING_HOST_DRAW_CHANNEL_BG);
            node.lastBackgroundColor =
                    (nodeFlags & DockNodeFlags.PASSTHROUGH_CENTRAL_NODE) != 0
                            ? 0
                            : IkGuiImplUtils.getColorWithGlobalAlpha(
                                    ColorType.DOCKING_EMPTY_BACKGROUND);
            if (node.lastBackgroundColor != 0) {
                hostWindow.drawList.addRectFilled(
                        node.position.x,
                        node.position.y,
                        node.position.x + node.size.x,
                        node.position.y + node.size.y,
                        node.lastBackgroundColor);
            }
            node.isBackgroundDrawnThisFrame = true;
        }

        // Draw the whole dockspace background if PASSTHROUGH_CENTRAL_NODE is set. We need to draw
        // a background at the root level if requested, but we will only know the correct pos/size
        // after processing the resizing splitters. So we are using the draw list channel
        // splitting facility to submit drawing primitives out of order!
        final boolean renderDockspaceBackground =
                node.isRootNode()
                        && hostWindow != null
                        && (nodeFlags & DockNodeFlags.PASSTHROUGH_CENTRAL_NODE) != 0;
        if (renderDockspaceBackground && node.isVisible) {
            hostWindow.drawList.channelsSetCurrent(DOCKING_HOST_DRAW_CHANNEL_BG);
            final int color = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.WINDOW_BACKGROUND);
            if (centralNodeHole) {
                IkGuiInternal.renderRectFilledWithHole(
                        hostWindow.drawList, node.rect(), centralNode.rect(), color, 0.0f);
            } else {
                hostWindow.drawList.addRectFilled(
                        node.position.x,
                        node.position.y,
                        node.position.x + node.size.x,
                        node.position.y + node.size.y,
                        color,
                        0.0f);
            }
        }

        // Draw and populate the tab bar
        if (hostWindow != null) {
            hostWindow.drawList.channelsSetCurrent(DOCKING_HOST_DRAW_CHANNEL_FG);
        }
        if (hostWindow != null && !node.windows.isEmpty()) {
            dockNodeUpdateTabBar(node, hostWindow);
        } else {
            node.wantCloseAll = false;
            node.wantCloseTabID = 0;
            node.isFocused = false;
        }
        if (node.tabBar != null && node.tabBar.selectedTabID != 0) {
            node.selectedTabID = node.tabBar.selectedTabID;
        } else if (!node.windows.isEmpty()) {
            node.selectedTabID = node.windows.getFirst().idTab;
        }

        // Draw the payload drop target
        if (hostWindow != null
                && node.isVisible
                && node.isRootNode()
                && (context.windowMoving == null
                        || context.windowMoving.rootWindowDockTree != hostWindow)) {
            beginDockableDragDropTarget(hostWindow);
        }

        // We update this after dockNodeUpdateTabBar()
        node.lastFrameActive = context.frameCount;

        // Recurse into children
        if (hostWindow != null) {
            if (node.childNodes[0] != null) {
                dockNodeUpdate(node.childNodes[0]);
            }
            if (node.childNodes[1] != null) {
                dockNodeUpdate(node.childNodes[1]);
            }

            // Render outer borders last (after the tab bar)
            if (node.isRootNode()) {
                IkGuiImplWindows.renderWindowOuterBorders(hostWindow);
            }
        }

        // End the host window
        if (beganIntoHostWindow) {
            IkGuiImplWindows.end();
        }
    }

    /**
     * Compare tabs by the last known dock order of their windows (which persists in the .ini file
     * as a hint), used to sort tabs when multiple tabs are added on the same frame.
     *
     * @param a The first tab.
     * @param b The second tab.
     * @return The comparison result.
     */
    private static int tabItemComparerByDockOrder(@NonNull TabItem a, @NonNull TabItem b) {
        final Window windowA = a.window;
        final Window windowB = b.window;
        final int orderA = windowA.dockOrder == -1 ? Integer.MAX_VALUE : windowA.dockOrder;
        final int orderB = windowB.dockOrder == -1 ? Integer.MAX_VALUE : windowB.dockOrder;
        final int d = Integer.compare(orderA, orderB);
        if (d != 0) {
            return d;
        }
        return Integer.compare(windowA.beginOrderWithinContext, windowB.beginOrderWithinContext);
    }

    /**
     * The default window menu handler, which displays the list of windows in a dock node.
     *
     * @param ctx The context.
     * @param node The node.
     * @param tabBar The tab bar of the node.
     */
    static void dockNodeWindowMenuHandlerDefault(
            @NonNull Context ctx, @NonNull DockNode node, @NonNull TabBar tabBar) {
        if (tabBar.tabs.size() == 1) {
            // "Hide tab bar" option
            if (IkGuiImplMenus.menuItem(HIDE_TAB_BAR_LABEL, null, node.isHiddenTabBar(), true)) {
                node.wantHiddenTabBarToggle = true;
            }
        } else {
            // Display a selectable list of windows in this docking node
            for (TabItem tab : new ArrayList<>(tabBar.tabs)) {
                if ((tab.flags & TabItemFlags.INTERNAL_BUTTON) != 0) {
                    continue;
                }
                if (IkGuiImplMiscWidgets.selectable(
                        IkGuiImplTabs.tabBarGetTabName(tab),
                        tab.id == tabBar.selectedTabID,
                        SelectableFlags.NONE,
                        0,
                        0)) {
                    IkGuiImplTabs.tabBarQueueFocus(tabBar, tab);
                }
                IkGuiImplLayout.sameLine(0.0f, -1.0f);
                IkGuiImplText.text("   ");
            }
        }
    }

    /**
     * Display the window menu popup of a dock node.
     *
     * @param node The node.
     * @param tabBar The tab bar of the node.
     */
    static void dockNodeWindowMenuUpdate(@NonNull DockNode node, @NonNull TabBar tabBar) {
        // Try to position the menu so it is more likely to stay within the same viewport
        final float frameHeight = IkGuiImplLayout.getFrameHeight();
        if (context.style.variable.windowMenuButtonPosition == WindowMenuButtonPosition.LEFT) {
            IkGuiImplWindows.setNextWindowPos(
                    node.position.x, node.position.y + frameHeight, Condition.ALWAYS, 0.0f, 0.0f);
        } else {
            IkGuiImplWindows.setNextWindowPos(
                    node.position.x + node.size.x,
                    node.position.y + frameHeight,
                    Condition.ALWAYS,
                    1.0f,
                    0.0f);
        }
        if (IkGuiImplPopups.beginPopup(WINDOW_MENU_POPUP, WindowFlags.NONE)) {
            node.isFocused = true;
            if (context.dockNodeWindowMenuHandler != null) {
                context.dockNodeWindowMenuHandler.windowMenu(context, node, tabBar);
            }
            IkGuiImplPopups.endPopup();
        }
    }

    /**
     * Helper to append to/amend the tab bar of a dock node, most commonly used to add e.g. a "+"
     * button. Call {@link #dockNodeEndAmendTabBar()} if this returns true.
     *
     * @param node The node.
     * @return True if the tab bar can be amended.
     */
    static boolean dockNodeBeginAmendTabBar(@NonNull DockNode node) {
        if (node.tabBar == null || node.hostWindow == null) {
            return false;
        }
        if ((node.mergedFlags & DockNodeFlags.KEEP_ALIVE_ONLY) != 0) {
            return false;
        }
        if (node.tabBar.id == 0) {
            return false;
        }
        IkGuiImplWindows.begin(node.hostWindow.name, null, WindowFlags.NONE);
        IkGuiInternal.pushOverrideID(node.id);
        final boolean result =
                IkGuiImplTabs.beginTabBarEx(node.tabBar, node.tabBar.barRect, node.tabBar.flags);
        if (!result) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Failed to begin the tab bar of dock node 0x{}",
                    Integer.toHexString(node.id));
        }
        return true;
    }

    /** End amending a dock node tab bar, after {@link #dockNodeBeginAmendTabBar(DockNode)}. */
    static void dockNodeEndAmendTabBar() {
        IkGuiImplTabs.endTabBar();
        IkGuiImplUtils.popID();
        IkGuiImplWindows.end();
    }

    /**
     * Check if the title bar of a node should be highlighted, because it or one of its docked
     * windows is focused.
     *
     * @param node The node.
     * @param rootNode The root node of the node.
     * @return True if highlighted.
     */
    private static boolean isDockNodeTitleBarHighlighted(
            @NonNull DockNode node, @NonNull DockNode rootNode) {
        // Ctrl+Tab highlight (only highlighting the leaf node, not the whole hierarchy)
        if (context.navWindowingTarget != null) {
            return context.navWindowingTarget.dockNode == node;
        }

        if (context.navFocusedWindow != null && rootNode.lastFocusedNodeID == node.id) {
            Window parentWindow = context.navFocusedWindow.rootWindow;
            while ((parentWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
                parentWindow = parentWindow.parentWindow.rootWindow;
            }
            final DockNode startParentNode =
                    parentWindow.dockNodeAsHost != null
                            ? parentWindow.dockNodeAsHost
                            : parentWindow.dockNode;
            DockNode parentNode = startParentNode;
            while (parentNode != null) {
                parentNode = dockNodeGetRootNode(parentNode);
                if (parentNode == rootNode) {
                    return true;
                }
                parentNode =
                        parentNode.hostWindow != null
                                ? parentNode.hostWindow.rootWindow.dockNode
                                : null;
            }
        }
        return false;
    }

    /**
     * Submit the tab bar corresponding to a dock node, and various housekeeping details.
     *
     * @param node The node.
     * @param hostWindow The host window of the node.
     */
    static void dockNodeUpdateTabBar(@NonNull DockNode node, @NonNull Window hostWindow) {
        final StyleVariables style = context.style.variable;

        final boolean nodeWasActive = node.lastFrameActive + 1 == context.frameCount;
        node.wantCloseAll = false;
        node.wantCloseTabID = 0;

        // Decide if we should use a focused title bar color
        boolean isFocused = false;
        final DockNode rootNode = dockNodeGetRootNode(node);
        if (isDockNodeTitleBarHighlighted(node, rootNode)) {
            isFocused = true;
        }

        // A hidden tab bar will show a triangle on the upper-left (in begin())
        if (node.isHiddenTabBar() || node.isNoTabBar()) {
            node.visibleWindow = !node.windows.isEmpty() ? node.windows.getFirst() : null;
            node.isFocused = isFocused;
            if (isFocused) {
                node.lastFrameFocused = context.frameCount;
            }
            if (node.visibleWindow != null) {
                // Notify the root of the visible window (used to display the title in the OS task
                // bar)
                if (isFocused || rootNode.visibleWindow == null) {
                    rootNode.visibleWindow = node.visibleWindow;
                }
                if (node.tabBar != null) {
                    node.tabBar.visibleTabID = node.visibleWindow.idTab;
                }
            }
            return;
        }

        // Move ourselves to the menu layer (so we can be accessed by tapping Alt) and undo the
        // skip items flag in order to draw over the title bar even if the window is collapsed
        final boolean backupSkipItems = hostWindow.skipItems;
        if (!node.isDockSpace()) {
            hostWindow.skipItems = false;
            hostWindow.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;
        }

        // Use pushOverrideID() instead of pushID() to use the node ID without the host window ID.
        // This is to facilitate computing those IDs from the outside, and will affect more or less
        // only the ID of the collapse button, popup and tabs, as docked windows themselves will
        // override the stack with their own root ID.
        IkGuiInternal.pushOverrideID(node.id);
        TabBar tabBar = node.tabBar;
        // Tab bars are automatically destroyed when a node gets hidden
        final boolean tabBarIsRecreated = tabBar == null;
        if (tabBar == null) {
            dockNodeAddTabBar(node);
            tabBar = node.tabBar;
        }

        int focusTabID = 0;
        node.isFocused = isFocused;

        final int nodeFlags = node.mergedFlags;
        final boolean hasWindowMenuButton =
                (nodeFlags & DockNodeFlags.INTERNAL_NO_WINDOW_MENU_BUTTON) == 0
                        && style.windowMenuButtonPosition != WindowMenuButtonPosition.NONE;

        // In a dock node, the collapse button turns into the window menu button
        if (hasWindowMenuButton
                && IkGuiImplPopups.isPopupOpen(WINDOW_MENU_POPUP, PopupFlags.NONE)) {
            final int nextSelectedTabID = tabBar.nextSelectedTabID;
            dockNodeWindowMenuUpdate(node, tabBar);
            if (tabBar.nextSelectedTabID != 0 && tabBar.nextSelectedTabID != nextSelectedTabID) {
                focusTabID = tabBar.nextSelectedTabID;
            }
            isFocused |= node.isFocused;
        }

        // Layout
        final RectFloat titleBarRect = new RectFloat();
        final RectFloat tabBarRect = new RectFloat();
        final Vector2f windowMenuButtonPosition = new Vector2f();
        final Vector2f closeButtonPosition = new Vector2f();
        dockNodeCalcTabBarLayout(
                node, titleBarRect, tabBarRect, windowMenuButtonPosition, closeButtonPosition);

        // Submit new tabs, they will be added as unsorted and sorted below based on the relative
        // dock order value
        final int tabsCountOld = tabBar.tabs.size();
        for (Window window : node.windows) {
            if (IkGuiInternal.tabBarFindTabByID(tabBar, window.idTab) == null) {
                IkGuiInternal.tabBarAddTab(tabBar, TabItemFlags.INTERNAL_UNSORTED, window);
            }
        }

        // Title bar
        if (isFocused) {
            node.lastFrameFocused = context.frameCount;
        }
        final int titleBarColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        hostWindow.collapsed
                                ? ColorType.TITLE_BACKGROUND_COLLAPSED
                                : isFocused
                                        ? ColorType.TITLE_BACKGROUND_ACTIVE
                                        : ColorType.TITLE_BACKGROUND);
        final int roundingFlags =
                IkGuiInternal.calcRoundingFlagsForRectInRect(
                        titleBarRect, hostWindow.getRect(), style.dockingSeparatorSize);
        hostWindow.drawList.addRectFilled(
                titleBarRect.getLeft(),
                titleBarRect.getTop(),
                titleBarRect.getRight(),
                titleBarRect.getBottom(),
                titleBarColor,
                hostWindow.rounding,
                roundingFlags);

        // Docking/collapse button
        if (hasWindowMenuButton) {
            // The same as dockNodeGetWindowMenuButtonID(node)
            if (IkGuiImplWindows.collapseButton(
                    hostWindow.getID("#COLLAPSE"),
                    windowMenuButtonPosition.x,
                    windowMenuButtonPosition.y,
                    node)) {
                IkGuiImplPopups.openPopup(WINDOW_MENU_POPUP, PopupFlags.NONE);
            }
            if (IkGuiImplUtils.isItemActive()) {
                focusTabID = tabBar.selectedTabID;
            }
            if (IkGuiImplUtils.isItemHovered(HoveredFlags.FOR_TOOLTIP | HoveredFlags.DELAY_NORMAL)
                    && context.hoveredIDTimer > 500) {
                IkGuiImplPopups.setTooltip(DRAG_TO_UNDOCK_OR_MOVE_NODE);
            }
        }

        // If multiple tabs are appearing on the same frame, sort them based on their persistent
        // dock order value
        int tabsUnsortedStart = tabBar.tabs.size();
        for (int tabIndex = tabBar.tabs.size() - 1;
                tabIndex >= 0
                        && (tabBar.tabs.get(tabIndex).flags & TabItemFlags.INTERNAL_UNSORTED) != 0;
                --tabIndex) {
            tabBar.tabs.get(tabIndex).flags &= ~TabItemFlags.INTERNAL_UNSORTED;
            tabsUnsortedStart = tabIndex;
        }
        if (tabBar.tabs.size() > tabsUnsortedStart) {
            log.trace(
                    "In node 0x{}: {} new appearing tabs",
                    Integer.toHexString(node.id),
                    tabBar.tabs.size() - tabsUnsortedStart);
            if (tabBar.tabs.size() > tabsUnsortedStart + 1) {
                tabBar.tabs
                        .subList(tabsUnsortedStart, tabBar.tabs.size())
                        .sort(IkGuiImplDocking::tabItemComparerByDockOrder);
            }
        }

        // Apply nav window focus back to the tab bar
        if (context.navFocusedWindow != null
                && context.navFocusedWindow.rootWindow.dockNode == node) {
            tabBar.selectedTabID = context.navFocusedWindow.rootWindow.idTab;
        }

        // Select newly added tabs, or the persistent tab ID if the tab bar was just recreated
        if (tabBarIsRecreated
                && IkGuiInternal.tabBarFindTabByID(tabBar, node.selectedTabID) != null) {
            tabBar.selectedTabID = node.selectedTabID;
            tabBar.nextSelectedTabID = node.selectedTabID;
        } else if (tabBar.tabs.size() > tabsCountOld) {
            tabBar.selectedTabID = tabBar.tabs.getLast().window.idTab;
            tabBar.nextSelectedTabID = tabBar.selectedTabID;
        }

        // Begin the tab bar
        int tabBarFlags =
                TabBarFlags.REORDERABLE
                        | TabBarFlags.AUTO_SELECT_NEW_TABS
                        | TabBarFlags.INTERNAL_SAVE_SETTINGS
                        | TabBarFlags.INTERNAL_DOCK_NODE
                        // Enforce the default policy
                        | TabBarFlags.FITTING_POLICY_MIXED
                        | TabBarFlags.DRAW_SELECTED_OVERLINE;
        if (!hostWindow.collapsed && isFocused) {
            tabBarFlags |= TabBarFlags.INTERNAL_IS_FOCUSED;
        }
        tabBar.id = node.id;
        // The separator covers the whole node width
        tabBar.separatorMinX = node.position.x + hostWindow.borderSize;
        tabBar.separatorMaxX = node.position.x + node.size.x - hostWindow.borderSize;
        IkGuiImplTabs.beginTabBarEx(tabBar, tabBarRect, tabBarFlags);

        // Submit the actual tabs
        node.visibleWindow = null;
        for (Window window : new ArrayList<>(node.windows)) {
            if (window.lastFrameActive + 1 < context.frameCount && nodeWasActive) {
                // Windows are normally removed in dockNodeUpdateFlagsAndCollapse()
                continue;
            }

            int tabItemFlags = window.windowClass.tabItemFlagsOverrideSet;
            if ((window.flags & WindowFlags.UNSAVED_DOCUMENT) != 0) {
                tabItemFlags |= TabItemFlags.UNSAVED_DOCUMENT;
            }
            if ((tabBar.flags & TabBarFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON) != 0) {
                tabItemFlags |= TabItemFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON;
            }

            // Apply the stored style overrides for the window
            pushDockStyleColors(window);

            // Note that tabItemEx() calculates the tab ID from the window, so our tab item ID will
            // ignore the current ID stack (rightly so)
            final IkBoolean tabOpen = new IkBoolean(true);
            IkGuiImplTabs.tabItemEx(
                    tabBar,
                    window.name,
                    window.hasCloseButton ? tabOpen : null,
                    tabItemFlags,
                    window);
            if (!tabOpen.get()) {
                node.wantCloseTabID = window.idTab;
            }
            if (tabBar.visibleTabID == window.idTab) {
                node.visibleWindow = window;
            }

            // Store the last item data so it can be queried with isItemXXX() functions after the
            // user begin() call
            window.dockTabItemStatusFlags = context.lastItemData.statusFlags;
            window.dockTabItemRect.set(context.lastItemData.rect);

            // Update the navigation ID on the menu layer
            if (context.navFocusedWindow != null
                    && context.navFocusedWindow.rootWindow == window
                    && (window.navLayersActiveMask & (1 << IkGuiImplNav.NAV_LAYER_MENU)) == 0) {
                hostWindow.navLastIDs[1] = window.idTab;
            }

            // Restore the style colors
            IkGuiImplUtils.popStyleColor(WindowDockStyleColor.COUNT);
        }

        // Notify the root of the visible window (used to display the title in the OS task bar)
        if (node.visibleWindow != null && (isFocused || rootNode.visibleWindow == null)) {
            rootNode.visibleWindow = node.visibleWindow;
        }

        // Close button (after the visible window was updated). Note that the visible window may
        // have been overridden by Ctrl+Tab, so visibleWindow.idTab may be different from the
        // selected tab ID.
        final boolean closeButtonIsEnabled =
                node.hasCloseButton
                        && node.visibleWindow != null
                        && node.visibleWindow.hasCloseButton;
        final boolean closeButtonIsVisible = node.hasCloseButton;
        if (closeButtonIsVisible) {
            if (!closeButtonIsEnabled) {
                IkGuiImplUtils.pushItemFlag(ItemFlags.DISABLED, true);
                IkGuiImplUtils.pushStyleColor(
                        ColorType.TEXT,
                        com.ikalagaming.graphics.gui.util.Color.multiplyAlpha(
                                context.style.color.text, 0.4f));
            }
            if (IkGuiImplWindows.closeButton(
                    hostWindow.getID("#CLOSE"), closeButtonPosition.x, closeButtonPosition.y)) {
                node.wantCloseAll = true;
                for (TabItem tab : new ArrayList<>(tabBar.tabs)) {
                    IkGuiImplTabs.tabBarCloseTab(tabBar, tab);
                }
            }
            if (!closeButtonIsEnabled) {
                IkGuiImplUtils.popStyleColor();
                IkGuiImplUtils.popItemFlag();
            }
        }

        // When clicking on the title bar outside of tabs, we still focus the selected tab for
        // that node. Tab items submitted earlier use allow overlap, so we manually perform a more
        // specific test for now (hovered || held) in order to not cover them.
        final int titleBarID = hostWindow.getID("#TITLEBAR");
        if (context.hoveredID == 0
                || context.hoveredID == titleBarID
                || context.activeID == titleBarID) {
            // Allow overlap mode is required for appending into the dock node tab bar, otherwise
            // dragging the window will steal the hovered ID and amended tabs cannot get them
            final IkBoolean held = new IkBoolean();
            IkGuiInternal.keepAliveID(titleBarID);
            IkGuiInternal.buttonBehavior(
                    titleBarRect, titleBarID, null, held, ButtonFlags.ALLOW_OVERLAP);
            if (context.hoveredID == titleBarID) {
                context.lastItemData.id = titleBarID;
            }
            if (held.get()) {
                if (IkGuiImplUtils.isMouseClicked(MouseButton.LEFT, false)) {
                    focusTabID = tabBar.selectedTabID;
                }

                // Forward the moving request to the selected window
                final TabItem tab = IkGuiInternal.tabBarFindTabByID(tabBar, tabBar.selectedTabID);
                if (tab != null) {
                    // Undock from the tab bar empty space
                    IkGuiInternal.startMouseMovingWindowOrNode(
                            tab.window != null ? tab.window : node.hostWindow, node, false);
                }
            }
        }

        // When clicking on a tab we request focus to the docked child. This overrides the value
        // set by "forward focus from host node to selected window".
        if (tabBar.nextSelectedTabID != 0) {
            focusTabID = tabBar.nextSelectedTabID;
        }

        // Apply navigation focus
        if (focusTabID != 0) {
            final TabItem tab = IkGuiInternal.tabBarFindTabByID(tabBar, focusTabID);
            if (tab != null && tab.window != null) {
                IkGuiInternal.focusWindow(tab.window, WindowFocusRequestFlags.NONE);
                // Only initialize if focusWindow() didn't restore anything
                if (context.navID == 0) {
                    IkGuiImplNav.navInitWindow(tab.window, false);
                }
            }
        }

        IkGuiImplTabs.endTabBar();
        IkGuiImplUtils.popID();

        // Restore the skip items flag
        if (!node.isDockSpace()) {
            hostWindow.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
            hostWindow.skipItems = backupSkipItems;
        }
    }

    /**
     * Push the dock style colors stored in a window, so its tab uses the colors that were active
     * when the window called begin(). Pop with popStyleColor({@link WindowDockStyleColor#COUNT}).
     *
     * @param window The window.
     */
    private static void pushDockStyleColors(@NonNull Window window) {
        for (WindowDockStyleColor dockStyleColor : WindowDockStyleColor.values()) {
            IkGuiImplUtils.pushStyleColor(
                    WindowDockStyleColor.mapToColor(dockStyleColor),
                    window.dockStyle.colors[dockStyleColor.ordinal()]);
        }
    }

    /**
     * Create a tab bar for a node.
     *
     * @param node The node, which must not have a tab bar.
     */
    static void dockNodeAddTabBar(@NonNull DockNode node) {
        if (node.tabBar != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Dock node 0x{} already has a tab bar", Integer.toHexString(node.id));
            return;
        }
        node.tabBar = new TabBar();
    }

    /**
     * Remove the tab bar of a node.
     *
     * @param node The node.
     */
    static void dockNodeRemoveTabBar(@NonNull DockNode node) {
        node.tabBar = null;
    }

    /**
     * Check if a single payload window can be docked into a host window.
     *
     * @param payload The payload window.
     * @param hostWindow The host window.
     * @return True if allowed.
     */
    private static boolean dockNodeIsDropAllowedOne(
            @NonNull Window payload, @NonNull Window hostWindow) {
        if (hostWindow.dockNodeAsHost != null
                && hostWindow.dockNodeAsHost.isDockSpace()
                && payload.beginOrderWithinContext < hostWindow.beginOrderWithinContext) {
            return false;
        }

        final WindowClass hostClass =
                hostWindow.dockNodeAsHost != null
                        ? hostWindow.dockNodeAsHost.windowClass
                        : hostWindow.windowClass;
        final WindowClass payloadClass = payload.windowClass;
        if (hostClass.classID != payloadClass.classID) {
            boolean pass =
                    hostClass.classID != 0
                            && hostClass.dockingAllowUnclassed
                            && payloadClass.classID == 0;
            if (payloadClass.classID != 0
                    && payloadClass.dockingAllowUnclassed
                    && hostClass.classID == 0) {
                pass = true;
            }
            if (!pass) {
                return false;
            }
        }

        // Prevent docking any window created above a popup. Technically we should support it (e.g.
        // in the case of a long-lived modal window that had fancy docking features), but it would
        // require more work on our end because the dock host windows are technically created in
        // newFrame() and our parent/root window references are currently misleading or lacking.
        for (int i = context.openPopupStack.size() - 1; i >= 0; --i) {
            final Window popupWindow = context.openPopupStack.get(i).window;
            // The payload is created from within a popup begin stack
            if (popupWindow != null
                    && IkGuiInternal.isWindowWithinBeginStackOf(payload, popupWindow)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a payload (a loose window or a dock node host) can be docked into a host window.
     *
     * @param hostWindow The host window.
     * @param rootPayload The payload.
     * @return True if allowed.
     */
    static boolean dockNodeIsDropAllowed(@NonNull Window hostWindow, @NonNull Window rootPayload) {
        if (rootPayload.dockNodeAsHost != null && rootPayload.dockNodeAsHost.isSplitNode()) {
            // Missing filtering
            return true;
        }

        if (rootPayload.dockNodeAsHost == null) {
            return dockNodeIsDropAllowedOne(rootPayload, hostWindow);
        }
        for (Window payload : rootPayload.dockNodeAsHost.windows) {
            if (dockNodeIsDropAllowedOne(payload, hostWindow)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate the layout of the title bar of a node. The window menu button is the same as the
     * collapse button when not in a dock node.
     *
     * @param node The node.
     * @param outTitleRect The title bar rectangle, may be null.
     * @param outTabBarRect The tab bar rectangle, may be null.
     * @param outWindowMenuButtonPosition The position of the window menu button, may be null.
     * @param outCloseButtonPosition The position of the close button, may be null.
     */
    static void dockNodeCalcTabBarLayout(
            @NonNull DockNode node,
            RectFloat outTitleRect,
            RectFloat outTabBarRect,
            Vector2f outWindowMenuButtonPosition,
            Vector2f outCloseButtonPosition) {
        final StyleVariables style = context.style.variable;
        final float fontSize = IkGuiInternal.getFontSize();

        final RectFloat r =
                new RectFloat(
                        node.position.x,
                        node.position.y,
                        node.position.x + node.size.x,
                        node.position.y + fontSize + style.framePadding.y * 2.0f);
        if (outTitleRect != null) {
            outTitleRect.set(r);
        }

        r.setLeft(r.getLeft() + style.windowBorderSize);
        r.setRight(r.getRight() - style.windowBorderSize);

        final float buttonSize = fontSize;
        r.setLeft(r.getLeft() + style.framePadding.x);
        r.setRight(r.getRight() - style.framePadding.x);
        float windowMenuButtonX = r.getLeft();
        final float buttonY = r.getTop() + style.framePadding.y;
        if (node.hasCloseButton) {
            if (outCloseButtonPosition != null) {
                outCloseButtonPosition.set(r.getRight() - buttonSize, buttonY);
            }
            r.setRight(r.getRight() - (buttonSize + style.itemInnerSpacing.x));
        }
        if (node.hasWindowMenuButton
                && style.windowMenuButtonPosition == WindowMenuButtonPosition.LEFT) {
            r.setLeft(r.getLeft() + buttonSize + style.itemInnerSpacing.x);
        } else if (node.hasWindowMenuButton
                && style.windowMenuButtonPosition == WindowMenuButtonPosition.RIGHT) {
            windowMenuButtonX = r.getRight() - buttonSize;
            r.setRight(r.getRight() - (buttonSize + style.itemInnerSpacing.x));
        }
        if (outTabBarRect != null) {
            outTabBarRect.set(r);
        }
        if (outWindowMenuButtonPosition != null) {
            outWindowMenuButtonPosition.set(windowMenuButtonX, buttonY);
        }
    }

    /**
     * Calculate the rectangles for an existing node and a new node created by splitting it.
     *
     * @param positionOld The position of the old node, modified.
     * @param sizeOld The size of the old node, modified.
     * @param positionNew The position of the new node, output.
     * @param sizeNew The size of the new node, output.
     * @param direction The direction the new node is placed in.
     * @param sizeNewDesired The desired size of the new node.
     */
    static void dockNodeCalcSplitRects(
            @NonNull Vector2f positionOld,
            @NonNull Vector2f sizeOld,
            @NonNull Vector2f positionNew,
            @NonNull Vector2f sizeNew,
            @NonNull Direction direction,
            @NonNull Vector2f sizeNewDesired) {
        final float dockSpacing = context.style.variable.itemInnerSpacing.x;
        final int axis = (direction == Direction.LEFT || direction == Direction.RIGHT) ? 0 : 1;
        final int otherAxis = axis ^ 1;
        positionNew.setComponent(otherAxis, positionOld.get(otherAxis));
        sizeNew.setComponent(otherAxis, sizeOld.get(otherAxis));

        // Distribute the size on the given axis (with a desired size or equally)
        final float widthAvailable = sizeOld.get(axis) - dockSpacing;
        if (sizeNewDesired.get(axis) > 0.0f && sizeNewDesired.get(axis) <= widthAvailable * 0.5f) {
            sizeNew.setComponent(axis, sizeNewDesired.get(axis));
        } else {
            sizeNew.setComponent(axis, IkGuiInternal.truncate(widthAvailable * 0.5f));
        }
        sizeOld.setComponent(axis, IkGuiInternal.truncate(widthAvailable - sizeNew.get(axis)));

        // Position each node
        if (direction == Direction.RIGHT || direction == Direction.DOWN) {
            positionNew.setComponent(axis, positionOld.get(axis) + sizeOld.get(axis) + dockSpacing);
        } else if (direction == Direction.LEFT || direction == Direction.UP) {
            positionNew.setComponent(axis, positionOld.get(axis));
            positionOld.setComponent(axis, positionNew.get(axis) + sizeNew.get(axis) + dockSpacing);
        }
    }

    /**
     * Retrieve the drop rectangles for a given direction or for the center, and perform hit
     * testing.
     *
     * @param parent The parent rectangle.
     * @param direction The direction, or NONE for the center.
     * @param outRect Where to store the drop rectangle.
     * @param outerDocking Whether this is for outer docking.
     * @param testMousePosition The position to test, may be null to skip testing.
     * @return True if the position is over the drop rectangle.
     */
    static boolean dockNodeCalcDropRectsAndTestMousePos(
            @NonNull RectFloat parent,
            @NonNull Direction direction,
            @NonNull RectFloat outRect,
            boolean outerDocking,
            Vector2f testMousePosition) {
        final float fontSize = IkGuiInternal.getFontSize();
        final float parentSmallerAxis = Math.min(parent.getWidth(), parent.getHeight());
        final float halfSizeForCentralNodes =
                Math.min(fontSize * 1.5f, Math.max(fontSize * 0.5f, parentSmallerAxis / 8.0f));
        // Half-size, longer axis
        final float halfSizeWidth;
        // Half-size, smaller axis
        final float halfSizeHeight;
        // Distance from the edge or center
        final float offsetX;
        final float offsetY;
        if (outerDocking) {
            halfSizeWidth = IkGuiInternal.truncate(halfSizeForCentralNodes * 1.50f);
            halfSizeHeight = IkGuiInternal.truncate(halfSizeForCentralNodes * 0.80f);
            offsetX = IkGuiInternal.truncate(parent.getWidth() * 0.5f - halfSizeHeight);
            offsetY = IkGuiInternal.truncate(parent.getHeight() * 0.5f - halfSizeHeight);
        } else {
            halfSizeWidth = IkGuiInternal.truncate(halfSizeForCentralNodes);
            halfSizeHeight = IkGuiInternal.truncate(halfSizeForCentralNodes * 0.90f);
            offsetX = IkGuiInternal.truncate(halfSizeWidth * 2.40f);
            offsetY = offsetX;
        }

        final float centerX = IkGuiInternal.truncate(parent.getCenterX());
        final float centerY = IkGuiInternal.truncate(parent.getCenterY());
        switch (direction) {
            case NONE ->
                    outRect.set(
                            centerX - halfSizeWidth,
                            centerY - halfSizeWidth,
                            centerX + halfSizeWidth,
                            centerY + halfSizeWidth);
            case UP ->
                    outRect.set(
                            centerX - halfSizeWidth,
                            centerY - offsetY - halfSizeHeight,
                            centerX + halfSizeWidth,
                            centerY - offsetY + halfSizeHeight);
            case DOWN ->
                    outRect.set(
                            centerX - halfSizeWidth,
                            centerY + offsetY - halfSizeHeight,
                            centerX + halfSizeWidth,
                            centerY + offsetY + halfSizeHeight);
            case LEFT ->
                    outRect.set(
                            centerX - offsetX - halfSizeHeight,
                            centerY - halfSizeWidth,
                            centerX - offsetX + halfSizeHeight,
                            centerY + halfSizeWidth);
            case RIGHT ->
                    outRect.set(
                            centerX + offsetX - halfSizeHeight,
                            centerY - halfSizeWidth,
                            centerX + offsetX + halfSizeHeight,
                            centerY + halfSizeWidth);
        }

        if (testMousePosition == null) {
            return false;
        }

        final RectFloat hitRect = new RectFloat(outRect);
        if (!outerDocking) {
            // Custom hit testing for the 5-way selection, designed to reduce flickering when
            // moving diagonally between sides
            final float expand = IkGuiInternal.truncate(halfSizeWidth * 0.30f);
            hitRect.expand(expand, expand);
            final float mouseDeltaX = testMousePosition.x - centerX;
            final float mouseDeltaY = testMousePosition.y - centerY;
            final float mouseDeltaLength2 = mouseDeltaX * mouseDeltaX + mouseDeltaY * mouseDeltaY;
            final float thresholdCenter = halfSizeWidth * 1.4f;
            final float thresholdSides = halfSizeWidth * (1.4f + 1.2f);
            if (mouseDeltaLength2 < thresholdCenter * thresholdCenter) {
                return direction == Direction.NONE;
            }
            if (mouseDeltaLength2 < thresholdSides * thresholdSides) {
                return direction == IkGuiImplNav.getDirQuadrantFromDelta(mouseDeltaX, mouseDeltaY);
            }
        }
        return hitRect.contains(testMousePosition);
    }

    /**
     * Figure out where we are allowed to dock a payload, and calculate the preview of the docking
     * operation.
     *
     * @param hostWindow The host window.
     * @param hostNode The host node, may be null if the window doesn't have a dock node already.
     * @param payloadWindow The payload window.
     * @param payloadNode The payload node, may be null.
     * @param data Where to store the results.
     * @param isExplicitTarget Whether the mouse is over an explicit target (e.g. the tab bar).
     * @param isOuterDocking Whether this is for outer docking.
     */
    static void dockNodePreviewDockSetup(
            @NonNull Window hostWindow,
            DockNode hostNode,
            @NonNull Window payloadWindow,
            DockNode payloadNode,
            @NonNull DockPreviewData data,
            boolean isExplicitTarget,
            boolean isOuterDocking) {
        // There is an edge case when docking into a dockspace which only has inactive nodes. In
        // this case dockNodeTreeFindNodeByPos() will have selected a leaf node which is inactive.
        // Because the inactive leaf node doesn't have proper pos/size yet, we'll use the root node
        // as reference.
        if (payloadNode == null) {
            payloadNode = payloadWindow.dockNodeAsHost;
        }
        final DockNode referenceNodeForRect =
                (hostNode != null && !hostNode.isVisible)
                        ? dockNodeGetRootNode(hostNode)
                        : hostNode;
        if (referenceNodeForRect != null && !referenceNodeForRect.isVisible) {
            IkGuiImplDebugTools.reportError(
                    log, "The reference dock node for the preview is not visible");
        }

        // Filter, figure out where we are allowed to dock
        final int sourceNodeFlags =
                payloadNode != null
                        ? payloadNode.mergedFlags
                        : payloadWindow.windowClass.dockNodeFlagsOverrideSet;
        final int destinationNodeFlags =
                hostNode != null
                        ? hostNode.mergedFlags
                        : hostWindow.windowClass.dockNodeFlagsOverrideSet;
        data.isCenterAvailable = true;
        if (isOuterDocking) {
            data.isCenterAvailable = false;
        } else if (context.io.configDockingNoDockingOver) {
            data.isCenterAvailable = false;
        } else if ((destinationNodeFlags & DockNodeFlags.INTERNAL_NO_DOCKING_OVER_ME) != 0) {
            data.isCenterAvailable = false;
        } else if (hostNode != null
                && (destinationNodeFlags & DockNodeFlags.NO_DOCKING_OVER_CENTRAL_NODE) != 0
                && hostNode.isCentralNode()) {
            data.isCenterAvailable = false;
        } else if ((hostNode == null || !hostNode.isEmpty())
                && payloadNode != null
                && payloadNode.isSplitNode()
                && payloadNode.onlyNodeWithWindows == null) {
            // Is visibly split?
            data.isCenterAvailable = false;
        } else if ((sourceNodeFlags & DockNodeFlags.INTERNAL_NO_DOCKING_OVER_OTHER) != 0
                && (hostNode == null || !hostNode.isEmpty())) {
            data.isCenterAvailable = false;
        } else if ((sourceNodeFlags & DockNodeFlags.INTERNAL_NO_DOCKING_OVER_EMPTY) != 0
                && hostNode != null
                && hostNode.isEmpty()) {
            data.isCenterAvailable = false;
        }

        data.isSidesAvailable = true;
        if ((destinationNodeFlags & DockNodeFlags.NO_DOCKING_SPLIT) != 0
                || context.io.configDockingNoSplit) {
            data.isSidesAvailable = false;
        } else if (!isOuterDocking
                && hostNode != null
                && hostNode.parentNode == null
                && hostNode.isCentralNode()) {
            data.isSidesAvailable = false;
        } else if ((sourceNodeFlags & DockNodeFlags.INTERNAL_NO_DOCKING_SPLIT_OTHER) != 0) {
            data.isSidesAvailable = false;
        }

        // Build a tentative future node (reuse the same structure because it is practical, the
        // shape will be readjusted when previewing a split)
        data.futureNode.hasCloseButton =
                (hostNode != null ? hostNode.hasCloseButton : hostWindow.hasCloseButton)
                        || payloadWindow.hasCloseButton;
        data.futureNode.hasWindowMenuButton =
                hostNode != null || (hostWindow.flags & WindowFlags.NO_COLLAPSE) == 0;
        data.futureNode.position.set(
                referenceNodeForRect != null ? referenceNodeForRect.position : hostWindow.position);
        data.futureNode.size.set(
                referenceNodeForRect != null ? referenceNodeForRect.size : hostWindow.size);

        // Calculate the drop shapes geometry for allowed splitting directions
        data.splitNode = hostNode;
        data.splitDirection = Direction.NONE;
        data.isSplitDirectionExplicit = false;
        if (!hostWindow.collapsed) {
            final RectFloat futureRect = data.futureNode.rect();
            for (Direction direction : DIRECTIONS_WITH_NONE) {
                if (direction == Direction.NONE && !data.isCenterAvailable) {
                    continue;
                }
                if (direction != Direction.NONE && !data.isSidesAvailable) {
                    continue;
                }
                if (dockNodeCalcDropRectsAndTestMousePos(
                        futureRect,
                        direction,
                        data.dropRectsDraw[directionIndex(direction) + 1],
                        isOuterDocking,
                        context.io.mousePosition)) {
                    data.splitDirection = direction;
                    data.isSplitDirectionExplicit = true;
                }
            }
        }

        // When docking without holding shift, we only allow and preview docking when hovering
        // over a drop rect or over the title bar
        data.isDropAllowed = data.splitDirection != Direction.NONE || data.isCenterAvailable;
        if (!isExplicitTarget
                && !data.isSplitDirectionExplicit
                && !context.io.configDockingWithShift) {
            data.isDropAllowed = false;
        }

        // Calculate the split area
        data.splitRatio = 0.0f;
        if (data.splitDirection != Direction.NONE) {
            final Direction splitDirection = data.splitDirection;
            final int splitAxis =
                    (splitDirection == Direction.LEFT || splitDirection == Direction.RIGHT) ? 0 : 1;
            final Vector2f positionNew = new Vector2f();
            final Vector2f positionOld = new Vector2f(data.futureNode.position);
            final Vector2f sizeNew = new Vector2f();
            final Vector2f sizeOld = new Vector2f(data.futureNode.size);
            dockNodeCalcSplitRects(
                    positionOld, sizeOld, positionNew, sizeNew, splitDirection, payloadWindow.size);

            // Calculate the split ratio so we can pass it down the docking request
            final float splitRatio =
                    MathUtil.clamp(
                            sizeNew.get(splitAxis) / data.futureNode.size.get(splitAxis),
                            0.0f,
                            1.0f);
            data.futureNode.position.set(positionNew);
            data.futureNode.size.set(sizeNew);
            data.splitRatio =
                    (splitDirection == Direction.RIGHT || splitDirection == Direction.DOWN)
                            ? (1.0f - splitRatio)
                            : splitRatio;
        }
    }

    /**
     * Render the docking preview overlay.
     *
     * @param hostWindow The host window, which must be the current window.
     * @param hostNode The host node, may be null.
     * @param rootPayload The payload window.
     * @param data The preview data.
     */
    static void dockNodePreviewDockRender(
            @NonNull Window hostWindow,
            DockNode hostNode,
            @NonNull Window rootPayload,
            @NonNull DockPreviewData data) {
        // Because we rely on the font size to calculate tab sizes
        if (context.windowCurrent != hostWindow) {
            IkGuiImplDebugTools.reportError(
                    log, "Dock previews must be rendered while the host window is current");
        }

        // With this option, we only display the preview on the target viewport, and the payload
        // viewport is made transparent. To compensate for the single layer obstructed by the
        // payload, we'll increase the alpha of the preview nodes.
        final boolean isTransparentPayload = context.io.configDockingTransparentPayload;

        // In case the two windows involved are on different viewports, we draw the overlay on
        // each of them
        dockNodePreviewDockRender(
                IkGuiImplUtils.getForegroundDrawList(hostWindow.viewport),
                hostWindow,
                hostNode,
                rootPayload,
                data);
        if (hostWindow.viewport != rootPayload.viewport && !isTransparentPayload) {
            dockNodePreviewDockRender(
                    IkGuiImplUtils.getForegroundDrawList(rootPayload.viewport),
                    hostWindow,
                    hostNode,
                    rootPayload,
                    data);
        }
    }

    /**
     * Render the docking preview overlay into one draw list.
     *
     * @param overlayDrawList The draw list to render into.
     * @param hostWindow The host window, which must be the current window.
     * @param hostNode The host node, may be null.
     * @param rootPayload The payload window.
     * @param data The preview data.
     */
    private static void dockNodePreviewDockRender(
            @NonNull DrawList overlayDrawList,
            @NonNull Window hostWindow,
            DockNode hostNode,
            @NonNull Window rootPayload,
            @NonNull DockPreviewData data) {
        final StyleVariables style = context.style.variable;
        final boolean isTransparentPayload = context.io.configDockingTransparentPayload;

        // Draw the main preview rectangle
        final int overlayColorMain =
                IkGuiImplUtils.getColor(
                        ColorType.DOCKING_PREVIEW, isTransparentPayload ? 0.60f : 0.40f);
        final int overlayColorDrop =
                IkGuiImplUtils.getColor(
                        ColorType.DOCKING_PREVIEW, isTransparentPayload ? 0.90f : 0.70f);
        final int overlayColorDropHovered =
                IkGuiImplUtils.getColor(
                        ColorType.DOCKING_PREVIEW, isTransparentPayload ? 1.20f : 1.00f);
        final int overlayColorLines =
                IkGuiImplUtils.getColor(
                        ColorType.NAV_WINDOWING_HIGHLIGHT, isTransparentPayload ? 0.80f : 0.60f);

        // Display the area preview
        final boolean canPreviewTabs =
                rootPayload.dockNodeAsHost == null || !rootPayload.dockNodeAsHost.windows.isEmpty();
        if (data.isDropAllowed) {
            final RectFloat overlayRect = data.futureNode.rect();
            if (data.splitDirection == Direction.NONE && canPreviewTabs) {
                overlayRect.setTop(overlayRect.getTop() + IkGuiImplLayout.getFrameHeight());
            }
            if (data.splitDirection != Direction.NONE || data.isCenterAvailable) {
                overlayDrawList.addRectFilled(
                        overlayRect.getLeft(),
                        overlayRect.getTop(),
                        overlayRect.getRight(),
                        overlayRect.getBottom(),
                        overlayColorMain,
                        hostWindow.rounding,
                        IkGuiInternal.calcRoundingFlagsForRectInRect(
                                overlayRect, hostWindow.getRect(), style.dockingSeparatorSize));
            }
        }

        // Display the tab shape/label preview unless we are splitting the node (it generally makes
        // the situation harder to read)
        if (data.isDropAllowed
                && canPreviewTabs
                && data.splitDirection == Direction.NONE
                && data.isCenterAvailable) {
            // Compute the target tab bar geometry so we can locate our preview tabs
            final RectFloat tabBarRect = new RectFloat();
            dockNodeCalcTabBarLayout(data.futureNode, null, tabBarRect, null, null);
            float tabX = tabBarRect.getLeft();
            final float tabY = tabBarRect.getTop();
            final Vector2f tabSize = new Vector2f();
            if (hostNode != null && hostNode.tabBar != null) {
                if (!hostNode.isHiddenTabBar() && !hostNode.isNoTabBar()) {
                    // We don't use the new tab offset because when using a non-persistent-order
                    // tab bar it is incremented with each tab submission
                    tabX += hostNode.tabBar.widthAllTabs + style.itemInnerSpacing.x;
                } else {
                    tabX +=
                            style.itemInnerSpacing.x
                                    + IkGuiImplTabs.tabItemCalcSize(
                                                    hostNode.windows.getFirst(), tabSize)
                                            .x;
                }
            } else if ((hostWindow.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
                // Account for the slight offset which will be added when changing from a title
                // bar to a tab bar
                tabX +=
                        style.itemInnerSpacing.x
                                + IkGuiImplTabs.tabItemCalcSize(hostWindow, tabSize).x;
            }

            // Draw the tab shape/label preview (the payload may be a loose window or a host window
            // carrying multiple tabbed windows)
            final TabBar tabBarWithPayload =
                    rootPayload.dockNodeAsHost != null ? rootPayload.dockNodeAsHost.tabBar : null;
            final int payloadCount = tabBarWithPayload != null ? tabBarWithPayload.tabs.size() : 1;
            for (int payloadIndex = 0; payloadIndex < payloadCount; ++payloadIndex) {
                // The tab bar of a dock node may have non-window tabs manually appended by the
                // user
                final Window payloadWindow =
                        tabBarWithPayload != null
                                ? tabBarWithPayload.tabs.get(payloadIndex).window
                                : rootPayload;
                if (payloadWindow == null) {
                    continue;
                }
                if (!dockNodeIsDropAllowedOne(payloadWindow, hostWindow)) {
                    continue;
                }

                // Calculate the tab bounding box for each payload window
                IkGuiImplTabs.tabItemCalcSize(payloadWindow, tabSize);
                final RectFloat tabBB =
                        new RectFloat(tabX, tabY, tabX + tabSize.x, tabY + tabSize.y);
                tabX += tabSize.x + style.itemInnerSpacing.x;
                final int overlayColorText =
                        payloadWindow.dockStyle.colors[WindowDockStyleColor.TEXT.ordinal()];
                final int overlayColorTabs =
                        payloadWindow.dockStyle.colors[WindowDockStyleColor.TAB_SELECTED.ordinal()];
                final int tabFlags =
                        (payloadWindow.flags & WindowFlags.UNSAVED_DOCUMENT) != 0
                                ? TabItemFlags.UNSAVED_DOCUMENT
                                : TabItemFlags.NONE;
                final boolean clipToTabBar = !tabBarRect.contains(tabBB);
                if (clipToTabBar) {
                    overlayDrawList.pushClipRect(
                            tabBarRect.getLeft(),
                            tabBarRect.getTop(),
                            tabBarRect.getRight(),
                            tabBarRect.getBottom(),
                            true);
                }
                IkGuiImplTabs.tabItemBackground(overlayDrawList, tabBB, tabFlags, overlayColorTabs);
                IkGuiInternal.renderTextClippedEx(
                        overlayDrawList,
                        tabBB.getLeft() + style.framePadding.x,
                        tabBB.getTop() + style.framePadding.y,
                        tabBB.getRight() - style.framePadding.x,
                        tabBB.getBottom(),
                        payloadWindow.name,
                        null,
                        0.0f,
                        0.0f,
                        null,
                        overlayColorText);
                if (clipToTabBar) {
                    overlayDrawList.popClipRect();
                }
            }
        }

        // Display the drop boxes
        final float overlayRounding = Math.max(3.0f, style.frameRounding);
        for (Direction direction : DIRECTIONS_WITH_NONE) {
            final RectFloat drawRect = data.dropRectsDraw[directionIndex(direction) + 1];
            if (!drawRect.isInverted()) {
                final RectFloat drawRectInner = new RectFloat(drawRect);
                drawRectInner.expand(-2.0f, -2.0f);
                final int overlayColor =
                        (data.splitDirection == direction && data.isSplitDirectionExplicit)
                                ? overlayColorDropHovered
                                : overlayColorDrop;
                final float thickness = 1.0f;
                final float centerX = (float) Math.floor(drawRectInner.getCenterX());
                final float centerY = (float) Math.floor(drawRectInner.getCenterY());
                overlayDrawList.addRectFilled(
                        drawRect.getLeft(),
                        drawRect.getTop(),
                        drawRect.getRight(),
                        drawRect.getBottom(),
                        overlayColor,
                        overlayRounding);
                overlayDrawList.addRect(
                        drawRectInner.getLeft(),
                        drawRectInner.getTop(),
                        drawRectInner.getRight(),
                        drawRectInner.getBottom(),
                        overlayColorLines,
                        overlayRounding,
                        DrawFlags.ROUND_CORNERS_ALL,
                        thickness);
                if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                    overlayDrawList.addLine(
                            centerX,
                            drawRectInner.getTop(),
                            centerX,
                            drawRectInner.getBottom(),
                            overlayColorLines,
                            thickness);
                }
                if (direction == Direction.UP || direction == Direction.DOWN) {
                    overlayDrawList.addLine(
                            drawRectInner.getLeft(),
                            centerY,
                            drawRectInner.getRight(),
                            centerY,
                            overlayColorLines,
                            thickness);
                }
            }

            // Stop after the center
            if ((hostNode != null && (hostNode.mergedFlags & DockNodeFlags.NO_DOCKING_SPLIT) != 0)
                    || context.io.configDockingNoSplit) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dock node tree manipulation
    // ---------------------------------------------------------------------------------------------

    /**
     * Fetch the root node for a given node.
     *
     * @param node The node to fetch the root for.
     * @return The root node, or null if node was null.
     */
    static DockNode dockNodeGetRootNode(DockNode node) {
        if (node == null) {
            return null;
        }
        while (node.parentNode != null) {
            node = node.parentNode;
        }
        return node;
    }

    /**
     * Check if a node is the parent node or an ancestor of another.
     *
     * @param node The node to check.
     * @param parent The potential ancestor.
     * @return True if node is parent or below it in the hierarchy.
     */
    static boolean dockNodeIsInHierarchyOf(DockNode node, DockNode parent) {
        while (node != null) {
            if (node == parent) {
                return true;
            }
            node = node.parentNode;
        }
        return false;
    }

    /**
     * The depth of a node in the tree, 0 for root nodes.
     *
     * @param node The node.
     * @return The depth.
     */
    static int dockNodeGetDepth(@NonNull DockNode node) {
        int depth = 0;
        while (node.parentNode != null) {
            node = node.parentNode;
            ++depth;
        }
        return depth;
    }

    /**
     * The ID of the window menu button of a node.
     *
     * @param node The node.
     * @return The button ID.
     */
    static int dockNodeGetWindowMenuButtonID(@NonNull DockNode node) {
        return com.ikalagaming.graphics.gui.util.Hash.getID("#COLLAPSE", node.id);
    }

    /**
     * Split a node into two child nodes.
     *
     * @param parentNode The node to split.
     * @param splitAxis The axis to split along.
     * @param splitInheritorChildIndex The index of the child that inherits the contents of the
     *     parent.
     * @param splitRatio The ratio of the size of the first child.
     * @param newNode An existing node to use for the new child, may be null to create one.
     */
    static void dockNodeTreeSplit(
            @NonNull DockNode parentNode,
            @NonNull Axis splitAxis,
            int splitInheritorChildIndex,
            float splitRatio,
            DockNode newNode) {
        if (splitAxis == Axis.NONE) {
            IkGuiImplDebugTools.reportError(log, "Splitting a dock node requires an axis");
            return;
        }
        final StyleVariables style = context.style.variable;

        final DockNode child0 =
                (newNode != null && splitInheritorChildIndex != 0)
                        ? newNode
                        : dockContextAddNode(0);
        child0.parentNode = parentNode;

        final DockNode child1 =
                (newNode != null && splitInheritorChildIndex != 1)
                        ? newNode
                        : dockContextAddNode(0);
        child1.parentNode = parentNode;

        final DockNode childInheritor = splitInheritorChildIndex == 0 ? child0 : child1;
        dockNodeMoveChildNodes(childInheritor, parentNode);
        parentNode.childNodes[0] = child0;
        parentNode.childNodes[1] = child1;
        parentNode.childNodes[splitInheritorChildIndex].visibleWindow = parentNode.visibleWindow;
        parentNode.splitAxis = splitAxis;
        parentNode.visibleWindow = null;
        parentNode.authorityForPosition = DataAuthority.DOCK_NODE;
        parentNode.authorityForSize = DataAuthority.DOCK_NODE;

        final int axis = splitAxis.intValue;
        float sizeAvailable = parentNode.size.get(axis) - style.dockingSeparatorSize;
        sizeAvailable = Math.max(sizeAvailable, style.windowMinSize.get(axis) * 2.0f);
        if (sizeAvailable <= 0.0f) {
            // If you created a node manually with dockBuilderAddNode(), you need to also call
            // dockBuilderSetNodeSize() before splitting
            IkGuiImplDebugTools.reportError(log, "Splitting a dock node with no size");
        }
        child0.sizeRef.set(parentNode.size);
        child1.sizeRef.set(parentNode.size);
        child0.sizeRef.setComponent(axis, IkGuiInternal.truncate(sizeAvailable * splitRatio));
        child1.sizeRef.setComponent(
                axis, IkGuiInternal.truncate(sizeAvailable - child0.sizeRef.get(axis)));

        dockNodeMoveWindows(parentNode.childNodes[splitInheritorChildIndex], parentNode);
        IkGuiImplDockSettings.dockSettingsRenameNodeReferences(
                parentNode.id, parentNode.childNodes[splitInheritorChildIndex].id);
        dockNodeUpdateHasCentralNodeChild(dockNodeGetRootNode(parentNode));
        dockNodeTreeUpdatePosSize(parentNode, parentNode.position, parentNode.size, null);

        // Flags transfer (e.g. this is where we transfer the central node property)
        child0.sharedFlags = parentNode.sharedFlags & DockNodeFlags.SHARED_FLAGS_INHERIT_MASK;
        child1.sharedFlags = parentNode.sharedFlags & DockNodeFlags.SHARED_FLAGS_INHERIT_MASK;
        childInheritor.localFlags = parentNode.localFlags & DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlags &= ~DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        child0.updateMergedFlags();
        child1.updateMergedFlags();
        parentNode.updateMergedFlags();
        if (childInheritor.isCentralNode()) {
            dockNodeGetRootNode(parentNode).centralNode = childInheritor;
        }
    }

    /**
     * Merge the children of a node back into the node.
     *
     * @param parentNode The node.
     * @param mergeLeadChild The child whose child nodes are moved into the parent.
     */
    static void dockNodeTreeMerge(@NonNull DockNode parentNode, DockNode mergeLeadChild) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockNodeTreeMerge: 0x%08X + 0x%08X back into parent 0x%08X",
                parentNode.childNodes[0] != null ? parentNode.childNodes[0].id : 0,
                parentNode.childNodes[1] != null ? parentNode.childNodes[1].id : 0,
                parentNode.id);
        // When called from dockContextProcessUndockNode() it is possible that one of the children
        // is null
        final DockNode child0 = parentNode.childNodes[0];
        final DockNode child1 = parentNode.childNodes[1];
        if (child0 == null && child1 == null) {
            IkGuiImplDebugTools.reportError(log, "No child nodes to merge");
            return;
        }
        if (mergeLeadChild != child0 && mergeLeadChild != child1) {
            IkGuiImplDebugTools.reportError(
                    log, "The merge lead child must be one of the children");
            return;
        }
        if ((child0 != null && !child0.windows.isEmpty())
                || (child1 != null && !child1.windows.isEmpty())) {
            if (parentNode.tabBar != null) {
                IkGuiImplDebugTools.reportError(
                        log, "Parent node already has a tab bar while merging dock nodes");
            }
            if (!parentNode.windows.isEmpty()) {
                IkGuiImplDebugTools.reportError(
                        log, "Parent node already has windows while merging dock nodes");
            }
        }
        log.trace(
                "dockNodeTreeMerge: 0x{} + 0x{} back into parent 0x{}",
                Integer.toHexString(child0 != null ? child0.id : 0),
                Integer.toHexString(child1 != null ? child1.id : 0),
                Integer.toHexString(parentNode.id));

        final Vector2f backupLastExplicitSize = new Vector2f(parentNode.sizeRef);
        if (mergeLeadChild != null) {
            dockNodeMoveChildNodes(parentNode, mergeLeadChild);
        }
        if (child0 != null) {
            // Generally only 1 of the 2 child nodes will have windows
            dockNodeMoveWindows(parentNode, child0);
            IkGuiImplDockSettings.dockSettingsRenameNodeReferences(child0.id, parentNode.id);
        }
        if (child1 != null) {
            dockNodeMoveWindows(parentNode, child1);
            IkGuiImplDockSettings.dockSettingsRenameNodeReferences(child1.id, parentNode.id);
        }
        dockNodeApplyPosSizeToWindows(parentNode);
        parentNode.authorityForPosition = DataAuthority.AUTO;
        parentNode.authorityForSize = DataAuthority.AUTO;
        parentNode.authorityForViewport = DataAuthority.AUTO;
        parentNode.visibleWindow = mergeLeadChild != null ? mergeLeadChild.visibleWindow : null;
        parentNode.sizeRef.set(backupLastExplicitSize);

        // Flags transfer (preserve the dockspace flag)
        parentNode.localFlags &= ~DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlags |=
                (child0 != null ? child0.localFlags : DockNodeFlags.NONE)
                        & DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlags |=
                (child1 != null ? child1.localFlags : DockNodeFlags.NONE)
                        & DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlagsInWindows =
                (child0 != null ? child0.localFlagsInWindows : DockNodeFlags.NONE)
                        | (child1 != null ? child1.localFlagsInWindows : DockNodeFlags.NONE);
        parentNode.updateMergedFlags();

        if (child0 != null) {
            dockContextDeleteNode(child0);
        }
        if (child1 != null) {
            dockContextDeleteNode(child1);
        }
    }

    /**
     * Update the position and size for a node hierarchy, without affecting the child windows yet.
     * Depth-first, pre-order.
     *
     * @param node The node.
     * @param position The position of the node.
     * @param size The size of the node.
     * @param onlyWriteToSingleNode Only set when turning a node visible mid-frame and we need its
     *     size right away, otherwise null to write to all nodes.
     */
    static void dockNodeTreeUpdatePosSize(
            @NonNull DockNode node,
            @NonNull Vector2f position,
            @NonNull Vector2f size,
            DockNode onlyWriteToSingleNode) {
        final StyleVariables style = context.style.variable;
        final boolean writeToNode = onlyWriteToSingleNode == null || onlyWriteToSingleNode == node;
        if (writeToNode) {
            node.position.set(position);
            node.size.set(size);
        }

        if (node.isLeafNode()) {
            return;
        }

        final DockNode child0 = node.childNodes[0];
        final DockNode child1 = node.childNodes[1];
        final Vector2f child0Position = new Vector2f(position);
        final Vector2f child1Position = new Vector2f(position);
        final Vector2f child0Size = new Vector2f(size);
        final Vector2f child1Size = new Vector2f(size);

        final boolean child0IsTowardSingleNode =
                onlyWriteToSingleNode != null
                        && dockNodeIsInHierarchyOf(onlyWriteToSingleNode, child0);
        final boolean child1IsTowardSingleNode =
                onlyWriteToSingleNode != null
                        && dockNodeIsInHierarchyOf(onlyWriteToSingleNode, child1);
        final boolean child0IsOrWillBeVisible = child0.isVisible || child0IsTowardSingleNode;
        final boolean child1IsOrWillBeVisible = child1.isVisible || child1IsTowardSingleNode;

        if (child0IsOrWillBeVisible && child1IsOrWillBeVisible) {
            final float spacing = style.dockingSeparatorSize;
            final int axis = node.splitAxis.intValue;
            final float sizeAvailable = Math.max(size.get(axis) - spacing, 0.0f);

            // Size allocation policy
            // 1) The first 0..windowMinSize[axis]*2 are allocated evenly to both windows.
            final float sizeMinEach =
                    IkGuiInternal.truncate(
                            Math.min(sizeAvailable, style.windowMinSize.get(axis) * 2.0f) * 0.5f);

            // 2) Process locked absolute size (during a splitter resize we preserve the child of
            // nodes not touching the splitter edge)
            if (child0.wantLockSizeOnce && !child1.wantLockSizeOnce) {
                child0Size.setComponent(
                        axis, Math.min(sizeAvailable - 1.0f, child0.size.get(axis)));
                child0.sizeRef.setComponent(axis, child0Size.get(axis));
                child1Size.setComponent(axis, sizeAvailable - child0Size.get(axis));
                child1.sizeRef.setComponent(axis, child1Size.get(axis));
            } else if (child1.wantLockSizeOnce && !child0.wantLockSizeOnce) {
                child1Size.setComponent(
                        axis, Math.min(sizeAvailable - 1.0f, child1.size.get(axis)));
                child1.sizeRef.setComponent(axis, child1Size.get(axis));
                child0Size.setComponent(axis, sizeAvailable - child1Size.get(axis));
                child0.sizeRef.setComponent(axis, child0Size.get(axis));
            } else if (child0.wantLockSizeOnce && child1.wantLockSizeOnce) {
                // We cannot honor the requested size, so apply the ratio. Currently this path will
                // only be taken if code programmatically sets wantLockSizeOnce.
                final float splitRatio =
                        child0Size.get(axis) / (child0Size.get(axis) + child1Size.get(axis));
                child0Size.setComponent(axis, IkGuiInternal.truncate(sizeAvailable * splitRatio));
                child0.sizeRef.setComponent(axis, child0Size.get(axis));
                child1Size.setComponent(axis, sizeAvailable - child0Size.get(axis));
                child1.sizeRef.setComponent(axis, child1Size.get(axis));
            }

            // 3) If one window is the central node (~ use the remaining space, should be made
            // explicit!), use the explicit size from the other, and the remainder for the central
            // node
            else if (child0.sizeRef.get(axis) != 0.0f && child1.hasCentralNodeChild) {
                child0Size.setComponent(
                        axis, Math.min(sizeAvailable - sizeMinEach, child0.sizeRef.get(axis)));
                child1Size.setComponent(axis, sizeAvailable - child0Size.get(axis));
            } else if (child1.sizeRef.get(axis) != 0.0f && child0.hasCentralNodeChild) {
                child1Size.setComponent(
                        axis, Math.min(sizeAvailable - sizeMinEach, child1.sizeRef.get(axis)));
                child0Size.setComponent(axis, sizeAvailable - child1Size.get(axis));
            } else {
                // 4) Otherwise distribute according to the relative ratio of each size ref value
                final float splitRatio =
                        child0.sizeRef.get(axis)
                                / (child0.sizeRef.get(axis) + child1.sizeRef.get(axis));
                child0Size.setComponent(
                        axis,
                        Math.max(
                                sizeMinEach,
                                IkGuiInternal.truncate(sizeAvailable * splitRatio + 0.5f)));
                child1Size.setComponent(axis, sizeAvailable - child0Size.get(axis));
            }

            child1Position.setComponent(
                    axis, child1Position.get(axis) + spacing + child0Size.get(axis));
        }

        if (onlyWriteToSingleNode == null) {
            child0.wantLockSizeOnce = false;
            child1.wantLockSizeOnce = false;
        }

        final boolean child0Recurse =
                onlyWriteToSingleNode != null ? child0IsTowardSingleNode : child0.isVisible;
        final boolean child1Recurse =
                onlyWriteToSingleNode != null ? child1IsTowardSingleNode : child1.isVisible;
        if (child0Recurse) {
            dockNodeTreeUpdatePosSize(child0, child0Position, child0Size, null);
        }
        if (child1Recurse) {
            dockNodeTreeUpdatePosSize(child1, child1Position, child1Size, null);
        }
    }

    /**
     * Find the leaf nodes that touch a splitter.
     *
     * @param node The node to search.
     * @param axis The axis of the splitter.
     * @param side The side of the splitter the node is on.
     * @param touchingNodes Where to store the touching nodes.
     */
    private static void dockNodeTreeUpdateSplitterFindTouchingNode(
            @NonNull DockNode node,
            @NonNull Axis axis,
            int side,
            @NonNull List<DockNode> touchingNodes) {
        if (node.isLeafNode()) {
            touchingNodes.add(node);
            return;
        }
        if (node.childNodes[0].isVisible
                && (node.splitAxis != axis || side == 0 || !node.childNodes[1].isVisible)) {
            dockNodeTreeUpdateSplitterFindTouchingNode(
                    node.childNodes[0], axis, side, touchingNodes);
        }
        if (node.childNodes[1].isVisible
                && (node.splitAxis != axis || side == 1 || !node.childNodes[0].isVisible)) {
            dockNodeTreeUpdateSplitterFindTouchingNode(
                    node.childNodes[1], axis, side, touchingNodes);
        }
    }

    /**
     * Process and draw the resizing splitters of a node tree. Depth-first, pre-order.
     *
     * @param node The node.
     */
    static void dockNodeTreeUpdateSplitter(@NonNull DockNode node) {
        if (node.isLeafNode()) {
            return;
        }
        final StyleVariables style = context.style.variable;

        final DockNode child0 = node.childNodes[0];
        final DockNode child1 = node.childNodes[1];
        if (child0.isVisible && child1.isVisible) {
            // The bounding box of the splitter covers the space between both nodes (width =
            // spacing, height = size on the other axis for when splitting horizontally)
            final Axis axisEnum = node.splitAxis;
            if (axisEnum == Axis.NONE) {
                IkGuiImplDebugTools.reportError(
                        log, "Split dock node 0x{} has no axis", Integer.toHexString(node.id));
                return;
            }
            final int axis = axisEnum.intValue;
            final Vector2f bbMin = new Vector2f(child0.position);
            final Vector2f bbMax = new Vector2f(child1.position);
            bbMin.setComponent(axis, bbMin.get(axis) + child0.size.get(axis));
            bbMax.setComponent(axis ^ 1, bbMax.get(axis ^ 1) + child1.size.get(axis ^ 1));
            final RectFloat bb = new RectFloat(bbMin.x, bbMin.y, bbMax.x, bbMax.y);

            // Merged flags for both children
            final int mergedFlags = child0.mergedFlags | child1.mergedFlags;
            final int noResizeAxisFlag =
                    axisEnum == Axis.X
                            ? DockNodeFlags.INTERNAL_NO_RESIZE_X
                            : DockNodeFlags.INTERNAL_NO_RESIZE_Y;
            if ((mergedFlags & DockNodeFlags.NO_RESIZE) != 0
                    || (mergedFlags & noResizeAxisFlag) != 0) {
                final Window window = context.windowCurrent;
                window.drawList.addRectFilled(
                        bb.getLeft(),
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.SEPARATOR),
                        style.frameRounding);
            } else {
                IkGuiImplUtils.pushID(node.id);

                // Find the resizing limits by gathering the list of nodes that are touching the
                // splitter line
                final List<DockNode> touchingNodes0 = new ArrayList<>();
                final List<DockNode> touchingNodes1 = new ArrayList<>();
                final float minSize = style.windowMinSize.get(axis);
                final float[] resizeLimits = {
                    child0.position.get(axis) + minSize,
                    child1.position.get(axis) + child1.size.get(axis) - minSize
                };

                final int splitterID = IkGuiImplUtils.getID("##Splitter");
                // Only process when the splitter is active
                if (context.activeID == splitterID) {
                    dockNodeTreeUpdateSplitterFindTouchingNode(child0, axisEnum, 1, touchingNodes0);
                    dockNodeTreeUpdateSplitterFindTouchingNode(child1, axisEnum, 0, touchingNodes1);
                    for (DockNode touchingNode : touchingNodes0) {
                        resizeLimits[0] =
                                Math.max(
                                        resizeLimits[0], touchingNode.position.get(axis) + minSize);
                    }
                    for (DockNode touchingNode : touchingNodes1) {
                        resizeLimits[1] =
                                Math.min(
                                        resizeLimits[1],
                                        touchingNode.position.get(axis)
                                                + touchingNode.size.get(axis)
                                                - minSize);
                    }
                }

                // Use a short delay before highlighting the splitter (and changing the mouse
                // cursor) in order for regular mouse movement to not highlight many splitters
                final float[] currentSize0 = {child0.size.get(axis)};
                final float[] currentSize1 = {child1.size.get(axis)};
                final float minSize0 = resizeLimits[0] - child0.position.get(axis);
                final float minSize1 =
                        child1.position.get(axis) + child1.size.get(axis) - resizeLimits[1];
                final int backgroundColor =
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.WINDOW_BACKGROUND);
                if (IkGuiImplLayout.splitterBehavior(
                        bb,
                        splitterID,
                        axisEnum,
                        currentSize0,
                        currentSize1,
                        minSize0,
                        minSize1,
                        context.windowBorderHoverPadding,
                        IkGuiImplWindows.WINDOWS_RESIZE_FROM_EDGES_FEEDBACK_TIMER,
                        backgroundColor)) {
                    if (!touchingNodes0.isEmpty() && !touchingNodes1.isEmpty()) {
                        child0.size.setComponent(axis, currentSize0[0]);
                        child0.sizeRef.setComponent(axis, currentSize0[0]);
                        child1.position.setComponent(
                                axis,
                                child1.position.get(axis)
                                        - (currentSize1[0] - child1.size.get(axis)));
                        child1.size.setComponent(axis, currentSize1[0]);
                        child1.sizeRef.setComponent(axis, currentSize1[0]);

                        // Lock the size of every node that is a sibling of the node we are
                        // touching. This might be less desirable if we can merge siblings of the
                        // same axis into the same parental level.
                        for (int side = 0; side < 2; ++side) {
                            final List<DockNode> touchingNodes =
                                    side == 0 ? touchingNodes0 : touchingNodes1;
                            for (DockNode touchingNode : touchingNodes) {
                                while (touchingNode.parentNode != node) {
                                    if (touchingNode.parentNode.splitAxis == axisEnum) {
                                        // Mark the other node so its size will be preserved
                                        // during the upcoming call to
                                        // dockNodeTreeUpdatePosSize()
                                        final DockNode nodeToPreserve =
                                                touchingNode.parentNode.childNodes[side];
                                        nodeToPreserve.wantLockSizeOnce = true;
                                    }
                                    touchingNode = touchingNode.parentNode;
                                }
                            }
                        }

                        dockNodeTreeUpdatePosSize(
                                child0,
                                new Vector2f(child0.position),
                                new Vector2f(child0.size),
                                null);
                        dockNodeTreeUpdatePosSize(
                                child1,
                                new Vector2f(child1.position),
                                new Vector2f(child1.size),
                                null);
                        IkGuiInternal.markIniSettingsDirty();
                    }
                }
                IkGuiImplUtils.popID();
            }
        }

        if (child0.isVisible) {
            dockNodeTreeUpdateSplitter(child0);
        }
        if (child1.isVisible) {
            dockNodeTreeUpdateSplitter(child1);
        }
    }

    /**
     * Find the first leaf node in a hierarchy.
     *
     * @param node The node to search.
     * @return The first leaf node.
     */
    static DockNode dockNodeTreeFindFallbackLeafNode(@NonNull DockNode node) {
        if (node.isLeafNode()) {
            return node;
        }
        final DockNode leafNode0 = dockNodeTreeFindFallbackLeafNode(node.childNodes[0]);
        if (leafNode0 != null) {
            return leafNode0;
        }
        return dockNodeTreeFindFallbackLeafNode(node.childNodes[1]);
    }

    /**
     * Find the visible node in a hierarchy that contains a position.
     *
     * @param node The node to search.
     * @param position The position.
     * @return The leaf node at the position, a parent node if hovering over a splitter, or null.
     */
    static DockNode dockNodeTreeFindVisibleNodeByPos(
            @NonNull DockNode node, @NonNull Vector2f position) {
        if (!node.isVisible) {
            return null;
        }

        if (!node.rect().contains(position)) {
            return null;
        }

        if (node.isLeafNode()) {
            return node;
        }
        final DockNode hoveredNode0 =
                dockNodeTreeFindVisibleNodeByPos(node.childNodes[0], position);
        if (hoveredNode0 != null) {
            return hoveredNode0;
        }
        final DockNode hoveredNode1 =
                dockNodeTreeFindVisibleNodeByPos(node.childNodes[1], position);
        if (hoveredNode1 != null) {
            return hoveredNode1;
        }

        // This means we are hovering over the splitter/spacing of a parent node
        return node;
    }

    // ---------------------------------------------------------------------------------------------
    // Public functions
    // ---------------------------------------------------------------------------------------------

    /**
     * Set the dock node of a window, called via setNextWindowDockID().
     *
     * @param window The window.
     * @param dockID The dock node ID.
     * @param condition The condition.
     */
    static void setWindowDock(@NonNull Window window, int dockID, @NonNull Condition condition) {
        // Test the condition and clear flags for next time
        if (!ConditionAllowed.shouldResolve(condition, window.dockConditionAllowed)) {
            return;
        }
        window.dockConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);

        if (window.dockID == dockID) {
            return;
        }

        // If the user attempts to set a dock ID that is a split node, we'll dig within to find a
        // suitable docking spot
        DockNode newNode = dockContextFindNodeByID(dockID);
        if (newNode != null && newNode.isSplitNode()) {
            // Policy: find the central node or the latest focused node. We first move back to our
            // root node.
            newNode = dockNodeGetRootNode(newNode);
            if (newNode.centralNode != null) {
                if (!newNode.centralNode.isCentralNode()) {
                    IkGuiImplDebugTools.reportError(
                            log, "The central node of a dock tree is not marked as central");
                }
                dockID = newNode.centralNode.id;
            } else {
                dockID = newNode.lastFocusedNodeID;
            }
        }

        if (window.dockID == dockID) {
            return;
        }

        if (window.dockNode != null) {
            dockNodeRemoveWindow(window.dockNode, window, 0);
        }
        window.dockID = dockID;
    }

    /**
     * Create an explicit dockspace node within an existing window. Also exposes dock node flags,
     * and creates a central node by default. The central node is always displayed even when empty
     * and shrinks/extends according to the requested size of its neighbors. dockSpace() needs to be
     * submitted before any window it can host. If you use a dockspace, submit it early in your
     * frame. When {@link DockNodeFlags#KEEP_ALIVE_ONLY} is set, nothing is submitted in the current
     * window (the function may be called from any location).
     *
     * @param dockspaceID The ID of the dockspace, which must not be 0.
     * @param width The width, 0 to use the available width or negative to leave space.
     * @param height The height, 0 to use the available height or negative to leave space.
     * @param flags Dock node flags.
     * @param windowClass The window class of the dockspace, may be null.
     * @return The dockspace ID.
     * @see DockNodeFlags
     */
    static int dockSpace(
            int dockspaceID, float width, float height, int flags, WindowClass windowClass) {
        Window window = IkGuiInternal.getCurrentWindowRead();
        if ((context.io.configFlags & ConfigFlags.DOCKING_ENABLE) == 0) {
            return 0;
        }

        // Early out if the parent window is hidden/collapsed. This is faster, but also
        // dockNodeUpdateTabBar() relies on the tab bar layout running (which won't if skip items
        // is true) to set the next selected tab ID to 0.
        if (window.skipItems) {
            flags |= DockNodeFlags.KEEP_ALIVE_ONLY;
        }
        if ((flags & DockNodeFlags.KEEP_ALIVE_ONLY) == 0) {
            // Call to mark the window as written to
            window = IkGuiInternal.getCurrentWindow();
        }

        // These flags are automatically set by dockSpace() as local flags, not shared flags!
        if ((flags & DockNodeFlags.INTERNAL_DOCK_SPACE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Don't pass the internal dockspace flag to dockSpace()");
            flags &= ~DockNodeFlags.INTERNAL_DOCK_SPACE;
        }
        if ((flags & DockNodeFlags.INTERNAL_CENTRAL_NODE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Don't pass the internal central node flag to dockSpace()");
            flags &= ~DockNodeFlags.INTERNAL_CENTRAL_NODE;
        }

        if (dockspaceID == 0) {
            IkGuiImplDebugTools.reportError(log, "Dockspace IDs must not be 0");
            return 0;
        }
        DockNode node = dockContextFindNodeByID(dockspaceID);
        if (node == null) {
            log.trace("dockSpace: dockspace node 0x{} created", Integer.toHexString(dockspaceID));
            node = dockContextAddNode(dockspaceID);
            node.setLocalFlags(DockNodeFlags.INTERNAL_CENTRAL_NODE);
        }
        if (windowClass != null && windowClass.classID != node.windowClass.classID) {
            log.trace(
                    "dockSpace: dockspace node 0x{}: set up window class 0x{} -> 0x{}",
                    Integer.toHexString(dockspaceID),
                    Integer.toHexString(node.windowClass.classID),
                    Integer.toHexString(windowClass.classID));
        }
        node.sharedFlags = flags;
        if (windowClass != null) {
            node.windowClass.set(windowClass);
        } else {
            node.windowClass.reset();
        }

        // When a dockspace transitioned from implicit to explicit this may be called a second
        // time. It is possible that the node has already been claimed by a docked window which
        // appeared before the dockspace node, so we overwrite the dockspace flag again.
        if (node.lastFrameActive == context.frameCount
                && (flags & DockNodeFlags.KEEP_ALIVE_ONLY) == 0) {
            if (node.isDockSpace()) {
                IkGuiImplDebugTools.reportError(
                        log, "Cannot call dockSpace() twice a frame with the same ID");
            }
            node.setLocalFlags(node.localFlags | DockNodeFlags.INTERNAL_DOCK_SPACE);
            return dockspaceID;
        }
        node.setLocalFlags(node.localFlags | DockNodeFlags.INTERNAL_DOCK_SPACE);

        // Keep alive mode, this allows windows docked into this node to stay docked even if they
        // are not visible
        if ((flags & DockNodeFlags.KEEP_ALIVE_ONLY) != 0) {
            node.lastFrameAlive = context.frameCount;
            return dockspaceID;
        }

        final Vector2f contentAvailable = new Vector2f();
        IkGuiImplUtils.getContentRegionAvailable(contentAvailable);
        float sizeX = IkGuiInternal.truncate(width);
        float sizeY = IkGuiInternal.truncate(height);
        // Arbitrary minimum child size (0 causes too many issues)
        if (sizeX <= 0.0f) {
            sizeX = Math.max(contentAvailable.x + sizeX, 4.0f);
        }
        if (sizeY <= 0.0f) {
            sizeY = Math.max(contentAvailable.y + sizeY, 4.0f);
        }

        node.position.set(window.cursorPosition);
        node.size.set(sizeX, sizeY);
        node.sizeRef.set(sizeX, sizeY);
        IkGuiImplWindows.setNextWindowPos(
                node.position.x, node.position.y, Condition.NONE, 0.0f, 0.0f);
        IkGuiImplWindows.setNextWindowSize(node.size.x, node.size.y, Condition.NONE);
        context.nextWindowData.positionUndock = false;

        final int windowFlags =
                WindowFlags.INTERNAL_CHILD_WINDOW
                        | WindowFlags.INTERNAL_DOCK_NODE_HOST
                        | WindowFlags.NO_SAVED_SETTINGS
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_COLLAPSE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SCROLLBAR
                        | WindowFlags.NO_SCROLL_WITH_MOUSE
                        | WindowFlags.NO_BACKGROUND;

        final String title = String.format("%s/DockSpace_%08X", window.name, dockspaceID);

        IkGuiImplUtils.pushStyleVarFloat(StyleVariable.CHILD_BORDER_SIZE, 0.0f);
        IkGuiImplWindows.begin(title, null, windowFlags);
        IkGuiImplUtils.popStyleVar();

        final Window hostWindow = context.windowCurrent;
        dockNodeSetupHostWindow(node, hostWindow);
        hostWindow.childID = window.getID(title);
        node.onlyNodeWithWindows = null;

        if (!node.isRootNode()) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Dockspace node 0x{} is not a root node",
                    Integer.toHexString(dockspaceID));
        }

        // We need to handle the rare case where a central node is missing. This can happen if the
        // node was first created manually with dockBuilderAddNode() but without the dockspace
        // flag. Doing it correctly would set the central node flag, which would then propagate
        // according to subsequent splits. It would also be ambiguous to attempt to assign a
        // central node while there are split nodes, so we wait until there's a single node
        // remaining. The specific sub-property of the central node we are interested in
        // recovering here is the "don't delete when empty" property, as it doesn't make sense for
        // an empty dockspace to not have this property.
        if (node.isLeafNode() && !node.isCentralNode()) {
            node.setLocalFlags(node.localFlags | DockNodeFlags.INTERNAL_CENTRAL_NODE);
        }

        // Update the node
        dockNodeUpdate(node);

        IkGuiImplWindows.end();

        final RectFloat bb =
                new RectFloat(
                        node.position.x,
                        node.position.y,
                        node.position.x + sizeX,
                        node.position.y + sizeY);
        IkGuiInternal.itemSize(sizeX, sizeY);
        // Not a nav point (could be, would need to draw the nav rect and replicate/refactor
        // activation from beginChild(), but Ctrl+Tab seems to work better here)
        IkGuiInternal.itemAdd(bb, dockspaceID, null, ItemFlags.NO_NAV);
        // To fulfill isItemHovered(), similar to endChild()
        if ((context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_RECT) != 0
                && IkGuiInternal.isWindowChildOf(context.windowHovered, hostWindow, false, true)) {
            context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_WINDOW;
        }

        return dockspaceID;
    }

    /**
     * Create a dockspace covering a viewport. Use with {@link
     * DockNodeFlags#PASSTHROUGH_CENTRAL_NODE}! The limitation with this call is that your window
     * won't have a local menu bar, but you can also use beginMainMenuBar().
     *
     * @param dockspaceID The ID of the dockspace, or 0 to use the default.
     * @param viewport The viewport, or null for the main viewport.
     * @param dockspaceFlags Dock node flags.
     * @param windowClass The window class of the dockspace, may be null.
     * @return The dockspace ID.
     */
    static int dockSpaceOverViewport(
            int dockspaceID, Viewport viewport, int dockspaceFlags, WindowClass windowClass) {
        if (viewport == null) {
            viewport = IkGuiImplViewports.getMainViewport();
        }

        // Submit a window filling the entire viewport
        IkGuiImplWindows.setNextWindowPos(
                viewport.workPosition.x, viewport.workPosition.y, Condition.NONE, 0.0f, 0.0f);
        IkGuiImplWindows.setNextWindowSize(
                viewport.workSize.x, viewport.workSize.y, Condition.NONE);
        IkGuiImplViewports.setNextWindowViewport(viewport.id);

        int hostWindowFlags =
                WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_COLLAPSE
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_MOVE
                        | WindowFlags.NO_DOCKING
                        | WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS
                        | WindowFlags.NO_NAV_FOCUS;
        if ((dockspaceFlags & DockNodeFlags.PASSTHROUGH_CENTRAL_NODE) != 0) {
            hostWindowFlags |= WindowFlags.NO_BACKGROUND;
        }

        // When using KEEP_ALIVE_ONLY with dockSpaceOverViewport() we might be able to spare
        // submitting the window, since dockSpace() with that flag doesn't need a window. We'd only
        // need to compute the default ID accordingly.
        if ((dockspaceFlags & DockNodeFlags.KEEP_ALIVE_ONLY) != 0) {
            hostWindowFlags |= WindowFlags.NO_MOUSE_INPUTS;
        }

        final String label = String.format("WindowOverViewport_%08X", viewport.id);

        IkGuiImplUtils.pushStyleVarFloat(StyleVariable.WINDOW_ROUNDING, 0.0f);
        IkGuiImplUtils.pushStyleVarFloat(StyleVariable.WINDOW_BORDER_SIZE, 0.0f);
        IkGuiImplUtils.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
        IkGuiImplWindows.begin(label, null, hostWindowFlags);
        IkGuiImplUtils.popStyleVar(3);

        // Submit the dockspace
        if (dockspaceID == 0) {
            dockspaceID = IkGuiImplUtils.getID("DockSpace");
        }
        dockSpace(dockspaceID, 0.0f, 0.0f, dockspaceFlags, windowClass);

        IkGuiImplWindows.end();

        return dockspaceID;
    }

    // ---------------------------------------------------------------------------------------------
    // Begin/end support
    // ---------------------------------------------------------------------------------------------

    /**
     * Check if a window always wants its own tab bar, because of {@link
     * IkIO#configDockingAlwaysTabBar} or its window class.
     *
     * @param window The window.
     * @return True if the window wants its own tab bar.
     */
    static boolean getWindowAlwaysWantOwnTabBar(@NonNull Window window) {
        // We don't support always having a tab bar on the fallback/implicit window to avoid unused
        // dock node overhead/noise
        return (context.io.configDockingAlwaysTabBar || window.windowClass.dockingAlwaysTabBar)
                && (window.flags
                                & (WindowFlags.INTERNAL_CHILD_WINDOW
                                        | WindowFlags.NO_TITLE_BAR
                                        | WindowFlags.NO_DOCKING))
                        == 0
                && !window.isFallbackWindow;
    }

    /**
     * Bind a window to the dock node for its dock ID, creating the node if needed.
     *
     * @param window The window.
     * @return The node, or null if the window was undocked instead.
     */
    static DockNode dockContextBindNodeToWindow(@NonNull Window window) {
        DockNode node = dockContextFindNodeByID(window.dockID);
        if (window.dockNode != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Binding window {} that is already in a dock node", window.name);
            return window.dockNode;
        }

        // We should not be docking into a split node (setWindowDock() should avoid this)
        if (node != null && node.isSplitNode()) {
            dockContextProcessUndockWindow(window, true);
            return null;
        }

        // Create the node
        if (node == null) {
            node = dockContextAddNode(window.dockID);
            node.authorityForPosition = DataAuthority.WINDOW;
            node.authorityForSize = DataAuthority.WINDOW;
            node.authorityForViewport = DataAuthority.WINDOW;
            node.lastFrameAlive = context.frameCount;
        }

        // If the node just turned visible and is part of a hierarchy, it doesn't have a size
        // assigned by dockNodeTreeUpdatePosSize() yet, so we're forcing a pos/size update from the
        // first ancestor that is already visible (often it will be the root node). If we don't do
        // this, the window will be assigned a zero size on its first frame, which won't ideally
        // warm up the layout. This is a little wonky because we don't normally update the pos/size
        // of visible nodes mid-frame.
        if (!node.isVisible) {
            DockNode ancestorNode = node;
            while (!ancestorNode.isVisible && ancestorNode.parentNode != null) {
                ancestorNode = ancestorNode.parentNode;
            }
            if (ancestorNode.size.x <= 0.0f || ancestorNode.size.y <= 0.0f) {
                log.warn(
                        "Visible ancestor of dock node 0x{} has no size",
                        Integer.toHexString(node.id));
            }
            dockNodeUpdateHasCentralNodeChild(dockNodeGetRootNode(ancestorNode));
            dockNodeTreeUpdatePosSize(
                    ancestorNode,
                    new Vector2f(ancestorNode.position),
                    new Vector2f(ancestorNode.size),
                    node);
        }

        // Add the window to the node
        final boolean nodeWasVisible = node.isVisible;
        dockNodeAddWindow(node, window, true);
        // Don't mark visible right away (so dockContextEndFrame() doesn't render it)
        node.isVisible = nodeWasVisible;
        if (node != window.dockNode) {
            IkGuiImplDebugTools.reportError(
                    log, "Failed to bind window {} to its dock node", window.name);
        }
        return node;
    }

    /**
     * Store the current style colors used by dock node tabs into a window.
     *
     * @param window The window.
     */
    static void storeDockStyleForWindow(@NonNull Window window) {
        for (WindowDockStyleColor dockStyleColor : WindowDockStyleColor.values()) {
            window.dockStyle.colors[dockStyleColor.ordinal()] =
                    IkGuiImplUtils.getColor(WindowDockStyleColor.mapToColor(dockStyleColor));
        }
    }

    /**
     * Docking processing for a window, called from begin() on the first begin of the frame.
     *
     * @param window The window.
     * @param open The open state of the window, may be null.
     */
    static void beginDocked(@NonNull Window window, IkBoolean open) {
        // Specific extra processing for the fallback window
        if (window.isFallbackWindow && !window.wasActive) {
            dockNodeHideWindowDuringHostWindowCreation(window);
            return;
        }

        final boolean autoDockNode = getWindowAlwaysWantOwnTabBar(window);
        if (autoDockNode) {
            if (window.dockID == 0) {
                if (window.dockNode != null) {
                    IkGuiImplDebugTools.reportError(
                            log, "Window {} has a dock node but no dock ID", window.name);
                }
                window.dockID = dockContextGenerateNodeID();
            }
        } else {
            // Calling setNextWindowPos() undocks windows by default (by setting positionUndock)
            boolean wantUndock = (window.flags & WindowFlags.NO_DOCKING) != 0;
            wantUndock |=
                    (context.nextWindowData.fieldFlags & NextWindowFlags.HAS_POSITION) != 0
                            && ConditionAllowed.shouldResolve(
                                    context.nextWindowData.positionCondition,
                                    window.positionConditionAllowed)
                            && context.nextWindowData.positionUndock;
            if (wantUndock) {
                dockContextProcessUndockWindow(window, true);
                return;
            }
        }

        // Bind to our dock node
        DockNode node = window.dockNode;
        if (node != null && window.dockID != node.id) {
            IkGuiImplDebugTools.reportError(
                    log, "Window {} dock ID does not match its dock node", window.name);
        }
        if (window.dockID != 0 && node == null) {
            node = dockContextBindNodeToWindow(window);
            if (node == null) {
                return;
            }
        }
        if (node == null) {
            return;
        }

        // Undock if our dockspace node disappeared. Note how we are testing for lastFrameAlive and
        // NOT lastFrameActive. A dockspace node can be kept alive while being inactive with
        // KEEP_ALIVE_ONLY.
        if (node.lastFrameAlive < context.frameCount) {
            // If the window has been orphaned, transition the dock node to an implicit node
            // processed in dockContextNewFrameUpdateDocking()
            final DockNode rootNode = dockNodeGetRootNode(node);
            if (rootNode.lastFrameAlive < context.frameCount) {
                dockContextProcessUndockWindow(window, true);
            } else {
                window.dockIsActive = true;
            }
            return;
        }

        // Store style overrides
        storeDockStyleForWindow(window);

        // Fast path return. It is common for windows to hold on to a persistent dock ID but be the
        // only visible window, and never create a host window or a tab bar.
        if (node.hostWindow == null) {
            if (node.state == DockNodeState.HOST_WINDOW_HIDDEN_BECAUSE_WINDOWS_ARE_RESIZING) {
                window.dockIsActive = true;
            }
            // Only hide appearing windows
            if (node.windows.size() > 1 && window.appearing) {
                dockNodeHideWindowDuringHostWindowCreation(window);
            }
            return;
        }

        // We can have zero-sized nodes (e.g. children of a small-size dockspace)
        if (!node.isLeafNode()) {
            IkGuiImplDebugTools.reportError(
                    log, "Window {} is docked into a split node", window.name);
            return;
        }
        node.state = DockNodeState.HOST_WINDOW_VISIBLE;

        // Undock if we are submitted earlier than the host window
        if ((node.mergedFlags & DockNodeFlags.KEEP_ALIVE_ONLY) == 0
                && window.beginOrderWithinContext < node.hostWindow.beginOrderWithinContext) {
            dockContextProcessUndockWindow(window, true);
            return;
        }

        // Position/size the window
        IkGuiImplWindows.setNextWindowPos(
                node.position.x, node.position.y, Condition.NONE, 0.0f, 0.0f);
        IkGuiImplWindows.setNextWindowSize(node.size.x, node.size.y, Condition.NONE);
        // Cancel the implicit undocking of setNextWindowPos()
        context.nextWindowData.positionUndock = false;
        window.dockIsActive = true;
        window.dockNodeIsVisible = true;
        window.dockTabIsVisible = false;
        if ((node.mergedFlags & DockNodeFlags.KEEP_ALIVE_ONLY) != 0) {
            return;
        }

        // When the window is selected we mark it as visible
        if (node.visibleWindow == window) {
            window.dockTabIsVisible = true;
        }

        // Update the window flags
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Docked window {} has the child window flag", window.name);
        }
        window.flags |= WindowFlags.INTERNAL_CHILD_WINDOW | WindowFlags.NO_RESIZE;
        window.flagsAsChildWindow |= ChildFlags.ALWAYS_USE_WINDOW_PADDING;
        if (node.isHiddenTabBar() || node.isNoTabBar()) {
            window.flags |= WindowFlags.NO_TITLE_BAR;
        } else {
            // Clear the no title bar flag in case the user set it: confusingly enough we need a
            // title bar height so we are correctly offset, but it won't be displayed!
            window.flags &= ~WindowFlags.NO_TITLE_BAR;
        }

        // Save the new dock order only if the window has been visible once already. This allows
        // multiple windows to be created in the same frame and have their respective dock orders
        // preserved.
        if (node.tabBar != null && window.wasActive) {
            window.dockOrder = (short) dockNodeGetTabOrder(window);
        }

        if ((node.wantCloseAll || node.wantCloseTabID == window.idTab) && open != null) {
            open.set(false);
        }

        // Update the child ID to allow returning from child to parent with Escape
        final Window parentWindow = window.dockNode.hostWindow;
        window.childID = parentWindow.getID(window.name);
    }

    /**
     * Turn a window being moved into a drag and drop source for docking.
     *
     * @param window The window being moved, which must be the current window.
     */
    static void beginDockableDragDropSource(@NonNull Window window) {
        if (context.activeID != window.idMove
                || context.windowMoving != window
                || context.windowCurrent != window) {
            IkGuiImplDebugTools.reportError(
                    log, "Dockable drag and drop sources must be the current moving window");
            return;
        }

        // false: hold shift to disable docking, true: hold shift to enable docking
        if (context.io.configDockingWithShift != context.io.keyShift) {
            // When configDockingWithShift is set, display a tooltip to increase UI affordance. We
            // cannot test the hovered window under the moving window here, as it is only valid
            // once drag and drop is already active.
            if (context.io.configDockingWithShift
                    && context.io.mouseStationaryTimer >= 1000
                    && context.activeIDTimer >= 1000) {
                IkGuiImplPopups.setTooltip(HOLD_SHIFT_TO_DOCK);
            }
            return;
        }

        context.lastItemData.id = window.idMove;
        window = window.rootWindowDockTree;
        if ((window.flags & WindowFlags.NO_DOCKING) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Trying to dock window {} which has docking disabled", window.name);
            return;
        }
        final boolean isDragDocking =
                context.io.configDockingWithShift
                        || new RectFloat(0, 0, window.sizeFull.x, IkGuiImplLayout.getFrameHeight())
                                .contains(context.activeIDClickOffset);
        final int dragDropFlags =
                DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP
                        | DragDropFlags.SOURCE_NO_HOLD_TO_OPEN_OTHERS
                        | DragDropFlags.PAYLOAD_AUTO_EXPIRE
                        | DragDropFlags.PAYLOAD_NO_CROSS_CONTEXT
                        | DragDropFlags.PAYLOAD_NO_CROSS_PROCESS;
        if (isDragDocking && IkGuiImplDragDrop.beginDragDropSource(dragDropFlags)) {
            IkGuiImplDragDrop.setDragDropPayload(
                    IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW, window, Condition.ALWAYS);
            IkGuiImplDragDrop.endDragDropSource();
            // Store style overrides while dragging (even when not docked) because the docking
            // preview may need it
            storeDockStyleForWindow(window);
        }
    }

    /**
     * Let a window act as a target for docking windows being dragged.
     *
     * @param window The window, a root dock tree window or a dockspace host.
     */
    static void beginDockableDragDropTarget(@NonNull Window window) {
        if ((window.flags & WindowFlags.NO_DOCKING) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Window {} has docking disabled and can't be a dock target", window.name);
            return;
        }
        if (!context.dragDropActive) {
            return;
        }
        if (!IkGuiImplDragDrop.beginDragDropTargetCustom(window.getRect(), window.id)) {
            return;
        }

        // Peek into the payload before calling acceptDragDropPayload() so we can handle
        // overlapping dock nodes with filtering (this is a little unusual pattern, normally most
        // code would call acceptDragDropPayload() directly)
        final Payload payload = context.dragDropPayload;
        if (!payload.isDataType(IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW)
                || !(payload.data instanceof Window payloadWindow)
                || !dockNodeIsDropAllowed(window, payloadWindow)) {
            IkGuiImplDragDrop.endDragDropTarget();
            return;
        }

        if (IkGuiImplDragDrop.acceptDragDropPayload(
                        IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW,
                        DragDropFlags.ACCEPT_BEFORE_DELIVERY
                                | DragDropFlags.ACCEPT_NO_DRAW_DEFAULT_RECT)
                != null) {
            // Select the target node. (Important: we cannot use debugHoveredDockNode here!
            // Because each of our target nodes have filters based on the payload, each candidate
            // drop target will do its own evaluation.)
            boolean dockIntoFloatingWindow = false;
            DockNode node = null;
            if (window.dockNodeAsHost != null) {
                // Cannot assume that the node will be non-null even though we passed the
                // rectangle test: it depends on padding/spacing handled by
                // dockNodeTreeFindVisibleNodeByPos()
                node =
                        dockNodeTreeFindVisibleNodeByPos(
                                window.dockNodeAsHost, context.io.mousePosition);

                // There is an edge case when docking into a dockspace which only has inactive
                // nodes (because none of the windows are active). In this case we need to fall
                // back to any leaf node, possibly the central node.
                if (node != null && node.isDockSpace() && node.isRootNode()) {
                    node =
                            (node.centralNode != null && node.isLeafNode())
                                    ? node.centralNode
                                    : dockNodeTreeFindFallbackLeafNode(node);
                }
            } else {
                if (window.dockNode != null) {
                    node = window.dockNode;
                } else {
                    // Dock into a regular window
                    dockIntoFloatingWindow = true;
                }
            }

            final RectFloat explicitTargetRect =
                    (node != null
                                    && node.tabBar != null
                                    && !node.isHiddenTabBar()
                                    && !node.isNoTabBar())
                            ? new RectFloat(node.tabBar.barRect)
                            : new RectFloat(
                                    window.position.x,
                                    window.position.y,
                                    window.position.x + window.size.x,
                                    window.position.y + IkGuiImplLayout.getFrameHeight());
            final boolean isExplicitTarget =
                    context.io.configDockingWithShift
                            || IkGuiInternal.isMouseHoveringRect(
                                    explicitTargetRect.getLeft(),
                                    explicitTargetRect.getTop(),
                                    explicitTargetRect.getRight(),
                                    explicitTargetRect.getBottom(),
                                    true);

            // Preview the docking request and find out the split direction/ratio
            final boolean doPreview = payload.isPreview() || payload.isDelivery();
            if (doPreview && (node != null || dockIntoFloatingWindow)) {
                // If we have a non-leaf node it means we are hovering the border of a parent node,
                // in which case only outer markers will appear
                final DockPreviewData splitInner = new DockPreviewData();
                final DockPreviewData splitOuter = new DockPreviewData();
                DockPreviewData splitData = splitInner;
                if (node != null
                        && (node.parentNode != null
                                || node.isCentralNode()
                                || !node.isLeafNode())) {
                    final DockNode rootNode = dockNodeGetRootNode(node);
                    dockNodePreviewDockSetup(
                            window,
                            rootNode,
                            payloadWindow,
                            null,
                            splitOuter,
                            isExplicitTarget,
                            true);
                    if (splitOuter.isSplitDirectionExplicit) {
                        splitData = splitOuter;
                    }
                }
                if (node == null || node.isLeafNode()) {
                    dockNodePreviewDockSetup(
                            window, node, payloadWindow, null, splitInner, isExplicitTarget, false);
                }
                if (splitData == splitOuter) {
                    splitInner.isDropAllowed = false;
                }

                // Draw inner then outer, so that the previewed tab (in inner data) will be behind
                // the outer drop boxes
                dockNodePreviewDockRender(window, node, payloadWindow, splitInner);
                dockNodePreviewDockRender(window, node, payloadWindow, splitOuter);

                // Queue the docking request
                if (splitData.isDropAllowed && payload.isDelivery()) {
                    dockContextQueueDock(
                            window,
                            splitData.splitNode,
                            payloadWindow,
                            splitData.splitDirection,
                            splitData.splitRatio,
                            splitData == splitOuter);
                }
            }
        }
        IkGuiImplDragDrop.endDragDropTarget();
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDocking() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
