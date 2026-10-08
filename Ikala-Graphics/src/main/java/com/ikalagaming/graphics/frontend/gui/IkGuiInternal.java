package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.IntFunction;

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

    /**
     * Close any popups that are over the reference window. When popups are stacked, clicking on a
     * lower level popup puts focus back to it and closes popups above it.
     *
     * @param referenceWindow The reference window, if null all popups are closed.
     * @param restoreFocusToWindowUnderPopup Whether to restore focus to the window under the
     *     bottom-most closed popup.
     */
    public static void closePopupsOverWindow(
            Window referenceWindow, boolean restoreFocusToWindowUnderPopup) {
        IkGuiImplPopups.closePopupsOverWindow(referenceWindow, restoreFocusToWindowUnderPopup);
    }

    /**
     * Try to recover the full window stack to the sizes in a stored state, reporting each missing
     * call. Called by endFrame() when io.configErrorRecovery is set, but may be called for manual
     * recovery. Recovery is not guaranteed to be complete.
     *
     * @param stateIn The state to recover to, from errorRecoveryStoreState().
     */
    public static void errorRecoveryTryToRecoverState(@NonNull ErrorRecoveryState stateIn) {
        while (context.windowStack.size() > stateIn.sizeOfWindowStack) {
            // Begin()/beginChild() return false to indicate the window is collapsed or fully
            // clipped, but a matching end() is always needed for each begin() call, regardless of
            // its return value!
            final Window window = context.windowCurrent;
            if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
                if (context.currentTable != null && context.currentTable.innerWindow == window) {
                    IkGuiImplDebugTools.reportError(log, "Missing endTable()");
                    IkGuiImplTables.endTable();
                } else {
                    IkGuiImplDebugTools.reportError(log, "Missing endChild()");
                    IkGuiImplWindows.endChild();
                }
            } else if ((window.flags & WindowFlags.INTERNAL_POPUP) != 0) {
                // Upstream calls end() here, which also reports that endPopup() wasn't used
                IkGuiImplDebugTools.reportError(log, "Missing endPopup()");
                IkGuiImplPopups.endPopup();
            } else {
                IkGuiImplDebugTools.reportError(log, "Missing end()");
                IkGuiImplWindows.end();
            }
        }
        if (context.windowStack.size() == stateIn.sizeOfWindowStack) {
            errorRecoveryTryToRecoverWindowState(stateIn);
        }
    }

    /**
     * Try to recover from incorrect usage of the begin/end and push/pop functions in the current
     * window, reporting each missing call. Called by end() when io.configErrorRecovery is set, but
     * may be called for manual recovery.
     *
     * @param stateIn The state to recover to, from errorRecoveryStoreState().
     */
    public static void errorRecoveryTryToRecoverWindowState(@NonNull ErrorRecoveryState stateIn) {
        while (context.currentTable != null
                && context.currentTable.innerWindow == context.windowCurrent) {
            IkGuiImplDebugTools.reportError(log, "Missing endTable()");
            IkGuiImplTables.endTable();
        }

        final Window window = context.windowCurrent;

        // Can't recover from inside beginTabItem()/endTabItem() yet
        while (context.currentTabBar != null && context.currentTabBar.window == window) {
            IkGuiImplDebugTools.reportError(log, "Missing endTabBar()");
            IkGuiImplTabs.endTabBar();
        }
        while (context.currentMultiSelect != null
                && context.currentMultiSelect.storage.window == window) {
            IkGuiImplDebugTools.reportError(log, "Missing endMultiSelect()");
            IkGuiImplMultiSelect.endMultiSelect();
        }
        if (window.menuBarAppending) {
            IkGuiImplDebugTools.reportError(log, "Missing endMenuBar()");
            IkGuiImplMenus.endMenuBar();
        }
        while (window.treeDepth > stateIn.sizeOfTreeStack) {
            IkGuiImplDebugTools.reportError(log, "Missing treePop()");
            IkGuiImplTrees.treePop();
        }
        while (context.groupStack.size() > stateIn.sizeOfGroupStack) {
            IkGuiImplDebugTools.reportError(log, "Missing endGroup()");
            IkGuiImplLayout.endGroup();
        }
        while (window.idStack.size() > stateIn.sizeOfIDStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popID()");
            IkGuiImplUtils.popID();
        }
        while (context.disabledStackSize > stateIn.sizeOfDisabledStack) {
            IkGuiImplDebugTools.reportError(log, "Missing endDisabled()");
            if ((context.currentItemFlags & ItemFlags.DISABLED) != 0) {
                IkGuiImplUtils.endDisabled();
            } else {
                IkGuiImplWindows.endDisabledOverrideReenable();
                context.windowStack.getLast().disabledOverrideReenable = false;
            }
        }
        while (context.colorStack.size() > stateIn.sizeOfColorStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popStyleColor()");
            IkGuiImplUtils.popStyleColor();
        }
        while (context.itemFlagsStack.size() > stateIn.sizeOfItemFlagsStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popItemFlag()");
            IkGuiImplUtils.popItemFlag();
        }
        while (context.styleVariableStack.size() > stateIn.sizeOfStyleVarStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popStyleVar()");
            IkGuiImplUtils.popStyleVar();
        }
        while (context.fontStack.size() > stateIn.sizeOfFontStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popFont()");
            IkGuiImplText.popFont();
        }
        while (context.focusScopeStack.size() > stateIn.sizeOfFocusScopeStack) {
            IkGuiImplDebugTools.reportError(log, "Missing popFocusScope()");
            IkGuiImplNav.popFocusScope();
        }
    }

    /**
     * Save the current stack sizes, for error recovery. Called by newFrame() and begin(), but may
     * be called for manual recovery.
     *
     * @param stateOut Where to store the stack sizes.
     */
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

        // Special handling for the window being moved: ignore the mouse viewport check (because it
        // may reset/lose its viewport during the undocking frame)
        final Viewport backupMovingWindowViewport;
        if (context.windowMoving != null) {
            backupMovingWindowViewport = context.windowMoving.viewport;
            context.windowMoving.viewport = context.mouseViewport;
            if ((context.windowMoving.flags & WindowFlags.NO_MOUSE_INPUTS) == 0) {
                context.windowHovered = context.windowMoving;
            }
        } else {
            backupMovingWindowViewport = null;
        }

        Vector2f paddingRegular = IkGui.getStyleVarFloat2(StyleVariable.TOUCH_EXTRA_PADDING);
        Vector2f paddingForResize =
                new Vector2f(context.windowBorderHoverPadding, context.windowBorderHoverPadding);

        for (Window window : context.windowDisplayOrder.reversed()) {
            if (!window.wasActive
                    || window.hidden
                    || (window.flags & WindowFlags.NO_MOUSE_INPUTS) != 0) {
                continue;
            }
            if (context.mouseViewport != null && window.viewport != context.mouseViewport) {
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
                            || window.rootWindowDockTree
                                    != context.windowMoving.rootWindowDockTree)) {
                context.windowHoveredUnderMovingWindow = window;
            }
            if (context.windowHoveredUnderMovingWindow != null) {
                break;
            }
        }

        if (context.windowMoving != null) {
            context.windowMoving.viewport = backupMovingWindowViewport;
        }
    }

    /**
     * Move a window to the front of the display and set focus to it. Passing null removes focus
     * from all windows.
     *
     * @param window The window to focus, may be null.
     * @param focusRequestFlags Flags for the focus request.
     * @see WindowFocusRequestFlags
     */
    public static void focusWindow(Window window, int focusRequestFlags) {
        // Modal check
        if ((focusRequestFlags & WindowFocusRequestFlags.UNLESS_BELOW_MODAL) != 0
                && context.navFocusedWindow != window) {
            Window blockingModal = findBlockingModal(window);
            if (blockingModal != null) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_FOCUS,
                        "[focus] FocusWindow(\"%s\", UnlessBelowModal): prevented by \"%s\".",
                        IkGuiImplDebugTools.nameOf(window),
                        blockingModal.name);
                if (window != null
                        && window == window.rootWindow
                        && (window.flags & WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS) == 0) {
                    // Still bring right under modal
                    bringWindowToDisplayBehind(window, blockingModal);
                }
                closePopupsOverWindow(getTopmostPopupModal(), false);
                return;
            }
        }

        // Find the last focused child (if any) and focus it instead
        if ((focusRequestFlags & WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD) != 0
                && window != null) {
            window = IkGuiImplNav.navRestoreLastChildNavWindow(window);
        }

        // Apply focus
        if (context.navFocusedWindow != window) {
            IkGuiImplNav.setNavWindow(window);
            if (window != null && context.navHighlightItemUnderNav) {
                context.navMousePositionDirty = true;
            }
            // Restore the nav ID
            context.navID = window != null ? window.navLastIDs[0] : 0;
            context.navLayer = IkGuiImplNav.NAV_LAYER_MAIN;
            IkGuiImplNav.setNavFocusScope(window != null ? window.navRootFocusScopeID : 0);
            context.navIDAlive = false;
            context.navLastValidSelectionUserData = -1;

            // Close popups if any
            closePopupsOverWindow(window, false);
        }

        // Move the root window to the top of the pile
        final Window focusFrontWindow = window != null ? window.rootWindow : null;
        final Window displayFrontWindow = window != null ? window.rootWindowDockTree : null;
        final DockNode dockNode = window != null ? window.dockNode : null;
        final boolean activeIDWindowIsDockNodeHost =
                context.activeIDWindow != null
                        && dockNode != null
                        && dockNode.hostWindow == context.activeIDWindow;

        // Steal active widgets
        if (context.activeID != 0
                && context.activeIDWindow != null
                && context.activeIDWindow.rootWindow != focusFrontWindow
                && !context.activeIDNoClearOnFocusLost
                && !activeIDWindowIsDockNodeHost) {
            clearActiveID();
        }

        // Passing null allows us to disable keyboard focus
        if (window == null) {
            return;
        }
        window.lastFrameJustFocused = context.frameCount;

        // Bring to front
        bringWindowToFocusFront(focusFrontWindow);
        if (((window.flags | focusFrontWindow.flags | displayFrontWindow.flags)
                        & WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS)
                == 0) {
            bringWindowToDisplayFront(displayFrontWindow);
        }
    }

    /**
     * Fetch the window associated with the highest popup modal.
     *
     * @return The window, or null if none are found.
     */
    public static Window getTopmostPopupModal() {
        for (int i = context.openPopupStack.size() - 1; i >= 0; --i) {
            final Window popup = context.openPopupStack.get(i).window;
            if (popup != null && (popup.flags & WindowFlags.INTERNAL_MODAL) != 0) {
                return popup;
            }
        }
        return null;
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
            context.activeIDIsAlive = id;
        }
        if (context.deactivatedItemData.id == id) {
            context.deactivatedItemData.isAlive = true;
        }
    }

    public static void markIniSettingsDirty() {
        if (context.settingsDirtyTimer <= 0) {
            context.settingsDirtyTimer = context.io.iniSavingRate;
        }
    }

    public static void markIniSettingsDirty(@NonNull Window window) {
        if ((window.flags & WindowFlags.NO_SAVED_SETTINGS) == 0
                && context.settingsDirtyTimer <= 0) {
            context.settingsDirtyTimer = context.io.iniSavingRate;
        }
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
            context.deactivatedItemData.isAlive = context.activeIDIsAlive == context.activeID;

            // Let the input text back up its value so it can still be applied to the user string
            if (context.inputTextState.id == context.activeID) {
                IkGuiImplInputText.inputTextDeactivateHook(context.activeID);
            }

            if (context.windowMoving != null && context.activeID == context.windowMoving.idMove) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_ACTIVE_ID, "SetActiveID() cancel MovingWindow");
                stopMouseMovingWindow();
            }
        }

        // Set active ID
        context.activeIDIsJustActivated = context.activeID != id;

        if (context.activeIDIsJustActivated) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_ACTIVE_ID,
                    "SetActiveID() 0x%08X in \"%s\" (previously 0x%08X in \"%s\")",
                    id,
                    window != null ? window.name : "",
                    context.activeID,
                    context.activeIDWindow != null ? context.activeIDWindow.name : "");
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
            context.activeIDIsAlive = id;
            context.activeIDSource =
                    (context.navActivateID == id || context.navJustMovedToID == id)
                            ? context.navInputSource
                            : GuiInputSource.MOUSE;

            if (context.activeIDSource == GuiInputSource.NONE) {
                IkGuiImplDebugTools.reportError(log, "Invalid input source when setting active ID");
            }
        }

        // Clear the declaration of inputs claimed by the widget
        context.activeIDUsingNavDirMask = 0;
        context.activeIDUsingAllKeyboardKeys = false;
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
        context.activeIDClickOffset.sub(window.rootWindowDockTree.position);
        context.activeIDNoClearOnFocusLost = true;

        boolean canMoveWindow =
                (window.flags & WindowFlags.NO_MOVE) == 0
                        && (window.rootWindowDockTree.flags & WindowFlags.NO_MOVE) == 0;
        final DockNode node = window.dockNodeAsHost;
        if (node != null
                && node.visibleWindow != null
                && (node.visibleWindow.flags & WindowFlags.NO_MOVE) != 0) {
            canMoveWindow = false;
        }
        if (canMoveWindow) {
            context.windowMoving = window;
        }
    }

    /**
     * Start moving a window, or undock a dock node. We use undock == false when dragging from the
     * title bar to allow moving groups of floating nodes without undocking them.
     *
     * @param window The window to move.
     * @param node The dock node, may be null.
     * @param undock Whether to undock the node if possible.
     */
    public static void startMouseMovingWindowOrNode(
            @NonNull Window window, DockNode node, boolean undock) {
        boolean canUndockNode = false;
        if (undock
                && node != null
                && node.visibleWindow != null
                && (node.visibleWindow.flags & WindowFlags.NO_MOVE) == 0
                && (node.mergedFlags & DockNodeFlags.NO_UNDOCKING) == 0) {
            // Can undock if:
            // - part of a hierarchy with more than one visible node (if only one is visible,
            //   we'll just move the root window)
            // - part of a dockspace node hierarchy: so we can undock the last single visible node
            //   too. Undocking from a fixed/central node will create a new node and copy windows.
            final DockNode rootNode = dockNodeGetRootNode(node);
            if (rootNode.onlyNodeWithWindows != node || rootNode.centralNode != null) {
                canUndockNode = true;
            }
        }

        final boolean clicked = IkGuiImplUtils.isMouseClicked(MouseButton.LEFT, false);
        final boolean dragging = IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f);
        if (canUndockNode && dragging) {
            // Will lead to dockNodeStartMouseMovingWindow() -> startMouseMovingWindow() being
            // called next frame
            dockContextQueueUndockNode(node);
        } else if (!canUndockNode && (clicked || dragging) && context.windowMoving != window) {
            startMouseMovingWindow(window);
        }
    }

    /** Stop moving the window, but doesn't clear the active ID. */
    public static void stopMouseMovingWindow() {
        final Window window = context.windowMoving;
        if (window != null && window.viewport != null) {
            // Try to merge the window back into the main viewport. This works because the mouse
            // viewport should be different from the moving window's viewport on release (as per
            // the code in updateViewportsNewFrame())
            if (IkGuiImplViewports.viewportsEnabled()) {
                IkGuiImplViewports.updateTryMergeWindowIntoHostViewport(
                        window.rootWindowDockTree, context.mouseViewport);
            }

            // Restore the mouse viewport so that we don't hover the viewport under the moved
            // window during the frame we released the mouse button
            if (!isDragDropPayloadBeingAccepted()) {
                context.mouseViewport = window.viewport;
            }

            // Clear the no inputs flag set by the viewport system in addUpdateViewport()
            boolean windowCanUseInputs =
                    (context.windowMoving.flags & WindowFlags.NO_MOUSE_INPUTS) == 0
                            || (context.windowMoving.flags & WindowFlags.NO_NAV_INPUTS) == 0;
            if (windowCanUseInputs) {
                context.windowMoving.viewport.flags &= ~ViewportFlags.NO_INPUTS;
            }
        }

        context.windowMoving = null;
    }

    /**
     * Add a window to the tab bar. The purpose of this is to register tab in advance, so we can
     * control their order at the time they appear. Otherwise, calling this is unnecessary as tabs
     * are appending as needed by the beginTabItem() function.
     *
     * @param tabBar The tab bar we are adding to.
     * @param tabFlags Tab flags.
     * @param window The window to add.
     * @see TabItemFlags
     */
    public static void tabBarAddTab(@NonNull TabBar tabBar, int tabFlags, @NonNull Window window) {
        if (tabBarFindTabByID(tabBar, window.idTab) != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Tab bar already contains the tab we are trying to add");
            return;
        }
        if (context.currentTabBar == tabBar) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Can't add tabs to the active tab bar, as the tab doesn't have an X offset yet");
            return;
        }
        if (!window.hasCloseButton) {
            // Set immediately because it will be used for the first frame width calculation
            tabFlags |= TabItemFlags.INTERNAL_NO_CLOSE_BUTTON;
        }

        TabItem newTab = new TabItem();
        newTab.id = window.idTab;
        newTab.flags = tabFlags;
        // Required so BeginTabBar() doesn't ditch the tab
        newTab.lastFrameVisible = tabBar.currentFrameVisible;
        if (newTab.lastFrameVisible == -1) {
            newTab.lastFrameVisible = context.frameCount - 1;
        }
        // Required so tab bar layout can compute the tab width before tab submission
        newTab.window = window;
        tabBar.tabs.add(newTab);
    }

    public static TabItem tabBarFindTabByID(@NonNull TabBar tabBar, int tabID) {
        if (tabID == 0) {
            return null;
        }
        return tabBar.tabs.stream().filter(item -> item.id == tabID).findAny().orElse(null);
    }

    public static void tabBarRemoveTab(@NonNull TabBar tabBar, int tabID) {
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
     * Push an exact ID on the ID stack of the current window, without combining it with the current
     * ID stack.
     *
     * @param id The ID to push.
     */
    public static void pushOverrideID(int id) {
        debugHookIDInfo(id, null);
        getCurrentWindow().idStack.push(id);
    }

    /**
     * Register an item with the test engine, if there is one.
     *
     * @param id The ID of the item.
     * @param bb The bounding box of the item.
     * @param itemData The last item data for the item, or null for none.
     */
    public static void testEngineItemAdd(int id, @NonNull RectFloat bb, LastItemData itemData) {
        if (context.testEngineHookItems && context.testEngine != null) {
            context.testEngine.itemAdd(id, bb, itemData);
        }
    }

    /**
     * Register an item's label and status with the test engine, if there is one.
     *
     * @param id The ID of the item.
     * @param label The label of the item.
     * @param statusFlags The status flags of the item.
     */
    public static void testEngineItemInfo(int id, String label, int statusFlags) {
        if (context.testEngineHookItems && context.testEngine != null) {
            context.testEngine.itemInfo(id, label, statusFlags);
        }
    }

    /**
     * Hook called by the ID functions, which reports to the ID stack tool when it is looking for
     * the produced ID. This is cheap when the tool is not in use.
     *
     * @param id The ID that was produced.
     * @param data What was hashed to produce the ID: a String, an Integer, or null when the ID was
     *     pushed directly as an override.
     */
    public static void debugHookIDInfo(int id, Object data) {
        if (context != null && context.debugHookIDInfoID == id && id != 0) {
            IkGuiImplDebugTools.debugHookIDInfo(id, data);
        }
    }

    /**
     * Called when a debug tool requests breaking into the debugger. Java can't trigger a breakpoint
     * itself, so put a breakpoint in IkGuiImplDebugTools.debugBreak() and look at the call stack to
     * find the code that submitted the item or window.
     *
     * @param reason What triggered the break, for the log.
     */
    public static void debugBreak(@NonNull String reason) {
        IkGuiImplDebugTools.debugBreak(reason);
    }

    /**
     * Check if a tree node is open, from the current window's storage. Use with
     * IkGui.setNextItemStorageID() to query nodes from anywhere.
     *
     * @param storageID The ID the open state is stored under.
     * @return True if the node is open.
     */
    public static boolean treeNodeGetOpen(int storageID) {
        return IkGuiImplTrees.treeNodeGetOpen(storageID);
    }

    /**
     * Open or close a tree node, in the current window's storage.
     *
     * @param storageID The ID the open state is stored under.
     * @param open Whether the node should be open.
     */
    public static void treeNodeSetOpen(int storageID, boolean open) {
        IkGuiImplTrees.treeNodeSetOpen(storageID, open);
    }

    /**
     * Consume character inputs and return a typing-select request, if any.
     *
     * @return The request, or null if nothing has been typed.
     * @see #getTypingSelectRequest(int)
     */
    public static TypingSelectRequest getTypingSelectRequest() {
        return IkGuiImplTypingSelect.getTypingSelectRequest(TypingSelectFlags.NONE);
    }

    /**
     * Consume character inputs and return a typing-select request, if any. This would typically
     * only be called for the focused window or location you want to grab inputs for. Calling it
     * from multiple locations is safe (e.g. to get the buffer), unless ALLOW_BACKSPACE is used.
     *
     * @param typingSelectFlags The typing-select flags.
     * @return The request, or null if nothing has been typed.
     * @see TypingSelectFlags
     */
    public static TypingSelectRequest getTypingSelectRequest(int typingSelectFlags) {
        return IkGuiImplTypingSelect.getTypingSelectRequest(typingSelectFlags);
    }

    /**
     * The default handler for finding the item matching a typing-select request.
     *
     * @param request The request, may be null so both calls can be done in the same spot.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @param navItemIndex The index of the focused item, needed by single char mode to go to the
     *     next match, or -1.
     * @return The index of the matching item, or -1 for no match.
     */
    public static int typingSelectFindMatch(
            TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName,
            int navItemIndex) {
        return IkGuiImplTypingSelect.typingSelectFindMatch(
                request, itemsCount, getItemName, navItemIndex);
    }

    /**
     * Find the next item starting with the repeated character of a single char mode request.
     *
     * @param request The request.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @param navItemIndex The index of the focused item, or -1 to return the first match.
     * @return The index of the matching item, or -1 for no match.
     */
    public static int typingSelectFindNextSingleCharMatch(
            @NonNull TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName,
            int navItemIndex) {
        return IkGuiImplTypingSelect.typingSelectFindNextSingleCharMatch(
                request, itemsCount, getItemName, navItemIndex);
    }

    /**
     * Find the item whose name has the longest leading match with the search.
     *
     * @param request The request.
     * @param itemsCount The number of items.
     * @param getItemName Fetches the name of an item by index.
     * @return The index of the best matching item, or -1 for no match.
     */
    public static int typingSelectFindBestLeadingMatch(
            @NonNull TypingSelectRequest request,
            int itemsCount,
            @NonNull IntFunction<String> getItemName) {
        return IkGuiImplTypingSelect.typingSelectFindBestLeadingMatch(
                request, itemsCount, getItemName);
    }

    /**
     * Fetch the box-select state, if the given box-select is active.
     *
     * @param id The box-select ID.
     * @return The state, or null if that box-select is not active.
     */
    public static BoxSelectState getBoxSelectState(int id) {
        return IkGuiImplMultiSelect.getBoxSelectState(id);
    }

    /**
     * Fetch the persistent state of a multi-select scope.
     *
     * @param id The ID of the scope.
     * @return The state, or null if there is none.
     */
    public static MultiSelectState getMultiSelectState(int id) {
        return IkGuiImplMultiSelect.getMultiSelectState(id);
    }

    /**
     * For custom widgets supporting multi-select, called before the button behavior.
     *
     * @param id The item ID.
     * @param selected The selection state, which may be modified.
     * @param buttonFlags The button flags, which may be modified. May be null.
     */
    public static void multiSelectItemHeader(
            int id, @NonNull IkBoolean selected, IkInt buttonFlags) {
        IkGuiImplMultiSelect.multiSelectItemHeader(id, selected, buttonFlags);
    }

    /**
     * For custom widgets supporting multi-select, called after the button behavior.
     *
     * @param id The item ID.
     * @param selected The selection state, which may be modified.
     * @param pressed The pressed state, which may be modified.
     */
    public static void multiSelectItemFooter(
            int id, @NonNull IkBoolean selected, @NonNull IkBoolean pressed) {
        IkGuiImplMultiSelect.multiSelectItemFooter(id, selected, pressed, MultiSelectFlags.NONE);
    }

    /**
     * Report an error about incorrect API usage, to the logger of the class that found it and to
     * the debug log (as an error event). For classes outside the gui package, like the data
     * classes.
     *
     * @param logger The logger of the class reporting the error.
     * @param format The SLF4J style message format, using {} for arguments.
     * @param arguments The arguments. A trailing throwable is logged with its stack trace.
     */
    public static void reportError(@NonNull Logger logger, String format, Object... arguments) {
        IkGuiImplDebugTools.reportError(logger, format, arguments);
    }

    /**
     * Add text to the debug log, unconditionally.
     *
     * @param text The text to log.
     */
    public static void debugLog(@NonNull String text) {
        IkGuiImplDebugTools.debugLog(text);
    }

    /**
     * Highlight an item for the next couple of frames, when it is submitted.
     *
     * @param targetID The ID of the item.
     */
    public static void debugLocateItem(int targetID) {
        IkGuiImplDebugTools.debugLocateItem(targetID);
    }

    /**
     * When the last item is hovered, highlight the item with the given ID.
     *
     * @param targetID The ID of the item to locate.
     */
    public static void debugLocateItemOnHover(int targetID) {
        IkGuiImplDebugTools.debugLocateItemOnHover(targetID);
    }

    /**
     * Draw a small cross at the cursor position in the current window.
     *
     * @param color The color to draw with.
     */
    public static void debugDrawCursorPos(int color) {
        IkGuiImplDebugTools.debugDrawCursorPos(color);
    }

    /**
     * Draw the extents of the current line in the current window, around the cursor.
     *
     * @param color The color to draw with.
     */
    public static void debugDrawLineExtents(int color) {
        IkGuiImplDebugTools.debugDrawLineExtents(color);
    }

    /**
     * Draw the rectangle of the last item in the foreground draw list.
     *
     * @param color The color to draw with.
     */
    public static void debugDrawItemRect(int color) {
        IkGuiImplDebugTools.debugDrawItemRect(color);
    }

    /**
     * A button for triggering debug breaks, which doesn't take focus or input ownership.
     *
     * @param label The label.
     * @param descriptionOfLocation Where the break will happen, for the tooltip.
     * @return True if the button was pressed.
     */
    public static boolean debugBreakButton(
            @NonNull String label, @NonNull String descriptionOfLocation) {
        return IkGuiImplDebugTools.debugBreakButton(label, descriptionOfLocation);
    }

    /**
     * Display the contents of a window, for the metrics window.
     *
     * @param window The window, may be null.
     * @param label The label.
     */
    public static void debugNodeWindow(Window window, @NonNull String label) {
        IkGuiImplMetrics.debugNodeWindow(window, label);
    }

    /**
     * Display the contents of a dock node, for the metrics window.
     *
     * @param node The node.
     * @param label The label.
     */
    public static void debugNodeDockNode(@NonNull DockNode node, @NonNull String label) {
        IkGuiImplMetrics.debugNodeDockNode(node, label);
    }

    /**
     * Display the contents of a draw list, for the metrics window.
     *
     * @param window The window that owns the draw list, may be null.
     * @param viewport The viewport the draw list is in, may be null.
     * @param drawList The draw list.
     * @param label The label.
     */
    public static void debugNodeDrawList(
            Window window, Viewport viewport, @NonNull DrawList drawList, @NonNull String label) {
        IkGuiImplMetrics.debugNodeDrawList(window, viewport, drawList, label);
    }

    /**
     * Display the contents of a table, for the metrics window.
     *
     * @param table The table.
     */
    public static void debugNodeTable(@NonNull Table table) {
        IkGuiImplMetrics.debugNodeTable(table);
    }

    /**
     * Display the contents of a tab bar, for the metrics window.
     *
     * @param tabBar The tab bar.
     * @param label The label.
     */
    public static void debugNodeTabBar(@NonNull TabBar tabBar, @NonNull String label) {
        IkGuiImplMetrics.debugNodeTabBar(tabBar, label);
    }

    /**
     * Display the contents of a viewport, for the metrics window.
     *
     * @param viewport The viewport.
     */
    public static void debugNodeViewport(@NonNull Viewport viewport) {
        IkGuiImplMetrics.debugNodeViewport(viewport);
    }

    /**
     * Display the contents of a storage, for the metrics window.
     *
     * @param storage The storage.
     * @param label The label.
     */
    public static void debugNodeStorage(@NonNull Storage storage, @NonNull String label) {
        IkGuiImplMetrics.debugNodeStorage(storage, label);
    }

    /**
     * Shrink excess width from a set of items, by removing width from the larger items first. Items
     * with a negative width are not shrunk. The items are sorted by width as a side effect.
     *
     * @param items The list of items.
     * @param offset The index of the first item to consider.
     * @param count How many items to consider.
     * @param widthExcess How much width we need to remove in total.
     * @param widthMin The minimum width of any item.
     */
    public static void shrinkWidths(
            @NonNull List<ShrinkWidthItem> items,
            int offset,
            int count,
            float widthExcess,
            float widthMin) {
        if (count <= 0) {
            return;
        }
        final List<ShrinkWidthItem> view = items.subList(offset, offset + count);
        if (count == 1) {
            final ShrinkWidthItem item = view.getFirst();
            if (item.width >= 0.0f) {
                item.width = Math.max(item.width - widthExcess, widthMin);
            }
            return;
        }
        // Sort largest first, smallest last
        view.sort(
                (a, b) -> {
                    final int difference = (int) (b.width - a.width);
                    if (difference != 0) {
                        return difference;
                    }
                    return b.index - a.index;
                });
        int countSameWidth = 1;
        while (widthExcess > 0.001f && countSameWidth < count) {
            while (countSameWidth < count && view.get(0).width <= view.get(countSameWidth).width) {
                countSameWidth++;
            }
            float maxWidthToRemovePerItem =
                    (countSameWidth < count && view.get(countSameWidth).width >= 0.0f)
                            ? (view.get(0).width - view.get(countSameWidth).width)
                            : (view.get(0).width - 1.0f);
            maxWidthToRemovePerItem =
                    Math.min(view.get(0).width - widthMin, maxWidthToRemovePerItem);
            if (maxWidthToRemovePerItem <= 0.0f) {
                break;
            }
            final float baseWidthToRemovePerItem =
                    Math.min(widthExcess / countSameWidth, maxWidthToRemovePerItem);
            for (int i = 0; i < countSameWidth; ++i) {
                final ShrinkWidthItem item = view.get(i);
                final float widthToRemove =
                        Math.min(baseWidthToRemovePerItem, item.width - widthMin);
                item.width -= widthToRemove;
                widthExcess -= widthToRemove;
            }
        }

        // Round widths and redistribute the remainder, ensuring that e.g. the right-most tab of a
        // shrunk tab bar always reaches exactly the same distance from the right edge
        widthExcess = 0.0f;
        for (ShrinkWidthItem item : view) {
            final float widthRounded = truncate(item.width);
            widthExcess += item.width - widthRounded;
            item.width = widthRounded;
        }
        while (widthExcess > 0.0f) {
            boolean anyAdded = false;
            for (int i = 0; i < count && widthExcess > 0.0f; ++i) {
                final ShrinkWidthItem item = view.get(i);
                final float widthToAdd = Math.min(item.initialWidth - item.width, 1.0f);
                if (widthToAdd > 0.0f) {
                    anyAdded = true;
                }
                item.width += widthToAdd;
                widthExcess -= widthToAdd;
            }
            if (!anyAdded) {
                // Nothing left to grow, avoid looping forever
                break;
            }
        }
    }

    /**
     * Render text that is clipped with an ellipsis ("...") if it doesn't fit.
     *
     * @param minX The left of the text area.
     * @param minY The top of the text area.
     * @param maxX The right of the text area, past which text is clipped.
     * @param maxY The bottom of the text area.
     * @param ellipsisMaxX The right edge the ellipsis may extend to, which can be past maxX.
     * @param text The text, anything after "##" is hidden.
     * @param textSize The size of the text if known, may be null.
     */
    public static void renderTextEllipsis(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float ellipsisMaxX,
            @NonNull String text,
            Vector2f textSize) {
        final String displayed = Hash.getDisplayedText(text);
        final Vector2f size = new Vector2f();
        if (textSize != null) {
            size.set(textSize);
        } else {
            IkGuiImplUtils.calcTextSize(size, displayed, false, -1.0f);
        }

        if (size.x <= maxX - minX) {
            renderTextClippedEx(minX, minY, maxX, maxY, displayed, size, 0.0f, 0.0f, null);
            if (context.logEnabled) {
                IkGuiImplLogging.logRenderedText(minY, displayed);
            }
            return;
        }

        // Hello wo...
        // |       |   |
        // min   max   ellipsis max
        final String ellipsis = "...";
        final Vector2f measure = new Vector2f();
        IkGuiImplUtils.calcTextSize(measure, ellipsis, false, -1.0f);
        final float ellipsisWidth = measure.x;
        final float availableWidth =
                Math.max((Math.max(maxX, ellipsisMaxX) - ellipsisWidth) - minX, 1.0f);

        // Find the longest prefix that fits, text width grows with length
        int low = 0;
        int high = displayed.length();
        while (low < high) {
            final int mid = (low + high + 1) / 2;
            IkGuiImplUtils.calcTextSize(measure, displayed.substring(0, mid), false, -1.0f);
            if (measure.x <= availableWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        final String prefix = displayed.substring(0, low);
        IkGuiImplUtils.calcTextSize(measure, prefix, false, -1.0f);
        final float prefixWidth = low > 0 ? measure.x : 0.0f;
        if (low > 0) {
            renderTextClippedEx(minX, minY, maxX, maxY, prefix, null, 0.0f, 0.0f, null);
        }
        final float ellipsisX = truncate(minX + prefixWidth);
        final float clipMaxX = Math.max(maxX, ellipsisMaxX);
        renderTextClippedEx(
                ellipsisX,
                minY,
                clipMaxX,
                maxY,
                ellipsis,
                null,
                0.0f,
                0.0f,
                new RectFloat(minX, minY, clipMaxX, maxY));
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(minY, displayed);
        }
    }

    /**
     * Truncate the given float to an integer value.
     *
     * @param value The float.
     * @return The truncated float.
     */
    public static float truncate(float value) {
        return (int) value;
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

        if (context.wantCaptureMouseNextFrame != -1) {
            context.io.wantCaptureMouse = context.wantCaptureMouseNextFrame != 0;
            context.io.wantCaptureMouseUnlessPopupClose = context.wantCaptureMouseNextFrame != 0;
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

        // Manual override
        if (context.wantCaptureKeyboardNextFrame != -1) {
            context.io.wantCaptureKeyboard = context.wantCaptureKeyboardNextFrame != 0;
        }

        // Allows systems without a keyboard to show a software keyboard if possible
        context.io.wantTextInput =
                context.wantTextInputNextFrame != -1 && context.wantTextInputNextFrame != 0;
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

        // Click on empty space to focus window and start moving
        if (IkGuiImplUtils.isMouseClicked(
                MouseButton.LEFT, InputFlags.NONE, KeyRoutingData.KEY_OWNER_NO_OWNER)) {
            final Window hoveredRoot =
                    context.windowHovered != null ? context.windowHovered.rootWindow : null;
            final boolean isClosedPopup =
                    hoveredRoot != null
                            && (hoveredRoot.flags & WindowFlags.INTERNAL_POPUP) != 0
                            && !IkGui.isPopupOpen(
                                    hoveredRoot.idAsPopupWindow, PopupFlags.ANY_POPUP_LEVEL);

            if (hoveredRoot != null && !isClosedPopup) {
                startMouseMovingWindow(context.windowHovered);

                // Cancel moving if clicked outside of title bar
                if (context.io.configWindowsMoveFromTitleBarOnly
                        && ((hoveredRoot.flags & WindowFlags.NO_TITLE_BAR) == 0
                                || hoveredRoot.dockIsActive)) {
                    RectFloat titleBar = hoveredRoot.getTitleBarRect(new RectFloat());
                    if (!isMouseHoveringRect(
                            titleBar.getLeft(),
                            titleBar.getTop(),
                            titleBar.getRight(),
                            titleBar.getBottom(),
                            false)) {
                        context.windowMoving = null;
                    }
                }

                // Cancel moving if clicked over an item which was disabled or inhibited by popups
                if (context.hoveredIDDisabled) {
                    context.windowMoving = null;
                    context.activeIDDisabledId = context.hoveredID;
                }
            } else if (hoveredRoot == null && context.navFocusedWindow != null) {
                // Clicking on void disables focus
                focusWindow(null, WindowFocusRequestFlags.UNLESS_BELOW_MODAL);
            }
        }

        if (context.hoveredID == 0
                && IkGuiImplUtils.isMouseClicked(
                        MouseButton.RIGHT, InputFlags.NONE, KeyRoutingData.KEY_OWNER_NO_OWNER)) {
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

            if (context.windowMoving.rootWindowDockTree == null) {
                IkGuiImplDebugTools.reportError(log, "Null root window when moving a window");
                return;
            }
            // We actually want to move the root window, the moving window is the window we clicked
            // on (could be a child window)
            Window movingWindow = context.windowMoving.rootWindowDockTree;
            // When a window stops being submitted while being dragged, it may lose its viewport
            // until the next begin()
            final boolean windowDisappeared = !movingWindow.wasActive && !movingWindow.active;
            if (context.io.getMouseDown(MouseButton.LEFT)
                    && IkGui.isMousePosValid(context.io.mousePosition)
                    && !windowDisappeared) {
                Vector2f pos =
                        new Vector2f(context.io.mousePosition).sub(context.activeIDClickOffset);
                if (movingWindow.position.x != pos.x || movingWindow.position.y != pos.y) {
                    IkGui.setWindowPos(movingWindow, pos, Condition.ALWAYS);
                    // Synchronize the viewport immediately, because some overlays may rely on the
                    // clipping rectangle before we begin() into the window
                    if (movingWindow.viewport != null && movingWindow.viewportOwned) {
                        movingWindow.viewport.position.set(pos);
                        movingWindow.viewport.updateWorkRect();
                    }
                }
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
                && (windowFlags & WindowFlags.INTERNAL_TOOLTIP) == 0) {
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
                && (windowFlags
                                & (WindowFlags.INTERNAL_CHILD_WINDOW
                                        | WindowFlags.INTERNAL_POPUP
                                        | WindowFlags.INTERNAL_TOOLTIP))
                        != 0) {
            window.rootWindowForTitleBarHighlight = parentWindow.rootWindowForTitleBarHighlight;
        }
        while ((window.rootWindowForNavigation.flagsAsChildWindow & ChildFlags.NAV_FLATTENED)
                != 0) {
            if (window.rootWindowForNavigation.parentWindow == null) {
                IkGuiImplDebugTools.reportError(
                        log, "Parent window null while navigation child flags are set");
                return;
            }
            window.rootWindowForNavigation = window.rootWindowForNavigation.parentWindow;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Windows
    // ---------------------------------------------------------------------------------------------

    /**
     * Fetch the current window, marking it as written to. Use this when submitting items or
     * modifying the window, as the implicit fallback window is only displayed when written to.
     *
     * @return The current window.
     */
    public static Window getCurrentWindow() {
        Window window = context.windowCurrent;
        window.writeAccessed = true;
        return window;
    }

    /**
     * Fetch the current window, without marking it as written to.
     *
     * @return The current window.
     */
    public static Window getCurrentWindowRead() {
        return context.windowCurrent;
    }

    /**
     * Look up a window by its ID.
     *
     * @param id The window ID.
     * @return The window, or null if not found.
     */
    public static Window findWindowByID(int id) {
        return context.windowByID.get(id);
    }

    /**
     * Look up a window by its name.
     *
     * @param name The window name.
     * @return The window, or null if not found.
     */
    public static Window findWindowByName(@NonNull String name) {
        return findWindowByID(Hash.getID(name));
    }

    /**
     * Find the index of a window in the display order list.
     *
     * @param window The window.
     * @return The index, or -1 if not found.
     */
    public static int findWindowDisplayIndex(Window window) {
        return context.windowDisplayOrder.indexOf(window);
    }

    /**
     * The current font size, in pixels.
     *
     * @return The height of a line of text, in pixels.
     */
    public static float getFontSize() {
        return IkGuiImplLayout.getTextLineHeight();
    }

    /**
     * The vertical metrics of the current font at the current font size.
     *
     * @return The metrics, or null if no fonts are loaded.
     */
    public static FontMetrics getFontMetrics() {
        return context.io.fonts == null ? null : context.io.fonts.getFontMetrics(context.fontSize);
    }

    /**
     * Check if a window is active this frame and not hidden.
     *
     * @param window The window.
     * @return True if the window is active and visible.
     */
    public static boolean isWindowActiveAndVisible(@NonNull Window window) {
        return window.active && !window.hidden;
    }

    /**
     * Walk up the root window links until we stop changing, optionally following the popup and dock
     * hierarchies.
     *
     * @param window The window to start from.
     * @param popupHierarchy Whether to follow popup tree root links.
     * @param dockHierarchy Whether to follow dock tree root links.
     * @return The combined root window.
     */
    public static Window getCombinedRootWindow(
            @NonNull Window window, boolean popupHierarchy, boolean dockHierarchy) {
        Window lastWindow = null;
        while (lastWindow != window) {
            lastWindow = window;
            window = window.rootWindow;
            if (popupHierarchy) {
                window = window.rootWindowPopupTree;
            }
            if (dockHierarchy) {
                window = window.rootWindowDockTree;
            }
        }
        return window;
    }

    /**
     * Check if a window is a child of another window.
     *
     * @param window The window that might be a child.
     * @param potentialParent The window that might be a parent.
     * @param popupHierarchy Whether to treat popups as children of the window that opened them.
     * @param dockHierarchy Whether to treat docked windows as children of the dock host.
     * @return True if window is a (possibly indirect) child of potentialParent, or is the same
     *     window.
     */
    public static boolean isWindowChildOf(
            Window window, Window potentialParent, boolean popupHierarchy, boolean dockHierarchy) {
        if (window == null || potentialParent == null) {
            return false;
        }
        Window windowRoot = getCombinedRootWindow(window, popupHierarchy, dockHierarchy);
        if (windowRoot == potentialParent) {
            return true;
        }
        while (window != null) {
            if (window == potentialParent) {
                return true;
            }
            if (window == windowRoot) {
                return false;
            }
            window = window.parentWindow;
        }
        return false;
    }

    /**
     * Find the modal that would block the given window from being focused.
     *
     * @param window The window we want to focus, or null if clicking on the background.
     * @return The blocking modal window, or null if nothing is blocking.
     */
    public static Window findBlockingModal(Window window) {
        if (context.openPopupStack.isEmpty()) {
            return null;
        }
        // Find a modal that has a common parent with the window, the window should be behind it
        for (PopupData popupData : context.openPopupStack) {
            final Window popupWindow = popupData.window;
            if (popupWindow == null || (popupWindow.flags & WindowFlags.INTERNAL_MODAL) == 0) {
                continue;
            }
            if (!popupWindow.active && !popupWindow.wasActive) {
                continue;
            }
            if (window == null) {
                return popupWindow;
            }
            if (isWindowWithinBeginStackOf(window, popupWindow)) {
                continue;
            }
            return popupWindow;
        }
        return null;
    }

    /**
     * Move a root window to the end of the focus order list.
     *
     * @param window The root window.
     */
    public static void bringWindowToFocusFront(@NonNull Window window) {
        final int currentOrder = context.windowFocusOrder.indexOf(window);
        if (currentOrder < 0 || currentOrder == context.windowFocusOrder.size() - 1) {
            return;
        }
        context.windowFocusOrder.remove(currentOrder);
        context.windowFocusOrder.add(window);
        for (int i = currentOrder; i < context.windowFocusOrder.size(); ++i) {
            context.windowFocusOrder.get(i).focusOrder = (short) i;
        }
    }

    /**
     * Move a window to the front of the display order (rendered last).
     *
     * @param window The window.
     */
    public static void bringWindowToDisplayFront(@NonNull Window window) {
        if (context.windowDisplayOrder.isEmpty()) {
            return;
        }
        Window currentFront = context.windowDisplayOrder.getLast();
        if (currentFront == window || currentFront.rootWindowDockTree == window) {
            return;
        }
        if (context.windowDisplayOrder.remove(window)) {
            context.windowDisplayOrder.add(window);
        }
    }

    /**
     * Move a window to the back of the display order (rendered first).
     *
     * @param window The window.
     */
    public static void bringWindowToDisplayBack(@NonNull Window window) {
        if (context.windowDisplayOrder.isEmpty()
                || context.windowDisplayOrder.getFirst() == window) {
            return;
        }
        if (context.windowDisplayOrder.remove(window)) {
            context.windowDisplayOrder.addFirst(window);
        }
    }

    /**
     * Move a window to be displayed right behind another window.
     *
     * @param window The window to move.
     * @param behindWindow The window that should end up in front.
     */
    public static void bringWindowToDisplayBehind(
            @NonNull Window window, @NonNull Window behindWindow) {
        window = window.rootWindow;
        behindWindow = behindWindow.rootWindow;
        if (window == behindWindow || !context.windowDisplayOrder.remove(window)) {
            return;
        }
        final int behindIndex = context.windowDisplayOrder.indexOf(behindWindow);
        if (behindIndex < 0) {
            context.windowDisplayOrder.add(window);
            return;
        }
        context.windowDisplayOrder.add(behindIndex, window);
    }

    /**
     * Focus the top-most window that is under the given window, used when closing or hiding
     * windows.
     *
     * @param underThisWindow The window to start looking under, or null to start at the top.
     * @param ignoreWindow A window that we should not focus, may be null.
     * @param focusRequestFlags Flags for the focus request.
     * @see WindowFocusRequestFlags
     */
    public static void focusTopMostWindowUnderOne(
            Window underThisWindow, Window ignoreWindow, int focusRequestFlags) {
        focusTopMostWindowUnderOne(underThisWindow, ignoreWindow, null, focusRequestFlags);
    }

    /**
     * Focus the top-most window that can be focused, starting below a given window, and only
     * considering windows in a given viewport.
     *
     * @param underThisWindow The window to start looking under, or null to start at the top.
     * @param ignoreWindow A window that we should not focus, may be null.
     * @param filterViewport Only consider windows in this viewport, or null for any viewport.
     * @param focusRequestFlags Flags for the focus request.
     * @see WindowFocusRequestFlags
     */
    public static void focusTopMostWindowUnderOne(
            Window underThisWindow,
            Window ignoreWindow,
            Viewport filterViewport,
            int focusRequestFlags) {
        int startIndex = context.windowFocusOrder.size() - 1;
        if (underThisWindow != null) {
            // Aim at root window behind us, if we are in a child window that's our own root
            int offset = -1;
            while ((underThisWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    && underThisWindow.parentWindow != null) {
                underThisWindow = underThisWindow.parentWindow;
                offset = 0;
            }
            startIndex = context.windowFocusOrder.indexOf(underThisWindow) + offset;
        }
        for (int i = startIndex; i >= 0; --i) {
            Window window = context.windowFocusOrder.get(i);
            if (window == ignoreWindow || !window.wasActive) {
                continue;
            }
            if (filterViewport != null && window.viewport != filterViewport) {
                continue;
            }
            final int noInputs = WindowFlags.NO_MOUSE_INPUTS | WindowFlags.NO_NAV_INPUTS;
            if ((window.flags & noInputs) != noInputs) {
                focusWindow(window, focusRequestFlags);
                return;
            }
        }
        focusWindow(null, focusRequestFlags);
    }

    /**
     * Calculate the size the window would need to be to fit its contents next frame.
     *
     * @param window The window.
     * @param output Where to store the result.
     * @return The output vector, for convenience.
     */
    public static Vector2f calcWindowNextAutoFitSize(
            @NonNull Window window, @NonNull Vector2f output) {
        Vector2f current = new Vector2f();
        Vector2f ideal = new Vector2f();
        IkGuiImplWindows.calcWindowContentSizes(window, current, ideal);
        IkGuiImplWindows.calcWindowAutoFitSize(window, ideal, ~0, output);
        IkGuiImplWindows.calcWindowSizeAfterConstraint(window, output, output);
        return output;
    }

    /**
     * Set the scroll position of a window on the x-axis, which will be applied next frame.
     *
     * @param window The window.
     * @param scrollX The new scroll position.
     */
    public static void setScrollX(@NonNull Window window, float scrollX) {
        window.scrollTarget.x = scrollX;
        window.scrollTargetCenterRatio.x = 0.0f;
        window.scrollTargetEdgeSnapDist.x = 0.0f;
    }

    /**
     * Set the scroll position of a window on the y-axis, which will be applied next frame.
     *
     * @param window The window.
     * @param scrollY The new scroll position.
     */
    public static void setScrollY(@NonNull Window window, float scrollY) {
        window.scrollTarget.y = scrollY;
        window.scrollTargetCenterRatio.y = 0.0f;
        window.scrollTargetEdgeSnapDist.y = 0.0f;
    }

    /**
     * Scroll so that a local x position is at a certain point within the window.
     *
     * @param window The window.
     * @param localX The position, relative to the window position.
     * @param centerXRatio 0 for the left of the window, 0.5 for the center, 1 for the right.
     */
    public static void setScrollFromPosX(@NonNull Window window, float localX, float centerXRatio) {
        if (centerXRatio < 0.0f || centerXRatio > 1.0f) {
            IkGuiImplDebugTools.reportError(
                    log, "Scroll center ratio {} is out of the range [0, 1]", centerXRatio);
            return;
        }
        window.scrollTarget.x =
                truncate(
                        localX
                                - window.decoOuterSizeX1
                                - window.decoInnerSizeX1
                                + window.scrollPosition.x);
        window.scrollTargetCenterRatio.x = centerXRatio;
        window.scrollTargetEdgeSnapDist.x = 0.0f;
    }

    /**
     * Scroll so that a local y position is at a certain point within the window.
     *
     * @param window The window.
     * @param localY The position, relative to the window position.
     * @param centerYRatio 0 for the top of the window, 0.5 for the center, 1 for the bottom.
     */
    public static void setScrollFromPosY(@NonNull Window window, float localY, float centerYRatio) {
        if (centerYRatio < 0.0f || centerYRatio > 1.0f) {
            IkGuiImplDebugTools.reportError(
                    log, "Scroll center ratio {} is out of the range [0, 1]", centerYRatio);
            return;
        }
        window.scrollTarget.y =
                truncate(
                        localY
                                - window.decoOuterSizeY1
                                - window.decoInnerSizeY1
                                + window.scrollPosition.y);
        window.scrollTargetCenterRatio.y = centerYRatio;
        window.scrollTargetEdgeSnapDist.y = 0.0f;
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    /**
     * Advance the layout cursor given the size of an item, and register the space it needs so it
     * can be used for auto-fitting the window.
     *
     * @param width The width of the item.
     * @param height The height of the item.
     * @param textBaselineY The offset of the text baseline within the item, or -1 if not relevant.
     */
    public static void itemSize(float width, float height, float textBaselineY) {
        Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        // We increase the height to accommodate for baseline offset
        final float offsetToMatchBaselineY =
                textBaselineY >= 0
                        ? Math.max(0.0f, window.baseOffsetCurrentLine - textBaselineY)
                        : 0.0f;

        final float lineY1 =
                window.sameLine ? window.cursorPreviousLinePosition.y : window.cursorPosition.y;
        final float lineHeight =
                Math.max(
                        window.lineSizeCurrent.y,
                        window.cursorPosition.y - lineY1 + height + offsetToMatchBaselineY);

        // Always align ourselves on pixel boundaries
        window.cursorPreviousLinePosition.set(window.cursorPosition.x + width, lineY1);
        window.cursorPosition.set(
                truncate(window.position.x + window.indent + window.columnsOffset),
                truncate(lineY1 + lineHeight + context.style.variable.itemSpacing.y));
        window.cursorMaxPosition.x =
                Math.max(window.cursorMaxPosition.x, window.cursorPreviousLinePosition.x);
        window.cursorMaxPosition.y =
                Math.max(
                        window.cursorMaxPosition.y,
                        window.cursorPosition.y - context.style.variable.itemSpacing.y);

        window.lineSizePrevious.y = lineHeight;
        window.lineSizeCurrent.y = 0.0f;
        window.baseOffsetPreviousLine = Math.max(window.baseOffsetCurrentLine, textBaselineY);
        window.baseOffsetCurrentLine = 0.0f;
        window.sameLine = false;
        window.setPos = false;

        // Horizontal layout mode
        if (window.layoutType == LayoutType.HORIZONTAL) {
            IkGuiImplLayout.sameLine(0.0f, -1.0f);
        }
    }

    /**
     * Advance the layout cursor given the size of an item.
     *
     * @param width The width of the item.
     * @param height The height of the item.
     */
    public static void itemSize(float width, float height) {
        itemSize(width, height, -1.0f);
    }

    /**
     * Advance the layout cursor given the bounding box of an item.
     *
     * @param bb The bounding box.
     * @param textBaselineY The offset of the text baseline within the item, or -1 if not relevant.
     */
    public static void itemSize(@NonNull RectFloat bb, float textBaselineY) {
        itemSize(bb.getWidth(), bb.getHeight(), textBaselineY);
    }

    /**
     * Declare an item bounding box for clipping and interaction. Sets up the last item data, and
     * returns false if the item is clipped and should not be rendered/processed.
     *
     * @param bb The bounding box of the item.
     * @param id The ID of the item, or 0 for non-interactive items.
     * @param navBB The navigation bounding box, or null to use the regular bounding box.
     * @param extraFlags Extra item flags to apply.
     * @return False if the item is clipped.
     * @see ItemFlags
     */
    public static boolean itemAdd(@NonNull RectFloat bb, int id, RectFloat navBB, int extraFlags) {
        Window window = context.windowCurrent;

        // Set item data (display rect is left untouched, only valid when HAS_DISPLAY_RECT is set)
        context.lastItemData.id = id;
        context.lastItemData.rect.set(bb);
        context.lastItemData.navRect.set(navBB != null ? navBB : bb);
        context.lastItemData.itemFlags =
                context.currentItemFlags | context.nextItemData.itemFlags | extraFlags;
        context.lastItemData.statusFlags = ItemStatusFlags.NONE;

        if (id != 0) {
            keepAliveID(id);

            // Directional navigation processing. This runs prior to the clipping early out, so
            // that init requests can select a default widget in newly opened windows, and so we
            // can scroll past clipped items.
            if ((context.lastItemData.itemFlags & ItemFlags.NO_NAV) == 0) {
                window.navLayersActiveMaskNext |= 1 << window.navLayerCurrent;
                if ((context.navID == id || context.navAnyRequest)
                        && context.navFocusedWindow != null
                        && context.navFocusedWindow.rootWindowForNavigation
                                == window.rootWindowForNavigation
                        && (window == context.navFocusedWindow
                                || ((window.flagsAsChildWindow
                                                        | context.navFocusedWindow
                                                                .flagsAsChildWindow)
                                                & ChildFlags.NAV_FLATTENED)
                                        != 0)) {
                    IkGuiImplNav.navProcessItem();
                }
            }

            if ((context.nextItemData.fieldFlags & NextItemFlags.HAS_SHORTCUT) != 0) {
                IkGuiImplKeys.itemHandleShortcut(id);
            }
        }

        // Lightweight clear of setNextItem*() data
        context.nextItemData.clearFlags();

        if (id != 0) {
            testEngineItemAdd(id, context.lastItemData.navRect, context.lastItemData);
        }

        // Clipping test
        final boolean isRectVisible = bb.overlaps(window.rectCurrentClip);
        if (!isRectVisible
                && (id == 0
                        || (id != context.activeID
                                && id != context.activeIDPreviousFrame
                                && id != context.navID
                                && id != context.navActivateID))
                && !context.itemUnclipByLog) {
            return false;
        }

        // Debug tools: locating items, and the item picker
        if (id != 0) {
            IkGuiImplDebugTools.debugHookItemAdd(id, bb);
        }

        if (id != 0 && context.deactivatedItemData.id == id) {
            context.deactivatedItemData.elapsedFrame = context.frameCount;
        }

        // We need to calculate this now to take account of the current clipping rectangle
        if (isRectVisible) {
            context.lastItemData.statusFlags |= ItemStatusFlags.VISIBLE;
        }
        if (isMouseHoveringRect(bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), true)) {
            context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_RECT;
        }
        return true;
    }

    /**
     * Declare an item bounding box for clipping and interaction.
     *
     * @param bb The bounding box of the item.
     * @param id The ID of the item, or 0 for non-interactive items.
     * @return False if the item is clipped.
     */
    public static boolean itemAdd(@NonNull RectFloat bb, int id) {
        return itemAdd(bb, id, null, ItemFlags.NONE);
    }

    /**
     * Check if a bounding box is clipped by the current window clip rect.
     *
     * @param bb The bounding box.
     * @param id The item ID, which is never considered clipped if it is active.
     * @return True if the item is clipped.
     */
    public static boolean isClippedEx(@NonNull RectFloat bb, int id) {
        Window window = context.windowCurrent;
        return !bb.overlaps(window.rectCurrentClip)
                && (id == 0
                        || (id != context.activeID
                                && id != context.activeIDPreviousFrame
                                && id != context.navID
                                && id != context.navActivateID))
                && !context.itemUnclipByLog;
    }

    /**
     * Set the last item data, typically used by widgets that don't go through itemAdd().
     *
     * @param itemID The item ID.
     * @param itemFlags The item flags.
     * @param statusFlags The item status flags.
     * @param itemRect The rectangle of the item.
     */
    public static void setLastItemData(
            int itemID, int itemFlags, int statusFlags, @NonNull RectFloat itemRect) {
        context.lastItemData.id = itemID;
        context.lastItemData.itemFlags = itemFlags;
        context.lastItemData.statusFlags = statusFlags;
        context.lastItemData.rect.set(itemRect);
        context.lastItemData.navRect.set(itemRect);
    }

    /**
     * Mark the item as edited, so isItemEdited()/isItemDeactivatedAfterEdit() work.
     *
     * @param id The item ID.
     */
    public static void markItemEdited(int id) {
        context.lastItemData.statusFlags |= ItemStatusFlags.EDITED_INTERNAL;
        if ((context.lastItemData.itemFlags & ItemFlags.INTERNAL_NO_MARK_EDITED) != 0) {
            return;
        }
        context.lastItemData.statusFlags |= ItemStatusFlags.EDITED;
        if (context.activeID == id || context.activeID == 0) {
            context.anyIDHasBeenEditedThisFrame = true;
            context.activeIDHasBeenEditedThisFrame = true;
            context.activeIDHasBeenEditedBefore = true;
        }
        if (context.deactivatedItemData.id == id) {
            context.deactivatedItemData.hasBeenEditedBefore = true;
        }
    }

    /**
     * Set the currently hovered item.
     *
     * @param id The item ID.
     */
    public static void setHoveredID(int id) {
        context.hoveredID = id;
        context.hoveredIDAllowOverlap = false;
        if (id != 0 && context.hoveredIDPreviousFrame != id) {
            context.hoveredIDTimer = 0;
            context.hoveredIDInactiveTimer = 0;
        }
    }

    /**
     * Fetch the currently hovered item ID, or the previous frames if nothing has been hovered yet
     * this frame.
     *
     * @return The hovered ID.
     */
    public static int getHoveredID() {
        return context.hoveredID != 0 ? context.hoveredID : context.hoveredIDPreviousFrame;
    }

    /**
     * Check if the contents of a window can be hovered, which they can't be if a popup or modal is
     * blocking it.
     *
     * @param window The window.
     * @param hoveredFlags The hovered flags.
     * @return True if the window contents can be hovered.
     * @see HoveredFlags
     */
    public static boolean isWindowContentHoverable(@NonNull Window window, int hoveredFlags) {
        // An active popup disables hovering on other windows (apart from its own children)
        if (context.navFocusedWindow != null) {
            Window focusedRootWindow = context.navFocusedWindow.rootWindowDockTree;
            if (focusedRootWindow != null
                    && focusedRootWindow.wasActive
                    && focusedRootWindow != window.rootWindowDockTree) {
                // Modal windows are also popups, so the order of the checks is important
                boolean wantInhibit = false;
                if ((focusedRootWindow.flags & WindowFlags.INTERNAL_MODAL) != 0) {
                    wantInhibit = true;
                } else if ((focusedRootWindow.flags & WindowFlags.INTERNAL_POPUP) != 0
                        && (hoveredFlags & HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP) == 0) {
                    wantInhibit = true;
                }

                // Inhibit hover unless the window is within the stack of our modal/popup
                if (wantInhibit
                        && !isWindowWithinBeginStackOf(window.rootWindow, focusedRootWindow)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Submit an item as hoverable, used when submitting widgets. Returns whether the item was
     * hovered, which differs slightly from isItemHovered().
     *
     * @param bb The bounding box.
     * @param id The item ID, can be 0 for a simple hover test.
     * @param itemFlags The item flags, typically the last item data flags.
     * @return True if the item is hovered.
     */
    public static boolean itemHoverable(@NonNull RectFloat bb, int id, int itemFlags) {
        Window window = context.windowCurrent;

        // Detect ID conflicts. This is done here by comparing against the hovered ID because it is
        // algorithmically cheap, one comparison per item.
        if (id != 0
                && context.hoveredIDPreviousFrame == id
                && (itemFlags & ItemFlags.ALLOW_DUPLICATE_ID) == 0) {
            context.hoveredIDPreviousFrameItemCount++;
            if (context.debugDrawIdConflictsID == id) {
                window.drawList.addRect(
                        bb.getLeft() - 1,
                        bb.getTop() - 1,
                        bb.getRight() + 1,
                        bb.getBottom() + 1,
                        Color.rgba(255, 0, 0, 255),
                        0.0f,
                        DrawFlags.NONE,
                        2.0f);
            }
        }

        if (context.windowHovered != window) {
            return false;
        }
        if (!isMouseHoveringRect(bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), true)) {
            return false;
        }

        if (context.hoveredID != 0 && context.hoveredID != id && !context.hoveredIDAllowOverlap) {
            return false;
        }
        if (context.activeID != 0
                && context.activeID != id
                && !context.activeIDAllowOverlap
                && !context.activeIDFromShortcut) {
            return false;
        }

        // We are done with rectangle culling so we can perform heavier checks now
        if ((itemFlags & ItemFlags.INTERNAL_NO_WINDOW_HOVERABLE_CHECK) == 0
                && !isWindowContentHoverable(window, HoveredFlags.NONE)) {
            context.hoveredIDDisabled = true;
            return false;
        }

        // We exceptionally allow this function to be called with id == 0 for a simple hover test
        if (id != 0) {
            // Drag sources don't report as hovered
            if (context.dragDropActive
                    && context.dragDropPayload.sourceID == id
                    && (context.dragDropSourceFlags & DragDropFlags.SOURCE_NO_DISABLE_HOVER) == 0) {
                return false;
            }

            setHoveredID(id);

            // AllowOverlap mode requires previous frame hovered ID to be null or to match
            if ((itemFlags & ItemFlags.ALLOW_OVERLAP) != 0) {
                context.hoveredIDAllowOverlap = true;
                if (context.hoveredIDPreviousFrame != id) {
                    return false;
                }
            }

            // Display the shortcut (only works with the mouse)
            if (id == context.lastItemData.id
                    && (context.lastItemData.statusFlags & ItemStatusFlags.HAS_SHORTCUT) != 0
                    && context.activeID != id
                    && IkGuiImplUtils.isItemHovered(
                            HoveredFlags.FOR_TOOLTIP | HoveredFlags.DELAY_NORMAL)) {
                IkGuiImplPopups.setTooltip(KeyChord.getName(context.lastItemData.shortcut));
            }
        }

        // When disabled we'll return false but still set the hovered ID
        if ((itemFlags & ItemFlags.DISABLED) != 0) {
            // Release active ID if turning disabled
            if (context.activeID == id && id != 0) {
                clearActiveID();
            }
            context.hoveredIDDisabled = true;
            return false;
        }

        return true;
    }

    /**
     * Check if the mouse is hovering over a rectangle.
     *
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param clip Whether to clip the rectangle with the current window clip rect.
     * @return True if the mouse is within the rectangle.
     */
    public static boolean isMouseHoveringRect(
            float minX, float minY, float maxX, float maxY, boolean clip) {
        float left = minX;
        float top = minY;
        float right = maxX;
        float bottom = maxY;
        if (clip && context.windowCurrent != null) {
            RectFloat clipRect = context.windowCurrent.rectCurrentClip;
            left = Math.max(left, clipRect.getLeft());
            top = Math.max(top, clipRect.getTop());
            right = Math.min(right, clipRect.getRight());
            bottom = Math.min(bottom, clipRect.getBottom());
        }

        // Expand for touch input
        final Vector2f padding = context.style.variable.touchExtraPadding;
        final Vector2f mouse = context.io.mousePosition;
        return mouse.x >= left - padding.x
                && mouse.y >= top - padding.y
                && mouse.x < right + padding.x
                && mouse.y < bottom + padding.y;
    }

    /**
     * Calculate how many times a repeating key/button would have triggered between two times.
     *
     * @param t0 The previous time, in milliseconds.
     * @param t1 The current time, in milliseconds.
     * @param repeatDelay The delay before repeating starts, in milliseconds.
     * @param repeatRate The rate of repeating, in milliseconds.
     * @return The number of repeats that happened between the times.
     */
    public static int calcTypematicRepeatAmount(
            long t0, long t1, long repeatDelay, long repeatRate) {
        if (t1 == 0) {
            return 1;
        }
        if (t0 >= t1) {
            return 0;
        }
        if (repeatRate <= 0) {
            return (t0 < repeatDelay && t1 >= repeatDelay) ? 1 : 0;
        }
        final long countT0 = t0 < repeatDelay ? -1 : (t0 - repeatDelay) / repeatRate;
        final long countT1 = t1 < repeatDelay ? -1 : (t1 - repeatDelay) / repeatRate;
        return (int) (countT1 - countT0);
    }

    /**
     * Check if a mouse button was clicked this frame, if the button is available to the owner.
     * Mouse buttons are keys (Key.MOUSE_LEFT etc.), so they use key ownership.
     *
     * @param button The mouse button.
     * @param inputFlags InputFlags.REPEAT to also return true while the button repeats.
     * @param ownerID The owner ID, KEY_OWNER_ANY to accept any owner, or KEY_OWNER_NO_OWNER to
     *     require that nothing owns the button.
     * @return True if the button was clicked.
     * @see KeyRoutingData#KEY_OWNER_ANY
     * @see KeyRoutingData#KEY_OWNER_NO_OWNER
     */
    public static boolean isMouseClicked(@NonNull MouseButton button, int inputFlags, int ownerID) {
        return IkGuiImplUtils.isMouseClicked(button, inputFlags, ownerID);
    }

    /**
     * Check if a mouse button was double-clicked this frame, if the button is available to the
     * owner.
     *
     * @param button The mouse button.
     * @param ownerID The owner ID, or KEY_OWNER_ANY to accept any owner.
     * @return True if the button was double-clicked.
     */
    public static boolean isMouseDoubleClicked(@NonNull MouseButton button, int ownerID) {
        return IkGuiImplUtils.isMouseDoubleClicked(button, ownerID);
    }

    /**
     * Check if a mouse button is held down, if the button is available to the owner.
     *
     * @param button The mouse button.
     * @param ownerID The owner ID, or KEY_OWNER_ANY to accept any owner.
     * @return True if the button is down.
     */
    public static boolean isMouseDown(@NonNull MouseButton button, int ownerID) {
        return IkGuiImplUtils.isMouseDown(button, ownerID);
    }

    /**
     * Check if a mouse button was released this frame, if the button is available to the owner.
     *
     * @param button The mouse button.
     * @param ownerID The owner ID, KEY_OWNER_ANY to accept any owner, or KEY_OWNER_NO_OWNER to
     *     require that nothing owns the button.
     * @return True if the button was released.
     */
    public static boolean isMouseReleased(@NonNull MouseButton button, int ownerID) {
        return IkGuiImplUtils.isMouseReleased(button, ownerID);
    }

    /**
     * Fetch the owner of a key, which includes the mouse keys.
     *
     * @param key The key.
     * @return The owner ID, or KEY_OWNER_NO_OWNER.
     */
    public static int getKeyOwner(@NonNull Key key) {
        return IkGuiImplKeys.getKeyOwner(key);
    }

    /**
     * Claim ownership of a key, which includes the mouse keys. Ownership is automatically released
     * on the frame after the key is released.
     *
     * @param key The key.
     * @param ownerID The owner ID, KEY_OWNER_NO_OWNER to clear the owner.
     * @param inputFlags INTERNAL_LOCK_THIS_FRAME or INTERNAL_LOCK_UNTIL_RELEASE to lock the key
     *     away from code that doesn't check ownership, or NONE.
     */
    public static void setKeyOwner(@NonNull Key key, int ownerID, int inputFlags) {
        IkGuiImplKeys.setKeyOwner(key, ownerID, inputFlags);
    }

    /**
     * Check if a key is available to an owner.
     *
     * @param key The key.
     * @param ownerID The owner ID, KEY_OWNER_ANY to only check for locks, or KEY_OWNER_NO_OWNER to
     *     require that nothing owns the key.
     * @return True if the key is available to the owner.
     */
    public static boolean testKeyOwner(@NonNull Key key, int ownerID) {
        return IkGuiImplKeys.testKeyOwner(key, ownerID);
    }

    /**
     * Check if the mouse has been dragged further than a threshold while held.
     *
     * @param button The button.
     * @param lockThreshold The threshold in pixels, or negative to use the IO default.
     * @return True if the mouse has dragged past the threshold.
     */
    public static boolean isMouseDragPastThreshold(
            @NonNull MouseButton button, float lockThreshold) {
        if (lockThreshold < 0.0f) {
            lockThreshold = context.io.mouseDragThreshold;
        }
        return context.io.mouseDragMaxDistanceSquare[button.index] >= lockThreshold * lockThreshold;
    }

    /**
     * Process button behavior for a widget. This handles hovering, clicking and holding the mouse.
     *
     * @param bb The bounding box of the button.
     * @param id The ID of the button.
     * @param outHovered Set to whether the button is hovered, may be null.
     * @param outHeld Set to whether the button is held down, may be null.
     * @param buttonFlags Flags for the button behavior.
     * @return True if the button was pressed.
     * @see ButtonFlags
     */
    public static boolean buttonBehavior(
            @NonNull RectFloat bb,
            int id,
            IkBoolean outHovered,
            IkBoolean outHeld,
            int buttonFlags) {
        Window window = getCurrentWindow();

        // Default behavior inherited from item flags
        int itemFlags =
                context.lastItemData.id == id
                        ? context.lastItemData.itemFlags
                        : context.currentItemFlags;
        if ((buttonFlags & ButtonFlags.ALLOW_OVERLAP) != 0) {
            itemFlags |= ItemFlags.ALLOW_OVERLAP;
        }
        if ((itemFlags & ItemFlags.NO_FOCUS) != 0) {
            buttonFlags |= ButtonFlags.INTERNAL_NO_FOCUS | ButtonFlags.INTERNAL_NO_NAV_FOCUS;
        }

        // Default only reacts to left mouse button
        if ((buttonFlags & ButtonFlags.MOUSE_BUTTON_MASK) == 0) {
            buttonFlags |= ButtonFlags.MOUSE_BUTTON_LEFT;
        }

        // Default behavior requires click + release inside bounding box
        if ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_MASK) == 0) {
            buttonFlags |=
                    (itemFlags & ItemFlags.BUTTON_REPEAT) != 0
                            ? ButtonFlags.INTERNAL_PRESSED_ON_CLICK
                            : ButtonFlags.INTERNAL_PRESSED_ON_DEFAULT;
        }

        final Window backupHoveredWindow = context.windowHovered;
        final boolean flattenHoveredChildren =
                (buttonFlags & ButtonFlags.INTERNAL_FLATTEN_CHILDREN) != 0
                        && context.windowHovered != null
                        && context.windowHovered.rootWindowDockTree == window.rootWindowDockTree;
        if (flattenHoveredChildren) {
            context.windowHovered = window;
        }

        // Alternate test engine registration spot, for when the caller didn't use itemAdd()
        if (context.lastItemData.id != id) {
            testEngineItemAdd(id, bb, null);
        }

        boolean pressed = false;
        boolean hovered = itemHoverable(bb, id, itemFlags);

        // Special mode for drag and drop used by openables (tree nodes, tabs, etc.) where holding
        // the button pressed for a long time while dragging a payload item triggers the button
        if (context.dragDropActive) {
            if ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_DRAG_DROP_HOLD) != 0
                    && (context.dragDropSourceFlags & DragDropFlags.SOURCE_NO_HOLD_TO_OPEN_OTHERS)
                            == 0
                    && IkGuiImplUtils.isItemHovered(
                            HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM)) {
                hovered = true;
                setHoveredID(id);
                if (context.hoveredIDTimer - context.io.deltaTime
                                <= IkGuiImplDragDrop.DRAG_DROP_HOLD_TO_OPEN_TIMER
                        && context.hoveredIDTimer
                                >= IkGuiImplDragDrop.DRAG_DROP_HOLD_TO_OPEN_TIMER) {
                    pressed = true;
                    context.dragDropHoldJustPressedID = id;
                    focusWindow(window, WindowFocusRequestFlags.NONE);
                }
            }
            if (context.dragDropAcceptIDPrev == id
                    && (context.dragDropAcceptFlagsPrev & DragDropFlags.ACCEPT_DRAW_AS_HOVERED)
                            != 0) {
                hovered = true;
            }
        }

        if (flattenHoveredChildren) {
            context.windowHovered = backupHoveredWindow;
        }

        // Mouse handling
        final int testOwnerID =
                (buttonFlags & ButtonFlags.INTERNAL_NO_TEST_KEY_OWNER) != 0
                        ? KeyRoutingData.KEY_OWNER_ANY
                        : id;
        if (hovered) {
            // Poll mouse buttons. The clicked button is generally carried into
            // activeIDMouseButton when setting the active ID.
            MouseButton mouseButtonClicked = MouseButton.NONE;
            MouseButton mouseButtonReleased = MouseButton.NONE;
            final MouseButton[] buttons = {MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE};
            for (int i = 0; i < buttons.length; ++i) {
                if ((buttonFlags & (ButtonFlags.MOUSE_BUTTON_LEFT << i)) == 0) {
                    continue;
                }
                if (mouseButtonClicked == MouseButton.NONE
                        && IkGuiImplUtils.isMouseClicked(
                                buttons[i], InputFlags.NONE, testOwnerID)) {
                    mouseButtonClicked = buttons[i];
                }
                if (mouseButtonReleased == MouseButton.NONE
                        && IkGuiImplUtils.isMouseReleased(buttons[i], testOwnerID)) {
                    mouseButtonReleased = buttons[i];
                }
            }

            // Process initial action
            final boolean modsOk =
                    (buttonFlags & ButtonFlags.INTERNAL_NO_KEY_MODS_ALLOWED) == 0
                            || (!context.io.keyCtrl && !context.io.keyShift && !context.io.keyAlt);
            if (modsOk) {
                if (mouseButtonClicked != MouseButton.NONE && context.activeID != id) {
                    if ((buttonFlags & ButtonFlags.INTERNAL_NO_SET_KEY_OWNER) == 0) {
                        IkGuiImplKeys.setKeyOwner(
                                Key.fromMouseButton(mouseButtonClicked), id, InputFlags.NONE);
                    }
                    if ((buttonFlags
                                    & (ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE
                                            | ButtonFlags
                                                    .INTERNAL_PRESSED_ON_CLICK_RELEASE_ANYWHERE))
                            != 0) {
                        setActiveID(id, window);
                        context.activeIDMouseButton = mouseButtonClicked;
                        if ((buttonFlags & ButtonFlags.INTERNAL_NO_NAV_FOCUS) == 0) {
                            IkGuiImplNav.setFocusID(id, window);
                            focusWindow(window, WindowFocusRequestFlags.NONE);
                        } else if ((buttonFlags & ButtonFlags.INTERNAL_NO_FOCUS) == 0) {
                            // Still need to focus and bring to front, but try to avoid losing
                            // the nav ID when navigating a child
                            focusWindow(window, WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
                        }
                    }
                    if ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_CLICK) != 0
                            || ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK) != 0
                                    && context.io.getMouseClickedCount(mouseButtonClicked) == 2)) {
                        pressed = true;
                        if ((buttonFlags & ButtonFlags.INTERNAL_NO_HOLDING_ACTIVE_ID) != 0) {
                            clearActiveID();
                        } else {
                            setActiveID(id, window);
                        }
                        context.activeIDMouseButton = mouseButtonClicked;
                        if ((buttonFlags & ButtonFlags.INTERNAL_NO_NAV_FOCUS) == 0) {
                            IkGuiImplNav.setFocusID(id, window);
                            focusWindow(window, WindowFocusRequestFlags.NONE);
                        } else if ((buttonFlags & ButtonFlags.INTERNAL_NO_FOCUS) == 0) {
                            // Still need to focus and bring to front, but try to avoid losing
                            // the nav ID when navigating a child
                            focusWindow(window, WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
                        }
                    }
                    if ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_RELEASE) != 0) {
                        if ((buttonFlags & ButtonFlags.INTERNAL_NO_HOLDING_ACTIVE_ID) == 0) {
                            setActiveID(id, window);
                        }
                        context.activeIDMouseButton = mouseButtonClicked;
                    }
                }
                if ((buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_RELEASE) != 0
                        && mouseButtonReleased != MouseButton.NONE) {
                    // Repeat mode trumps on release behavior
                    final boolean hasRepeatedAtLeastOnce =
                            (itemFlags & ItemFlags.BUTTON_REPEAT) != 0
                                    && context.io
                                                    .mouseDownDurationPrevious[
                                                    mouseButtonReleased.index]
                                            >= context.io.keyRepeatDelay;
                    if (!hasRepeatedAtLeastOnce) {
                        pressed = true;
                    }
                    if ((buttonFlags & ButtonFlags.INTERNAL_NO_NAV_FOCUS) == 0) {
                        IkGuiImplNav.setFocusID(id, window);
                    }
                    clearActiveID();
                }

                // 'Repeat' mode acts when held regardless of PressedOn flags
                if (context.activeID == id
                        && (itemFlags & ItemFlags.BUTTON_REPEAT) != 0
                        && context.activeIDMouseButton != MouseButton.NONE
                        && context.io.getMouseDownDuration(context.activeIDMouseButton) > 0
                        && IkGuiImplUtils.isMouseClicked(
                                context.activeIDMouseButton, InputFlags.REPEAT, testOwnerID)) {
                    pressed = true;
                }
            }

            if (pressed && context.io.configNavCursorVisibleAuto) {
                context.navCursorVisible = false;
            }
        }

        // Keyboard/gamepad navigation handling. We report navigated and navigation activated
        // items as hovered, but we don't set the hovered ID to not interfere with the mouse.
        if ((itemFlags & ItemFlags.DISABLED) == 0) {
            if (context.navID == id
                    && context.navCursorVisible
                    && context.navHighlightItemUnderNav
                    && (buttonFlags & ButtonFlags.INTERNAL_NO_HOVERED_ON_FOCUS) == 0) {
                hovered = true;
            }
            if (context.navActivateDownID == id) {
                final boolean navActivatedByCode = context.navActivateID == id;
                boolean navActivatedByInputs = context.navActivatePressedID == id;
                if (!navActivatedByInputs && (itemFlags & ItemFlags.BUTTON_REPEAT) != 0) {
                    // Avoid pressing multiple keys from triggering an excessive amount of repeats
                    final long t1 =
                            Math.max(
                                    Math.max(
                                            context.io.getKeyDownDuration(Key.SPACE),
                                            context.io.getKeyDownDuration(Key.ENTER)),
                                    context.io.getKeyDownDuration(
                                            IkGuiImplNav.navGamepadActivateKey()));
                    navActivatedByInputs =
                            calcTypematicRepeatAmount(
                                            t1 - context.io.deltaTime,
                                            t1,
                                            context.io.keyRepeatDelay,
                                            context.io.keyRepeatRate)
                                    > 0;
                }
                if (navActivatedByCode || navActivatedByInputs) {
                    // Set the active ID so it can be queried with isItemActive(), equivalent of
                    // holding the mouse button
                    pressed = true;
                    setActiveID(id, window);
                    context.activeIDSource = context.navInputSource;
                    if ((buttonFlags & ButtonFlags.INTERNAL_NO_NAV_FOCUS) == 0
                            && (context.navActivateFlags & ActivateFlags.FROM_SHORTCUT) == 0) {
                        IkGuiImplNav.setFocusID(id, window);
                    }
                    if ((context.navActivateFlags & ActivateFlags.FROM_SHORTCUT) != 0) {
                        context.activeIDFromShortcut = true;
                    }
                }
            }
        }

        // Process while held
        boolean held = false;
        if (context.activeID == id) {
            if (context.activeIDSource == GuiInputSource.MOUSE) {
                if (context.activeIDIsJustActivated) {
                    context.activeIDClickOffset.set(context.io.mousePosition);
                    context.activeIDClickOffset.sub(bb.getLeft(), bb.getTop());
                }

                final MouseButton mouseButton = context.activeIDMouseButton;
                if (mouseButton == MouseButton.NONE) {
                    // Fallback for the rare situation where the active ID was set elsewhere
                    clearActiveID();
                } else if (IkGuiImplUtils.isMouseDown(mouseButton, testOwnerID)) {
                    held = true;
                } else {
                    final boolean releaseIn =
                            hovered
                                    && (buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE)
                                            != 0;
                    final boolean releaseAnywhere =
                            (buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE_ANYWHERE)
                                    != 0;
                    if ((releaseIn || releaseAnywhere) && !context.dragDropActive) {
                        // Report as pressed when releasing the mouse (the most common path)
                        final boolean isDoubleClickRelease =
                                (buttonFlags & ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK) != 0
                                        && context.io.getMouseReleased(mouseButton)
                                        && context.io.getMouseClickedLastCount(mouseButton) == 2;
                        final boolean isRepeatingAlready =
                                (itemFlags & ItemFlags.BUTTON_REPEAT) != 0
                                        && context.io.mouseDownDurationPrevious[mouseButton.index]
                                                >= context.io.keyRepeatDelay;
                        final boolean isButtonAvailableOrOwned =
                                IkGuiImplKeys.testKeyOwner(
                                        Key.fromMouseButton(mouseButton), testOwnerID);
                        if (!isDoubleClickRelease
                                && !isRepeatingAlready
                                && isButtonAvailableOrOwned) {
                            pressed = true;
                        }
                    }
                    clearActiveID();
                }
                if ((buttonFlags & ButtonFlags.INTERNAL_NO_NAV_FOCUS) == 0
                        && context.io.configNavCursorVisibleAuto) {
                    context.navCursorVisible = false;
                }
            } else if (context.activeIDSource == GuiInputSource.KEYBOARD
                    || context.activeIDSource == GuiInputSource.GAMEPAD) {
                // When activated using navigation, we hold on to the active ID until the
                // activation button is released
                if (context.navActivateDownID == id) {
                    held = true;
                } else {
                    clearActiveID();
                }
            }
            if (pressed) {
                context.activeIDHasBeenPressedBefore = true;
            }
        }

        // Activation highlight (this may be a remote activation)
        if (context.navHighlightActivatedID == id && (itemFlags & ItemFlags.DISABLED) == 0) {
            hovered = true;
        }

        if (outHovered != null) {
            outHovered.set(hovered);
        }
        if (outHeld != null) {
            outHeld.set(held);
        }

        return pressed;
    }

    /**
     * Calculate the full size of an item given the user provided size and defaults. Zero values use
     * the default, negative values align to the right/bottom edge of the available content region.
     *
     * @param size The user provided size, which will be modified to hold the result.
     * @param defaultWidth The default width.
     * @param defaultHeight The default height.
     * @return The size vector, for convenience.
     */
    public static Vector2f calcItemSize(
            @NonNull Vector2f size, float defaultWidth, float defaultHeight) {
        Vector2f available = new Vector2f();
        if (size.x < 0.0f || size.y < 0.0f) {
            IkGuiImplUtils.getContentRegionAvailable(available);
        }

        if (size.x == 0.0f) {
            size.x = defaultWidth;
        } else if (size.x < 0.0f) {
            size.x = Math.max(4.0f, available.x + size.x);
        }

        if (size.y == 0.0f) {
            size.y = defaultHeight;
        } else if (size.y < 0.0f) {
            size.y = Math.max(4.0f, available.y + size.y);
        }

        return size;
    }

    /**
     * Calculate the wrap width for text at a given position.
     *
     * @param posX The screen x position of the start of the text.
     * @param wrapPosX The local wrap position, 0 for the end of the content region, negative for no
     *     wrapping.
     * @return The wrap width, or a negative value if not wrapping.
     */
    public static float calcWrapWidthForPos(float posX, float wrapPosX) {
        if (wrapPosX < 0.0f) {
            return 0.0f;
        }

        Window window = context.windowCurrent;
        if (wrapPosX == 0.0f) {
            // We could decide to setup a default wrapping max point for auto-resizing windows,
            // or have auto-wrap (with unspecified wrapping pos) behave as a content size extending
            // function?
            wrapPosX = window.rectWork.getRight();
        } else if (wrapPosX > 0.0f) {
            // Wrap position is relative to the window
            wrapPosX += window.position.x - window.scrollPosition.x;
        }

        return Math.max(wrapPosX - posX, 1.0f);
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Render a frame (filled rectangle with optional border), as used by buttons and other framed
     * widgets.
     *
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param color The fill color.
     * @param border Whether to render a border, if the style has a frame border size.
     * @param rounding The corner rounding.
     */
    public static void renderFrame(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            boolean border,
            float rounding) {
        Window window = context.windowCurrent;
        window.drawList.addRectFilled(
                minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL);
        final float borderSize = context.style.variable.frameBorderSize;
        if (border && borderSize > 0.0f) {
            window.drawList.addRect(
                    minX + 1,
                    minY + 1,
                    maxX + 1,
                    maxY + 1,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER_SHADOW),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    borderSize);
            window.drawList.addRect(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    borderSize);
        }
    }

    /**
     * Render the border of a frame, if the style has a frame border size.
     *
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param rounding The corner rounding.
     */
    public static void renderFrameBorder(
            float minX, float minY, float maxX, float maxY, float rounding) {
        Window window = context.windowCurrent;
        final float borderSize = context.style.variable.frameBorderSize;
        if (borderSize > 0.0f) {
            window.drawList.addRect(
                    minX + 1,
                    minY + 1,
                    maxX + 1,
                    maxY + 1,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER_SHADOW),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    borderSize);
            window.drawList.addRect(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    borderSize);
        }
    }

    /**
     * Render text, which may contain multiple lines, at the given position in the current window.
     *
     * @param posX The left of the text.
     * @param posY The top of the text.
     * @param text The text.
     * @param hideTextAfterHash Whether to hide anything after "##".
     */
    public static void renderText(
            float posX, float posY, @NonNull String text, boolean hideTextAfterHash) {
        final String displayed = hideTextAfterHash ? Hash.getDisplayedText(text) : text;
        if (displayed.isEmpty()) {
            return;
        }
        renderTextLines(
                context.windowCurrent.drawList,
                posX,
                posY,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                displayed);
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(posY, displayed);
        }
    }

    /**
     * Render text that may contain multiple lines, with a specific color.
     *
     * @param drawList The draw list to render to.
     * @param posX The left of the text.
     * @param posY The top of the text.
     * @param color The color of the text.
     * @param text The text, which may contain newlines.
     */
    public static void renderTextLines(
            @NonNull DrawList drawList, float posX, float posY, int color, @NonNull String text) {
        final float lineHeight = getFontSize();
        int lineStart = 0;
        float y = posY;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            if (lineEnd > lineStart) {
                drawList.addText(
                        context.fontSize, posX, y, color, text.substring(lineStart, lineEnd));
            }
            lineStart = lineEnd + 1;
            y += lineHeight;
        }
    }

    /**
     * Render wrapped text, which may contain multiple lines.
     *
     * @param posX The left of the text.
     * @param posY The top of the text.
     * @param text The text.
     * @param wrapWidth The width to wrap at, or 0 or less for no wrapping.
     */
    public static void renderTextWrapped(
            float posX, float posY, @NonNull String text, float wrapWidth) {
        if (text.isEmpty()) {
            return;
        }
        final String wrapped = wrapWidth > 0 ? wrapText(text, wrapWidth) : text;
        renderTextLines(
                context.windowCurrent.drawList,
                posX,
                posY,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                wrapped);
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(posY, text);
        }
    }

    /**
     * Render text clipped to a rectangle, and aligned within it. The text is not rendered at all if
     * it does not overlap the clip rect.
     *
     * @param minX The left of the layout rectangle.
     * @param minY The top of the layout rectangle.
     * @param maxX The right of the layout rectangle.
     * @param maxY The bottom of the layout rectangle.
     * @param text The text to render.
     * @param textSize The size of the text if already known, may be null.
     * @param alignX Horizontal alignment, 0 for left, 0.5 for centered, 1 for right.
     * @param alignY Vertical alignment, 0 for top, 0.5 for centered, 1 for bottom.
     * @param clipRect The clip rect, may be null to use the layout rectangle.
     */
    public static void renderTextClipped(
            float minX,
            float minY,
            float maxX,
            float maxY,
            @NonNull String text,
            Vector2f textSize,
            float alignX,
            float alignY,
            RectFloat clipRect) {
        final String displayed = Hash.getDisplayedText(text);
        if (displayed.isEmpty()) {
            return;
        }
        renderTextClippedEx(minX, minY, maxX, maxY, displayed, textSize, alignX, alignY, clipRect);
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(minY, displayed);
        }
    }

    /**
     * Render text clipped to a rectangle, and aligned within it, without logging it. The text is
     * not rendered at all if it does not overlap the clip rect.
     *
     * @param minX The left of the layout rectangle.
     * @param minY The top of the layout rectangle.
     * @param maxX The right of the layout rectangle.
     * @param maxY The bottom of the layout rectangle.
     * @param text The text to render, anything after "##" is hidden.
     * @param textSize The size of the text if already known, may be null.
     * @param alignX Horizontal alignment, 0 for left, 0.5 for centered, 1 for right.
     * @param alignY Vertical alignment, 0 for top, 0.5 for centered, 1 for bottom.
     * @param clipRect The clip rect, may be null to use the layout rectangle.
     */
    public static void renderTextClippedEx(
            float minX,
            float minY,
            float maxX,
            float maxY,
            @NonNull String text,
            Vector2f textSize,
            float alignX,
            float alignY,
            RectFloat clipRect) {
        renderTextClippedEx(
                context.windowCurrent.drawList,
                minX,
                minY,
                maxX,
                maxY,
                text,
                textSize,
                alignX,
                alignY,
                clipRect,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT));
    }

    /**
     * Render text clipped to a rectangle into a specific draw list, with a specific color.
     *
     * @param drawList The draw list.
     * @param minX The left of the layout rectangle.
     * @param minY The top of the layout rectangle.
     * @param maxX The right of the layout rectangle.
     * @param maxY The bottom of the layout rectangle.
     * @param text The text, anything after "##" is hidden.
     * @param textSize The size of the text if known, may be null.
     * @param alignX The horizontal alignment, from 0 (left) to 1 (right).
     * @param alignY The vertical alignment, from 0 (top) to 1 (bottom).
     * @param clipRect The clip rectangle, or null to clip to the layout rectangle.
     * @param color The text color.
     */
    public static void renderTextClippedEx(
            @NonNull DrawList drawList,
            float minX,
            float minY,
            float maxX,
            float maxY,
            @NonNull String text,
            Vector2f textSize,
            float alignX,
            float alignY,
            RectFloat clipRect,
            int color) {
        final String displayed = Hash.getDisplayedText(text);
        if (displayed.isEmpty()) {
            return;
        }
        Vector2f size = textSize;
        if (size == null) {
            size = new Vector2f();
            IkGuiImplUtils.calcTextSize(size, displayed, false, -1.0f);
        }

        float posX = minX;
        float posY = minY;
        if (alignX > 0.0f) {
            posX = Math.max(posX, posX + (maxX - posX - size.x) * alignX);
        }
        if (alignY > 0.0f) {
            posY = Math.max(posY, posY + (maxY - posY - size.y) * alignY);
        }

        float clipMinX = clipRect != null ? clipRect.getLeft() : minX;
        float clipMinY = clipRect != null ? clipRect.getTop() : minY;
        float clipMaxX = clipRect != null ? clipRect.getRight() : maxX;
        float clipMaxY = clipRect != null ? clipRect.getBottom() : maxY;

        // Skip text that is entirely clipped
        if (posX + size.x <= clipMinX
                || posY + size.y <= clipMinY
                || posX >= clipMaxX
                || posY >= clipMaxY) {
            return;
        }

        final boolean needClipping =
                posX + size.x >= clipMaxX
                        || posY + size.y >= clipMaxY
                        || posX < clipMinX
                        || posY < clipMinY;
        if (needClipping) {
            drawList.pushClipRect(clipMinX, clipMinY, clipMaxX, clipMaxY, true);
        }
        renderTextLines(drawList, posX, posY, color, displayed);
        if (needClipping) {
            drawList.popClipRect();
        }
    }

    /**
     * Render an arrow, as used by collapsing headers, tree nodes, and arrow buttons.
     *
     * @param drawList The draw list.
     * @param posX The left of the arrow box.
     * @param posY The top of the arrow box.
     * @param color The color.
     * @param direction The direction the arrow points.
     * @param scale The scale relative to the font size.
     */
    public static void renderArrow(
            @NonNull DrawList drawList,
            float posX,
            float posY,
            int color,
            @NonNull Direction direction,
            float scale) {
        final float h = getFontSize();
        float r = h * 0.40f * scale;
        final float centerX = posX + h * 0.50f;
        final float centerY = posY + h * 0.50f * scale;

        float ax;
        float ay;
        float bx;
        float by;
        float cx;
        float cy;
        switch (direction) {
            case UP, DOWN -> {
                if (direction == Direction.UP) {
                    r = -r;
                }
                ax = 0.000f * r;
                ay = 0.750f * r;
                bx = -0.866f * r;
                by = -0.750f * r;
                cx = 0.866f * r;
                cy = -0.750f * r;
            }
            case LEFT, RIGHT -> {
                if (direction == Direction.LEFT) {
                    r = -r;
                }
                ax = 0.750f * r;
                ay = 0.000f * r;
                bx = -0.750f * r;
                by = 0.866f * r;
                cx = -0.750f * r;
                cy = -0.866f * r;
            }
            default -> {
                return;
            }
        }
        drawList.addTriangleFilled(
                centerX + ax,
                centerY + ay,
                centerX + bx,
                centerY + by,
                centerX + cx,
                centerY + cy,
                color);
    }

    /**
     * Render a bullet point.
     *
     * @param drawList The draw list.
     * @param centerX The center x position.
     * @param centerY The center y position.
     * @param color The color.
     */
    public static void renderBullet(
            @NonNull DrawList drawList, float centerX, float centerY, int color) {
        drawList.addCircleFilled(centerX, centerY, getFontSize() * 0.20f, color);
    }

    /**
     * Render a check mark, as used by checkboxes and menu items.
     *
     * @param drawList The draw list.
     * @param posX The left of the check mark box.
     * @param posY The top of the check mark box.
     * @param color The color.
     * @param size The size of the check mark box.
     */
    public static void renderCheckMark(
            @NonNull DrawList drawList, float posX, float posY, int color, float size) {
        final float thickness = Math.max(size / 5.0f, 1.0f);
        size -= thickness * 0.5f;
        posX += thickness * 0.25f;
        posY += thickness * 0.25f;

        final float third = size / 3.0f;
        final float bx = posX + third;
        final float by = posY + size - third * 0.5f;
        drawList.pathLineTo(bx - third, by - third);
        drawList.pathLineTo(bx, by);
        drawList.pathLineTo(bx + third * 2.0f, by - third * 2.0f);
        drawList.pathStroke(color, false, thickness);
    }

    /**
     * Render a triangle pointing at a position.
     *
     * @param drawList The draw list.
     * @param posX The x coordinate of the point of the arrow.
     * @param posY The y coordinate of the point of the arrow.
     * @param halfSizeX Half the width of the arrow.
     * @param halfSizeY Half the height of the arrow.
     * @param direction The direction the arrow points.
     * @param color The color.
     */
    public static void renderArrowPointingAt(
            @NonNull DrawList drawList,
            float posX,
            float posY,
            float halfSizeX,
            float halfSizeY,
            @NonNull Direction direction,
            int color) {
        switch (direction) {
            case LEFT ->
                    drawList.addTriangleFilled(
                            posX + halfSizeX,
                            posY - halfSizeY,
                            posX + halfSizeX,
                            posY + halfSizeY,
                            posX,
                            posY,
                            color);
            case RIGHT ->
                    drawList.addTriangleFilled(
                            posX - halfSizeX,
                            posY + halfSizeY,
                            posX - halfSizeX,
                            posY - halfSizeY,
                            posX,
                            posY,
                            color);
            case UP ->
                    drawList.addTriangleFilled(
                            posX + halfSizeX,
                            posY + halfSizeY,
                            posX - halfSizeX,
                            posY + halfSizeY,
                            posX,
                            posY,
                            color);
            case DOWN ->
                    drawList.addTriangleFilled(
                            posX - halfSizeX,
                            posY - halfSizeY,
                            posX + halfSizeX,
                            posY - halfSizeY,
                            posX,
                            posY,
                            color);
            default -> {
                // Nothing to draw
            }
        }
    }

    /**
     * Render the icon of the window menu button of dock nodes, a bar over a down arrow.
     *
     * @param drawList The draw list.
     * @param posX The left of the icon.
     * @param posY The top of the icon.
     * @param size The size of the icon.
     * @param color The color.
     */
    public static void renderArrowDockMenu(
            @NonNull DrawList drawList, float posX, float posY, float size, int color) {
        drawList.addRectFilled(
                posX + size * 0.20f,
                posY + size * 0.15f,
                posX + size * 0.80f,
                posY + size * 0.30f,
                color);
        renderArrowPointingAt(
                drawList,
                posX + size * 0.50f,
                posY + size * 0.85f,
                size * 0.30f,
                size * 0.40f,
                Direction.DOWN,
                color);
    }

    /**
     * Fill the area between an outer rectangle and an inner rectangle (the hole).
     *
     * @param drawList The draw list.
     * @param outer The outer rectangle.
     * @param inner The inner rectangle, which is not filled.
     * @param color The color.
     * @param rounding The rounding of the outer corners.
     */
    public static void renderRectFilledWithHole(
            @NonNull DrawList drawList,
            @NonNull RectFloat outer,
            @NonNull RectFloat inner,
            int color,
            float rounding) {
        final boolean fillLeft = inner.getLeft() > outer.getLeft();
        final boolean fillRight = inner.getRight() < outer.getRight();
        final boolean fillUp = inner.getTop() > outer.getTop();
        final boolean fillDown = inner.getBottom() < outer.getBottom();
        if (fillLeft) {
            drawList.addRectFilled(
                    outer.getLeft(),
                    inner.getTop(),
                    inner.getLeft(),
                    inner.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_NONE
                            | (fillUp ? 0 : DrawFlags.ROUND_CORNERS_TOP_LEFT)
                            | (fillDown ? 0 : DrawFlags.ROUND_CORNERS_BOTTOM_LEFT));
        }
        if (fillRight) {
            drawList.addRectFilled(
                    inner.getRight(),
                    inner.getTop(),
                    outer.getRight(),
                    inner.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_NONE
                            | (fillUp ? 0 : DrawFlags.ROUND_CORNERS_TOP_RIGHT)
                            | (fillDown ? 0 : DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT));
        }
        if (fillUp) {
            drawList.addRectFilled(
                    inner.getLeft(),
                    outer.getTop(),
                    inner.getRight(),
                    inner.getTop(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_NONE
                            | (fillLeft ? 0 : DrawFlags.ROUND_CORNERS_TOP_LEFT)
                            | (fillRight ? 0 : DrawFlags.ROUND_CORNERS_TOP_RIGHT));
        }
        if (fillDown) {
            drawList.addRectFilled(
                    inner.getLeft(),
                    inner.getBottom(),
                    inner.getRight(),
                    outer.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_NONE
                            | (fillLeft ? 0 : DrawFlags.ROUND_CORNERS_BOTTOM_LEFT)
                            | (fillRight ? 0 : DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT));
        }
        if (fillLeft && fillUp) {
            drawList.addRectFilled(
                    outer.getLeft(),
                    outer.getTop(),
                    inner.getLeft(),
                    inner.getTop(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_TOP_LEFT);
        }
        if (fillRight && fillUp) {
            drawList.addRectFilled(
                    inner.getRight(),
                    outer.getTop(),
                    outer.getRight(),
                    inner.getTop(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_TOP_RIGHT);
        }
        if (fillLeft && fillDown) {
            drawList.addRectFilled(
                    outer.getLeft(),
                    inner.getBottom(),
                    inner.getLeft(),
                    outer.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_BOTTOM_LEFT);
        }
        if (fillRight && fillDown) {
            drawList.addRectFilled(
                    inner.getRight(),
                    inner.getBottom(),
                    outer.getRight(),
                    outer.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT);
        }
    }

    /**
     * Calculate which corners of a rectangle should be rounded to match the corners of an outer
     * rectangle it is inside of.
     *
     * @param inner The inner rectangle.
     * @param outer The outer rectangle.
     * @param threshold How close the edges need to be to count as touching.
     * @return The rounding corner draw flags.
     * @see DrawFlags
     */
    public static int calcRoundingFlagsForRectInRect(
            @NonNull RectFloat inner, @NonNull RectFloat outer, float threshold) {
        final boolean roundLeft = inner.getLeft() <= outer.getLeft() + threshold;
        final boolean roundRight = inner.getRight() >= outer.getRight() - threshold;
        final boolean roundTop = inner.getTop() <= outer.getTop() + threshold;
        final boolean roundBottom = inner.getBottom() >= outer.getBottom() - threshold;
        return DrawFlags.ROUND_CORNERS_NONE
                | ((roundTop && roundLeft) ? DrawFlags.ROUND_CORNERS_TOP_LEFT : 0)
                | ((roundTop && roundRight) ? DrawFlags.ROUND_CORNERS_TOP_RIGHT : 0)
                | ((roundBottom && roundLeft) ? DrawFlags.ROUND_CORNERS_BOTTOM_LEFT : 0)
                | ((roundBottom && roundRight) ? DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT : 0);
    }

    // ---------------------------------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Insert line breaks into text so that no line is wider than the wrap width, breaking at spaces
     * where possible.
     *
     * @param text The text to wrap.
     * @param wrapWidth The maximum line width in pixels.
     * @return The wrapped text.
     */
    public static String wrapText(@NonNull String text, float wrapWidth) {
        return DrawList.wrapText(context.fontSize, text, wrapWidth);
    }

    // ---------------------------------------------------------------------------------------------
    // Drag and drop
    // ---------------------------------------------------------------------------------------------

    /**
     * Begin a drag and drop target over an arbitrary rectangle, rather than the last item. If this
     * returns true, call acceptDragDropPayload() and then endDragDropTarget().
     *
     * @param bb The bounding box of the target, in screen space.
     * @param id The ID of the target, must not be 0.
     * @return True if a payload is being dragged over the target.
     */
    public static boolean beginDragDropTargetCustom(@NonNull RectFloat bb, int id) {
        return IkGuiImplDragDrop.beginDragDropTargetCustom(bb, id);
    }

    /**
     * Begin a drag and drop target covering a viewport. The hover test is left to the caller,
     * typically by checking that no window is hovered. If this returns true, call
     * acceptDragDropPayload() and then endDragDropTarget().
     *
     * @param viewport The viewport.
     * @param bb The bounding box of the target in screen space, or null to use the viewport work
     *     area.
     * @return True if a payload is being dragged over the viewport.
     */
    public static boolean beginDragDropTargetViewport(@NonNull Viewport viewport, RectFloat bb) {
        return IkGuiImplDragDrop.beginDragDropTargetViewport(viewport, bb);
    }

    /** Cancel any drag and drop operation, and clear the payload. */
    public static void clearDragDrop() {
        IkGuiImplDragDrop.clearDragDrop();
    }

    /**
     * Whether a drag and drop operation is in progress.
     *
     * @return True if drag and drop is active.
     */
    public static boolean isDragDropActive() {
        return IkGuiImplDragDrop.isDragDropActive();
    }

    /**
     * Whether a target accepted the payload last frame.
     *
     * @return True if drag and drop is active and the payload is being accepted by a target.
     */
    public static boolean isDragDropPayloadBeingAccepted() {
        return IkGuiImplDragDrop.isDragDropPayloadBeingAccepted();
    }

    /**
     * Render the default drop target highlight for an item in the current window.
     *
     * @param bb The bounding box of the target.
     */
    public static void renderDragDropTargetRectForItem(@NonNull RectFloat bb) {
        IkGuiImplDragDrop.renderDragDropTargetRectForItem(bb);
    }

    /**
     * Render the default drop target highlight for a full viewport target.
     *
     * @param viewportID The viewport ID.
     * @param bb The bounding box of the target.
     */
    public static void renderDragDropTargetRectForViewport(int viewportID, @NonNull RectFloat bb) {
        IkGuiImplDragDrop.renderDragDropTargetRectForViewport(viewportID, bb);
    }

    /**
     * Render a drop target highlight rectangle.
     *
     * @param drawList The draw list to render to.
     * @param bb The rectangle.
     * @param rounding The corner rounding.
     */
    public static void renderDragDropTargetRectEx(
            @NonNull DrawList drawList, @NonNull RectFloat bb, float rounding) {
        IkGuiImplDragDrop.renderDragDropTargetRectEx(drawList, bb, rounding);
    }

    // ---------------------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------------------

    /**
     * Register a handler for a type of .ini entry. There can only be one handler per type name.
     *
     * @param handler The handler.
     */
    public static void addSettingsHandler(@NonNull SettingsHandler handler) {
        IkGuiImplConfig.addSettingsHandler(handler);
    }

    /**
     * Clean up or patch settings, e.g. discarding entries that have not been used recently.
     *
     * @param args What to clean up.
     */
    public static void cleanupIniSettings(@NonNull SettingsCleanupArgs args) {
        IkGuiImplConfig.cleanupIniSettings(args);
    }

    /** Clear all settings (windows, tables, docking, etc.). */
    public static void clearIniSettings() {
        IkGuiImplConfig.clearIniSettings();
    }

    /**
     * Clear the settings for a window, reverting it to its initial state. This includes enabling
     * the FIRST_USE_EVER and ONCE conditions again.
     *
     * @param name The window name.
     */
    public static void clearWindowSettings(@NonNull String name) {
        IkGuiImplConfig.clearWindowSettings(name);
    }

    /**
     * Create settings for a window, and add them to the context.
     *
     * @param name The window name.
     * @return The new settings.
     */
    public static WindowSettings createNewWindowSettings(@NonNull String name) {
        return IkGuiImplConfig.createNewWindowSettings(name);
    }

    /**
     * Find a settings handler by type name.
     *
     * @param typeName The type name.
     * @return The handler, or null if there is none for that type.
     */
    public static SettingsHandler findSettingsHandler(@NonNull String typeName) {
        return IkGuiImplConfig.findSettingsHandler(typeName);
    }

    /**
     * Find window settings by window ID.
     *
     * @param id The window ID.
     * @return The settings, or null if there are none.
     */
    public static WindowSettings findWindowSettingsByID(int id) {
        return IkGuiImplConfig.findWindowSettingsByID(id);
    }

    /**
     * Find the settings for a window.
     *
     * @param window The window.
     * @return The settings, or null if there are none.
     */
    public static WindowSettings findWindowSettingsByWindow(@NonNull Window window) {
        return IkGuiImplConfig.findWindowSettingsByWindow(window);
    }

    /**
     * Remove a settings handler.
     *
     * @param typeName The type name of the handler.
     */
    public static void removeSettingsHandler(@NonNull String typeName) {
        IkGuiImplConfig.removeSettingsHandler(typeName);
    }

    // ---------------------------------------------------------------------------------------------
    // Logging
    // ---------------------------------------------------------------------------------------------

    /**
     * Start logging/capturing text output.
     *
     * @param logFlags Exactly one output type flag.
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     * @see LogFlags
     */
    public static void logBegin(int logFlags, int autoOpenDepth) {
        IkGuiImplLogging.logBegin(logFlags, autoOpenDepth);
    }

    /**
     * Log text that was rendered at a position, splitting it into lines and indenting based on the
     * tree depth.
     *
     * @param refPosY The y position of the text, used to decide on new lines, or NaN if there is no
     *     position.
     * @param text The text.
     */
    public static void logRenderedText(float refPosY, @NonNull String text) {
        IkGuiImplLogging.logRenderedText(refPosY, text);
    }

    /**
     * Text with a horizontal line on either side. The text is aligned within the separator using
     * the separator text alignment style, and space can be reserved after the text to submit
     * another item with sameLine().
     *
     * @param id The ID of the item, which may be 0.
     * @param label The label, anything after "##" is hidden.
     * @param extraWidth Extra width to reserve after the text.
     */
    public static void separatorTextEx(int id, @NonNull String label, float extraWidth) {
        IkGuiImplLayout.separatorTextEx(id, label, extraWidth);
    }

    /**
     * Set the text to log before and after the next rendered text, e.g. "[" and "]" for buttons.
     *
     * @param prefix The prefix, or null for none.
     * @param suffix The suffix, or null for none.
     */
    public static void logSetNextTextDecoration(String prefix, String suffix) {
        IkGuiImplLogging.logSetNextTextDecoration(prefix, suffix);
    }

    /**
     * Start logging to the context log buffer. Unlike ImGui, the buffer keeps its contents after
     * logFinish(), until logging starts again.
     *
     * @param autoOpenDepth Tree nodes are opened up to this depth, or the default depth if
     *     negative.
     */
    public static void logToBuffer(int autoOpenDepth) {
        IkGuiImplLogging.logToBuffer(autoOpenDepth);
    }

    // ---------------------------------------------------------------------------------------------
    // List clipper
    // ---------------------------------------------------------------------------------------------

    /**
     * Implementation of {@link ListClipper#begin(int, float)}.
     *
     * @param clipper The clipper.
     * @param itemsCount The number of items.
     * @param itemsHeight The item height, or -1 to calculate it.
     */
    public static void listClipperBegin(
            @NonNull ListClipper clipper, int itemsCount, float itemsHeight) {
        IkGuiImplListClipper.begin(clipper, itemsCount, itemsHeight);
    }

    /**
     * Implementation of {@link ListClipper#end()}.
     *
     * @param clipper The clipper.
     */
    public static void listClipperEnd(@NonNull ListClipper clipper) {
        IkGuiImplListClipper.end(clipper);
    }

    /**
     * Implementation of {@link ListClipper#includeItemsByIndex(int, int)}.
     *
     * @param clipper The clipper.
     * @param itemBegin The first item, inclusive.
     * @param itemEnd The last item, exclusive.
     */
    public static void listClipperIncludeItemsByIndex(
            @NonNull ListClipper clipper, int itemBegin, int itemEnd) {
        IkGuiImplListClipper.includeItemsByIndex(clipper, itemBegin, itemEnd);
    }

    /**
     * Implementation of {@link ListClipper#seekCursorForItem(int)}.
     *
     * @param clipper The clipper.
     * @param itemIndex The item index.
     */
    public static void listClipperSeekCursorForItem(@NonNull ListClipper clipper, int itemIndex) {
        IkGuiImplListClipper.seekCursorForItem(clipper, itemIndex);
    }

    /**
     * Implementation of {@link ListClipper#step()}.
     *
     * @param clipper The clipper.
     * @return True if there are items to display.
     */
    public static boolean listClipperStep(@NonNull ListClipper clipper) {
        return IkGuiImplListClipper.step(clipper);
    }

    // ---------------------------------------------------------------------------------------------
    // Tables
    // ---------------------------------------------------------------------------------------------

    /**
     * Begin a table with an explicit ID.
     *
     * @param name The table name, used for the child window when scrolling.
     * @param id The table ID.
     * @param columnsCount The number of columns.
     * @param tableFlags Flags for the table.
     * @param outerWidth The outer width.
     * @param outerHeight The outer height.
     * @param innerWidth The inner width, only used with TableFlags.SCROLL_X.
     * @return True if the table is visible, only call endTable() if this returns true.
     */
    public static boolean beginTableEx(
            @NonNull String name,
            int id,
            int columnsCount,
            int tableFlags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        return IkGuiImplTables.beginTableEx(
                name, id, columnsCount, tableFlags, outerWidth, outerHeight, innerWidth);
    }

    /**
     * The table currently being submitted.
     *
     * @return The current table, or null if not in a table.
     */
    public static Table getCurrentTable() {
        return context.currentTable;
    }

    /**
     * Find a table by ID.
     *
     * @param id The table ID.
     * @return The table, or null if there is none with that ID.
     */
    public static Table tableFindByID(int id) {
        return IkGuiImplTables.tableFindByID(id);
    }

    /**
     * Submit a row of angled headers with custom data.
     *
     * @param rowID The row ID.
     * @param angle The angle in radians, from -pi/2 to pi/2.
     * @param maxLabelWidth The maximum label width, or 0 to calculate it automatically.
     * @param data The headers to display, left to right.
     */
    public static void tableAngledHeadersRowEx(
            int rowID, float angle, float maxLabelWidth, @NonNull TableHeaderData[] data) {
        IkGuiImplTableHeaders.tableAngledHeadersRowEx(rowID, angle, maxLabelWidth, data);
    }

    /**
     * Begin the table context menu popup, if it is open. Use this to append to or replace the
     * default context menu.
     *
     * @param table The table.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean tableBeginContextMenuPopup(@NonNull Table table) {
        return IkGuiImplTableHeaders.tableBeginContextMenuPopup(table);
    }

    /**
     * Output the default table context menu into the current window.
     *
     * @param table The table.
     * @param flagsForSectionToDisplay Which sections to display, using table flags.
     */
    public static void tableDrawDefaultContextMenu(
            @NonNull Table table, int flagsForSectionToDisplay) {
        IkGuiImplTableHeaders.tableDrawDefaultContextMenu(table, flagsForSectionToDisplay);
    }

    /**
     * Calculate the maximum label width for angled headers.
     *
     * @return The maximum width.
     */
    public static float tableGetHeaderAngledMaxLabelWidth() {
        return IkGuiImplTableHeaders.tableGetHeaderAngledMaxLabelWidth();
    }

    /**
     * Calculate the header row height.
     *
     * @return The header row height.
     */
    public static float tableGetHeaderRowHeight() {
        return IkGuiImplTableHeaders.tableGetHeaderRowHeight();
    }

    /**
     * The hovered row in the current table, with one frame of latency.
     *
     * @return The hovered row from the previous frame, -1 when not hovered.
     */
    public static int tableGetHoveredRow() {
        return IkGuiImplTables.tableGetHoveredRow();
    }

    /**
     * Open the table context menu.
     *
     * @param columnIndex The column the menu is for, or -1 for the current column / no column.
     */
    public static void tableOpenContextMenu(int columnIndex) {
        IkGuiImplTableHeaders.tableOpenContextMenu(columnIndex);
    }

    /** Switch to the table background channel, to draw across columns. */
    public static void tablePushBackgroundChannel() {
        IkGuiImplTables.tablePushBackgroundChannel();
    }

    /** Switch back from the table background channel. */
    public static void tablePopBackgroundChannel() {
        IkGuiImplTables.tablePopBackgroundChannel();
    }

    /**
     * Switch to the draw channel of a column.
     *
     * @param columnIndex The column index.
     */
    public static void tablePushColumnChannel(int columnIndex) {
        IkGuiImplTables.tablePushColumnChannel(columnIndex);
    }

    /** Switch back to the draw channel of the current column. */
    public static void tablePopColumnChannel() {
        IkGuiImplTables.tablePopColumnChannel();
    }

    /**
     * Set the sort direction of a column in the current table.
     *
     * @param columnIndex The column index.
     * @param sortDirection The sort direction.
     * @param appendToSortSpecs Whether to add to the existing sort specs, or replace them.
     */
    public static void tableSetColumnSortDirection(
            int columnIndex, @NonNull SortDirection sortDirection, boolean appendToSortSpecs) {
        IkGuiImplTables.tableSetColumnSortDirection(columnIndex, sortDirection, appendToSortSpecs);
    }

    /**
     * Set the width of a column in the current table. Call before the first row.
     *
     * @param columnIndex The column index.
     * @param width The inner column width, without padding.
     */
    public static void tableSetColumnWidth(int columnIndex, float width) {
        IkGuiImplTables.tableSetColumnWidth(columnIndex, width);
    }

    // ---------------------------------------------------------------------------------------------
    // Docking
    // ---------------------------------------------------------------------------------------------

    /**
     * Remove dock nodes, undocking their windows.
     *
     * @param rootID The ID of the root node to clear, or 0 to clear all nodes.
     * @param clearSettingsRefs Whether to also clear the references to the nodes in the window
     *     settings.
     */
    public static void dockContextClearNodes(int rootID, boolean clearSettingsRefs) {
        IkGuiImplDocking.dockContextClearNodes(rootID, clearSettingsRefs);
    }

    /** [DEBUG] Rebuild all the nodes from the current settings. */
    public static void dockContextRebuildNodes() {
        IkGuiImplDocking.dockContextRebuildNodes();
    }

    /**
     * Generate an ID for a new dock node.
     *
     * @return An unused node ID.
     */
    public static int dockContextGenerateNodeID() {
        return IkGuiImplDocking.dockContextGenerateNodeID();
    }

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
    public static void dockContextQueueDock(
            Window target,
            DockNode targetNode,
            @NonNull Window payload,
            @NonNull Direction splitDirection,
            float splitRatio,
            boolean splitOuter) {
        IkGuiImplDocking.dockContextQueueDock(
                target, targetNode, payload, splitDirection, splitRatio, splitOuter);
    }

    /**
     * Queue a request to undock a window.
     *
     * @param window The window to undock.
     */
    public static void dockContextQueueUndockWindow(@NonNull Window window) {
        IkGuiImplDocking.dockContextQueueUndockWindow(window);
    }

    /**
     * Queue a request to undock a whole node.
     *
     * @param node The node to undock.
     */
    public static void dockContextQueueUndockNode(@NonNull DockNode node) {
        IkGuiImplDocking.dockContextQueueUndockNode(node);
    }

    /**
     * Undock a window from its node immediately.
     *
     * @param window The window to undock.
     * @param clearPersistentDockingRef Whether to also forget the dock ID of the window.
     */
    public static void dockContextProcessUndockWindow(
            @NonNull Window window, boolean clearPersistentDockingRef) {
        IkGuiImplDocking.dockContextProcessUndockWindow(window, clearPersistentDockingRef);
    }

    /**
     * Undock a whole leaf node immediately, so it becomes a floating node.
     *
     * @param node The node to undock.
     */
    public static void dockContextProcessUndockNode(@NonNull DockNode node) {
        IkGuiImplDocking.dockContextProcessUndockNode(node);
    }

    /**
     * Calculate where the mouse would need to be dropped to dock a window or node, mostly used for
     * automation.
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
    public static boolean dockContextCalcDropPosForDocking(
            @NonNull Window target,
            DockNode targetNode,
            @NonNull Window payloadWindow,
            DockNode payloadNode,
            @NonNull Direction splitDirection,
            boolean splitOuter,
            @NonNull Vector2f outPosition) {
        return IkGuiImplDocking.dockContextCalcDropPosForDocking(
                target,
                targetNode,
                payloadWindow,
                payloadNode,
                splitDirection,
                splitOuter,
                outPosition);
    }

    /**
     * Look up a dock node by ID.
     *
     * @param id The node ID.
     * @return The node, or null if not found.
     */
    public static DockNode dockContextFindNodeByID(int id) {
        return IkGuiImplDocking.dockContextFindNodeByID(id);
    }

    /**
     * The default window menu handler, which displays the list of windows in a dock node. Custom
     * handlers set in {@link Context#dockNodeWindowMenuHandler} may want to call this.
     *
     * @param ctx The context.
     * @param node The node.
     * @param tabBar The tab bar of the node.
     */
    public static void dockNodeWindowMenuHandlerDefault(
            @NonNull Context ctx, @NonNull DockNode node, @NonNull TabBar tabBar) {
        IkGuiImplDocking.dockNodeWindowMenuHandlerDefault(ctx, node, tabBar);
    }

    /**
     * Helper to append to/amend the tab bar of a dock node, most commonly used to add e.g. a "+"
     * button. Call {@link #dockNodeEndAmendTabBar()} if this returns true.
     *
     * @param node The node.
     * @return True if the tab bar can be amended.
     */
    public static boolean dockNodeBeginAmendTabBar(@NonNull DockNode node) {
        return IkGuiImplDocking.dockNodeBeginAmendTabBar(node);
    }

    /** End amending a dock node tab bar, after {@link #dockNodeBeginAmendTabBar(DockNode)}. */
    public static void dockNodeEndAmendTabBar() {
        IkGuiImplDocking.dockNodeEndAmendTabBar();
    }

    /**
     * Fetch the root node for a given node.
     *
     * @param node The node.
     * @return The root node, or null if node was null.
     */
    public static DockNode dockNodeGetRootNode(DockNode node) {
        return IkGuiImplDocking.dockNodeGetRootNode(node);
    }

    /**
     * Check if a node is the parent node or an ancestor of another.
     *
     * @param node The node to check.
     * @param parent The potential ancestor.
     * @return True if node is parent or below it in the hierarchy.
     */
    public static boolean dockNodeIsInHierarchyOf(DockNode node, DockNode parent) {
        return IkGuiImplDocking.dockNodeIsInHierarchyOf(node, parent);
    }

    /**
     * The depth of a node in the tree, 0 for root nodes.
     *
     * @param node The node.
     * @return The depth.
     */
    public static int dockNodeGetDepth(@NonNull DockNode node) {
        return IkGuiImplDocking.dockNodeGetDepth(node);
    }

    /**
     * The ID of the window menu button of a node.
     *
     * @param node The node.
     * @return The button ID.
     */
    public static int dockNodeGetWindowMenuButtonID(@NonNull DockNode node) {
        return IkGuiImplDocking.dockNodeGetWindowMenuButtonID(node);
    }

    /**
     * The dock node of the current window.
     *
     * @return The dock node, or null if not docked.
     */
    public static DockNode getWindowDockNode() {
        return context.windowCurrent.dockNode;
    }

    /**
     * Check if a window always wants its own tab bar.
     *
     * @param window The window.
     * @return True if the window wants its own tab bar.
     */
    public static boolean getWindowAlwaysWantOwnTabBar(@NonNull Window window) {
        return IkGuiImplDocking.getWindowAlwaysWantOwnTabBar(window);
    }

    /**
     * Docking processing for a window, called from begin().
     *
     * @param window The window.
     * @param open The open state of the window, may be null.
     */
    public static void beginDocked(@NonNull Window window, IkBoolean open) {
        IkGuiImplDocking.beginDocked(window, open);
    }

    /**
     * Turn a window being moved into a drag and drop source for docking.
     *
     * @param window The window being moved.
     */
    public static void beginDockableDragDropSource(@NonNull Window window) {
        IkGuiImplDocking.beginDockableDragDropSource(window);
    }

    /**
     * Let a window act as a target for docking windows being dragged.
     *
     * @param window The window.
     */
    public static void beginDockableDragDropTarget(@NonNull Window window) {
        IkGuiImplDocking.beginDockableDragDropTarget(window);
    }

    /**
     * Set the dock node of a window.
     *
     * @param window The window.
     * @param dockID The dock node ID.
     * @param condition The condition.
     */
    public static void setWindowDock(
            @NonNull Window window, int dockID, @NonNull Condition condition) {
        IkGuiImplDocking.setWindowDock(window, dockID, condition);
    }

    /**
     * Dock a window into a node, or set up its settings to be docked when it is created. This
     * doesn't preserve the relative order of multiple docked windows.
     *
     * @param windowName The name of the window.
     * @param nodeID The ID of the node.
     */
    public static void dockBuilderDockWindow(@NonNull String windowName, int nodeID) {
        IkGuiImplDockBuilder.dockBuilderDockWindow(windowName, nodeID);
    }

    /**
     * Look up a dock node by ID.
     *
     * @param nodeID The node ID.
     * @return The node, or null if not found.
     */
    public static DockNode dockBuilderGetNode(int nodeID) {
        return IkGuiImplDockBuilder.dockBuilderGetNode(nodeID);
    }

    /**
     * Look up the central node of a dockspace.
     *
     * @param nodeID The ID of a node in the dockspace.
     * @return The central node, or null if not found.
     */
    public static DockNode dockBuilderGetCentralNode(int nodeID) {
        return IkGuiImplDockBuilder.dockBuilderGetCentralNode(nodeID);
    }

    /**
     * Create a node. Use the {@link DockNodeFlags#INTERNAL_DOCK_SPACE} flag to create a dockspace
     * node, otherwise this will create a floating node. If you intend to split the node immediately
     * after creating it, call {@link #dockBuilderSetNodeSize(int, float, float)} first.
     *
     * @param nodeID The ID of the node, or 0 to generate one. An existing node with the same ID is
     *     removed.
     * @param flags Dock node flags.
     * @return The ID of the node.
     */
    public static int dockBuilderAddNode(int nodeID, int flags) {
        return IkGuiImplDockBuilder.dockBuilderAddNode(nodeID, flags);
    }

    /**
     * Remove a node and all its children, undocking all windows.
     *
     * @param nodeID The ID of the node.
     */
    public static void dockBuilderRemoveNode(int nodeID) {
        IkGuiImplDockBuilder.dockBuilderRemoveNode(nodeID);
    }

    /**
     * Undock all the windows in a node hierarchy.
     *
     * @param nodeID The ID of the root node, or 0 for all nodes.
     * @param clearSettingsRefs Whether to also clear the dock IDs in the window settings.
     */
    public static void dockBuilderRemoveNodeDockedWindows(int nodeID, boolean clearSettingsRefs) {
        IkGuiImplDockBuilder.dockBuilderRemoveNodeDockedWindows(nodeID, clearSettingsRefs);
    }

    /**
     * Remove all the split hierarchy of a node. All remaining docked windows are re-docked into the
     * node.
     *
     * @param nodeID The ID of the root node, or 0 for all nodes.
     */
    public static void dockBuilderRemoveNodeChildNodes(int nodeID) {
        IkGuiImplDockBuilder.dockBuilderRemoveNodeChildNodes(nodeID);
    }

    /**
     * Set the position of a floating node.
     *
     * @param nodeID The ID of the node.
     * @param x The x position.
     * @param y The y position.
     */
    public static void dockBuilderSetNodePos(int nodeID, float x, float y) {
        IkGuiImplDockBuilder.dockBuilderSetNodePos(nodeID, x, y);
    }

    /**
     * Set the size of a node.
     *
     * @param nodeID The ID of the node.
     * @param width The width, which must be positive.
     * @param height The height, which must be positive.
     */
    public static void dockBuilderSetNodeSize(int nodeID, float width, float height) {
        IkGuiImplDockBuilder.dockBuilderSetNodeSize(nodeID, width, height);
    }

    /**
     * Split a node into 2 child nodes.
     *
     * @param nodeID The ID of the node to split.
     * @param splitDirection The direction of the new node.
     * @param sizeRatioForNodeAtDirection The size ratio of the node in the split direction.
     * @param outIDAtDirection Where to store the ID of the node at the split direction, may be
     *     null.
     * @param outIDAtOppositeDirection Where to store the ID of the node at the opposite direction,
     *     may be null.
     * @return The ID of the node at the split direction.
     */
    public static int dockBuilderSplitNode(
            int nodeID,
            @NonNull Direction splitDirection,
            float sizeRatioForNodeAtDirection,
            IkInt outIDAtDirection,
            IkInt outIDAtOppositeDirection) {
        return IkGuiImplDockBuilder.dockBuilderSplitNode(
                nodeID,
                splitDirection,
                sizeRatioForNodeAtDirection,
                outIDAtDirection,
                outIDAtOppositeDirection);
    }

    /**
     * Copy a whole dockspace, remapping windows into the copy.
     *
     * @param sourceDockspaceID The ID of the dockspace to copy.
     * @param destinationDockspaceID The ID of the new dockspace.
     * @param windowRemapPairs Pairs of window names, the source window followed by the destination
     *     window.
     */
    public static void dockBuilderCopyDockSpace(
            int sourceDockspaceID,
            int destinationDockspaceID,
            @NonNull List<String> windowRemapPairs) {
        IkGuiImplDockBuilder.dockBuilderCopyDockSpace(
                sourceDockspaceID, destinationDockspaceID, windowRemapPairs);
    }

    /**
     * Copy a node hierarchy.
     *
     * @param sourceNodeID The ID of the node to copy.
     * @param destinationNodeID The ID of the copy.
     * @param outNodeRemapPairs Filled with pairs of node IDs, the source node followed by the
     *     destination node.
     */
    public static void dockBuilderCopyNode(
            int sourceNodeID, int destinationNodeID, @NonNull List<Integer> outNodeRemapPairs) {
        IkGuiImplDockBuilder.dockBuilderCopyNode(
                sourceNodeID, destinationNodeID, outNodeRemapPairs);
    }

    /**
     * Copy the position, size, and collapsed state of a window to another window or its settings.
     *
     * @param sourceName The name of the window to copy from.
     * @param destinationName The name of the window to copy to.
     */
    public static void dockBuilderCopyWindowSettings(
            @NonNull String sourceName, @NonNull String destinationName) {
        IkGuiImplDockBuilder.dockBuilderCopyWindowSettings(sourceName, destinationName);
    }

    /**
     * Finish building a dock node hierarchy, binding windows to the nodes.
     *
     * @param nodeID The ID of the root node.
     */
    public static void dockBuilderFinish(int nodeID) {
        IkGuiImplDockBuilder.dockBuilderFinish(nodeID);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiInternal() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
