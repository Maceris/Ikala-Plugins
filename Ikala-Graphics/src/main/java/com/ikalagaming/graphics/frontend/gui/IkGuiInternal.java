package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * These are internal functions, not intended for end users. These are subject to change or removal
 * at any time.
 */
@Slf4j
public class IkGuiInternal {

    static Context context;

    public static void clearActiveID() {
        setActiveID(0, null);
    }

    public static void closePopupsOverWindow(
            Window referenceWindow, boolean restoreFocusToWindowUnderPopup) {
        if (context.openPopupStack.isEmpty()) {
            return;
        }

        // TODO(ches) close popups above the reference window
    }

    public static DockNode dockContextAddNode(int id) {
        // TODO(ches) complete this
        return null;
    }

    public static DockNode dockContextBindNodeToWindow(Window window) {
        // TODO(ches) complete this
        return null;
    }

    public static void dockContextBuildAddWindowsToNodes(int root_id) {
        // TODO(ches) complete this
    }

    public static void dockContextBuildNodesFromSettings(
            DockNodeSettings node_settings_array, int node_settings_count) {
        // TODO(ches) complete this
    }

    public static void dockContextDeleteNode(@NonNull DockNode node) {
        dockNodeRemoveTabBar(node);
        context.dockContext.nodes.remove(node.id);
    }

    /**
     * Look up a dock node by ID.
     *
     * @param dockID The dock ID.
     * @return The node, which might be null if not found.
     */
    public static DockNode dockContextFindNodeByID(int dockID) {
        return context.dockContext.nodes.get(dockID);
    }

    public static void dockContextProcessDock(DockNodeRequest req) {
        // TODO(ches) complete this
    }

    public static void dockContextPruneUnusedSettingsNodes() {
        // TODO(ches) complete this
    }

