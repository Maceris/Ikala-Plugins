package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.DataAuthority;
import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.enums.DockRequestType;
import com.ikalagaming.graphics.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.MathUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Docking builder functions, a very early end-user API to manipulate dock nodes. These are
 * internal, expect this API to change! It is expected that these functions are all called before
 * the dockspace node is submitted.
 */
@Slf4j
class IkGuiImplDockBuilder {

    static Context context;

    /**
     * Dock a window into a node, or set up its settings to be docked when it is created. This
     * doesn't preserve the relative order of multiple docked windows (it clears the dock order).
     *
     * @param windowName The name of the window.
     * @param nodeID The ID of the node.
     */
    static void dockBuilderDockWindow(@NonNull String windowName, int nodeID) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockBuilderDockWindow '%s' to node 0x%08X",
                windowName,
                nodeID);
        log.trace(
                "dockBuilderDockWindow '{}' to node 0x{}", windowName, Integer.toHexString(nodeID));
        final int windowID = Hash.getID(windowName);
        final Window window = IkGuiInternal.findWindowByID(windowID);
        if (window != null) {
            // Apply to the created window
            final int previousNodeID = window.dockID;
            IkGuiImplDocking.setWindowDock(window, nodeID, Condition.ALWAYS);
            if (window.dockID != previousNodeID) {
                window.dockOrder = -1;
            }
        } else {
            // Apply to the settings
            WindowSettings settings = IkGuiImplConfig.findWindowSettingsByID(windowID);
            if (settings == null) {
                settings = IkGuiImplConfig.createNewWindowSettings(windowName);
            }
            if (settings.dockID != nodeID) {
                settings.dockOrder = -1;
            }
            settings.dockID = nodeID;
        }
    }

    /**
     * Look up a dock node by ID.
     *
     * @param nodeID The node ID.
     * @return The node, or null if not found.
     */
    static DockNode dockBuilderGetNode(int nodeID) {
        return IkGuiImplDocking.dockContextFindNodeByID(nodeID);
    }

    /**
     * Look up the central node of the dockspace a node is part of.
     *
     * @param nodeID The ID of a node in the dockspace.
     * @return The central node, or null if not found.
     */
    static DockNode dockBuilderGetCentralNode(int nodeID) {
        final DockNode node = dockBuilderGetNode(nodeID);
        if (node == null) {
            return null;
        }
        return IkGuiImplDocking.dockNodeGetRootNode(node).centralNode;
    }

    /**
     * Set the position of a node.
     *
     * @param nodeID The ID of the node.
     * @param x The x position.
     * @param y The y position.
     */
    static void dockBuilderSetNodePos(int nodeID, float x, float y) {
        final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
        if (node == null) {
            return;
        }
        node.position.set(x, y);
        node.authorityForPosition = DataAuthority.DOCK_NODE;
    }

    /**
     * Set the size of a node.
     *
     * @param nodeID The ID of the node.
     * @param width The width, which must be positive.
     * @param height The height, which must be positive.
     */
    static void dockBuilderSetNodeSize(int nodeID, float width, float height) {
        final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
        if (node == null) {
            return;
        }
        if (width <= 0.0f || height <= 0.0f) {
            IkGuiImplDebugTools.reportError(log, "Dock node sizes must be positive");
            return;
        }
        node.size.set(width, height);
        node.sizeRef.set(width, height);
        node.authorityForSize = DataAuthority.DOCK_NODE;
    }

    /**
     * Create a node. Make sure to use the {@link DockNodeFlags#INTERNAL_DOCK_SPACE} flag to create
     * a dockspace node, otherwise this will create a floating node!
     *
     * <ul>
     *   <li>Floating node: you can then call dockBuilderSetNodePos()/dockBuilderSetNodeSize() to
     *       position and size the floating node.
     *   <li>Dockspace node: calling dockBuilderSetNodePos() is unnecessary.
     *   <li>If you intend to split a node immediately after creation using dockBuilderSplitNode(),
     *       make sure to call dockBuilderSetNodeSize() beforehand! The splitting code currently
     *       needs a base size, otherwise space may not be allocated as precisely as you would
     *       expect.
     *   <li>Use an ID of 0 to let the system allocate a node identifier.
     *   <li>An existing node with the same ID will be removed.
     * </ul>
     *
     * @param nodeID The ID of the node, or 0 to generate one.
     * @param flags Dock node flags.
     * @return The ID of the node.
     */
    static int dockBuilderAddNode(int nodeID, int flags) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING,
                "[docking] DockBuilderAddNode 0x%08X flags=%08X",
                nodeID,
                flags);
        log.trace(
                "dockBuilderAddNode 0x{} flags={}",
                Integer.toHexString(nodeID),
                Integer.toHexString(flags));

        if (nodeID != 0) {
            dockBuilderRemoveNode(nodeID);
        }

        DockNode node;
        if ((flags & DockNodeFlags.INTERNAL_DOCK_SPACE) != 0) {
            if (nodeID == 0) {
                nodeID = IkGuiImplDocking.dockContextGenerateNodeID();
            }
            IkGuiImplDocking.dockSpace(
                    nodeID,
                    0.0f,
                    0.0f,
                    (flags & ~DockNodeFlags.INTERNAL_DOCK_SPACE) | DockNodeFlags.KEEP_ALIVE_ONLY,
                    null);
            node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
            if (node == null) {
                // This happens if docking is disabled
                return 0;
            }
        } else {
            node = IkGuiImplDocking.dockContextAddNode(nodeID);
            node.setLocalFlags(flags);
        }
        // Set this, otherwise beginDocked() will undock during the same frame
        node.lastFrameAlive = context.frameCount;
        return node.id;
    }

    /**
     * Remove a node and all its children, undocking all windows.
     *
     * @param nodeID The ID of the node.
     */
    static void dockBuilderRemoveNode(int nodeID) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_DOCKING, "[docking] DockBuilderRemoveNode 0x%08X", nodeID);
        log.trace("dockBuilderRemoveNode 0x{}", Integer.toHexString(nodeID));

        DockNode node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
        if (node == null) {
            return;
        }
        dockBuilderRemoveNodeDockedWindows(nodeID, true);
        dockBuilderRemoveNodeChildNodes(nodeID);
        // The node may have moved or been deleted if e.g. any merge happened
        node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
        if (node == null) {
            return;
        }
        if (node.isCentralNode() && node.parentNode != null) {
            node.parentNode.setLocalFlags(
                    node.parentNode.localFlags | DockNodeFlags.INTERNAL_CENTRAL_NODE);
        }
        IkGuiImplDocking.dockContextRemoveNode(node, true);
    }

    /**
     * Remove all the split hierarchy of a node. All remaining docked windows will be re-docked to
     * the remaining root node.
     *
     * @param rootID The ID of the root node, or 0 to remove all nodes.
     */
    static void dockBuilderRemoveNodeChildNodes(int rootID) {
        final DockContext dc = context.dockContext;

        final DockNode rootNode =
                rootID != 0 ? IkGuiImplDocking.dockContextFindNodeByID(rootID) : null;
        if (rootID != 0 && rootNode == null) {
            return;
        }
        boolean hasCentralNode = false;

        final DataAuthority backupRootNodeAuthorityForPosition =
                rootNode != null ? rootNode.authorityForPosition : DataAuthority.AUTO;
        final DataAuthority backupRootNodeAuthorityForSize =
                rootNode != null ? rootNode.authorityForSize : DataAuthority.AUTO;

        // Process active windows
        final List<DockNode> nodesToRemove = new ArrayList<>();
        for (DockNode node : new ArrayList<>(dc.nodes.values())) {
            final boolean wantRemoval =
                    rootID == 0
                            || (node.id != rootID
                                    && IkGuiImplDocking.dockNodeGetRootNode(node).id == rootID);
            if (wantRemoval) {
                if (node.isCentralNode()) {
                    hasCentralNode = true;
                }
                if (rootID != 0) {
                    IkGuiImplDocking.dockContextQueueNotifyRemovedNode(node);
                }
                if (rootNode != null) {
                    IkGuiImplDocking.dockNodeMoveWindows(rootNode, node);
                    IkGuiImplDockSettings.dockSettingsRenameNodeReferences(node.id, rootNode.id);
                }
                nodesToRemove.add(node);
            }
        }

        // dockNodeMoveWindows() -> dockNodeAddWindow() will normally set those when reaching two
        // windows (which is only adequate during an interactive merge). Make sure we don't lose
        // our current pos/size.
        if (rootNode != null) {
            rootNode.authorityForPosition = backupRootNodeAuthorityForPosition;
            rootNode.authorityForSize = backupRootNodeAuthorityForSize;
        }

        // Apply to settings
        for (WindowSettings settings : context.settingsWindows) {
            final int windowSettingsDockID = settings.dockID;
            if (windowSettingsDockID == 0) {
                continue;
            }
            for (DockNode node : nodesToRemove) {
                if (node.id == windowSettingsDockID) {
                    settings.dockID = rootID;
                    break;
                }
            }
        }

        // Not really efficient, but it is easier to destroy a whole hierarchy considering
        // dockContextRemoveNode() is attempting to merge nodes
        if (nodesToRemove.size() > 1) {
            nodesToRemove.sort(
                    Comparator.comparingInt(IkGuiImplDocking::dockNodeGetDepth).reversed());
        }
        for (DockNode node : nodesToRemove) {
            IkGuiImplDocking.dockContextRemoveNode(node, false);
        }

        if (rootID == 0) {
            dc.nodes.clear();
            dc.requests.clear();
        } else if (hasCentralNode) {
            rootNode.centralNode = rootNode;
            rootNode.setLocalFlags(rootNode.localFlags | DockNodeFlags.INTERNAL_CENTRAL_NODE);
        }
    }

    /**
     * Undock all the windows in a node hierarchy.
     *
     * @param rootID The ID of the root node, or 0 for all nodes.
     * @param clearSettingsRefs Whether to also clear the dock IDs in the window settings.
     */
    static void dockBuilderRemoveNodeDockedWindows(int rootID, boolean clearSettingsRefs) {
        // Clear references in settings
        if (clearSettingsRefs) {
            for (WindowSettings settings : context.settingsWindows) {
                boolean wantRemoval = rootID == 0 || settings.dockID == rootID;
                if (!wantRemoval && settings.dockID != 0) {
                    final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(settings.dockID);
                    if (node != null && IkGuiImplDocking.dockNodeGetRootNode(node).id == rootID) {
                        wantRemoval = true;
                    }
                }
                if (wantRemoval) {
                    settings.dockID = 0;
                }
            }
        }

        // Clear references in windows
        for (Window window : new ArrayList<>(context.windowDisplayOrder)) {
            final boolean wantRemoval =
                    rootID == 0
                            || (window.dockNode != null
                                    && IkGuiImplDocking.dockNodeGetRootNode(window.dockNode).id
                                            == rootID)
                            || (window.dockNodeAsHost != null
                                    && window.dockNodeAsHost.id == rootID);
            if (wantRemoval) {
                final int backupDockID = window.dockID;
                IkGuiImplDocking.dockContextProcessUndockWindow(window, clearSettingsRefs);
                if (!clearSettingsRefs && window.dockID != backupDockID) {
                    IkGuiImplDebugTools.reportError(
                            log, "Undocking window {} lost its dock ID", window.name);
                }
            }
        }
    }

    /**
     * Split a node into 2 child nodes.
     *
     * @param id The ID of the node to split.
     * @param splitDirection The direction of the new node.
     * @param sizeRatioForNodeAtDirection The size ratio of the node in the split direction.
     * @param outIDAtDirection Where to store the ID of the node at the split direction, may be
     *     null.
     * @param outIDAtOppositeDirection Where to store the ID of the node at the opposite direction,
     *     may be null.
     * @return The ID of the node at the split direction, which is the same as outIDAtDirection.
     */
    static int dockBuilderSplitNode(
            int id,
            @NonNull Direction splitDirection,
            float sizeRatioForNodeAtDirection,
            IkInt outIDAtDirection,
            IkInt outIDAtOppositeDirection) {
        if (splitDirection == Direction.NONE) {
            IkGuiImplDebugTools.reportError(log, "Splitting a dock node requires a direction");
            return 0;
        }
        log.trace(
                "dockBuilderSplitNode: node 0x{}, split direction {}",
                Integer.toHexString(id),
                splitDirection);

        final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(id);
        if (node == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Splitting missing dock node 0x{}", Integer.toHexString(id));
            return 0;
        }

        final boolean directionIsFirst =
                splitDirection == Direction.LEFT || splitDirection == Direction.UP;
        final DockNodeRequest request = new DockNodeRequest();
        request.type = DockRequestType.SPLIT;
        request.dockTargetWindow = null;
        request.dockTargetNode = node;
        request.dockPayload = null;
        request.dockSplitDirection = splitDirection;
        request.dockSplitRatio =
                MathUtil.clamp(
                        directionIsFirst
                                ? sizeRatioForNodeAtDirection
                                : 1.0f - sizeRatioForNodeAtDirection,
                        0.0f,
                        1.0f);
        request.dockSplitOuter = false;
        IkGuiImplDocking.dockContextProcessDock(request);

        final int idAtDirection = node.childNodes[directionIsFirst ? 0 : 1].id;
        final int idAtOppositeDirection = node.childNodes[directionIsFirst ? 1 : 0].id;
        if (outIDAtDirection != null) {
            outIDAtDirection.set(idAtDirection);
        }
        if (outIDAtOppositeDirection != null) {
            outIDAtOppositeDirection.set(idAtOppositeDirection);
        }
        return idAtDirection;
    }

    /**
     * Recursively copy a node hierarchy.
     *
     * @param sourceNode The node to copy.
     * @param destinationNodeIDIfKnown The ID of the new node, or 0 to generate one.
     * @param outNodeRemapPairs Filled with pairs of the source node ID, followed by the destination
     *     node ID.
     * @return The new node.
     */
    private static DockNode dockBuilderCopyNodeRec(
            @NonNull DockNode sourceNode,
            int destinationNodeIDIfKnown,
            @NonNull List<Integer> outNodeRemapPairs) {
        final DockNode destinationNode =
                IkGuiImplDocking.dockContextAddNode(destinationNodeIDIfKnown);
        destinationNode.sharedFlags = sourceNode.sharedFlags;
        destinationNode.localFlags = sourceNode.localFlags;
        destinationNode.localFlagsInWindows = DockNodeFlags.NONE;
        destinationNode.position.set(sourceNode.position);
        destinationNode.size.set(sourceNode.size);
        destinationNode.sizeRef.set(sourceNode.sizeRef);
        destinationNode.splitAxis = sourceNode.splitAxis;
        destinationNode.updateMergedFlags();

        outNodeRemapPairs.add(sourceNode.id);
        outNodeRemapPairs.add(destinationNode.id);

        for (int child = 0; child < sourceNode.childNodes.length; ++child) {
            if (sourceNode.childNodes[child] != null) {
                destinationNode.childNodes[child] =
                        dockBuilderCopyNodeRec(sourceNode.childNodes[child], 0, outNodeRemapPairs);
                destinationNode.childNodes[child].parentNode = destinationNode;
            }
        }

        log.trace(
                "Fork node 0x{} -> 0x{} ({} children)",
                Integer.toHexString(sourceNode.id),
                Integer.toHexString(destinationNode.id),
                destinationNode.isSplitNode() ? 2 : 0);
        return destinationNode;
    }

    /**
     * Copy a node hierarchy.
     *
     * @param sourceNodeID The ID of the node to copy.
     * @param destinationNodeID The ID of the copy.
     * @param outNodeRemapPairs Filled with pairs of node IDs, the source node followed by the
     *     destination node.
     */
    static void dockBuilderCopyNode(
            int sourceNodeID, int destinationNodeID, @NonNull List<Integer> outNodeRemapPairs) {
        if (sourceNodeID == 0 || destinationNodeID == 0) {
            IkGuiImplDebugTools.reportError(log, "Copying dock nodes requires non-zero IDs");
            return;
        }

        dockBuilderRemoveNode(destinationNodeID);

        final DockNode sourceNode = IkGuiImplDocking.dockContextFindNodeByID(sourceNodeID);
        if (sourceNode == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Copying missing dock node 0x{}", Integer.toHexString(sourceNodeID));
            return;
        }

        outNodeRemapPairs.clear();
        dockBuilderCopyNodeRec(sourceNode, destinationNodeID, outNodeRemapPairs);
    }

    /**
     * Copy the position, size, and collapsed state of a window to another window, or its settings
     * if it doesn't exist yet.
     *
     * @param sourceName The name of the window to copy from.
     * @param destinationName The name of the window to copy to.
     */
    static void dockBuilderCopyWindowSettings(
            @NonNull String sourceName, @NonNull String destinationName) {
        final Window sourceWindow = IkGuiInternal.findWindowByName(sourceName);
        if (sourceWindow == null) {
            return;
        }
        final Window destinationWindow = IkGuiInternal.findWindowByName(destinationName);
        if (destinationWindow != null) {
            destinationWindow.position.set(sourceWindow.position);
            destinationWindow.size.set(sourceWindow.size);
            destinationWindow.sizeFull.set(sourceWindow.sizeFull);
            destinationWindow.collapsed = sourceWindow.collapsed;
        } else {
            WindowSettings destinationSettings =
                    IkGuiImplConfig.findWindowSettingsByID(Hash.getID(destinationName));
            if (destinationSettings == null) {
                destinationSettings = IkGuiImplConfig.createNewWindowSettings(destinationName);
            }
            if (sourceWindow.viewportID != 0 && sourceWindow.viewportID != Viewport.DEFAULT_ID) {
                destinationSettings.viewportPosition.set(
                        (int) sourceWindow.position.x, (int) sourceWindow.position.y);
                destinationSettings.viewportID = sourceWindow.viewportID;
                destinationSettings.position.set(0, 0);
            } else {
                destinationSettings.position.set(
                        (int) sourceWindow.position.x, (int) sourceWindow.position.y);
            }
            destinationSettings.size.set(
                    (int) sourceWindow.sizeFull.x, (int) sourceWindow.sizeFull.y);
            destinationSettings.collapsed = sourceWindow.collapsed;
        }
    }

    /**
     * Copy a whole dockspace. Windows docked in the source dockspace that are in the remapping list
     * are docked into the matching nodes of the copy (or have their settings copied if they are
     * floating). Other windows docked in the source dockspace are moved to the copy.
     *
     * @param sourceDockspaceID The ID of the dockspace to copy.
     * @param destinationDockspaceID The ID of the new dockspace.
     * @param windowRemapPairs Pairs of window names, the source window followed by the destination
     *     window.
     */
    static void dockBuilderCopyDockSpace(
            int sourceDockspaceID,
            int destinationDockspaceID,
            @NonNull List<String> windowRemapPairs) {
        if (sourceDockspaceID == 0 || destinationDockspaceID == 0) {
            IkGuiImplDebugTools.reportError(log, "Copying dockspaces requires non-zero IDs");
            return;
        }
        if (windowRemapPairs.size() % 2 != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Window remap pairs must have an even number of names");
            return;
        }

        // Duplicate the entire dock. When overwriting the destination dockspace, windows that
        // aren't part of our dockspace window class but that are docked in the same node will be
        // split apart, whereas we could attempt to at least keep them together in a new, same
        // floating node.
        final List<Integer> nodeRemapPairs = new ArrayList<>();
        dockBuilderCopyNode(sourceDockspaceID, destinationDockspaceID, nodeRemapPairs);

        // Attempt to transition all the upcoming windows associated with the destination dockspace
        // into the newly created hierarchy of dock nodes (the windows associated with the source
        // dockspace are staying in place)
        final Set<Integer> sourceWindows = new HashSet<>();
        for (int remapIndex = 0; remapIndex < windowRemapPairs.size(); remapIndex += 2) {
            final String sourceWindowName = windowRemapPairs.get(remapIndex);
            final String destinationWindowName = windowRemapPairs.get(remapIndex + 1);
            final int sourceWindowID = Hash.getID(sourceWindowName);
            sourceWindows.add(sourceWindowID);

            // Search in the remapping tables
            int sourceDockID = 0;
            final Window sourceWindow = IkGuiInternal.findWindowByID(sourceWindowID);
            if (sourceWindow != null) {
                sourceDockID = sourceWindow.dockID;
            } else {
                final WindowSettings sourceWindowSettings =
                        IkGuiImplConfig.findWindowSettingsByID(sourceWindowID);
                if (sourceWindowSettings != null) {
                    sourceDockID = sourceWindowSettings.dockID;
                }
            }
            int destinationDockID = 0;
            for (int dockRemapIndex = 0;
                    dockRemapIndex < nodeRemapPairs.size();
                    dockRemapIndex += 2) {
                if (nodeRemapPairs.get(dockRemapIndex) == sourceDockID) {
                    destinationDockID = nodeRemapPairs.get(dockRemapIndex + 1);
                    break;
                }
            }

            if (destinationDockID != 0) {
                // Docked windows get redocked into the new node hierarchy
                log.trace(
                        "Remap live window '{}' 0x{} -> '{}' 0x{}",
                        sourceWindowName,
                        Integer.toHexString(sourceDockID),
                        destinationWindowName,
                        Integer.toHexString(destinationDockID));
                dockBuilderDockWindow(destinationWindowName, destinationDockID);
            } else {
                // Floating windows get their settings transferred (regardless of whether the new
                // window already exists or not). When this is leading to a copy and not a move, we
                // would get two overlapping floating windows.
                log.trace(
                        "Remap window settings '{}' -> '{}'",
                        sourceWindowName,
                        destinationWindowName);
                dockBuilderCopyWindowSettings(sourceWindowName, destinationWindowName);
            }
        }

        // Anything else in the source nodes of the node remap pairs are windows that are not
        // included in the remapping list. Find those windows and move them to the cloned dock
        // node. Dock them as a second step, as undocking would invalidate source dock nodes.
        final List<Window> remainingWindows = new ArrayList<>();
        final List<Integer> remainingDockIDs = new ArrayList<>();
        for (int dockRemapIndex = 0; dockRemapIndex < nodeRemapPairs.size(); dockRemapIndex += 2) {
            final int sourceDockID = nodeRemapPairs.get(dockRemapIndex);
            if (sourceDockID == 0) {
                continue;
            }
            final int destinationDockID = nodeRemapPairs.get(dockRemapIndex + 1);
            final DockNode node = dockBuilderGetNode(sourceDockID);
            if (node == null) {
                continue;
            }
            for (Window window : node.windows) {
                if (sourceWindows.contains(window.id)) {
                    continue;
                }

                // Docked windows get redocked into the new node hierarchy
                log.trace(
                        "Remap window '{}' 0x{} -> 0x{}",
                        window.name,
                        Integer.toHexString(sourceDockID),
                        Integer.toHexString(destinationDockID));
                remainingWindows.add(window);
                remainingDockIDs.add(destinationDockID);
            }
        }
        for (int i = 0; i < remainingWindows.size(); ++i) {
            dockBuilderDockWindow(remainingWindows.get(i).name, remainingDockIDs.get(i));
        }
    }

    /**
     * Finish building a dock node hierarchy, binding windows to the nodes.
     *
     * @param rootID The ID of the root node.
     */
    static void dockBuilderFinish(int rootID) {
        IkGuiImplDocking.dockContextBuildAddWindowsToNodes(rootID);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDockBuilder() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
