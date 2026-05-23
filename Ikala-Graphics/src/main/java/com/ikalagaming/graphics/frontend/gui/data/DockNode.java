package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.enums.DataAuthority;
import com.ikalagaming.graphics.frontend.gui.enums.DockNodeState;
import com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class DockNode {
    public @NonNull DataAuthority authorityForPosition;
    public @NonNull DataAuthority authorityForSize;
    public @NonNull DataAuthority authorityForViewport;

    /** (Root node only) Pointer to central node. */
    public DockNode centralNode;

    /** (Split node only) Child nodes (left/right or top/bottom). The array is size 2. */
    public DockNode[] childNodes;

    /** (Root node only) */
    public int countNodeWithWindows;

    public boolean hasCentralNodeChild;

    /**
     * Provide space for a close button (if the docked windows has one). Note that the button may be
     * hidden on windows without one.
     */
    public boolean hasCloseButton;

    public boolean hasWindowMenuButton;
    public Window hostWindow;
    public int id;
    public boolean isBackgroundDrawnThisFrame;
    public boolean isFocused;

    /** Set to false when the node is hidden (usually disabled as it has no active window). */
    public boolean isVisible;

    public int lastBackgroundColor;

    /**
     * (Root node only) Which of our child docking nodes (any ancestor in the hierarchy) was last
     * focused.
     */
    public int lastFocusedNodeID;

    /** Last frame number the node was updated. */
    public int lastFrameActive;

    /**
     * Last frame number the node was updated or kept alive explicitly with dockSpace() + {@link
     * DockNodeFlags#KEEP_ALIVE_ONLY}.
     */
    public int lastFrameAlive;

    /** Last frame number the node was focused. */
    public int lastFrameFocused;

    /** (Write) flags specific to this node. */
    public int localFlags;

    /** (Write) flags specific to this node, applied from windows. */
    public int localFlagsInWindows;

    /** (Read) effective flags (== sharedFlags | localFlagsInNode | localFlagsInWindows). */
    public int mergedFlags;

    /** (Root node only) Set when there is a single visible node within the hierarchy. */
    public DockNode onlyNodeWithWindows;

    public DockNode parentNode;

    /** Current position. */
    public final Vector2f position;

    /** Reference viewport ID from visible window when hostWindow == null. */
    public int refViewportID;

    /** (Leaf node only) Which of our tabs/windows is selected. */
    public int selectedTabID;

    /**
     * (Write) Flags shared by all nodes of a same dockspace hierarchy (inherited from the root
     * node).
     */
    public int sharedFlags;

    /** Current size. */
    public final Vector2f size;

    /**
     * (Split node only) Last explicitly written-to size (overridden when using a splitter affecting
     * the node), used to calcuate size.
     */
    public final Vector2f sizeRef;

    /** (Split node only) Split axis (X or Y). */
    public Axis splitAxis;

    public @NonNull DockNodeState state;
    public TabBar tabBar;

    /**
     * Generally the window whose ID == selectedTabID, but when Ctrl+Tab-ing it could be different.
     */
    public Window visibleWindow;

    /** Set when closing all tabs at once. */
    public boolean wantCloseAll;

    /** (Leaf node only) Set when closing a specific tab/window. */
    public int wantCloseTabID;

    public boolean wantHiddenTabBarToggle;
    public boolean wantHiddenTabBarUpdate;
    public boolean wantLockSizeOnce;

    /**
     * After a node extraction we need to transition towards moving the newly created host window.
     */
    public boolean wantMouseMove;

    /** (Root node only). */
    public WindowClass windowClass;

    /** Note, unordered list. Iterate tabBar.tabs for user-order. */
    public List<Window> windows;

    public DockNode() {
        authorityForPosition = DataAuthority.AUTO;
        authorityForSize = DataAuthority.AUTO;
        authorityForViewport = DataAuthority.AUTO;
        centralNode = null;
        childNodes = new DockNode[2];
        countNodeWithWindows = 0;
        hasCentralNodeChild = false;
        hasCloseButton = false;
        hasWindowMenuButton = false;
        hostWindow = null;
        id = 0;
        isBackgroundDrawnThisFrame = false;
        isFocused = false;
        isVisible = false;
        lastBackgroundColor = 0;
        lastFocusedNodeID = 0;
        lastFrameActive = 0;
        lastFrameAlive = 0;
        lastFrameFocused = 0;
        localFlags = 0;
        localFlagsInWindows = 0;
        mergedFlags = 0;
        onlyNodeWithWindows = null;
        parentNode = null;
        position = new Vector2f(0.0f, 0.0f);
        refViewportID = 0;
        selectedTabID = 0;
        sharedFlags = 0;
        size = new Vector2f(0.0f, 0.0f);
        sizeRef = new Vector2f(0.0f, 0.0f);
        splitAxis = Axis.NONE;
        state = DockNodeState.UNKNOWN;
        tabBar = new TabBar();
        visibleWindow = null;
        wantCloseAll = false;
        wantCloseTabID = 0;
        wantHiddenTabBarToggle = false;
        wantHiddenTabBarUpdate = false;
        wantLockSizeOnce = false;
        wantMouseMove = false;
        windowClass = new WindowClass();
        windows = new ArrayList<>();
    }

    public boolean isRootNode() {
        return parentNode == null;
    }

    public boolean isDockSpace() {
        return (mergedFlags & DockNodeFlags.INTERNAL_DOCK_SPACE) != 0;
    }

    public boolean isFloatingNode() {
        return parentNode == null && (mergedFlags & DockNodeFlags.INTERNAL_DOCK_SPACE) == 0;
    }

    public boolean isCentralNode() {
        return (mergedFlags & DockNodeFlags.INTERNAL_CENTRAL_NODE) != 0;
    }

    public boolean isHiddenTabBar() {
        // Hidden tab bar can be shown back by clicking the small triangle
        return (mergedFlags & DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR) != 0;
    }

    public boolean isNoTabBar() {
        // Never show a tab bar
        return (mergedFlags & DockNodeFlags.INTERNAL_NO_TAB_BAR) != 0;
    }

    public boolean isSplitNode() {
        return childNodes[0] != null;
    }

    public boolean isLeafNode() {
        return childNodes[0] == null;
    }

    public boolean isEmpty() {
        return childNodes[0] == null && windows.isEmpty();
    }

    public RectFloat rect() {
        return new RectFloat(position.x, position.y, position.x + size.x, position.y + size.y);
    }

    public void setLocalFlags(int flags) {
        localFlags = flags;
        updateMergedFlags();
    }

    public void updateMergedFlags() {
        mergedFlags = sharedFlags | localFlags | localFlagsInWindows;
    }
}