    public static void dockContextQueueNotifyRemovedNode(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockContextRemoveNode(
            @NonNull DockNode node, boolean mergeSiblingIntoParentNode) {
        log.trace("Removing dock node {}", node.id);
        if (context.dockContext.nodes.get(node.id) != node) {
            log.error("Node out of sync with context's list of nodes");
            return;
        }
        if (node.childNodes[0] != null || node.childNodes[1] != null) {
            log.error("Child nodes aren't empty, can't reasonably remove node");
            return;
        }
        if (!node.windows.isEmpty()) {
            log.error("Node contains a window, can't reasonably remove it");
            return;
        }

        if (node.hostWindow != null) {
            node.hostWindow.dockNodeAsHost = null;
        }

        DockNode parentNode = node.parentNode;
        final boolean merge = mergeSiblingIntoParentNode && parentNode != null;
        if (merge) {
            if (parentNode.childNodes[0] != node && parentNode.childNodes[1] != node) {
                log.error("Parent node does not contain the node, merging would go weird");
                return;
            }
            DockNode siblingNode =
                    parentNode.childNodes[0] == node
                            ? parentNode.childNodes[1]
                            : parentNode.childNodes[0];
            dockNodeTreeMerge(parentNode, siblingNode);
        } else {
            for (int n = 0; parentNode != null && n < parentNode.childNodes.length; n++) {
                if (parentNode.childNodes[n] == node) {
                    node.parentNode.childNodes[n] = null;
                }
            }
            dockContextDeleteNode(node);
        }
    }

    public static void dockNodeAddTabBar(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeAddWindow(DockNode node, Window window, boolean add_to_tab_bar) {
        // TODO(ches) complete this
    }

    public static void dockNodeApplyPosSizeToWindows(DockNode node) {
        // TODO(ches) complete this
    }

    public static boolean dockNodeCalcDropRectsAndTestMousePos(
            RectFloat parent,
            Direction dir,
            RectFloat out_draw,
            boolean outer_docking,
            Vector2f test_mouse_pos) {
        // TODO(ches) complete this
        return false;
    }

    public static void dockNodeCalcSplitRects(
            Vector2f pos_old,
            Vector2f size_old,
            Vector2f pos_new,
            Vector2f size_new,
            Direction dir,
            Vector2f size_new_desired) {
        // TODO(ches) complete this
    }

    public static void dockNodeCalcTabBarLayout(
            DockNode node,
            RectFloat out_title_rect,
            RectFloat out_tab_bar_rect,
            Vector2f out_window_menu_button_pos,
            Vector2f out_close_button_pos) {
        // TODO(ches) complete this
    }

    public static Window dockNodeFindWindowByID(DockNode node, int id) {
        // TODO(ches) complete this
        return null;
    }

    public static String dockNodeGetHostWindowTitle(DockNode node, String buf) {
        // TODO(ches) complete this
        return null;
    }

    /**
     * Fetch the root node for a given node.
     *
     * @param node The node to fetch a parent for.
     * @return The root node, or null if node was null.
     */
    public static DockNode dockNodeGetRootNode(DockNode node) {
        if (node == null) {
            return null;
        }
        while (node.parentNode != null) {
            node = node.parentNode;
        }
        return node;
    }

    public static int dockNodeGetTabOrder(Window window) {
        // TODO(ches) complete this
        return 0;
    }

    public static void dockNodeHideHostWindow(DockNode node) {
        // TODO(ches) complete this
    }

    public static boolean dockNodeIsDropAllowed(Window host_window, Window payload_window) {
        // TODO(ches) complete this
        return false;
    }

    public static void dockNodeMoveChildNodes(DockNode destinationNode, DockNode sourceNode) {}

    public static void dockNodeMoveWindows(DockNode dst_node, DockNode src_node) {
        // TODO(ches) complete this
    }

    public static void dockNodePreviewDockRender(
            Window host_window,
            DockNode host_node,
            Window payload_window,
            DockPreviewData preview_data) {
        // TODO(ches) complete this
    }

    public static void dockNodePreviewDockSetup(
            Window host_window,
            DockNode host_node,
            Window payload_window,
            DockNode payload_node,
            DockPreviewData preview_data,
            boolean is_explicit_target,
            boolean is_outer_docking) {
        // TODO(ches) complete this
    }

    private static void dockNodeRemoveTabBar(@NonNull DockNode node) {
        if (node.tabBar == null) {
            return;
        }
        node.tabBar.tabs.clear();
        node.tabBar = null;
    }

    public static void dockNodeRemoveWindow(
            @NonNull DockNode node, @NonNull Window window, int saveDockID) {
        if (window.dockNode != node) {
            log.error("Window not in dock node we are trying to remove it from");
            return;
        }
        if (saveDockID != 0 && saveDockID != node.id) {
            log.error("Trying to save an invalid dock node ID while removing window");
            return;
        }

        window.dockNode = null;
        window.dockIsActive = false;
        window.dockTabWantClose = false;
        window.dockID = saveDockID;
        window.flags &= ~WindowFlags.INTERNAL_CHILD_WINDOW;
        if (window.parentWindow != null) {
            window.parentWindow.childWindows.remove(window);
        }
        updateWindowParentAndRootLinks(window, window.flags, null);

        if (node.hostWindow != null && node.hostWindow.viewportOwned) {
            // When undocking from a user interaction this will always run in newFrame() and have
            // little effect.
            // But mid-frame, if we clear viewport we need to mark window as hidden as well.
            window.viewport = null;
            window.viewportID = 0;
            window.viewportOwned = false;
            window.hidden = true;
        }

        final boolean erased = node.windows.remove(window);
        if (!erased) {
            log.error("Could not find window to remove");
            return;
        }
        if (node.visibleWindow == window) {
            node.visibleWindow = null;
        }
        node.wantHiddenTabBarUpdate = true;
        if (node.tabBar != null) {
            tabBarRemoveTab(node.tabBar, window.idTab);
            final int tabCountThresholdForTabBar = node.isCentralNode() ? 1 : 2;
            if (node.windows.size() < tabCountThresholdForTabBar) {
                dockNodeRemoveTabBar(node);
            }
        }

        if (node.windows.isEmpty()
                && !node.isCentralNode()
                && !node.isDockSpace()
                && window.dockID != node.id) {
            // Dock nodes delete themselves if they aren't holding at least one tab
            dockContextRemoveNode(node, true);
            return;
        }

        if (node.windows.size() == 1 && !node.isCentralNode() && node.hostWindow != null) {
            Window remainingWindow = node.windows.getFirst();
            remainingWindow.collapsed = node.hostWindow.collapsed;
        }

        /*
         * We need to update visibility immediately so the dockNodeUpdateRemoveInactiveChildren processing
         * can reflect changes up the tree
         */
        dockNodeUpdateVisibleFlag(node);
    }

    public static void dockNodeStartMouseMovingWindow(DockNode node, Window window) {
        // TODO(ches) complete this
    }

    public static DockNode dockNodeTreeFindFallbackLeafNode(DockNode node) {
        // TODO(ches) complete this
        return null;
    }

    public static DockNode dockNodeTreeFindVisibleNodeByPos(DockNode node, Vector2f pos) {
        // TODO(ches) complete this
        return null;
    }

    public static void dockNodeTreeMerge(@NonNull DockNode parentNode, DockNode mergeLeadChild) {
        // When called from dockContextProcessUndockNode() it is possible that one of the children
        // is NULL.
        DockNode child0 = parentNode.childNodes[0];
        DockNode child1 = parentNode.childNodes[1];

        if (child0 == null && child1 == null) {
            log.error("No children nodes to merge");
            return;
        }
        if ((child0 != null && !child0.windows.isEmpty())
                || (child1 != null && !child1.windows.isEmpty())) {
            if (parentNode.tabBar != null) {
                log.error("No tab bar when trying to merge dock nodes into parent");
                return;
            }
            if (!parentNode.windows.isEmpty()) {
                log.error("Parent node already has a window while merging dock nodes");
                return;
            }
        }
        log.trace(
                "dockNodeTreeMerge: {} + {} into parent {}",
                child0 != null ? child0.id : 0,
                child1 != null ? child1.id : 0,
                parentNode.id);

        Vector2f backupLastExplicitSize = new Vector2f(parentNode.sizeRef);
        dockNodeMoveChildNodes(parentNode, mergeLeadChild);
        if (child0 != null) {
            // Generally only 1 of the 2 child nodes will have windows
            dockNodeMoveWindows(parentNode, child0);
            dockSettingsRenameNodeReferences(child0.id, parentNode.id);
        }
        if (child1 != null) {
            dockNodeMoveWindows(parentNode, child1);
            dockSettingsRenameNodeReferences(child1.id, parentNode.id);
        }
        dockNodeApplyPosSizeToWindows(parentNode);
        parentNode.authorityForPosition = DataAuthority.AUTO;
        parentNode.authorityForSize = DataAuthority.AUTO;
        parentNode.authorityForViewport = DataAuthority.AUTO;
        parentNode.visibleWindow = mergeLeadChild.visibleWindow;
        parentNode.sizeRef.set(backupLastExplicitSize);

        // Flags transfer
        parentNode.localFlags &= ~DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlags |=
                (child0 != null ? child0.localFlags : DockNodeFlags.NONE)
                        & DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        parentNode.localFlags |=
                (child1 != null ? child1.localFlags : DockNodeFlags.NONE)
                        & DockNodeFlags.LOCAL_FLAGS_TRANSFER_MASK;
        // TODO(ches) Would be more consistent to update from actual windows
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

    public static void dockNodeTreeSplit(
            DockNode parent_node,
            Axis split_axis,
            int split_first_child,
            float split_ratio,
            DockNode new_node) {
        // TODO(ches) complete this
    }

    public static void dockNodeTreeUpdatePosSize(DockNode node, Vector2f pos, Vector2f size) {
        // TODO(ches) complete this
    }

    public static void dockNodeTreeUpdatePosSize(
            DockNode node, Vector2f pos, Vector2f size, DockNode onlyWriteToSingleNode) {
        // TODO(ches) complete this
    }

    public static void dockNodeTreeUpdateSplitter(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdate(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdateFlagsAndCollapse(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdateForRootNode(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdateHasCentralNodeChild(DockNode node) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdateTabBar(DockNode node, Window host_window) {
        // TODO(ches) complete this
    }

    public static void dockNodeUpdateVisibleFlag(@NonNull DockNode node) {
        boolean isVisible = node.parentNode != null ? node.isDockSpace() : node.isCentralNode();
        isVisible = isVisible || !node.windows.isEmpty();
        isVisible = isVisible || (node.childNodes[0] != null && node.childNodes[0].isVisible);
        isVisible = isVisible || (node.childNodes[1] != null && node.childNodes[1].isVisible);
        node.isVisible = isVisible;
    }

    public static void dockNodeWindowMenuUpdate(DockNode node, TabBar tab_bar) {
        // TODO(ches) complete this
    }

    public static DockNodeSettings dockSettingsFindNodeSettings(int node_id) {
        // TODO(ches) complete this
        return null;
    }

    public static void dockSettingsHandlerApplyAll(SettingsHandler handler) {
        // TODO(ches) complete this
    }

    public static void dockSettingsHandlerClearAll(SettingsHandler handler) {
        // TODO(ches) complete this
    }

    public static void dockSettingsHandlerReadLine(
            SettingsHandler handler, Object entry, String line) {
        // TODO(ches) complete this
    }

    public static void dockSettingsHandlerReadOpen(SettingsHandler handler, String name) {
        // TODO(ches) complete this
    }

    public static void dockSettingsHandlerWriteAll(SettingsHandler handler, StringBuilder buf) {
        // TODO(ches) complete this
    }

    public static void dockSettingsRemoveNodeReferences(int[] node_ids) {
        // TODO(ches) complete this
    }

    public static void dockSettingsRenameNodeReferences(int old_node_id, int new_node_id) {
        // TODO(ches) complete this
    }

    public static void errorRecoveryStoreState(@NonNull ErrorRecoveryState stateOut) {
        stateOut.sizeOfWindowStack = (short) context.windowStack.size();
        stateOut.sizeOfIDStack = (short) context.windowCurrent.idStack.size();
        stateOut.sizeOfTreeStack = (short) context.windowCurrent.treeDepth;
        stateOut.sizeOfColorStack = (short) context.colorStack.size();
        stateOut.sizeOfStyleVarStack = (short) context.styleVariableStack.size();
        stateOut.sizeOfFontStack = (short) context.fontStack.size();
        stateOut.sizeOfFocusScopeStack = (short) context.focusScopeStack.size();
        stateOut.sizeOfGroupStack = (short) context.groupStack.size();
        stateOut.sizeOfItemFlagsStack = (short) context.itemFlagsStack.size();
        stateOut.sizeOfBeginPopupStack = (short) context.beginPopupStack.size();
        stateOut.sizeOfDisabledStack = context.disabledStackSize;
    }

    /**
     * Finds the window that is hovered under the provided position, and updates {@link
     * Context#windowHovered} and {@link Context#windowHoveredUnderMovingWindow}.
     *
     * @param position The mouse position.
     */
    public static void findHoveredWindow(@NonNull Vector2f position) {
        context.windowHovered = null;
        context.windowHoveredUnderMovingWindow = null;

        if (context.windowMoving != null
                && ((context.windowMoving.flags & WindowFlags.NO_MOUSE_INPUTS) == 0)) {
            context.windowHovered = context.windowMoving;
        }

        Vector2f paddingRegular = IkGui.getStyleVarFloat2(StyleVariable.TOUCH_EXTRA_PADDING);
        Vector2f paddingForResize =
                new Vector2f(context.windowBorderHoverPadding, context.windowBorderHoverPadding);

        for (Window window : context.windowDisplayOrder.reversed()) {
            if (!window.active
                    || window.hidden
                    || (window.flags & WindowFlags.NO_MOUSE_INPUTS) != 0) {
                continue;
            }
            Vector2f hitPadding =
                    (window.flags & (WindowFlags.NO_RESIZE | WindowFlags.ALWAYS_AUTO_RESIZE)) != 0
                            ? paddingRegular
                            : paddingForResize;

            if (!window.rectOuterClipped.containsWithPadding(position, hitPadding)) {
                continue;
            }

            if (window.hitTestHoleSize.x != 0) {
                Vector2f holePosition = new Vector2f(window.hitTestHolePosition);
                holePosition.add(window.position);

                if (new RectFloat(holePosition, window.hitTestHoleSize).contains(position)) {
                    continue;
                }
            }

            if (context.windowHovered == null) {
                context.windowHovered = window;
            }
            if (context.windowHoveredUnderMovingWindow == null
                    && (context.windowMoving == null
                            || window.rootWindow != context.windowMoving.rootWindow)) {
                context.windowHoveredUnderMovingWindow = window;
            }
            if (context.windowHoveredUnderMovingWindow != null) {
                break;
            }
        }
    }

    public static void focusWindow(Window window, int focusRequestFlags) {
        // TODO(ches) set focus to window
    }

    /**
     * Fetch the window associated with the highest popup modal.
     *
     * @return The window, or null if none are found.
     */
    public static Window getTopmostPopupModal() {
        for (PopupData data : context.openPopupStack) {
            if ((data.window.flags & WindowFlags.INTERNAL_MODAL) != 0) {
                return data.window;
            }
        }
        return null;
    }

    /**
     * Checks if there is a popup request open for the given ID.
     *
     * @param id The ID we care about for navigation. If 0 we'll use the last item ID.
     * @param popupFlags Used to decide which mouse button we are checking for, if applicable.
     * @return Whether a mouse click or navigation action would result in a popup being requested.
     */
    public static boolean isPopupRequestOpenForItem(int id, int popupFlags) {
        MouseButton button;
        if ((popupFlags & PopupFlags.MOUSE_BUTTON_RIGHT) != 0) {
            button = MouseButton.RIGHT;
        } else if ((popupFlags & PopupFlags.MOUSE_BUTTON_MIDDLE) != 0) {
            button = MouseButton.MIDDLE;
        } else if ((popupFlags & PopupFlags.MOUSE_BUTTON_LEFT) != 0) {
            button = MouseButton.LEFT;
        } else {
            button = MouseButton.RIGHT;
        }

        int actualID = id != 0 ? id : context.lastItemData.id;

        if (IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP)
                && IkGui.isMouseReleased(button)) {
            return true;
        }
        return context.navOpenContextMenuItemID == actualID
                && (IkGui.isItemFocused() || actualID == context.windowCurrent.idMove);
    }

    /**
     * Check if one window is above another.
     *
     * @param above The window we think is above.
     * @param below The window we think is below.
     * @return If we are sure above is below the below.
     */
    public static boolean isWindowAbove(Window above, Window below) {
        if (above == null || below == null) {
            return false;
        }

        for (int i = 0; i < context.windowDisplayOrder.size(); ++i) {
            Window current = context.windowDisplayOrder.get(i);

            if (current == above) {
                return false;
            }
            if (current == below) {
                return true;
            }
        }

        return false;
    }

    public static boolean isWindowWithinBeginStackOf(Window window, Window potentialParent) {
        if (window.rootWindow == potentialParent) {
            return true;
        }
        while (window != null) {
            if (window == potentialParent) {
                return true;
            }
            window = window.parentWindowInBeginStack;
        }
        return false;
    }

    /**
     * Mark the given ID as alive.
     *
     * @param id The ID.
     */
    public static void keepAliveID(int id) {
        if (context.activeID == id) {
            context.activeIDIsAlive = true;
        }
        if (context.deactivatedItemData.id == id) {
            context.deactivatedItemData.isAlive = true;
        }
    }

    public static void markIniSettingsDirty(@NonNull Window window) {
        // TODO(ches) complete this
    }

    public static void setActiveID(int id, Window window) {
        if (context.activeID != 0) {
            // Clear previous active ID

            context.deactivatedItemData.id = context.activeID;
            context.deactivatedItemData.elapsedFrame =
                    context.lastItemData.id == context.activeID
                            ? context.frameCount
                            : context.frameCount + 1;
            context.deactivatedItemData.hasBeenEditedBefore = context.activeIDHasBeenEditedBefore;
            context.deactivatedItemData.isAlive = context.activeIDIsAlive;

            // TODO(ches) if it was an input text we might want to call a hook for it being
            // deactivated

            if (context.windowMoving != null && context.activeID == context.windowMoving.idMove) {
                stopMouseMovingWindow();
            }
        }

        // Set active ID
        context.activeIDIsJustActivated = context.activeID != id;

        if (context.activeIDIsJustActivated) {
            context.activeIDTimer = 0;
            context.activeIDHasBeenPressedBefore = false;
            context.activeIDHasBeenEditedBefore = false;
            context.activeIDMouseButton = MouseButton.NONE;
            if (id != 0) {
                context.lastActiveID = id;
                context.lastActiveIDTimer = 0;
            }
        }
        context.activeID = id;
        context.activeIDAllowOverlap = false;
        context.activeIDNoClearOnFocusLost = false;
        context.activeIDWindow = window;
        context.activeIDHasBeenEditedThisFrame = false;
        context.activeIDFromShortcut = false;
        context.activeIDDisabledId = 0;
        if (id != 0) {
            context.activeIDIsAlive = true;
            context.activeIDSource =
                    (context.navActivateID == id || context.navJustMovedToID == id)
                            ? context.navInputSource
                            : GuiInputSource.MOUSE;

            if (context.activeIDSource == GuiInputSource.NONE) {
                log.error("Invalid input source when setting active ID");
            }
        }
    }

    /**
     * Start moving a window, and set the active ID to the window's move ID.
     *
     * @param window The window we are dragging.
     */
    public static void startMouseMovingWindow(@NonNull Window window) {
        focusWindow(window, WindowFocusRequestFlags.NONE);

        setActiveID(window.idMove, window);
        context.activeIDClickOffset.set(context.io.mouseClickedPosition[MouseButton.LEFT.index]);
        context.activeIDClickOffset.sub(window.rootWindow.position);
        context.activeIDNoClearOnFocusLost = true;

        boolean canMoveWindow =
                !((window.flags & WindowFlags.NO_MOVE) != 0
                        || (window.rootWindow != null
                                && (window.rootWindow.flags & WindowFlags.NO_MOVE) != 0));
        if (window.dockNodeAsHost != null) {
            // TODO(ches) dock node visible window has nomove flag
        }
        if (canMoveWindow) {
            context.windowMoving = window;
        }
    }

    /** Stop moving the window, but doesn't clear the active ID. */
    public static void stopMouseMovingWindow() {
        if (context.windowMoving != null && context.windowMoving.viewport != null) {

            if ((context.io.configFlags & ConfigFlags.VIEWPORTS_ENABLE) != 0) {
                // TODO try to merge window into the current viewport
            }

            // TODO(ches) add isdragDropPayloadBeingAccepted
            // TODO(ches) if we aren't dragging and dropping, update the mouse viewport to the
            // window's viewport

            boolean windowCanUseInputs =
                    (context.windowMoving.flags & WindowFlags.NO_MOUSE_INPUTS) == 0
                            || (context.windowMoving.flags & WindowFlags.NO_NAV_INPUTS) == 0;
            if (windowCanUseInputs) {
                context.windowMoving.viewport.flags &= ~ViewportFlags.NO_INPUTS;
            }
        }

        context.windowMoving = null;
    }

    private static void tabBarRemoveTab(@NonNull TabBar tabBar, int tabID) {
        tabBar.tabs.removeIf(item -> item.id == tabID);
        if (tabBar.visibleTabID == tabID) {
            tabBar.visibleTabID = 0;
        }
        if (tabBar.selectedTabID == tabID) {
            tabBar.selectedTabID = 0;
        }
        if (tabBar.nextSelectedTabID == tabID) {
            tabBar.nextSelectedTabID = 0;
        }
    }

    /**
     * Truncate the given float to an integer value.
     *
     * @param x The float.
     * @return The truncated float.
     */
    public static float truncate(float x) {
        return (int) x;
    }

    /**
     * Truncate both coordinates to integer values. This modifies the provided vector, but also
     * returns it for convenience.
     *
     * @param vector The vector to truncate.
     * @return The provided vector.
     */
    public static Vector2f truncate(@NonNull Vector2f vector) {
        vector.set((int) vector.x, (int) vector.y);
        return vector;
    }

    public static void updateHoveredWindowAndCaptureFlags(@NonNull Vector2f mousePosition) {
        context.windowBorderHoverPadding =
                Math.max(
                        Math.max(
                                context.style.variable.touchExtraPadding.x,
                                context.style.variable.touchExtraPadding.y),
                        context.style.variable.windowBorderHoverPadding);

        boolean clearHoveredWindows = false;

        findHoveredWindow(mousePosition);
        context.windowHoveredBeforeClear = context.windowHovered;

        Window modalWindow = getTopmostPopupModal();
        if (modalWindow != null
                && context.windowHovered != null
                && !isWindowWithinBeginStackOf(context.windowHovered.rootWindow, modalWindow)) {
            clearHoveredWindows = true;
        }

        if ((context.io.configFlags & ConfigFlags.NO_MOUSE) != 0) {
            clearHoveredWindows = true;
        }

        final boolean hasOpenPopup = !context.openPopupStack.isEmpty();
        final boolean hasOpenModal = modalWindow != null;
        int mouseEarliestDown = -1;
        boolean mouseAnyDown = false;

        for (int i = 0; i < MouseButton.COUNT; ++i) {
            if (context.io.mouseClicked[i]) {
                context.io.mouseDownOwned[i] = (context.windowHovered != null) || hasOpenPopup;
                context.io.mouseDownOwnedUnlessPopupClose[i] =
                        (context.windowHovered != null) || hasOpenModal;
            }
            mouseAnyDown = mouseAnyDown || context.io.mouseDown[i];
            if ((context.io.mouseDown[i] || context.io.mouseReleased[i])
                    && (mouseEarliestDown == -1
                            || context.io.mouseClickedTime[i]
                                    < context.io.mouseClickedTime[mouseEarliestDown])) {
                mouseEarliestDown = i;
            }
        }

        final boolean mouseAvailable =
                (mouseEarliestDown == -1) || context.io.mouseDownOwned[mouseEarliestDown];
        final boolean mouseAvailableUnlessPopupClose =
                (mouseEarliestDown == -1)
                        || context.io.mouseDownOwnedUnlessPopupClose[mouseEarliestDown];

        final boolean mouseDraggingExternalPayload =
                context.dragDropActive
                        && (context.dragDropSourceFlags & DragDropFlags.SOURCE_EXTERN) != 0;

        if (!mouseAvailable && !mouseDraggingExternalPayload) {
            clearHoveredWindows = true;
        }

        if (clearHoveredWindows) {
            context.windowHovered = null;
            context.windowHoveredUnderMovingWindow = null;
        }

        if (context.io.wantCaptureMouseNextFrame) {
            context.io.wantCaptureMouse = true;
            context.io.wantCaptureMouseUnlessPopupClose = true;
        } else {
            context.io.wantCaptureMouse =
                    (mouseAvailable && (context.windowHovered != null || mouseAnyDown))
                            || hasOpenPopup;
            context.io.wantCaptureMouseUnlessPopupClose =
                    (mouseAvailableUnlessPopupClose
                                    && (context.windowHovered != null || mouseAnyDown))
                            || hasOpenModal;
        }

        context.io.wantCaptureKeyboard = false;
        if ((context.io.configFlags & ConfigFlags.NO_KEYBOARD) == 0) {
            if (context.activeID != 0 || modalWindow != null) {
                context.io.wantCaptureKeyboard = true;
            } else if (context.io.navActive
                    && ((context.io.configFlags & ConfigFlags.NAV_ENABLE_KEYBOARD) != 0)
                    && context.io.configNavCaptureKeyboard) {
                context.io.wantCaptureKeyboard = true;
            }
        }

        if (context.io.wantCaptureKeyboardNextFrame) {
            context.io.wantCaptureKeyboard = true;
        }

        if (context.io.wantTextInputNextFrame) {
            context.io.wantTextInput = true;
        }
    }

    /**
     * Handle focusing and moving window when clicking empty space within the window or the title
     * bar, just focus when clicking a disabled item, or right-clicking.
     */
    public static void updateMouseMovingWindowEndFrame() {
        if (context.activeID != 0) {
            // We already are interacting with something besides a window
            return;
        }
        if (context.hoveredID != 0 && !context.hoveredIDDisabled) {
            // We are hovering over something interactive
            return;
        }
        if (context.navFocusedWindow != null && context.navFocusedWindow.appearing) {
            // We just made a window appear
            return;
        }

        if (context.io.getMouseClicked(MouseButton.LEFT)) {
            Window hoveredRoot =
                    Optional.ofNullable(context.windowHovered)
                            .map(window -> window.rootWindow)
                            .orElse(null);
            boolean isClosingPopups =
                    hoveredRoot != null
                            && ((hoveredRoot.flags & WindowFlags.INTERNAL_POPUP) != 0)
                            && !IkGui.isPopupOpen(
                                    hoveredRoot.idAsPopupWindow, PopupFlags.ANY_POPUP_LEVEL);

            if (hoveredRoot != null && !isClosingPopups) {
                startMouseMovingWindow(hoveredRoot);
            } else if (context.windowHovered == null && context.navFocusedWindow != null) {
                // We are clicking outside a window, with a window focused
                focusWindow(null, WindowFocusRequestFlags.UNLESS_BELOW_MODAL);
            }
        }

        if (context.io.getMouseClicked(MouseButton.RIGHT) && context.hoveredID == 0) {
            /*
             * We right-clicked out in empty space, and therefore would like to close popups without
             * changing focus to where the mouse is. We can restore focus to the window under the bottom-most closed
             * popup.
             */

            Window modal = getTopmostPopupModal();
            boolean hoveredWindowAboveModal =
                    context.windowHovered != null
                            && (modal == null || isWindowAbove(context.windowHovered, modal));
            closePopupsOverWindow(hoveredWindowAboveModal ? context.windowHovered : modal, true);
        }
    }

    public static void updateMouseMovingWindowNewFrame() {
        if (context.windowMoving != null) {
            keepAliveID(context.activeID);

            if (context.windowMoving.rootWindow == null) {
                log.error("Null root window when moving a window");
                return;
            }
            Window movingWindow = context.windowMoving.rootWindow;
            if (context.io.getMouseDown(MouseButton.LEFT)
                    && IkGui.isMousePosValid(context.io.mousePosition)) {
                Vector2f pos =
                        new Vector2f(context.io.mousePosition).sub(context.activeIDClickOffset);
                IkGui.setWindowPos(movingWindow, pos, Condition.ALWAYS);
                focusWindow(context.windowMoving, WindowFocusRequestFlags.NONE);
            } else {
                stopMouseMovingWindow();
                clearActiveID();
            }

        } else {
            if (context.activeIDWindow != null
                    && context.activeIDWindow.idMove == context.activeID) {
                keepAliveID(context.activeID);
                if (!context.io.getMouseDown(MouseButton.LEFT)) {
                    clearActiveID();
                }
            }
        }
    }

    public static void updateWindowParentAndRootLinks(
            @NonNull Window window, int windowFlags, Window parentWindow) {
        window.parentWindow = parentWindow;
        window.rootWindow = window;
        window.rootWindowPopupTree = window;
        window.rootWindowDockTree = window;
        window.rootWindowForTitleBarHighlight = window;
        window.rootWindowForNavigation = window;

        if (parentWindow != null
                && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                && (windowFlags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
            window.rootWindowDockTree = parentWindow.rootWindowDockTree;
            if (!window.dockIsActive
                    && (parentWindow.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
                window.rootWindow = parentWindow.rootWindow;
            }
        }
        if (parentWindow != null && (windowFlags & WindowFlags.INTERNAL_POPUP) != 0) {
            window.rootWindowPopupTree = parentWindow.rootWindowPopupTree;
        }
        if (parentWindow != null
                && (windowFlags & WindowFlags.INTERNAL_MODAL) == 0
                && (windowFlags & WindowFlags.NO_TITLE_BAR) == 0) {
            window.rootWindowForTitleBarHighlight = parentWindow.rootWindowForTitleBarHighlight;
        }
        while ((window.rootWindowForNavigation.flagsAsChildWindow & ChildFlags.NAV_FLATTENED)
                != 0) {
            if (window.rootWindowForNavigation.parentWindow == null) {
                log.error("Parent window null while navigation child flags are set");
                return;
            }
            window.rootWindowForNavigation = window.rootWindowForNavigation.parentWindow;
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiInternal() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
