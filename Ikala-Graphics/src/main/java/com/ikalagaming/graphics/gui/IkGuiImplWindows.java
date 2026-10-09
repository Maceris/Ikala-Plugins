package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.function.Consumer;

@Slf4j
class IkGuiImplWindows {

    /** The hard minimum size of windows, regardless of style settings. */
    static final float WINDOW_HARD_MIN_SIZE = 4.0f;

    /**
     * How long we need to hover over a window border before we show resize feedback, in
     * milliseconds. Reduces visual noise when moving the mouse over borders.
     */
    static final long WINDOWS_RESIZE_FROM_EDGES_FEEDBACK_TIMER = 40;

    /** Corner definitions for resize grips: corner position (normalized) and inner direction. */
    private static final float[][] RESIZE_GRIP_DEF = {
        // cornerX, cornerY, innerDirX, innerDirY
        {1, 1, -1, -1}, // Lower-right
        {0, 1, +1, -1}, // Lower-left
        {0, 0, +1, +1}, // Upper-left (unused)
        {1, 0, -1, +1}, // Upper-right (unused)
    };

    /** Border definitions: segment start (normalized), segment end (normalized). */
    private static final float[][] RESIZE_BORDER_DEF = {
        // segment1X, segment1Y, segment2X, segment2Y
        {0, 1, 0, 0}, // Left
        {1, 0, 1, 1}, // Right
        {0, 0, 1, 0}, // Up
        {1, 1, 0, 1}, // Down
    };

    /** Index of the borders, matching {@link #RESIZE_BORDER_DEF}. */
    private static final Direction[] BORDER_DIRECTIONS = {
        Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN
    };

    static Context context;

    /**
     * Start a new window to add widgets to.
     *
     * @param name The unique name of the window, which is also used for the ID.
     * @param open If not null, a close button is shown and this is set to false when clicked.
     * @param windowFlags Window flags.
     * @return False if the window is collapsed or hidden, so items can be skipped.
     * @see WindowFlags
     */
    public static boolean begin(@NonNull String name, final IkBoolean open, int windowFlags) {
        if (name.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Window name is required");
            return false;
        }
        if (!context.withinFrameScope) {
            IkGuiImplDebugTools.reportError(log, "Forgot to call newFrame() before begin()");
            return false;
        }
        if (context.frameCountEnded == context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "Called render() or endFrame() and haven't called newFrame() again yet");
            return false;
        }

        final StyleVariables style = context.style.variable;
        final NextWindowData nextWindowData = context.nextWindowData;

        // Find or create
        Window window = IkGuiInternal.findWindowByName(name);
        final boolean windowJustCreated = window == null;
        if (windowJustCreated) {
            window = createNewWindow(name, windowFlags);
        }

        // Debug break requested by the user
        if (context.debugBreakInWindow == window.id) {
            IkGuiImplDebugTools.debugBreak("begin() of window '" + window.name + "'");
        }

        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_WINDOW_FLAGS) != 0) {
            windowFlags |= nextWindowData.windowFlags;
        }

        // Automatically disable manual moving/resizing when NoInputs is set
        if ((windowFlags & WindowFlags.NO_INPUTS) == WindowFlags.NO_INPUTS) {
            windowFlags |= WindowFlags.NO_MOVE | WindowFlags.NO_RESIZE;
        }

        final int currentFrame = context.frameCount;
        final boolean firstBeginOfFrame = window.lastFrameActive != currentFrame;
        window.isFallbackWindow =
                context.windowStack.isEmpty() && context.withinFrameScopeWithImplicitWindow;

        // Update the appearing flag (the beginDocked() path may also set this later)
        boolean windowJustActivatedByUser = window.lastFrameActive < currentFrame - 1;
        PopupData popupReference = null;
        if ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0) {
            final int popupLevel = context.beginPopupStack.size();
            if (popupLevel >= context.openPopupStack.size()) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Calling begin with a popup flag, instead of going through the popup flow");
                return false;
            }
            popupReference = context.openPopupStack.get(popupLevel);
            // We recycle popups, so treat windows as activated if the popup ID changed
            windowJustActivatedByUser =
                    windowJustActivatedByUser
                            || (window.idAsPopupWindow != popupReference.popupID)
                            || (window != popupReference.window);
        }

        // Update flags, last frame active, begin order
        final boolean windowWasAppearing = window.appearing;
        if (firstBeginOfFrame) {
            updateWindowInFocusOrderList(window, windowJustCreated, windowFlags);
            window.appearing = windowJustActivatedByUser;
            if (window.appearing) {
                window.setConditionAllowFlags(ConditionAllowed.APPEARING, true);
            }
            window.flagsPreviousFrame = window.flags;
            window.flags = windowFlags;
            window.flagsAsChildWindow =
                    (nextWindowData.fieldFlags & NextWindowFlags.HAS_CHILD_FLAGS) != 0
                            ? nextWindowData.childFlags
                            : ChildFlags.NONE;
            window.lastFrameActive = currentFrame;
            window.lastTimeActive = context.time;
            window.beginOrderWithinParent = 0;
            window.beginOrderWithinContext = (short) context.windowActiveCount;
            context.windowActiveCount += 1;
        } else {
            windowFlags = window.flags;
        }

        // Docking
        /*
         * Note: during the frame dock nodes are created, it is possible that (window.dockIsActive == false)
         * even though (window.dockNode.windows.size() > 1).
         */
        if (window.dockNode != null && window.dockNodeAsHost != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Cannot have both dock node and dock node as host set");
            return false;
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_DOCK) != 0) {
            IkGuiImplDocking.setWindowDock(
                    window, nextWindowData.dockID, nextWindowData.dockCondition);
        }
        if (firstBeginOfFrame) {
            final boolean hasDockNode = window.dockID != 0 || window.dockNode != null;
            final boolean newAutoDockNode =
                    !hasDockNode && IkGuiImplDocking.getWindowAlwaysWantOwnTabBar(window);
            final boolean dockNodeWasVisible = window.dockNodeIsVisible;
            final boolean dockTabWasVisible = window.dockTabIsVisible;
            window.dockIsActive = false;
            window.dockNodeIsVisible = false;
            window.dockTabIsVisible = false;

            if (hasDockNode || newAutoDockNode) {
                IkGuiImplDocking.beginDocked(window, open);
                windowFlags = window.flags;
                if (window.dockIsActive) {
                    // Docking currently overrides constraints
                    nextWindowData.fieldFlags &= ~NextWindowFlags.HAS_SIZE_CONSTRAINT;
                }

                // Amend the appearing flag
                if (window.dockTabIsVisible
                        && !dockTabWasVisible
                        && dockNodeWasVisible
                        && !window.appearing
                        && !windowWasAppearing) {
                    window.appearing = true;
                    window.setConditionAllowFlags(ConditionAllowed.APPEARING, true);
                }
            }
        }

        // Parent window is latched only on the first call to begin() of the frame, so further
        // append-calls can be done from a different window stack
        final Window parentWindowInStack =
                (window.dockIsActive && window.dockNode.hostWindow != null)
                        ? window.dockNode.hostWindow
                        : context.windowStack.isEmpty()
                                ? null
                                : context.windowStack.getLast().window;
        final Window parentWindow =
                firstBeginOfFrame
                        ? ((windowFlags
                                                & (WindowFlags.INTERNAL_CHILD_WINDOW
                                                        | WindowFlags.INTERNAL_POPUP
                                                        | WindowFlags.INTERNAL_TOOLTIP))
                                        != 0
                                ? parentWindowInStack
                                : null)
                        : window.parentWindow;
        if (parentWindow == null && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            IkGuiImplDebugTools.reportError(log, "Child window {} has no parent window", name);
            return false;
        }

        // We allow window memory to be compacted so recreate the base stack when needed
        if (window.idStack.isEmpty()) {
            window.idStack.push(window.id);
        }

        // Add to stack
        setCurrentWindow(window);
        WindowStackData windowStackData = new WindowStackData(window);
        windowStackData.parentLastItemDataBackup.set(context.lastItemData);
        windowStackData.disabledOverrideReenable =
                (windowFlags & WindowFlags.INTERNAL_TOOLTIP) != 0
                        && (context.currentItemFlags & ItemFlags.DISABLED) != 0;
        context.windowStack.add(windowStackData);
        IkGuiInternal.errorRecoveryStoreState(windowStackData.stackSizesInBegin);
        if ((windowFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            context.beginMenuDepth++;
        }

        // Update root window and other pointers (before any possible call to focusWindow())
        if (firstBeginOfFrame) {
            IkGuiInternal.updateWindowParentAndRootLinks(window, windowFlags, parentWindow);
            window.parentWindowInBeginStack = parentWindowInStack;
            window.parentWindowForFocusRoute =
                    window.rootWindow != window ? parentWindowInStack : null;
            if (window.parentWindowForFocusRoute == null
                    && window.dockNode != null
                    && (window.dockNode.mergedFlags
                                    & DockNodeFlags.INTERNAL_DOCKED_WINDOWS_IN_FOCUS_ROUTE)
                            != 0) {
                window.parentWindowForFocusRoute = window.dockNode.hostWindow;
            }

            // Override with the setNextWindowClass() field
            if (window.windowClass.focusRouteParentWindowID != 0) {
                window.parentWindowForFocusRoute =
                        IkGuiInternal.findWindowByID(window.windowClass.focusRouteParentWindowID);
                if (window.parentWindowForFocusRoute == null) {
                    IkGuiImplDebugTools.reportError(
                            log,
                            "Invalid focus route parent window ID 0x{} for window {}",
                            Integer.toHexString(window.windowClass.focusRouteParentWindowID),
                            name);
                }
            }
        }

        // Add to the focus scope stack
        IkGuiImplNav.pushFocusScope(
                (window.flagsAsChildWindow & ChildFlags.NAV_FLATTENED) != 0
                        ? context.currentFocusScopeID
                        : window.id);
        window.navRootFocusScopeID = context.currentFocusScopeID;

        // Add to popup stacks
        if (popupReference != null) {
            popupReference.window = window;
            popupReference.parentNavLayer =
                    parentWindowInStack != null ? parentWindowInStack.navLayerCurrent : 0;
            context.beginPopupStack.add(popupReference);
            window.idAsPopupWindow = popupReference.popupID;
        }

        // Process setNextWindow*() calls
        boolean windowPosSetByApi = false;
        boolean windowSizeXSetByApi = false;
        boolean windowSizeYSetByApi = false;
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_POSITION) != 0) {
            windowPosSetByApi =
                    ConditionAllowed.shouldResolve(
                            nextWindowData.positionCondition, window.positionConditionAllowed);
            if (windowPosSetByApi && nextWindowData.positionPivot.lengthSquared() > 0.000_01f) {
                // May be processed on the next frame if this is our first frame and we are
                // measuring size
                window.setWindowPosValue.set(nextWindowData.positionValue);
                window.setWindowPosPivot.set(nextWindowData.positionPivot);
                window.positionConditionAllowed &=
                        ~(ConditionAllowed.ONCE
                                | ConditionAllowed.FIRST_USE_EVER
                                | ConditionAllowed.APPEARING);
            } else {
                setWindowPos(
                        window,
                        nextWindowData.positionValue.x,
                        nextWindowData.positionValue.y,
                        nextWindowData.positionCondition);
            }
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE) != 0) {
            final boolean sizeConditionMet =
                    ConditionAllowed.shouldResolve(
                            nextWindowData.sizeCondition, window.sizeConditionAllowed);
            windowSizeXSetByApi = sizeConditionMet && nextWindowData.sizeValue.x > 0.0f;
            windowSizeYSetByApi = sizeConditionMet && nextWindowData.sizeValue.y > 0.0f;
            // Axis-specific conditions for beginChild()
            final boolean firstUseUsed =
                    (window.sizeConditionAllowed & ConditionAllowed.FIRST_USE_EVER) == 0;
            if ((window.flagsAsChildWindow & ChildFlags.RESIZE_X) != 0 && firstUseUsed) {
                nextWindowData.sizeValue.x = window.sizeFull.x;
            }
            if ((window.flagsAsChildWindow & ChildFlags.RESIZE_Y) != 0 && firstUseUsed) {
                nextWindowData.sizeValue.y = window.sizeFull.y;
            }
            setWindowSize(
                    window,
                    nextWindowData.sizeValue.x,
                    nextWindowData.sizeValue.y,
                    nextWindowData.sizeCondition);
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_SCROLL) != 0) {
            if (nextWindowData.scrollValue.x >= 0.0f) {
                window.scrollTarget.x = nextWindowData.scrollValue.x;
                window.scrollTargetCenterRatio.x = 0.0f;
            }
            if (nextWindowData.scrollValue.y >= 0.0f) {
                window.scrollTarget.y = nextWindowData.scrollValue.y;
                window.scrollTargetCenterRatio.y = 0.0f;
            }
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_CONTENT_SIZE) != 0) {
            window.contentSizeExplicit.set(nextWindowData.contentSizeValue);
        } else if (firstBeginOfFrame) {
            window.contentSizeExplicit.set(0.0f, 0.0f);
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_WINDOW_CLASS) != 0) {
            window.windowClass.set(nextWindowData.windowClass);
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_COLLAPSED) != 0) {
            setWindowCollapsed(
                    window, nextWindowData.collapsedValue, nextWindowData.collapsedCondition);
        }
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_FOCUS) != 0) {
            IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
        }
        if (window.appearing) {
            window.setConditionAllowFlags(ConditionAllowed.APPEARING, false);
        }

        // Tooltips over disabled items should not appear disabled themselves
        if (windowStackData.disabledOverrideReenable && window.rootWindow == window) {
            beginDisabledOverrideReenable();
        }

        final RectFloat titleBarRect = new RectFloat();

        // When reusing a window multiple times a frame, just append content (don't need to set up
        // again)
        if (firstBeginOfFrame) {
            // Initialize
            final boolean windowIsChildTooltip =
                    (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                            && (windowFlags & WindowFlags.INTERNAL_TOOLTIP) != 0;
            final boolean windowJustAppearingAfterHiddenForResize =
                    window.hiddenFramesCannotSkipItems.get() > 0;
            window.active = true;
            window.hasCloseButton = open != null;
            window.rectCurrentClip.set(
                    -Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            while (window.idStack.size() > 1) {
                window.idStack.pop();
            }
            window.drawList.clear();
            window.currentTableIndex = -1;
            if ((windowFlags & WindowFlags.INTERNAL_DOCK_NODE_HOST) != 0) {
                // Render decorations on the foreground channel, as we will render the backgrounds
                // manually later
                window.drawList.channelsSplit(2);
                window.drawList.channelsSetCurrent(IkGuiImplDocking.DOCKING_HOST_DRAW_CHANNEL_FG);
            }
            if (context.windowCurrent == window) {
                context.currentTable = null;
            }
            window.currentStateStorage = window.stateStorage;

            // Update stored window name when it changes (which can only happen with "###")
            // The title shows up in the Ctrl+Tab window list, and for child menus
            final boolean windowTitleVisibleElsewhere =
                    (window.viewport != null && window.viewport.window == window)
                            || window.dockIsActive
                            || (context.navWindowingListWindow != null
                                    && context.navWindowingListWindow.wasActive
                                    && (windowFlags & WindowFlags.NO_NAV_FOCUS) == 0)
                            || (windowFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0;
            if ((windowTitleVisibleElsewhere || windowJustActivatedByUser)
                    && !windowJustCreated
                    && !name.equals(window.name)) {
                window.name = name;
            }

            // UPDATE CONTENTS SIZE, UPDATE HIDDEN STATUS

            // Update contents size from last frame for auto-fitting (or use explicit size)
            calcWindowContentSizes(window, window.contentSize, window.contentSizeIdeal);

            /*
             * These flags are decremented before they are used. This means that in order to have these fields produce
             * their intended behaviors for one frame we must set them to at least 2.
             */
            decrement(window.hiddenFramesCanSkipItems);
            decrement(window.hiddenFramesCannotSkipItems);
            decrement(window.hiddenFramesForRenderOnly);

            // Hide new windows for one frame until they calculate their size
            if (windowJustCreated && (!windowSizeXSetByApi || !windowSizeYSetByApi)) {
                window.hiddenFramesCannotSkipItems.set(1);
            }

            // Hide popup/tooltip window when re-opening while we measure size (because we recycle
            // the windows)
            if (windowJustActivatedByUser
                    && (windowFlags & (WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_TOOLTIP))
                            != 0) {
                window.hiddenFramesCannotSkipItems.set(1);
                if ((windowFlags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0) {
                    if (!windowSizeXSetByApi) {
                        window.size.x = 0.0f;
                        window.sizeFull.x = 0.0f;
                    }
                    if (!windowSizeYSetByApi) {
                        window.size.y = 0.0f;
                        window.sizeFull.y = 0.0f;
                    }
                    window.contentSize.set(0.0f, 0.0f);
                    window.contentSizeIdeal.set(0.0f, 0.0f);
                }
            }

            // SELECT VIEWPORT
            // We need to do this before using any style/font sizes, as a viewport with a different
            // DPI may affect font sizes
            IkGuiImplViewports.windowSelectViewport(window);
            IkGuiImplViewports.setCurrentViewport(window, window.viewport);
            setCurrentWindow(window);
            windowFlags = window.flags;

            // LOCK BORDER SIZE AND PADDING FOR THE FRAME
            if (!window.dockIsActive && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
                window.borderSize = style.childBorderSize;
            } else {
                window.borderSize =
                        ((windowFlags & (WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_TOOLTIP))
                                                != 0
                                        && (windowFlags & WindowFlags.INTERNAL_MODAL) == 0)
                                ? style.popupBorderSize
                                : style.windowBorderSize;
            }
            window.padding.set(style.windowPadding);
            if (!window.dockIsActive
                    && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    && (windowFlags & WindowFlags.INTERNAL_POPUP) == 0
                    && (window.flagsAsChildWindow & ChildFlags.ALWAYS_USE_WINDOW_PADDING) == 0
                    && window.borderSize == 0.0f) {
                window.padding.set(
                        0.0f,
                        (windowFlags & WindowFlags.MENU_BAR) != 0 ? style.windowPadding.y : 0.0f);
            }

            // Lock menu offset so size calculation can use it as menu-bar windows need a minimum
            // size
            final float fontSize = IkGuiInternal.getFontSize();
            window.menuBarOffset.set(
                    Math.max(
                            Math.max(window.padding.x, style.itemSpacing.x),
                            nextWindowData.menuBarOffsetMinValue.x),
                    nextWindowData.menuBarOffsetMinValue.y);
            window.titleBarHeight =
                    (windowFlags & WindowFlags.NO_TITLE_BAR) != 0
                            ? 0.0f
                            : fontSize + style.framePadding.y * 2.0f;
            window.menuBarHeight =
                    (windowFlags & WindowFlags.MENU_BAR) != 0
                            ? window.menuBarOffset.y + fontSize + style.framePadding.y * 2.0f
                            : 0.0f;

            // Depending on the condition we use previous or current window size to compare
            // against contents size to decide if a scrollbar should be visible
            boolean useCurrentSizeForScrollbarX = windowJustCreated;
            boolean useCurrentSizeForScrollbarY = windowJustCreated;
            if (windowSizeXSetByApi && window.contentSizeExplicit.x != 0.0f) {
                useCurrentSizeForScrollbarX = true;
            }
            if (windowSizeYSetByApi && window.contentSizeExplicit.y != 0.0f) {
                useCurrentSizeForScrollbarY = true;
            }

            // Collapse window by double-clicking on the title bar
            if ((windowFlags & WindowFlags.NO_TITLE_BAR) == 0
                    && (windowFlags & WindowFlags.NO_COLLAPSE) == 0
                    && !window.dockIsActive) {
                // We don't use a regular button + ID to test for double click on the title bar,
                // so verify that we don't have items over the title bar
                window.getTitleBarRect(titleBarRect);
                if (context.windowHovered == window
                        && context.hoveredID == 0
                        && context.hoveredIDPreviousFrame == 0
                        && context.activeID == 0
                        && IkGuiInternal.isMouseHoveringRect(
                                titleBarRect.getLeft(),
                                titleBarRect.getTop(),
                                titleBarRect.getRight(),
                                titleBarRect.getBottom(),
                                true)
                        && context.io.getMouseClickedCount(MouseButton.LEFT) == 2
                        && IkGuiImplKeys.getKeyOwner(Key.MOUSE_LEFT)
                                == KeyRoutingData.KEY_OWNER_NO_OWNER) {
                    window.collapseToggleRequested = true;
                    // Claim the input the same way buttonBehavior() does, so a move on the same
                    // frame doesn't trigger other items
                    IkGuiImplKeys.setKeyOwner(Key.MOUSE_LEFT, window.idMove, InputFlags.NONE);
                }
                if (window.collapseToggleRequested) {
                    window.collapsed = !window.collapsed;
                    if (!window.collapsed) {
                        useCurrentSizeForScrollbarY = true;
                    }
                    IkGuiInternal.markIniSettingsDirty(window);
                }
            } else {
                window.collapsed = false;
            }
            window.collapseToggleRequested = false;

            // SIZE

            // Outer decoration sizes (we need to clear the scrollbar size immediately as
            // calcWindowAutoFitSize() needs it and can be called from other locations)
            final Vector2f scrollbarSizesFromLastFrame = new Vector2f(window.scrollbarSizes);
            window.decoOuterSizeX1 = 0.0f;
            window.decoOuterSizeX2 = 0.0f;
            window.decoOuterSizeY1 = window.titleBarHeight + window.menuBarHeight;
            window.decoOuterSizeY2 = 0.0f;
            window.scrollbarSizes.set(0.0f, 0.0f);

            // Calculate auto-fit size, handle automatic resize
            {
                final boolean alwaysAutoResize =
                        (windowFlags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0 && !window.collapsed;
                final boolean sizeAutoFitXAlways = !windowSizeXSetByApi && alwaysAutoResize;
                final boolean sizeAutoFitYAlways = !windowSizeYSetByApi && alwaysAutoResize;
                final boolean sizeAutoFitXCurrent =
                        !windowSizeXSetByApi && window.autoFitFramesX.get() > 0;
                final boolean sizeAutoFitYCurrent =
                        !windowSizeYSetByApi && window.autoFitFramesY.get() > 0;
                int sizeAutoFitMask = 0;
                if (sizeAutoFitXAlways || sizeAutoFitXCurrent) {
                    sizeAutoFitMask |= 1;
                }
                if (sizeAutoFitYAlways || sizeAutoFitYCurrent) {
                    sizeAutoFitMask |= 2;
                }
                final Vector2f sizeAutoFit = new Vector2f();
                calcWindowAutoFitSize(
                        window, window.contentSizeIdeal, sizeAutoFitMask, sizeAutoFit);

                final float oldSizeX = window.sizeFull.x;
                final float oldSizeY = window.sizeFull.y;
                if (sizeAutoFitXAlways || sizeAutoFitXCurrent) {
                    if (sizeAutoFitXAlways) {
                        window.sizeFull.x = sizeAutoFit.x;
                    } else {
                        window.sizeFull.x =
                                window.autoFitOnlyGrows
                                        ? Math.max(window.sizeFull.x, sizeAutoFit.x)
                                        : sizeAutoFit.x;
                    }
                    useCurrentSizeForScrollbarX = true;
                }
                if (sizeAutoFitYAlways || sizeAutoFitYCurrent) {
                    if (sizeAutoFitYAlways) {
                        window.sizeFull.y = sizeAutoFit.y;
                    } else {
                        window.sizeFull.y =
                                window.autoFitOnlyGrows
                                        ? Math.max(window.sizeFull.y, sizeAutoFit.y)
                                        : sizeAutoFit.y;
                    }
                    useCurrentSizeForScrollbarY = true;
                }
                if (oldSizeX != window.sizeFull.x || oldSizeY != window.sizeFull.y) {
                    IkGuiInternal.markIniSettingsDirty(window);
                }
            }

            // Apply minimum/maximum window size constraints and final size
            calcWindowSizeAfterConstraint(window, window.sizeFull, window.sizeFull);
            if (window.collapsed && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
                window.size.set(window.sizeFull.x, window.titleBarHeight);
            } else {
                window.size.set(window.sizeFull);
            }

            // POSITION

            // Popups latch their initial position, will position itself when it appears next frame
            if (windowJustActivatedByUser) {
                window.autoPosLastDirection = Direction.NONE;
                if ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0
                        && (windowFlags & WindowFlags.INTERNAL_MODAL) == 0
                        && !windowPosSetByApi
                        && !context.beginPopupStack.isEmpty()) {
                    window.position.set(context.beginPopupStack.getLast().preferredPosition);
                }
            }

            // Position child window
            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
                if (!parentWindow.active) {
                    IkGuiImplDebugTools.reportError(
                            log,
                            "Parent window {} of child {} is not active",
                            parentWindow.name,
                            name);
                }
                window.beginOrderWithinParent = (short) parentWindow.childWindows.size();
                parentWindow.childWindows.add(window);
                if ((windowFlags & WindowFlags.INTERNAL_POPUP) == 0
                        && !windowPosSetByApi
                        && !windowIsChildTooltip) {
                    window.position.set(parentWindow.cursorPosition);
                }
            }

            final boolean windowPosWithPivot =
                    window.setWindowPosValue.x != Float.MAX_VALUE
                            && window.hiddenFramesCannotSkipItems.get() == 0;
            if (windowPosWithPivot) {
                // Position given a pivot (e.g. for centering)
                setWindowPos(
                        window,
                        window.setWindowPosValue.x - window.size.x * window.setWindowPosPivot.x,
                        window.setWindowPosValue.y - window.size.y * window.setWindowPosPivot.y,
                        Condition.ALWAYS);
            } else if ((windowFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0
                    || ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0
                            && !windowPosSetByApi
                            && windowJustAppearingAfterHiddenForResize)
                    || ((windowFlags & WindowFlags.INTERNAL_TOOLTIP) != 0
                            && !windowPosSetByApi
                            && !windowIsChildTooltip)) {
                IkGuiImplPopups.findBestWindowPosForPopup(window, window.position);
            }

            // Late create a viewport if we don't fit within our current host viewport
            final RectFloat viewportRect = window.viewport.getMainRect(new RectFloat());
            if (window.viewportAllowPlatformMonitorExtend >= 0
                    && !window.viewportOwned
                    && (window.viewport.flags & ViewportFlags.IS_MINIMIZED) == 0
                    && !viewportRect.contains(window.getRect())) {
                // This is based on the assumption that the DPI will be known ahead (same as the
                // DPI of the selection done in windowSelectViewport())
                window.viewport =
                        IkGuiImplViewports.addUpdateViewport(
                                window,
                                window.id,
                                window.position,
                                window.size,
                                ViewportFlags.NO_FOCUS_ON_APPEARING);
                IkGuiImplViewports.setCurrentViewport(window, window.viewport);
                setCurrentWindow(window);
            }

            if (window.viewportOwned) {
                IkGuiImplViewports.windowSyncOwnedViewport(window, parentWindowInStack);
            }

            // Calculate the range of allowed position for that window (to be movable and visible
            // past safe area padding). When clamping to stay visible, we will enforce that the
            // window position stays inside the visibility rect.
            window.viewport.getMainRect(viewportRect);
            final RectFloat visibilityRect = window.viewport.getWorkRect(new RectFloat());
            final float visibilityPaddingX =
                    Math.max(style.displayWindowPadding.x, style.displaySafeAreaPadding.x);
            final float visibilityPaddingY =
                    Math.max(style.displayWindowPadding.y, style.displaySafeAreaPadding.y);
            visibilityRect.expand(-visibilityPaddingX, -visibilityPaddingY);

            // Clamp position/size so window stays visible within its viewport or monitor. Ignore
            // zero-sized display explicitly to avoid losing positions if a window manager reports
            // zero-sized window when initializing or minimizing.
            if (!windowPosSetByApi && (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
                if (!window.viewportOwned
                        && viewportRect.getWidth() > 0.0f
                        && viewportRect.getHeight() > 0.0f) {
                    clampWindowPos(window, visibilityRect);
                } else if (window.viewportOwned && !context.platformIO.monitors.isEmpty()) {
                    if (context.windowMoving != null
                            && window.rootWindowDockTree
                                    == context.windowMoving.rootWindowDockTree) {
                        // While moving windows we allow them to straddle monitors
                        visibilityRect.set(context.platformMonitorsFullWorkRect);
                    } else {
                        // When not moving, ensure the window is visible in its monitor. Lost
                        // windows (e.g. a monitor disconnected) will naturally be moved to the
                        // fallback monitor, aka the main viewport.
                        IkGuiImplViewports.getViewportPlatformMonitor(window.viewport)
                                .getWorkRect(visibilityRect);
                    }
                    visibilityRect.expand(-visibilityPaddingX, -visibilityPaddingY);
                    clampWindowPos(window, visibilityRect);
                }
            }
            IkGuiInternal.truncate(window.position);

            // Lock window rounding for the frame (so that altering them doesn't cause
            // inconsistencies)
            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0 && !window.dockIsActive) {
                window.rounding = style.childRounding;
            } else if (window.rootWindowDockTree.viewportOwned) {
                window.rounding = 0.0f;
            } else {
                window.rounding =
                        ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0
                                        && (windowFlags & WindowFlags.INTERNAL_MODAL) == 0)
                                ? style.popupRounding
                                : style.windowRounding;
            }
            window.alphaRadius = style.windowAlphaRadius;

            // Apply window focus (new and reactivated windows are moved to front)
            boolean wantFocus = false;
            if (windowJustActivatedByUser
                    && (windowFlags & WindowFlags.NO_FOCUS_ON_APPEARING) == 0) {
                if ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0) {
                    wantFocus = true;
                } else if ((window.dockIsActive
                                || (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0)
                        && (windowFlags & WindowFlags.INTERNAL_TOOLTIP) == 0) {
                    wantFocus = true;
                }
            }

            // Test engine: register the whole window in the item system, before submitting
            // further decorations
            if (context.testEngineHookItems) {
                window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;
                IkGuiInternal.testEngineItemAdd(window.id, window.getRect(), null);
                IkGuiInternal.testEngineItemInfo(
                        window.id,
                        window.name,
                        context.windowHovered == window
                                ? ItemStatusFlags.HOVERED_RECT
                                : ItemStatusFlags.NONE);
                window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
            }

            // Decide if we are going to handle borders and resize grips
            boolean handleBordersAndResizeGrips =
                    window.dockNodeAsHost != null || !window.dockIsActive;
            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0 && parentWindow.skipItems) {
                handleBordersAndResizeGrips = false;
            }

            // Handle manual resize: resize grips, borders
            int[] borderHovered = {-1};
            int[] borderHeld = {-1};
            int[] resizeGripColors = new int[4];
            final int resizeGripCount;
            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    && (windowFlags & WindowFlags.INTERNAL_POPUP) == 0) {
                resizeGripCount =
                        ((window.flagsAsChildWindow & ChildFlags.RESIZE_X) != 0
                                        && (window.flagsAsChildWindow & ChildFlags.RESIZE_Y) != 0)
                                ? 1
                                : 0;
            } else {
                resizeGripCount = context.io.configWindowsResizeFromEdges ? 2 : 1;
            }

            final float resizeGripDrawSize =
                    IkGuiInternal.truncate(
                            Math.max(fontSize * 1.10f, window.rounding + 1.0f + fontSize * 0.2f));
            if (handleBordersAndResizeGrips && !window.collapsed) {
                final int autoFitMask =
                        updateWindowManualResize(
                                window,
                                borderHovered,
                                borderHeld,
                                resizeGripCount,
                                resizeGripColors,
                                visibilityRect);
                if ((autoFitMask & 1) != 0) {
                    useCurrentSizeForScrollbarX = true;
                }
                if ((autoFitMask & 2) != 0) {
                    useCurrentSizeForScrollbarY = true;
                }
            }
            window.borderBeingHovered =
                    borderHovered[0] >= 0 ? BORDER_DIRECTIONS[borderHovered[0]] : Direction.NONE;
            window.borderBeingDragged =
                    borderHeld[0] >= 0 ? BORDER_DIRECTIONS[borderHeld[0]] : Direction.NONE;

            // Synchronize window -> viewport again and one last time (clamping and manual resize
            // may have affected either)
            if (window.viewportOwned) {
                if (!window.viewport.platformRequestMove) {
                    window.viewport.position.set(window.position);
                }
                if (!window.viewport.platformRequestResize) {
                    window.viewport.size.set(window.size);
                }
                window.viewport.updateWorkRect();
                window.viewport.getMainRect(viewportRect);
            }

            // Save the last known viewport position within the window itself (so it can be saved
            // in the settings and restored)
            window.viewportPosition.set(window.viewport.position);

            // SCROLLBAR VISIBILITY

            // Update scrollbar visibility (based on the size that was effective during last frame
            // or the auto-resized size)
            if (!window.collapsed) {
                // When reading the current size we need to read it after size constraints have
                // been applied. Intentionally use previous frame values for the inner rect and
                // scrollbar sizes.
                final float availableFromCurrentFrameX = window.sizeFull.x;
                final float availableFromCurrentFrameY =
                        window.sizeFull.y - (window.decoOuterSizeY1 + window.decoOuterSizeY2);
                final float availableFromLastFrameX =
                        window.rectInner.getWidth() + scrollbarSizesFromLastFrame.x;
                final float availableFromLastFrameY =
                        window.rectInner.getHeight() + scrollbarSizesFromLastFrame.y;
                final float neededFromLastFrameX =
                        windowJustCreated ? 0 : window.contentSize.x + window.padding.x * 2.0f;
                final float neededFromLastFrameY =
                        windowJustCreated ? 0 : window.contentSize.y + window.padding.y * 2.0f;
                final float sizeForScrollbarsX =
                        useCurrentSizeForScrollbarX
                                ? availableFromCurrentFrameX
                                : availableFromLastFrameX;
                final float sizeForScrollbarsY =
                        useCurrentSizeForScrollbarY
                                ? availableFromCurrentFrameY
                                : availableFromLastFrameY;
                final boolean noScrollbar = (windowFlags & WindowFlags.NO_SCROLLBAR) != 0;
                final boolean scrollbarXPrevious = window.scrollbarX;
                window.scrollbarY =
                        (windowFlags & WindowFlags.ALWAYS_VERTICAL_SCROLLBAR) != 0
                                || (neededFromLastFrameY > sizeForScrollbarsY && !noScrollbar);
                window.scrollbarX =
                        (windowFlags & WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR) != 0
                                || (neededFromLastFrameX
                                                > sizeForScrollbarsX
                                                        - (window.scrollbarY
                                                                ? style.scrollbarSize
                                                                : 0.0f)
                                        && !noScrollbar
                                        && (windowFlags & WindowFlags.HORIZONTAL_SCROLLBAR) != 0);

                // Track when the horizontal scrollbar visibility keeps toggling, which is a sign of
                // a feedback loop, and stabilize by enforcing visibility
                window.scrollbarXStabilizeToggledHistory =
                        ((window.scrollbarXStabilizeToggledHistory << 1)
                                        | (scrollbarXPrevious != window.scrollbarX ? 1 : 0))
                                & 0xFF;
                if (window.scrollbarXStabilizeToggledHistory != 0
                        && Integer.bitCount(window.scrollbarXStabilizeToggledHistory) >= 4) {
                    window.scrollbarX = true;
                }

                if (window.scrollbarX && !window.scrollbarY) {
                    window.scrollbarY =
                            neededFromLastFrameY > sizeForScrollbarsY - style.scrollbarSize
                                    && !noScrollbar;
                }
                window.scrollbarSizes.set(
                        window.scrollbarY ? style.scrollbarSize : 0.0f,
                        window.scrollbarX ? style.scrollbarSize : 0.0f);

                // Amend the partially filled decoration values
                window.decoOuterSizeX2 += window.scrollbarSizes.x;
                window.decoOuterSizeY2 += window.scrollbarSizes.y;
            }

            // UPDATE RECTANGLES (1 - THOSE NOT AFFECTED BY SCROLLING)

            // Outer rectangle, not affected by window border size
            final RectFloat hostRect =
                    ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                                    && (windowFlags & WindowFlags.INTERNAL_POPUP) == 0
                                    && !windowIsChildTooltip)
                            ? new RectFloat(
                                    parentWindow.rectCurrentClip.getLeft(),
                                    parentWindow.rectCurrentClip.getTop(),
                                    parentWindow.rectCurrentClip.getRight(),
                                    parentWindow.rectCurrentClip.getBottom())
                            : viewportRect;
            window.updateRectOuter();
            window.getTitleBarRect(titleBarRect);
            window.rectOuterClipped.set(window.rectOuter);
            if (window.dockIsActive) {
                window.rectOuterClipped.setTop(
                        window.rectOuterClipped.getTop() + window.titleBarHeight);
            }
            window.rectOuterClipped.clipWith(hostRect);

            // Inner rectangle, not affected by window border size
            window.rectInner.set(
                    window.position.x + window.decoOuterSizeX1,
                    window.position.y + window.decoOuterSizeY1,
                    window.position.x + window.size.x - window.decoOuterSizeX2,
                    window.position.y + window.size.y - window.decoOuterSizeY2);

            // Inner clipping rectangle, extends outside the regular work region up to borders
            final float topBorderSize =
                    ((windowFlags & WindowFlags.MENU_BAR) != 0
                                    || (windowFlags & WindowFlags.NO_TITLE_BAR) == 0)
                            ? style.frameBorderSize
                            : window.borderSize;
            window.rectInnerClip.set(
                    (float)
                            Math.floor(
                                    0.5f + window.rectInner.getLeft() + window.borderSize * 0.5f),
                    (float) Math.floor(0.5f + window.rectInner.getTop() + topBorderSize * 0.5f),
                    (float) Math.floor(window.rectInner.getRight() - window.borderSize * 0.5f),
                    (float) Math.floor(window.rectInner.getBottom() - window.borderSize * 0.5f));
            window.rectInnerClip.clipWithFull(hostRect);

            // SCROLLING

            // Lock down maximum scrolling. The value of the scroll max is ahead of the scrollbar
            // visibility, which intentionally uses the previous frame inner rect
            window.scrollMax.set(
                    Math.max(
                            0.0f,
                            window.contentSize.x
                                    + window.padding.x * 2.0f
                                    - window.rectInner.getWidth()),
                    Math.max(
                            0.0f,
                            window.contentSize.y
                                    + window.padding.y * 2.0f
                                    - window.rectInner.getHeight()));

            // Apply scrolling
            calcNextScrollFromScrollTargetAndClamp(window, window.scrollPosition);
            window.scrollTarget.set(Float.MAX_VALUE, Float.MAX_VALUE);
            window.decoInnerSizeX1 = 0.0f;
            window.decoInnerSizeY1 = 0.0f;

            // DRAWING

            // Set up the draw list and outer clipping rectangle
            pushWindowClipRect(window, hostRect, false);

            final boolean isUndockedOrDockedVisible =
                    !window.dockIsActive || window.dockTabIsVisible;
            if (isUndockedOrDockedVisible) {
                // Handle title bar, scrollbar, resize grips and resize borders
                final Window windowToHighlight =
                        context.navWindowingTarget != null
                                ? context.navWindowingTarget
                                : context.navFocusedWindow;
                final boolean titleBarIsHighlight =
                        wantFocus
                                || (windowToHighlight != null
                                        && (window.rootWindowForTitleBarHighlight
                                                        == windowToHighlight
                                                                .rootWindowForTitleBarHighlight
                                                || (window.dockNode != null
                                                        && window.dockNode
                                                                == windowToHighlight.dockNode)));
                renderWindowDecorations(
                        window,
                        titleBarRect,
                        titleBarIsHighlight,
                        handleBordersAndResizeGrips,
                        resizeGripCount,
                        resizeGripColors,
                        resizeGripDrawSize);
            }

            // UPDATE RECTANGLES (2 - THOSE AFFECTED BY SCROLLING)

            // Work rectangle, affected by window padding and border size
            final boolean allowScrollbarX =
                    (windowFlags & WindowFlags.NO_SCROLLBAR) == 0
                            && (windowFlags & WindowFlags.HORIZONTAL_SCROLLBAR) != 0;
            final boolean allowScrollbarY = (windowFlags & WindowFlags.NO_SCROLLBAR) == 0;
            final float workRectSizeX =
                    window.contentSizeExplicit.x != 0.0f
                            ? window.contentSizeExplicit.x
                            : Math.max(
                                    allowScrollbarX ? window.contentSize.x : 0.0f,
                                    window.size.x
                                            - window.padding.x * 2.0f
                                            - (window.decoOuterSizeX1 + window.decoOuterSizeX2));
            final float workRectSizeY =
                    window.contentSizeExplicit.y != 0.0f
                            ? window.contentSizeExplicit.y
                            : Math.max(
                                    allowScrollbarY ? window.contentSize.y : 0.0f,
                                    window.size.y
                                            - window.padding.y * 2.0f
                                            - (window.decoOuterSizeY1 + window.decoOuterSizeY2));
            final float workMinX =
                    IkGuiInternal.truncate(
                            window.rectInner.getLeft()
                                    - window.scrollPosition.x
                                    + Math.max(window.padding.x, window.borderSize));
            final float workMinY =
                    IkGuiInternal.truncate(
                            window.rectInner.getTop()
                                    - window.scrollPosition.y
                                    + Math.max(window.padding.y, window.borderSize));
            window.rectWork.set(
                    workMinX, workMinY, workMinX + workRectSizeX, workMinY + workRectSizeY);
            window.rectParentWork.set(window.rectWork);

            // Content region, by default the region leading to no scrolling
            final float contentMinX =
                    window.position.x
                            - window.scrollPosition.x
                            + window.padding.x
                            + window.decoOuterSizeX1;
            final float contentMinY =
                    window.position.y
                            - window.scrollPosition.y
                            + window.padding.y
                            + window.decoOuterSizeY1;
            window.rectContent.set(
                    contentMinX,
                    contentMinY,
                    contentMinX
                            + (window.contentSizeExplicit.x != 0.0f
                                    ? window.contentSizeExplicit.x
                                    : (window.size.x
                                            - window.padding.x * 2.0f
                                            - (window.decoOuterSizeX1 + window.decoOuterSizeX2))),
                    contentMinY
                            + (window.contentSizeExplicit.y != 0.0f
                                    ? window.contentSizeExplicit.y
                                    : (window.size.y
                                            - window.padding.y * 2.0f
                                            - (window.decoOuterSizeY1 + window.decoOuterSizeY2))));

            // Set up layout cursor
            window.indent = window.decoOuterSizeX1 + window.padding.x - window.scrollPosition.x;
            window.groupOffset = 0.0f;
            window.columnsOffset = 0.0f;
            // Record the precision lost in the start position, which happens with very large
            // scrolling amounts. The clipper uses this to compensate, which is the easy and cheap
            // option compared to using doubles everywhere.
            final double startPositionX =
                    (double) window.position.x
                            + window.padding.x
                            - (double) window.scrollPosition.x
                            + window.decoOuterSizeX1
                            + window.columnsOffset;
            final double startPositionY =
                    (double) window.position.y
                            + window.padding.y
                            - (double) window.scrollPosition.y
                            + window.decoOuterSizeY1;
            window.cursorStartPosition.set((float) startPositionX, (float) startPositionY);
            window.cursorStartPositionLossyness.set(
                    (float) (startPositionX - window.cursorStartPosition.x),
                    (float) (startPositionY - window.cursorStartPosition.y));
            window.cursorPosition.set(window.cursorStartPosition);
            window.cursorPreviousLinePosition.set(window.cursorPosition);
            window.cursorMaxPosition.set(window.cursorStartPosition);
            window.cursorIdealMaxPosition.set(window.cursorStartPosition);
            window.lineSizeCurrent.set(0.0f, 0.0f);
            window.lineSizePrevious.set(0.0f, 0.0f);
            window.baseOffsetCurrentLine = 0.0f;
            window.baseOffsetPreviousLine = 0.0f;
            window.sameLine = false;
            window.setPos = false;

            window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
            window.navLayersActiveMask = window.navLayersActiveMaskNext;
            window.navLayersActiveMaskNext = 0;
            window.navIsScrollPushableX = true;
            window.navHideHighlightOneFrame = false;
            window.navWindowHasScrollY = window.scrollMax.y > 0.0f;
            window.menuBarAppending = false;
            window.menuColumns.update(style.itemSpacing.x, windowJustActivatedByUser);
            window.treeDepth = 0;
            window.treeHasStackDataDepthMask = 0;
            window.treeRecordsClippedNodesY2Mask = 0;
            window.childWindows.clear();
            window.layoutType = LayoutType.VERTICAL;
            window.parentLayoutType =
                    parentWindow != null ? parentWindow.layoutType : LayoutType.VERTICAL;

            // Default item width. Make it proportional to the window size if the window can be
            // manually resized.
            final boolean isResizableWidth;
            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0 && !window.dockIsActive) {
                isResizableWidth =
                        window.size.x > 0.0f
                                && (window.flagsAsChildWindow
                                                & (ChildFlags.AUTO_RESIZE_X
                                                        | ChildFlags.ALWAYS_AUTO_RESIZE))
                                        == 0;
            } else {
                isResizableWidth =
                        window.size.x > 0.0f && (windowFlags & WindowFlags.ALWAYS_AUTO_RESIZE) == 0;
            }
            if (isResizableWidth) {
                window.itemWidthDefault = IkGuiInternal.truncate(window.size.x * 0.65f);
            } else {
                window.itemWidthDefault = IkGuiInternal.truncate(fontSize * 16.0f);
            }
            window.currentItemWidth = window.itemWidthDefault;
            window.itemWidthStack.clear();
            window.currentTextWrapPosition = -1.0f;
            window.textWrapPositionStack.clear();

            decrement(window.autoFitFramesX);
            decrement(window.autoFitFramesY);

            // Clear setNextWindow*() data
            nextWindowData.clearFlags();

            // Apply focus (we need to call focusWindow() after setting the cursor start position
            // so the initial navigation reference rectangle can start around there)
            if (wantFocus) {
                IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.UNLESS_BELOW_MODAL);
            }
            if (wantFocus && window == context.navFocusedWindow) {
                IkGuiImplNav.navInitWindow(window, false);
            }

            // Close requested by the platform window (applies to all windows in this viewport)
            if (open != null
                    && window.viewport.platformRequestClose
                    && window.viewport != IkGuiImplViewports.getMainViewport()
                    && !IkGuiImplViewports.isWindowInDockSpace(window)) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_VIEWPORT,
                        "[viewport] Window '%s' closed by PlatformRequestClose",
                        window.name);
                open.set(false);
                // Assume the platform request close is mapped to Alt+F4, so we disable Alt for
                // toggling the menu
                context.navWindowingToggleLayer = false;
            }

            // Pressing Ctrl+C copies the window contents to the clipboard
            // [Experimental] Breaks on nested begin()/end() pairs, and the text output has many
            // issues
            if (context.io.configWindowsCopyContentsWithCtrlC
                    && context.navFocusedWindow != null
                    && context.navFocusedWindow.rootWindow == window
                    && context.activeID == 0
                    && IkGuiImplKeys.shortcut(
                            KeyChord.of(KeyModFlags.CTRL, Key.C),
                            InputFlags.NONE,
                            KeyRoutingData.KEY_OWNER_ANY)) {
                IkGuiImplLogging.logToClipboard(0);
            }

            // Title bar
            if ((windowFlags & WindowFlags.NO_TITLE_BAR) == 0 && !window.dockIsActive) {
                renderWindowTitleBarContents(
                        window,
                        new RectFloat(
                                titleBarRect.getLeft() + window.borderSize,
                                titleBarRect.getTop(),
                                titleBarRect.getRight() - window.borderSize,
                                titleBarRect.getBottom()),
                        name,
                        open);
            }

            // Clear hit test shape every frame
            window.hitTestHoleSize.set(0, 0);

            if ((windowFlags & WindowFlags.INTERNAL_TOOLTIP) != 0) {
                context.tooltipPreviousWindow = window;
            }

            if ((context.io.configFlags & ConfigFlags.DOCKING_ENABLE) != 0) {
                // Docking: dragging a dockable window (or any of its children) turns it into a
                // drag and drop source. We need to do this before we overwrite the last item data
                // below, because beginDockableDragDropSource() also overwrites it.
                if (context.windowMoving == window
                        && (window.rootWindowDockTree.flags & WindowFlags.NO_DOCKING) == 0) {
                    IkGuiImplDocking.beginDockableDragDropSource(window);
                }

                // Docking: any dockable window can act as a target. For dock node hosts we call
                // beginDockableDragDropTarget() in dockNodeUpdate() instead.
                if (context.dragDropActive
                        && (windowFlags & WindowFlags.NO_DOCKING) == 0
                        && (context.windowMoving == null
                                || context.windowMoving.rootWindowDockTree != window)
                        && window == window.rootWindowDockTree
                        && (window.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
                    IkGuiImplDocking.beginDockableDragDropTarget(window);
                }
            }

            // We fill the last item data based on the title bar, in order for isItemHovered() and
            // isItemActive() to be usable after begin(). This is useful to allow creating context
            // menus on the title bar only, etc.
            window.windowItemStatusFlags =
                    IkGuiInternal.isMouseHoveringRect(
                                    titleBarRect.getLeft(),
                                    titleBarRect.getTop(),
                                    titleBarRect.getRight(),
                                    titleBarRect.getBottom(),
                                    false)
                            ? ItemStatusFlags.HOVERED_RECT
                            : ItemStatusFlags.NONE;
            setLastItemDataForWindow(window, titleBarRect);

            // Debug tools: locating the window
            if (context.debugLocateID != 0
                    && (window.id == context.debugLocateID
                            || window.idMove == context.debugLocateID)) {
                IkGuiImplDebugTools.debugLocateItemResolveWithLastItem();
            }

            // Test engine: register the title bar or tab with the move ID
            if ((window.flags & WindowFlags.NO_TITLE_BAR) == 0) {
                window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;
                IkGuiInternal.testEngineItemAdd(
                        context.lastItemData.id, context.lastItemData.rect, context.lastItemData);
                window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
            }
        } else {
            // Append
            IkGuiImplViewports.setCurrentViewport(window, window.viewport);
            setCurrentWindow(window);
            nextWindowData.clearFlags();
            setLastItemDataForWindow(window, window.getTitleBarRect(titleBarRect));
        }

        if ((windowFlags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
            pushWindowClipRect(window, window.rectInnerClip, true);
        }

        // Clear the accessed flag last thing, so the flag stays false when the default "Debug"
        // window is unused
        window.writeAccessed = false;
        window.beginCount++;

        // Update visibility
        if (firstBeginOfFrame) {
            // When we are about to select this tab (which will only be visible next frame), flag
            // it with a non-zero hidden frame count
            if (window.dockIsActive && !window.dockTabIsVisible) {
                if (window.lastFrameJustFocused == context.frameCount) {
                    window.hiddenFramesCannotSkipItems.set(1);
                } else {
                    window.hiddenFramesCanSkipItems.set(1);
                }
            }

            if ((windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    && (windowFlags & WindowFlags.INTERNAL_CHILD_MENU) == 0) {
                // Child window can be out of sight and have "negative" clip windows. Mark them as
                // collapsed so commands are skipped earlier.
                // While logging, we don't skip them so their contents are captured. Flattened
                // children aren't skipped during navigation requests, so they can be reached.
                final boolean navRequest =
                        (window.flagsAsChildWindow & ChildFlags.NAV_FLATTENED) != 0
                                && context.navAnyRequest
                                && context.navFocusedWindow != null
                                && context.navFocusedWindow.rootWindowForNavigation
                                        == window.rootWindowForNavigation;
                if (!context.logEnabled
                        && !navRequest
                        && (window.rectOuterClipped.getLeft() >= window.rectOuterClipped.getRight()
                                || window.rectOuterClipped.getTop()
                                        >= window.rectOuterClipped.getBottom())) {
                    if (window.autoFitFramesX.get() > 0 || window.autoFitFramesY.get() > 0) {
                        window.hiddenFramesCannotSkipItems.set(1);
                    } else {
                        window.hiddenFramesCanSkipItems.set(1);
                    }
                }

                // Hide along with parent or if parent is collapsed
                if (parentWindow.collapsed || parentWindow.hiddenFramesCanSkipItems.get() > 0) {
                    window.hiddenFramesCanSkipItems.set(1);
                }
                if (parentWindow.hiddenFramesCannotSkipItems.get() > 0) {
                    window.hiddenFramesCannotSkipItems.set(1);
                }
            }

            // Don't render if style alpha is 0 at the time of begin()
            if (style.alpha <= 0.0f) {
                window.hiddenFramesCanSkipItems.set(1);
            }

            // Update the hidden flag
            final boolean hiddenRegular =
                    window.hiddenFramesCanSkipItems.get() > 0
                            || window.hiddenFramesCannotSkipItems.get() > 0;
            window.hidden = hiddenRegular || window.hiddenFramesForRenderOnly.get() > 0;

            // Disable inputs for requested number of frames
            if (window.disableInputsFrames.get() > 0) {
                decrement(window.disableInputsFrames);
                window.flags |= WindowFlags.NO_INPUTS;
            }

            // Update the skip items flag, used to early out of all items functions (no layout
            // required)
            window.skipItems =
                    (window.collapsed || !window.active || hiddenRegular)
                            && window.autoFitFramesX.get() <= 0
                            && window.autoFitFramesY.get() <= 0
                            && window.hiddenFramesCannotSkipItems.get() <= 0;

            // Restore the active layers to the previous value when not visible, so Ctrl+Tab back
            // can use a safe value
            if (window.skipItems) {
                window.navLayersActiveMaskNext = window.navLayersActiveMask;
            }
        }

        return !window.skipItems;
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param name The name of the child, may be null if using an ID.
     * @param id The ID of the child.
     * @param width The width, 0 to use the remaining width, negative to leave space on the right.
     * @param height The height, 0 to use the remaining height, negative to leave space below.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static boolean beginChild(
            String name, int id, float width, float height, int childFlags, int windowFlags) {
        final Window parentWindow = context.windowCurrent;
        if (id == 0) {
            IkGuiImplDebugTools.reportError(log, "Child windows require a non-zero ID");
            return false;
        }
        if ((windowFlags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Cannot specify WindowFlags.ALWAYS_AUTO_RESIZE for beginChild(). Use ChildFlags.ALWAYS_AUTO_RESIZE!");
            windowFlags &= ~WindowFlags.ALWAYS_AUTO_RESIZE;
        }

        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_CHILD_FLAGS) != 0) {
            childFlags |= context.nextWindowData.childFlags;
        }
        if ((childFlags & ChildFlags.ALWAYS_AUTO_RESIZE) != 0) {
            if ((childFlags & (ChildFlags.RESIZE_X | ChildFlags.RESIZE_Y)) != 0) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Cannot use ChildFlags.RESIZE_X or ChildFlags.RESIZE_Y with ChildFlags.ALWAYS_AUTO_RESIZE!");
            }
            if ((childFlags & (ChildFlags.AUTO_RESIZE_X | ChildFlags.AUTO_RESIZE_Y)) == 0) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Must use ChildFlags.AUTO_RESIZE_X or ChildFlags.AUTO_RESIZE_Y with ChildFlags.ALWAYS_AUTO_RESIZE!");
            }
        }
        if ((childFlags & ChildFlags.AUTO_RESIZE_X) != 0) {
            childFlags &= ~ChildFlags.RESIZE_X;
        }
        if ((childFlags & ChildFlags.AUTO_RESIZE_Y) != 0) {
            childFlags &= ~ChildFlags.RESIZE_Y;
        }

        // Set window flags
        windowFlags |=
                WindowFlags.INTERNAL_CHILD_WINDOW
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_DOCKING;
        windowFlags |= parentWindow.flags & WindowFlags.NO_MOVE; // Inherit the no move flag
        if ((childFlags
                        & (ChildFlags.AUTO_RESIZE_X
                                | ChildFlags.AUTO_RESIZE_Y
                                | ChildFlags.ALWAYS_AUTO_RESIZE))
                != 0) {
            windowFlags |= WindowFlags.ALWAYS_AUTO_RESIZE;
        }
        if ((childFlags & (ChildFlags.RESIZE_X | ChildFlags.RESIZE_Y)) == 0) {
            windowFlags |= WindowFlags.NO_RESIZE | WindowFlags.NO_SAVED_SETTINGS;
        }

        // Special framed style
        int styleVarsPushed = 0;
        if ((childFlags & ChildFlags.FRAME_STYLE) != 0) {
            IkGuiImplUtils.pushStyleColor(
                    ColorType.CHILD_BACKGROUND,
                    IkGuiImplUtils.getColor(ColorType.FRAME_BACKGROUND));
            IkGuiImplUtils.pushStyleVarFloat(
                    StyleVariable.CHILD_ROUNDING, context.style.variable.frameRounding);
            IkGuiImplUtils.pushStyleVarFloat(
                    StyleVariable.CHILD_BORDER_SIZE, context.style.variable.frameBorderSize);
            IkGuiImplUtils.pushStyleVarFloat2(
                    StyleVariable.WINDOW_PADDING,
                    context.style.variable.framePadding.x,
                    context.style.variable.framePadding.y);
            styleVarsPushed += 3;
            childFlags |= ChildFlags.BORDERS | ChildFlags.ALWAYS_USE_WINDOW_PADDING;
            windowFlags |= WindowFlags.NO_MOVE;
        }

        // Forward size. begin() has special processing to switch the condition to first use ever
        // for a given axis when ChildFlags.RESIZE_* is set.
        final Vector2f available = new Vector2f();
        IkGuiImplUtils.getContentRegionAvailable(available);
        final Vector2f size = new Vector2f(width, height);
        IkGuiInternal.calcItemSize(
                size,
                (childFlags & ChildFlags.AUTO_RESIZE_X) != 0 ? 0.0f : available.x,
                (childFlags & ChildFlags.AUTO_RESIZE_Y) != 0 ? 0.0f : available.y);

        // A setNextWindowSize() call always has priority
        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE) != 0
                && (context.nextWindowData.sizeCondition == Condition.ALWAYS
                        || context.nextWindowData.sizeCondition == Condition.NONE)) {
            if (context.nextWindowData.sizeValue.x > 0.0f) {
                size.x = context.nextWindowData.sizeValue.x;
                childFlags &= ~ChildFlags.RESIZE_X;
            }
            if (context.nextWindowData.sizeValue.y > 0.0f) {
                size.y = context.nextWindowData.sizeValue.y;
                childFlags &= ~ChildFlags.RESIZE_Y;
            }
        }
        setNextWindowSize(size.x, size.y, Condition.NONE);
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_CHILD_FLAGS;
        context.nextWindowData.childFlags = childFlags;

        // Build up the name. If you need to append to the same child from multiple locations in
        // the ID stack, use beginChild(int id) with a stable value.
        final String windowName;
        if (name != null) {
            windowName = String.format("%s/%s_%08X", parentWindow.name, name, id);
        } else {
            windowName = String.format("%s/%08X", parentWindow.name, id);
        }

        // Set style
        if ((childFlags & ChildFlags.BORDERS) == 0) {
            IkGuiImplUtils.pushStyleVarFloat(StyleVariable.CHILD_BORDER_SIZE, 0.0f);
            styleVarsPushed += 1;
        }

        // Begin into window
        final boolean result = begin(windowName, null, windowFlags);

        // Restore style
        if (styleVarsPushed > 0) {
            IkGuiImplUtils.popStyleVar(styleVarsPushed);
        }
        if ((childFlags & ChildFlags.FRAME_STYLE) != 0) {
            IkGuiImplUtils.popStyleColor();
        }

        final Window childWindow = context.windowCurrent;
        childWindow.childID = id;

        // Set the cursor to handle the case where the user called setNextWindowPos() +
        // beginChild() manually.
        if (childWindow.beginCount == 1) {
            parentWindow.cursorPosition.set(childWindow.position);
        }

        // Process navigation into the child immediately so the init request can run on the first
        // frame. We can enter a child if it has navigable items or it can be scrolled.
        final int tempIDForActivation = Hash.getID("##Child", id);
        if (context.activeID == tempIDForActivation) {
            IkGuiInternal.clearActiveID();
        }
        if (context.navActivateID == id
                && (childFlags & ChildFlags.NAV_FLATTENED) == 0
                && (childWindow.navLayersActiveMask != 0 || childWindow.navWindowHasScrollY)) {
            IkGuiInternal.focusWindow(childWindow, WindowFocusRequestFlags.NONE);
            IkGuiImplNav.navInitWindow(childWindow, false);
            // Steal the active ID with another arbitrary ID so the key press won't activate a
            // child item
            IkGuiInternal.setActiveID(tempIDForActivation, childWindow);
            context.activeIDSource = context.navInputSource;
        }
        return result;
    }

    /**
     * Set the window class of the next window, which controls docking compatibility.
     *
     * @param windowClass The window class, which is copied.
     */
    public static void setNextWindowClass(@NonNull WindowClass windowClass) {
        // Cannot set and clear the same bit
        if ((windowClass.viewportFlagsOverrideSet & windowClass.viewportFlagsOverrideClear) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Window class sets and clears the same viewport flags");
        }
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_WINDOW_CLASS;
        context.nextWindowData.windowClass.set(windowClass);
    }

    /**
     * Set a rectangular hole in a window that is not hit-tested, so the mouse can pass through to
     * windows below. Windows only support one hole.
     *
     * @param window The window.
     * @param posX The left of the hole, in screen coordinates.
     * @param posY The top of the hole, in screen coordinates.
     * @param sizeX The width of the hole.
     * @param sizeY The height of the hole.
     */
    static void setWindowHitTestHole(
            @NonNull Window window, float posX, float posY, float sizeX, float sizeY) {
        if (window.hitTestHoleSize.x != 0) {
            IkGuiImplDebugTools.reportError(log, "Windows don't support multiple hit test holes");
            return;
        }
        window.hitTestHoleSize.set((int) sizeX, (int) sizeY);
        window.hitTestHolePosition.set(
                (int) (posX - window.position.x), (int) (posY - window.position.y));
    }

    /** End the current window, every begin() needs a matching end(). */
    public static void end() {
        // Recover from missing endTable() calls. Ending a scrolling table also ends its child
        // window. These are done before popping the clip rect, unlike upstream, which recovers
        // them with the rest of the window state below.
        while (context.io.configErrorRecovery
                && context.currentTable != null
                && context.currentTable.innerWindow == context.windowCurrent) {
            IkGuiImplDebugTools.reportError(
                    log, "Missing endTable() in window {}", context.windowCurrent.name);
            IkGuiImplTables.endTable();
        }
        while (context.io.configErrorRecovery
                && context.currentMultiSelect != null
                && context.currentMultiSelect.storage.window == context.windowCurrent) {
            IkGuiImplDebugTools.reportError(
                    log, "Missing endMultiSelect() in window {}", context.windowCurrent.name);
            IkGuiImplMultiSelect.endMultiSelect();
        }
        final Window window = context.windowCurrent;

        // Error checking: verify that the user hasn't called end() too many times
        if (window == null
                || context.windowStack.isEmpty()
                || (context.windowStack.size() <= 1
                        && context.withinFrameScopeWithImplicitWindow)) {
            IkGuiImplDebugTools.reportError(log, "Calling end() too many times!");
            return;
        }

        final WindowStackData windowStackData = context.windowStack.getLast();

        // Error checking: verify that the user doesn't directly call end() on a popup or child
        if ((window.flags & WindowFlags.INTERNAL_POPUP) != 0
                && context.withinEndPopupID != window.id) {
            IkGuiImplDebugTools.reportError(log, "Must call endPopup() and not end()!");
        }
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                && (window.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0
                && !window.dockIsActive
                && context.withinEndChildID != window.id) {
            IkGuiImplDebugTools.reportError(log, "Must call endChild() and not end()!");
        }

        // Close anything that is open
        if ((window.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) == 0) {
            // Pop inner window clip rectangle
            popWindowClipRect(window);
        }
        IkGuiImplNav.popFocusScope();
        if (windowStackData.disabledOverrideReenable && window.rootWindow == window) {
            endDisabledOverrideReenable();
        }

        // Stop logging
        if (context.logWindow == window) {
            IkGuiImplLogging.logFinish();
        }

        // Docking: report contents sizes to parent to allow for auto-resize
        if (window.dockNode != null
                && window.dockTabIsVisible
                && window.dockNode.hostWindow != null) {
            final Window hostWindow = window.dockNode.hostWindow;
            hostWindow.cursorMaxPosition.set(window.cursorMaxPosition);
            hostWindow.cursorMaxPosition.add(window.padding).sub(hostWindow.padding);
        }

        // Pop from window stack
        context.lastItemData.set(windowStackData.parentLastItemDataBackup);
        if ((window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            context.beginMenuDepth--;
        }
        if ((window.flags & WindowFlags.INTERNAL_POPUP) != 0
                && !context.beginPopupStack.isEmpty()) {
            context.beginPopupStack.removeLast();
        }

        // Error handling, state recovery
        if (context.io.configErrorRecovery) {
            IkGuiInternal.errorRecoveryTryToRecoverWindowState(windowStackData.stackSizesInBegin);
        }

        context.windowStack.removeLast();
        setCurrentWindow(
                context.windowStack.isEmpty() ? null : context.windowStack.getLast().window);
        if (context.windowCurrent != null) {
            IkGuiImplViewports.setCurrentViewport(
                    context.windowCurrent, context.windowCurrent.viewport);
        }
    }

    /**
     * Set the current window, and the current table from the window.
     *
     * @param window The window, may be null.
     */
    static void setCurrentWindow(Window window) {
        context.windowCurrent = window;
        context.currentTable =
                window != null && window.currentTableIndex != -1
                        ? context.tables.get(window.currentTableIndex)
                        : null;
    }

    /** End a child window, every beginChild() needs a matching endChild(). */
    public static void endChild() {
        final Window childWindow = context.windowCurrent;
        if (childWindow == null || (childWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginChild()/endChild() calls");
            return;
        }

        final int backupWithinEndChildID = context.withinEndChildID;
        context.withinEndChildID = childWindow.id;
        final Vector2f childSize = new Vector2f(childWindow.size);
        end();
        if (childWindow.beginCount == 1) {
            final Window parentWindow = context.windowCurrent;
            final RectFloat bb =
                    new RectFloat(
                            parentWindow.cursorPosition.x,
                            parentWindow.cursorPosition.y,
                            parentWindow.cursorPosition.x + childSize.x,
                            parentWindow.cursorPosition.y + childSize.y);
            IkGuiInternal.itemSize(childSize.x, childSize.y);
            final boolean navFlattened =
                    (childWindow.flagsAsChildWindow & ChildFlags.NAV_FLATTENED) != 0;
            if ((childWindow.navLayersActiveMask != 0 || childWindow.navWindowHasScrollY)
                    && !navFlattened) {
                IkGuiInternal.itemAdd(bb, childWindow.childID);
                IkGuiImplNav.renderNavCursor(
                        bb, childWindow.childID, NavRenderCursorFlags.NONE, -1.0f);

                // When browsing a window that has no activatable items (scroll only) we keep a
                // highlight on the child
                if (childWindow.navLayersActiveMask == 0
                        && childWindow == context.navFocusedWindow) {
                    IkGuiImplNav.renderNavCursor(
                            new RectFloat(
                                    bb.getLeft() - 2,
                                    bb.getTop() - 2,
                                    bb.getRight() + 2,
                                    bb.getBottom() + 2),
                            context.navID,
                            NavRenderCursorFlags.COMPACT,
                            -1.0f);
                }
            } else {
                // Not navigable into. This is mostly useful for undecorated, non-scrolling
                // contents, or empty children.
                IkGuiInternal.itemAdd(bb, childWindow.childID, null, ItemFlags.NO_NAV);

                // But when flattened we directly reach items, so adjust the active layer mask
                if (navFlattened) {
                    parentWindow.navLayersActiveMaskNext |= childWindow.navLayersActiveMaskNext;
                }
            }
            if (context.windowHovered == childWindow) {
                context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_WINDOW;
            }
            childWindow.childItemStatusFlags = context.lastItemData.statusFlags;
        } else {
            IkGuiInternal.setLastItemData(
                    childWindow.childID,
                    context.currentItemFlags,
                    childWindow.childItemStatusFlags,
                    childWindow.rectOuter);
        }

        context.withinEndChildID = backupWithinEndChildID;
    }

    public static int getWindowDockID() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.dockID;
    }

    public static DrawList getWindowDrawList() {
        return IkGuiInternal.getCurrentWindow().drawList;
    }

    public static float getWindowHeight() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.size.y;
    }

    public static void getWindowPos(@NonNull Vector2f pos) {
        Window window = context.windowCurrent;
        if (window == null) {
            pos.set(0, 0);
        } else {
            pos.set(window.position);
        }
    }

    public static float getWindowPosX() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.position.x;
    }

    public static float getWindowPosY() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.position.y;
    }

    public static void getWindowSize(@NonNull Vector2f size) {
        Window window = context.windowCurrent;
        if (window == null) {
            size.set(0, 0);
        } else {
            size.set(window.size);
        }
    }

    public static float getWindowWidth() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.size.x;
    }

    public static void setNextWindowBgAlpha(float alpha) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_BACKGROUND_ALPHA;
        context.nextWindowData.backgroundAlpha = alpha;
    }

    public static void setNextWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_COLLAPSED;
        context.nextWindowData.collapsedValue = collapsed;
        context.nextWindowData.collapsedCondition = condition;
    }

    public static void setNextWindowContentSize(float width, float height) {
        // In begin() we will add the size of window decorations (title bar, menu etc.) to that to
        // form a complete window size
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_CONTENT_SIZE;
        context.nextWindowData.contentSizeValue.set(width, height);
        IkGuiInternal.truncate(context.nextWindowData.contentSizeValue);
    }

    public static void setNextWindowDockID(int id, @NonNull Condition condition) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_DOCK;
        context.nextWindowData.dockID = id;
        context.nextWindowData.dockCondition = condition;
    }

    public static void setNextWindowFocus() {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_FOCUS;
    }

    public static void setNextWindowPos(
            float x, float y, @NonNull Condition condition, float pivotX, float pivotY) {
        if (pivotX < 0f || pivotX > 1.0f) {
            IkGuiImplDebugTools.reportError(
                    log, "Invalid pivotX in setNextWindowPos, should be in the range [0, 1]");
            return;
        }
        if (pivotY < 0f || pivotY > 1.0f) {
            IkGuiImplDebugTools.reportError(
                    log, "Invalid pivotY in setNextWindowPos, should be in the range [0, 1]");
            return;
        }

        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_POSITION;
        context.nextWindowData.positionPivot.set(pivotX, pivotY);
        context.nextWindowData.positionValue.set(x, y);
        context.nextWindowData.positionCondition = condition;
        context.nextWindowData.positionUndock = true;
    }

    public static void setNextWindowScroll(float x, float y) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_SCROLL;
        context.nextWindowData.scrollValue.set(x, y);
    }

    public static void setNextWindowSize(float x, float y, @NonNull Condition condition) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_SIZE;
        context.nextWindowData.sizeCondition = condition;
        context.nextWindowData.sizeValue.set(x, y);
    }

    public static void setNextWindowSizeConstraints(
            float minWidth, float minHeight, float maxWidth, float maxHeight) {
        setNextWindowSizeConstraints(minWidth, minHeight, maxWidth, maxHeight, null);
    }

    public static void setNextWindowSizeConstraints(
            float minWidth,
            float minHeight,
            float maxWidth,
            float maxHeight,
            Consumer<SizeCallbackData> callback) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_SIZE_CONSTRAINT;
        context.nextWindowData.sizeConstraintRect.set(minWidth, minHeight, maxWidth, maxHeight);
        context.nextWindowData.sizeCallback = callback;
    }

    public static void setWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        setWindowCollapsed(context.windowCurrent, collapsed, condition);
    }

    public static void setWindowCollapsed(
            @NonNull Window window, boolean collapsed, @NonNull Condition condition) {
        if (!ConditionAllowed.shouldResolve(condition, window.collapsedConditionAllowed)) {
            return;
        }
        window.collapsedConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);

        // Queue the collapse request, which is applied in begin()
        window.collapseToggleRequested = window.collapsed != collapsed;
    }

    public static void setWindowFocus() {
        IkGuiInternal.focusWindow(context.windowCurrent, WindowFocusRequestFlags.NONE);
    }

    public static void setWindowFocus(String name) {
        if (name == null) {
            IkGuiInternal.focusWindow(null, WindowFocusRequestFlags.NONE);
            return;
        }
        Window window = IkGuiInternal.findWindowByName(name);
        if (window != null) {
            IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
        }
    }

    public static void setWindowPos(
            @NonNull Window window, float x, float y, @NonNull Condition condition) {
        if (!ConditionAllowed.shouldResolve(condition, window.positionConditionAllowed)) {
            return;
        }

        window.positionConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);
        window.setWindowPosValue.set(Float.MAX_VALUE, Float.MAX_VALUE);

        final float oldX = window.position.x;
        final float oldY = window.position.y;

        window.position.set(x, y);
        IkGuiInternal.truncate(window.position);
        final float offsetX = window.position.x - oldX;
        final float offsetY = window.position.y - oldY;
        if (offsetX == 0.0f && offsetY == 0.0f) {
            return;
        }

        IkGuiInternal.markIniSettingsDirty(window);
        window.cursorPosition.add(offsetX, offsetY);
        window.cursorMaxPosition.add(offsetX, offsetY);
        window.cursorIdealMaxPosition.add(offsetX, offsetY);
        window.cursorStartPosition.add(offsetX, offsetY);
    }

    public static void setWindowSize(
            @NonNull Window window, float x, float y, @NonNull Condition condition) {
        if (!ConditionAllowed.shouldResolve(condition, window.sizeConditionAllowed)) {
            return;
        }
        window.sizeConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);

        // Enable auto-fit (not done in beginChild() path unless appearing or combined with
        // ChildFlags.ALWAYS_AUTO_RESIZE)
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0
                || window.appearing
                || (window.flagsAsChildWindow & ChildFlags.ALWAYS_AUTO_RESIZE) != 0) {
            window.autoFitFramesX.set(x <= 0.0f ? 2 : 0);
            window.autoFitFramesY.set(y <= 0.0f ? 2 : 0);
        }

        final float oldX = window.sizeFull.x;
        final float oldY = window.sizeFull.y;
        if (x <= 0.0f) {
            window.autoFitOnlyGrows = false;
        } else {
            window.sizeFull.x = IkGuiInternal.truncate(x);
        }
        if (y <= 0.0f) {
            window.autoFitOnlyGrows = false;
        } else {
            window.sizeFull.y = IkGuiInternal.truncate(y);
        }
        if (oldX != window.sizeFull.x || oldY != window.sizeFull.y) {
            IkGuiInternal.markIniSettingsDirty(window);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Calculate the content size of a window, based on the cursor positions from last frame.
     *
     * @param window The window.
     * @param contentSizeCurrent Where to store the content size.
     * @param contentSizeIdeal Where to store the ideal content size.
     */
    static void calcWindowContentSizes(
            @NonNull Window window,
            @NonNull Vector2f contentSizeCurrent,
            @NonNull Vector2f contentSizeIdeal) {
        boolean preserveOldContentSizes = false;
        if (window.collapsed
                && window.autoFitFramesX.get() <= 0
                && window.autoFitFramesY.get() <= 0) {
            preserveOldContentSizes = true;
        } else if (window.hidden
                && window.hiddenFramesCannotSkipItems.get() == 0
                && window.hiddenFramesCanSkipItems.get() > 0) {
            preserveOldContentSizes = true;
        }
        if (preserveOldContentSizes) {
            contentSizeCurrent.set(window.contentSize);
            contentSizeIdeal.set(window.contentSizeIdeal);
            return;
        }

        contentSizeCurrent.set(
                window.contentSizeExplicit.x != 0.0f
                        ? window.contentSizeExplicit.x
                        : IkGuiInternal.truncate(
                                window.cursorMaxPosition.x - window.cursorStartPosition.x),
                window.contentSizeExplicit.y != 0.0f
                        ? window.contentSizeExplicit.y
                        : IkGuiInternal.truncate(
                                window.cursorMaxPosition.y - window.cursorStartPosition.y));
        contentSizeIdeal.set(
                window.contentSizeExplicit.x != 0.0f
                        ? window.contentSizeExplicit.x
                        : IkGuiInternal.truncate(
                                Math.max(
                                                window.cursorMaxPosition.x,
                                                window.cursorIdealMaxPosition.x)
                                        - window.cursorStartPosition.x),
                window.contentSizeExplicit.y != 0.0f
                        ? window.contentSizeExplicit.y
                        : IkGuiInternal.truncate(
                                Math.max(
                                                window.cursorMaxPosition.y,
                                                window.cursorIdealMaxPosition.y)
                                        - window.cursorStartPosition.y));
    }

    /**
     * Calculate the minimum size of a window.
     *
     * @param window The window.
     * @param output Where to store the result.
     * @return The output vector, for convenience.
     */
    static Vector2f calcWindowMinSize(@NonNull Window window, @NonNull Vector2f output) {
        final Vector2f styleMin = context.style.variable.windowMinSize;
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                && (window.flags & WindowFlags.INTERNAL_POPUP) == 0) {
            output.set(
                    (window.flagsAsChildWindow & ChildFlags.RESIZE_X) != 0
                            ? styleMin.x
                            : WINDOW_HARD_MIN_SIZE,
                    (window.flagsAsChildWindow & ChildFlags.RESIZE_Y) != 0
                            ? styleMin.y
                            : WINDOW_HARD_MIN_SIZE);
        } else {
            final boolean autoResize = (window.flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0;
            output.set(
                    autoResize ? WINDOW_HARD_MIN_SIZE : styleMin.x,
                    autoResize ? WINDOW_HARD_MIN_SIZE : styleMin.y);
        }

        // Reduce artifacts with very small windows
        final Window windowForHeight =
                (window.dockNodeAsHost != null && window.dockNodeAsHost.visibleWindow != null)
                        ? window.dockNodeAsHost.visibleWindow
                        : window;
        output.y =
                Math.max(
                        output.y,
                        windowForHeight.titleBarHeight
                                + windowForHeight.menuBarHeight
                                + Math.max(0.0f, context.style.variable.windowRounding - 1.0f));
        return output;
    }

    /**
     * Apply the size constraints and minimum size to a desired size.
     *
     * @param window The window.
     * @param sizeDesired The desired size.
     * @param output Where to store the result, may be the same as sizeDesired.
     * @return The output vector, for convenience.
     */
    static Vector2f calcWindowSizeAfterConstraint(
            @NonNull Window window, @NonNull Vector2f sizeDesired, @NonNull Vector2f output) {
        float newX = sizeDesired.x;
        float newY = sizeDesired.y;
        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE_CONSTRAINT) != 0) {
            final RectFloat constraint = context.nextWindowData.sizeConstraintRect;
            newX =
                    (constraint.getLeft() >= 0 && constraint.getRight() >= 0)
                            ? MathUtil.clamp(newX, constraint.getLeft(), constraint.getRight())
                            : window.sizeFull.x;
            newY =
                    (constraint.getTop() >= 0 && constraint.getBottom() >= 0)
                            ? MathUtil.clamp(newY, constraint.getTop(), constraint.getBottom())
                            : window.sizeFull.y;
            final Consumer<SizeCallbackData> callback = context.nextWindowData.sizeCallback;
            if (callback != null) {
                final SizeCallbackData data = new SizeCallbackData();
                data.position.set(window.position);
                data.currentSize.set(window.sizeFull);
                data.desiredSize.set(newX, newY);
                callback.accept(data);
                newX = data.desiredSize.x;
                newY = data.desiredSize.y;
            }
            newX = IkGuiInternal.truncate(newX);
            newY = IkGuiInternal.truncate(newY);
        }

        // Minimum size
        final Vector2f sizeMin = calcWindowMinSize(window, new Vector2f());
        return output.set(Math.max(newX, sizeMin.x), Math.max(newY, sizeMin.y));
    }

    /**
     * Calculate the size the window would need to be to fit the contents.
     *
     * @param window The window.
     * @param sizeContents The size of the contents.
     * @param axisMask Which axes to fit, 1 for x, 2 for y.
     * @param output Where to store the result.
     * @return The output vector, for convenience.
     */
    static Vector2f calcWindowAutoFitSize(
            @NonNull Window window,
            @NonNull Vector2f sizeContents,
            int axisMask,
            @NonNull Vector2f output) {
        final StyleVariables style = context.style.variable;
        final float decorationWidthWithoutScrollbars =
                window.decoOuterSizeX1 + window.decoOuterSizeX2 - window.scrollbarSizes.x;
        final float decorationHeightWithoutScrollbars =
                window.decoOuterSizeY1 + window.decoOuterSizeY2 - window.scrollbarSizes.y;
        final float padX = window.padding.x * 2.0f;
        final float padY = window.padding.y * 2.0f;
        final float desiredX =
                (axisMask & 1) != 0
                        ? sizeContents.x + padX + decorationWidthWithoutScrollbars
                        : window.size.x;
        final float desiredY =
                (axisMask & 2) != 0
                        ? sizeContents.y + padY + decorationHeightWithoutScrollbars
                        : window.size.y;

        // Determine the maximum window size. Child windows are laid within their parent (unless
        // they are also popups/menus) and thus have no restriction.
        float maxX = Float.MAX_VALUE;
        float maxY = Float.MAX_VALUE;
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0
                || (window.flags & WindowFlags.INTERNAL_POPUP) != 0) {
            if (!window.viewportOwned) {
                final Viewport mainViewport = IkGuiImplViewports.getMainViewport();
                maxX = mainViewport.workSize.x - style.displaySafeAreaPadding.x * 2.0f;
                maxY = mainViewport.workSize.y - style.displaySafeAreaPadding.y * 2.0f;
            }
            final int monitorIndex = window.viewportAllowPlatformMonitorExtend;
            if (monitorIndex >= 0 && monitorIndex < context.platformIO.monitors.size()) {
                final PlatformMonitor monitor = context.platformIO.monitors.get(monitorIndex);
                maxX = monitor.workSize.x - style.displaySafeAreaPadding.x * 2.0f;
                maxY = monitor.workSize.y - style.displaySafeAreaPadding.y * 2.0f;
            }
        }

        if ((window.flags & WindowFlags.INTERNAL_TOOLTIP) != 0) {
            // Tooltips always resize (up to maximum size)
            return output.set(Math.min(desiredX, maxX), Math.min(desiredY, maxY));
        }

        final Vector2f sizeMin = calcWindowMinSize(window, new Vector2f());
        final Vector2f sizeAutoFit =
                new Vector2f(
                        MathUtil.clamp(
                                desiredX,
                                Math.min(sizeMin.x, maxX),
                                Math.max(maxX, Math.min(sizeMin.x, maxX))),
                        MathUtil.clamp(
                                desiredY,
                                Math.min(sizeMin.y, maxY),
                                Math.max(maxY, Math.min(sizeMin.y, maxY))));

        // When the window cannot fit all contents (either because of constraints, or because the
        // screen is too small), we grow the size on the other axis to compensate for the expected
        // scrollbar
        final Vector2f sizeAutoFitAfterConstraint =
                calcWindowSizeAfterConstraint(window, sizeAutoFit, new Vector2f());
        final float sizeContentsForScrollbarX =
                (axisMask & 1) != 0 ? sizeContents.x : window.contentSize.x;
        final float sizeContentsForScrollbarY =
                (axisMask & 2) != 0 ? sizeContents.y : window.contentSize.y;
        final boolean noScrollbar = (window.flags & WindowFlags.NO_SCROLLBAR) != 0;
        final boolean willHaveScrollbarX =
                (sizeAutoFitAfterConstraint.x
                                        < sizeContentsForScrollbarX
                                                + padX
                                                + decorationWidthWithoutScrollbars
                                && !noScrollbar
                                && (window.flags & WindowFlags.HORIZONTAL_SCROLLBAR) != 0)
                        || (window.flags & WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR) != 0;
        final boolean willHaveScrollbarY =
                (sizeAutoFitAfterConstraint.y
                                        < sizeContentsForScrollbarY
                                                + padY
                                                + decorationHeightWithoutScrollbars
                                && !noScrollbar)
                        || (window.flags & WindowFlags.ALWAYS_VERTICAL_SCROLLBAR) != 0;
        if (willHaveScrollbarX) {
            sizeAutoFit.y += style.scrollbarSize;
        }
        if (willHaveScrollbarY) {
            sizeAutoFit.x += style.scrollbarSize;
        }
        return output.set(sizeAutoFit);
    }

    /**
     * Calculate the next scroll position based on the scroll target, clamped to the valid range.
     *
     * @param window The window.
     * @param output Where to store the result, may be the window scroll position.
     */
    static void calcNextScrollFromScrollTargetAndClamp(
            @NonNull Window window, @NonNull Vector2f output) {
        float scrollX = window.scrollPosition.x;
        float scrollY = window.scrollPosition.y;
        final float decorationSizeX =
                window.decoOuterSizeX1 + window.decoInnerSizeX1 + window.decoOuterSizeX2;
        final float decorationSizeY =
                window.decoOuterSizeY1 + window.decoInnerSizeY1 + window.decoOuterSizeY2;

        if (window.scrollTarget.x < Float.MAX_VALUE) {
            float centerRatio = window.scrollTargetCenterRatio.x;
            float scrollTarget = window.scrollTarget.x;
            if (window.scrollTargetEdgeSnapDist.x > 0.0f) {
                final float snapMax = window.scrollMax.x + window.sizeFull.x - decorationSizeX;
                scrollTarget =
                        calcScrollEdgeSnap(
                                scrollTarget,
                                0.0f,
                                snapMax,
                                window.scrollTargetEdgeSnapDist.x,
                                centerRatio);
            }
            scrollX = scrollTarget - centerRatio * (window.sizeFull.x - decorationSizeX);
        }
        if (window.scrollTarget.y < Float.MAX_VALUE) {
            float centerRatio = window.scrollTargetCenterRatio.y;
            float scrollTarget = window.scrollTarget.y;
            if (window.scrollTargetEdgeSnapDist.y > 0.0f) {
                final float snapMax = window.scrollMax.y + window.sizeFull.y - decorationSizeY;
                scrollTarget =
                        calcScrollEdgeSnap(
                                scrollTarget,
                                0.0f,
                                snapMax,
                                window.scrollTargetEdgeSnapDist.y,
                                centerRatio);
            }
            scrollY = scrollTarget - centerRatio * (window.sizeFull.y - decorationSizeY);
        }

        scrollX = Math.round(Math.max(scrollX, 0.0f));
        scrollY = Math.round(Math.max(scrollY, 0.0f));
        if (!window.collapsed && !window.skipItems) {
            scrollX = Math.min(scrollX, window.scrollMax.x);
            scrollY = Math.min(scrollY, window.scrollMax.y);
        }
        output.set(scrollX, scrollY);
    }

    /**
     * Snap to edges when aiming at an item very close to the edge.
     *
     * @param target The target scroll position.
     * @param snapMin The minimum edge.
     * @param snapMax The maximum edge.
     * @param snapThreshold How close to the edge we need to be to snap.
     * @param centerRatio The center ratio for the scroll.
     * @return The adjusted target.
     */
    private static float calcScrollEdgeSnap(
            float target, float snapMin, float snapMax, float snapThreshold, float centerRatio) {
        if (target <= snapMin + snapThreshold) {
            return snapMin + (target - snapMin) * centerRatio;
        }
        if (target >= snapMax - snapThreshold) {
            return target + (snapMax - target) * centerRatio;
        }
        return target;
    }

    /**
     * Clamp the window position so that it stays visible within the visibility rect.
     *
     * @param window The window.
     * @param visibilityRect The rect the window must stay (at least partially) inside.
     */
    private static void clampWindowPos(@NonNull Window window, @NonNull RectFloat visibilityRect) {
        float sizeForClampingX = window.size.x;
        float sizeForClampingY = window.size.y;
        final boolean moveFromTitleBarOnly = context.io.configWindowsMoveFromTitleBarOnly;
        if (moveFromTitleBarOnly && window.dockNodeAsHost != null) {
            sizeForClampingY = IkGuiImplLayout.getFrameHeight();
        } else if (moveFromTitleBarOnly && (window.flags & WindowFlags.NO_TITLE_BAR) == 0) {
            sizeForClampingY = window.titleBarHeight;
        }
        window.position.set(
                MathUtil.clamp(
                        window.position.x,
                        visibilityRect.getLeft() - sizeForClampingX,
                        Math.max(
                                visibilityRect.getRight(),
                                visibilityRect.getLeft() - sizeForClampingX)),
                MathUtil.clamp(
                        window.position.y,
                        visibilityRect.getTop() - sizeForClampingY,
                        Math.max(
                                visibilityRect.getBottom(),
                                visibilityRect.getTop() - sizeForClampingY)));
    }

    /**
     * Create a new window, and add it to the context.
     *
     * @param name The window name.
     * @param windowFlags The window flags.
     * @return The newly created window.
     */
    private static Window createNewWindow(@NonNull String name, int windowFlags) {
        Window window = new Window(name);
        window.flags = windowFlags;
        context.windowByID.put(window.id, window);

        WindowSettings settings = null;
        if ((windowFlags & WindowFlags.NO_SAVED_SETTINGS) == 0) {
            settings = IkGuiImplConfig.findWindowSettingsByWindow(window);
            window.settings = settings;
        }
        IkGuiImplConfig.initOrLoadWindowSettings(window, settings);

        if ((windowFlags & WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS) != 0) {
            context.windowDisplayOrder.addFirst(window);
        } else {
            context.windowDisplayOrder.add(window);
        }
        return window;
    }

    /**
     * Decrement a byte counter if it is positive.
     *
     * @param value The counter.
     */
    private static void decrement(@NonNull IkByte value) {
        if (value.get() > 0) {
            value.set(value.get() - 1);
        }
    }

    /**
     * Calculate the IDs used for resizing a window.
     *
     * @param window The window.
     * @param n The grip (0-3) or border (4-7) number.
     * @return The ID.
     */
    static int getWindowResizeID(@NonNull Window window, int n) {
        final int id =
                window.dockIsActive && window.dockNode.hostWindow != null
                        ? window.dockNode.hostWindow.id
                        : window.id;
        return Hash.getID(n, Hash.getID("#RESIZE", id));
    }

    /**
     * Get the rectangle used for interacting with a resize border.
     *
     * @param window The window.
     * @param borderIndex Which border, 0-3 for left, right, up, down.
     * @param perpendicularPadding Padding to shrink the border by along its length.
     * @param thickness The thickness of the border on each side.
     * @return The border rectangle.
     */
    private static RectFloat getResizeBorderRect(
            @NonNull Window window, int borderIndex, float perpendicularPadding, float thickness) {
        RectFloat rect = new RectFloat();
        rect.set(window.updateRectOuter());
        if (thickness == 0.0f) {
            rect.setRight(rect.getRight() - 1);
            rect.setBottom(rect.getBottom() - 1);
        }
        return switch (borderIndex) {
            case 0 ->
                    new RectFloat(
                            rect.getLeft() - thickness,
                            rect.getTop() + perpendicularPadding,
                            rect.getLeft() + thickness,
                            rect.getBottom() - perpendicularPadding);
            case 1 ->
                    new RectFloat(
                            rect.getRight() - thickness,
                            rect.getTop() + perpendicularPadding,
                            rect.getRight() + thickness,
                            rect.getBottom() - perpendicularPadding);
            case 2 ->
                    new RectFloat(
                            rect.getLeft() + perpendicularPadding,
                            rect.getTop() - thickness,
                            rect.getRight() - perpendicularPadding,
                            rect.getTop() + thickness);
            default ->
                    new RectFloat(
                            rect.getLeft() + perpendicularPadding,
                            rect.getBottom() - thickness,
                            rect.getRight() - perpendicularPadding,
                            rect.getBottom() + thickness);
        };
    }

    /**
     * Calculate the new position and size of a window when resizing from one of the corners.
     *
     * @param window The window.
     * @param cornerTargetX The target x position of the corner.
     * @param cornerTargetY The target y position of the corner.
     * @param cornerNormX The normalized x position of the corner (0 or 1).
     * @param cornerNormY The normalized y position of the corner (0 or 1).
     * @param outPosition The resulting position.
     * @param outSize The resulting size.
     */
    private static void calcResizePosSizeFromAnyCorner(
            @NonNull Window window,
            float cornerTargetX,
            float cornerTargetY,
            float cornerNormX,
            float cornerNormY,
            @NonNull Vector2f outPosition,
            @NonNull Vector2f outSize) {
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            // Clamp resizing of children within the parent
            final Window parentWindow = window.parentWindow;
            final int parentFlags = parentWindow.flags;
            final RectFloat limitRect = new RectFloat();
            limitRect.set(parentWindow.rectInner);
            limitRect.expand(
                    -Math.max(parentWindow.padding.x, parentWindow.borderSize),
                    -Math.max(parentWindow.padding.y, parentWindow.borderSize));
            if ((parentFlags
                                    & (WindowFlags.HORIZONTAL_SCROLLBAR
                                            | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR))
                            == 0
                    || (parentFlags & WindowFlags.NO_SCROLLBAR) != 0) {
                cornerTargetX =
                        MathUtil.clamp(
                                cornerTargetX,
                                limitRect.getLeft(),
                                Math.max(limitRect.getLeft(), limitRect.getRight()));
            }
            if ((parentFlags & WindowFlags.NO_SCROLLBAR) != 0) {
                cornerTargetY =
                        MathUtil.clamp(
                                cornerTargetY,
                                limitRect.getTop(),
                                Math.max(limitRect.getTop(), limitRect.getBottom()));
            }
        }
        // Expected window upper-left
        final float posMinX = lerp(cornerTargetX, window.position.x, cornerNormX);
        final float posMinY = lerp(cornerTargetY, window.position.y, cornerNormY);
        // Expected window lower-right
        final float posMaxX = lerp(window.position.x + window.size.x, cornerTargetX, cornerNormX);
        final float posMaxY = lerp(window.position.y + window.size.y, cornerTargetY, cornerNormY);
        final Vector2f sizeExpected = new Vector2f(posMaxX - posMinX, posMaxY - posMinY);
        final Vector2f sizeConstrained =
                calcWindowSizeAfterConstraint(window, sizeExpected, new Vector2f());
        outPosition.set(posMinX, posMinY);
        if (cornerNormX == 0.0f) {
            outPosition.x -= sizeConstrained.x - sizeExpected.x;
        }
        if (cornerNormY == 0.0f) {
            outPosition.y -= sizeConstrained.y - sizeExpected.y;
        }
        outSize.set(sizeConstrained);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Handle manual resizing with resize grips and borders.
     *
     * @param window The window.
     * @param borderHovered Output for which border is hovered (single element array), -1 if none.
     * @param borderHeld Output for which border is held (single element array), -1 if none.
     * @param resizeGripCount The number of resize grips.
     * @param resizeGripColors Output colors for each resize grip.
     * @param visibilityRect The rect the window must stay visible within.
     * @return A mask of which axes were auto-fit (1 for x, 2 for y), when double-clicking.
     */
    private static int updateWindowManualResize(
            @NonNull Window window,
            int[] borderHovered,
            int[] borderHeld,
            int resizeGripCount,
            int[] resizeGripColors,
            @NonNull RectFloat visibilityRect) {
        final int flags = window.flags;

        if ((flags & WindowFlags.NO_RESIZE) != 0
                || window.autoFitFramesX.get() > 0
                || window.autoFitFramesY.get() > 0) {
            return 0;
        }
        if ((flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0
                && (window.flagsAsChildWindow & (ChildFlags.RESIZE_X | ChildFlags.RESIZE_Y)) == 0) {
            return 0;
        }
        if (!window.wasActive) {
            // Early out to avoid running this code for e.g. a hidden implicit/fallback window
            return 0;
        }

        int returnAutoFitMask = 0;
        final float fontSize = IkGuiInternal.getFontSize();

        // Resize grips and borders are on the menu layer
        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;
        final float gripDrawSize =
                IkGuiInternal.truncate(
                        Math.max(fontSize * 1.35f, window.rounding + 1.0f + fontSize * 0.2f));
        final float gripHoverInnerSize =
                resizeGripCount > 0 ? IkGuiInternal.truncate(gripDrawSize * 0.75f) : 0.0f;
        final float gripHoverOuterSize = context.windowBorderHoverPadding;

        final RectFloat clampRect = new RectFloat();
        clampRect.set(visibilityRect);
        final boolean windowMoveFromTitleBar =
                context.io.configWindowsMoveFromTitleBarOnly
                        && (window.flags & WindowFlags.NO_TITLE_BAR) == 0;
        if (windowMoveFromTitleBar) {
            clampRect.setTop(clampRect.getTop() - window.titleBarHeight);
        }

        final Vector2f positionTarget = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        final Vector2f sizeTarget = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);

        // Clip mouse interaction rectangles within the viewport rectangle. We only clip
        // interaction so we overwrite the clip rect, the draw list is not set up yet. When the
        // backend can tell us the mouse is over an undecorated viewport, we don't narrow, which
        // benefits OS windows without decorations that have a threshold for hovering outside
        // their limits.
        final boolean clipWithViewportRect =
                (context.io.backendFlags & BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT) == 0
                        || context.io.mouseHoveredViewport != window.viewportID
                        || (window.viewport.flags & ViewportFlags.NO_DECORATION) == 0;
        if (clipWithViewportRect) {
            window.viewport.getMainRect(window.rectCurrentClip);
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();

        // Manual resize grips
        for (int gripIndex = 0; gripIndex < resizeGripCount; ++gripIndex) {
            final float[] def = RESIZE_GRIP_DEF[gripIndex];
            final float cornerX =
                    lerp(window.position.x, window.position.x + window.size.x, def[0]);
            final float cornerY =
                    lerp(window.position.y, window.position.y + window.size.y, def[1]);

            // Using the flatten children flag we make the resize button accessible even if we are
            // hovering over a child window
            final float x1 = cornerX - def[2] * gripHoverOuterSize;
            final float y1 = cornerY - def[3] * gripHoverOuterSize;
            final float x2 = cornerX + def[2] * gripHoverInnerSize;
            final float y2 = cornerY + def[3] * gripHoverInnerSize;
            final RectFloat resizeRect =
                    new RectFloat(
                            Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
            final int resizeGripID = getWindowResizeID(window, gripIndex);
            IkGuiInternal.itemAdd(resizeRect, resizeGripID, null, ItemFlags.NO_NAV);
            IkGuiInternal.buttonBehavior(
                    resizeRect,
                    resizeGripID,
                    hovered,
                    held,
                    ButtonFlags.INTERNAL_FLATTEN_CHILDREN | ButtonFlags.INTERNAL_NO_NAV_FOCUS);
            if (hovered.get() || held.get()) {
                context.mouseCursor =
                        (gripIndex & 1) != 0 ? MouseCursor.RESIZE_NE_SW : MouseCursor.RESIZE_NW_SE;
            }

            if (held.get() && context.io.getMouseDoubleClicked(MouseButton.LEFT)) {
                // Auto-fit when double-clicking
                final Vector2f sizeAutoFit =
                        calcWindowAutoFitSize(window, window.contentSizeIdeal, ~0, new Vector2f());
                calcWindowSizeAfterConstraint(window, sizeAutoFit, sizeTarget);
                returnAutoFitMask = 0x03;
                IkGuiInternal.clearActiveID();
            } else if (held.get()) {
                // Resize from any of the four corners. We don't use an incremental mouse delta but
                // rather compute an absolute target size based on mouse position.
                final float clampMinX = def[0] == 1.0f ? clampRect.getLeft() : -Float.MAX_VALUE;
                final float clampMinY =
                        (def[1] == 1.0f || (def[1] == 0.0f && windowMoveFromTitleBar))
                                ? clampRect.getTop()
                                : -Float.MAX_VALUE;
                final float clampMaxX = def[0] == 0.0f ? clampRect.getRight() : Float.MAX_VALUE;
                final float clampMaxY = def[1] == 0.0f ? clampRect.getBottom() : Float.MAX_VALUE;
                // Corner of the window corresponding to our corner grip
                float cornerTargetX =
                        context.io.mousePosition.x
                                - context.activeIDClickOffset.x
                                + lerp(
                                        def[2] * gripHoverOuterSize,
                                        def[2] * -gripHoverInnerSize,
                                        def[0]);
                float cornerTargetY =
                        context.io.mousePosition.y
                                - context.activeIDClickOffset.y
                                + lerp(
                                        def[3] * gripHoverOuterSize,
                                        def[3] * -gripHoverInnerSize,
                                        def[1]);
                cornerTargetX =
                        MathUtil.clamp(cornerTargetX, clampMinX, Math.max(clampMinX, clampMaxX));
                cornerTargetY =
                        MathUtil.clamp(cornerTargetY, clampMinY, Math.max(clampMinY, clampMaxY));
                calcResizePosSizeFromAnyCorner(
                        window,
                        cornerTargetX,
                        cornerTargetY,
                        def[0],
                        def[1],
                        positionTarget,
                        sizeTarget);
            }

            // Only lower-left grip is visible before hovering/activating
            final boolean resizeGripVisible =
                    held.get()
                            || hovered.get()
                            || (gripIndex == 0
                                    && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0);
            if (resizeGripVisible) {
                resizeGripColors[gripIndex] =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                held.get()
                                        ? ColorType.RESIZE_GRIP_ACTIVE
                                        : hovered.get()
                                                ? ColorType.RESIZE_GRIP_HOVERED
                                                : ColorType.RESIZE_GRIP);
            }
        }

        int resizeBorderMask = 0;
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            resizeBorderMask |= (window.flagsAsChildWindow & ChildFlags.RESIZE_X) != 0 ? 0x02 : 0;
            resizeBorderMask |= (window.flagsAsChildWindow & ChildFlags.RESIZE_Y) != 0 ? 0x08 : 0;
        } else {
            resizeBorderMask = context.io.configWindowsResizeFromEdges ? 0x0F : 0x00;
        }
        for (int borderIndex = 0; borderIndex < 4; ++borderIndex) {
            if ((resizeBorderMask & (1 << borderIndex)) == 0) {
                continue;
            }
            final float[] def = RESIZE_BORDER_DEF[borderIndex];
            final boolean axisX = borderIndex == 0 || borderIndex == 1;

            final RectFloat borderRect =
                    getResizeBorderRect(
                            window,
                            borderIndex,
                            gripHoverInnerSize,
                            context.windowBorderHoverPadding);
            final int borderID = getWindowResizeID(window, borderIndex + 4);
            IkGuiInternal.itemAdd(borderRect, borderID, null, ItemFlags.NO_NAV);
            IkGuiInternal.buttonBehavior(
                    borderRect,
                    borderID,
                    hovered,
                    held,
                    ButtonFlags.INTERNAL_FLATTEN_CHILDREN | ButtonFlags.INTERNAL_NO_NAV_FOCUS);
            if (hovered.get()
                    && context.hoveredIDTimer <= WINDOWS_RESIZE_FROM_EDGES_FEEDBACK_TIMER) {
                hovered.set(false);
            }
            if (hovered.get() || held.get()) {
                context.mouseCursor = axisX ? MouseCursor.RESIZE_EW : MouseCursor.RESIZE_NS;
            }
            if (held.get() && context.io.getMouseDoubleClicked(MouseButton.LEFT)) {
                // Double-clicking bottom or right border auto-fits on this axis
                if (borderIndex == 1 || borderIndex == 3) {
                    final Vector2f sizeAutoFit =
                            calcWindowAutoFitSize(
                                    window, window.contentSizeIdeal, axisX ? 1 : 2, new Vector2f());
                    final Vector2f constrained =
                            calcWindowSizeAfterConstraint(window, sizeAutoFit, new Vector2f());
                    if (axisX) {
                        sizeTarget.x = constrained.x;
                    } else {
                        sizeTarget.y = constrained.y;
                    }
                    returnAutoFitMask |= axisX ? 1 : 2;
                    // So the border doesn't show highlighted at the new position
                    hovered.set(false);
                    held.set(false);
                }
                IkGuiInternal.clearActiveID();
            } else if (held.get()) {
                // Switch to relative resizing mode when border geometry moved (e.g. resizing a
                // child altering parent scroll), in order to avoid a resizing feedback loop
                final boolean justScrolledManuallyWhileResizing =
                        context.windowWheeling != null
                                && context.windowWheelingScrolledFrame == context.frameCount
                                && IkGuiInternal.isWindowChildOf(
                                        window, context.windowWheeling, false, true);
                if (context.activeIDIsJustActivated || justScrolledManuallyWhileResizing) {
                    context.windowResizeBorderExpectedRect.set(borderRect);
                    context.windowResizeRelativeMode = false;
                }
                if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                        && !context.windowResizeBorderExpectedRect.equals(borderRect)) {
                    context.windowResizeRelativeMode = true;
                }

                final float borderCurrent =
                        axisX
                                ? window.position.x + Math.min(def[0], def[2]) * window.size.x
                                : window.position.y + Math.min(def[1], def[3]) * window.size.y;
                final float mouseDelta = axisX ? context.io.mouseDelta.x : context.io.mouseDelta.y;
                final float borderTargetRelative = borderCurrent + mouseDelta;
                final float borderTargetAbsolute =
                        (axisX ? context.io.mousePosition.x : context.io.mousePosition.y)
                                - (axisX
                                        ? context.activeIDClickOffset.x
                                        : context.activeIDClickOffset.y)
                                + context.windowBorderHoverPadding;

                // Use absolute mode position
                float borderTargetX = window.position.x;
                float borderTargetY = window.position.y;
                if (axisX) {
                    borderTargetX = borderTargetAbsolute;
                } else {
                    borderTargetY = borderTargetAbsolute;
                }

                // Use relative mode target for child window, ignore resize when moving back
                // toward the ideal absolute position
                boolean ignoreResize = false;
                if (context.windowResizeRelativeMode) {
                    if (axisX) {
                        borderTargetX = borderTargetRelative;
                    } else {
                        borderTargetY = borderTargetRelative;
                    }
                    if (mouseDelta == 0.0f
                            || (mouseDelta > 0.0f)
                                    == (borderTargetRelative > borderTargetAbsolute)) {
                        ignoreResize = true;
                    }
                }

                // Clamp, apply
                final float clampMinX = borderIndex == 1 ? clampRect.getLeft() : -Float.MAX_VALUE;
                final float clampMinY =
                        (borderIndex == 3 || (borderIndex == 2 && windowMoveFromTitleBar))
                                ? clampRect.getTop()
                                : -Float.MAX_VALUE;
                final float clampMaxX = borderIndex == 0 ? clampRect.getRight() : Float.MAX_VALUE;
                final float clampMaxY = borderIndex == 2 ? clampRect.getBottom() : Float.MAX_VALUE;
                borderTargetX =
                        MathUtil.clamp(borderTargetX, clampMinX, Math.max(clampMinX, clampMaxX));
                borderTargetY =
                        MathUtil.clamp(borderTargetY, clampMinY, Math.max(clampMinY, clampMaxY));
                if (!ignoreResize) {
                    calcResizePosSizeFromAnyCorner(
                            window,
                            borderTargetX,
                            borderTargetY,
                            Math.min(def[0], def[2]),
                            Math.min(def[1], def[3]),
                            positionTarget,
                            sizeTarget);
                }
            }
            if (hovered.get()) {
                borderHovered[0] = borderIndex;
            }
            if (held.get()) {
                borderHeld[0] = borderIndex;
            }
        }

        // Navigation resize (keyboard/gamepad), with Ctrl+Tab held and Shift+arrows
        if (context.navWindowingTarget != null
                && context.navWindowingTarget.rootWindowDockTree == window) {
            float resizeX = 0.0f;
            float resizeY = 0.0f;
            final IkIO io = context.io;
            if (context.navInputSource == GuiInputSource.KEYBOARD && io.keyShift) {
                resizeX =
                        (io.getKeyDown(Key.ARROW_RIGHT) ? 1.0f : 0.0f)
                                - (io.getKeyDown(Key.ARROW_LEFT) ? 1.0f : 0.0f);
                resizeY =
                        (io.getKeyDown(Key.ARROW_DOWN) ? 1.0f : 0.0f)
                                - (io.getKeyDown(Key.ARROW_UP) ? 1.0f : 0.0f);
            }
            if (context.navInputSource == GuiInputSource.GAMEPAD) {
                resizeX =
                        (io.getKeyDown(Key.GAMEPAD_DPAD_RIGHT) ? 1.0f : 0.0f)
                                - (io.getKeyDown(Key.GAMEPAD_DPAD_LEFT) ? 1.0f : 0.0f);
                resizeY =
                        (io.getKeyDown(Key.GAMEPAD_DPAD_DOWN) ? 1.0f : 0.0f)
                                - (io.getKeyDown(Key.GAMEPAD_DPAD_UP) ? 1.0f : 0.0f);
            }
            if (resizeX != 0.0f || resizeY != 0.0f) {
                final float navResizeSpeed = 600.0f;
                final float resizeStep = navResizeSpeed * io.deltaTime / 1000.0f;
                final Vector2f accum = context.navWindowingAccumulatedDeltaSize;
                accum.add(resizeX * resizeStep, resizeY * resizeStep);
                // We need position + size >= the clamp rect min
                accum.x =
                        Math.max(accum.x, clampRect.getLeft() - window.position.x - window.size.x);
                accum.y = Math.max(accum.y, clampRect.getTop() - window.position.y - window.size.y);
                context.navWindowingToggleLayer = false;
                context.navHighlightItemUnderNav = true;
                resizeGripColors[0] = IkGuiImplUtils.getColor(ColorType.RESIZE_GRIP_ACTIVE);
                final float flooredX = IkGuiInternal.truncate(accum.x);
                final float flooredY = IkGuiInternal.truncate(accum.y);
                if (flooredX != 0.0f || flooredY != 0.0f) {
                    final Vector2f newSize =
                            new Vector2f(
                                    window.sizeFull.x + flooredX, window.sizeFull.y + flooredY);
                    calcWindowSizeAfterConstraint(window, newSize, newSize);
                    sizeTarget.set(newSize);
                    accum.sub(flooredX, flooredY);
                }
            }
        }

        // Apply back modified position/size to window
        final float oldPositionX = window.position.x;
        final float oldPositionY = window.position.y;
        final float oldSizeX = window.sizeFull.x;
        final float oldSizeY = window.sizeFull.y;
        if (sizeTarget.x != Float.MAX_VALUE) {
            window.size.x = sizeTarget.x;
            window.sizeFull.x = sizeTarget.x;
        }
        if (sizeTarget.y != Float.MAX_VALUE) {
            window.size.y = sizeTarget.y;
            window.sizeFull.y = sizeTarget.y;
        }
        if (positionTarget.x != Float.MAX_VALUE) {
            window.position.x = IkGuiInternal.truncate(positionTarget.x);
        }
        if (positionTarget.y != Float.MAX_VALUE) {
            window.position.y = IkGuiInternal.truncate(positionTarget.y);
        }
        if (oldPositionX != window.position.x
                || oldPositionY != window.position.y
                || oldSizeX != window.sizeFull.x
                || oldSizeY != window.sizeFull.y) {
            IkGuiInternal.markIniSettingsDirty(window);
        }

        // Recalculate next expected border coordinates
        if (borderHeld[0] != -1) {
            context.windowResizeBorderExpectedRect.set(
                    getResizeBorderRect(
                            window,
                            borderHeld[0],
                            gripHoverInnerSize,
                            context.windowBorderHoverPadding));
        }

        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
        return returnAutoFitMask;
    }

    /**
     * Fetch the background color type for a window.
     *
     * @param window The window.
     * @return The color type to use for the background.
     */
    private static ColorType getWindowBackgroundColorType(@NonNull Window window) {
        if ((window.flags & (WindowFlags.INTERNAL_TOOLTIP | WindowFlags.INTERNAL_POPUP)) != 0) {
            return ColorType.POPUP_BACKGROUND;
        }
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0 && !window.dockIsActive) {
            return ColorType.CHILD_BACKGROUND;
        }
        return ColorType.WINDOW_BACKGROUND;
    }

    /**
     * Draw a single border of the window, used for highlighting resize borders.
     *
     * @param window The window.
     * @param borderIndex The border, 0-3 for left, right, up, down.
     * @param color The color.
     * @param borderSize The thickness of the border.
     */
    private static void renderWindowOuterSingleBorder(
            @NonNull Window window, int borderIndex, int color, float borderSize) {
        final RectFloat outer = window.rectOuter;
        final float half = borderSize * 0.5f;
        switch (borderIndex) {
            case 0 ->
                    window.drawList.addRectFilled(
                            outer.getLeft() - half,
                            outer.getTop() + window.rounding,
                            outer.getLeft() + half,
                            outer.getBottom() - window.rounding,
                            color);
            case 1 ->
                    window.drawList.addRectFilled(
                            outer.getRight() - half,
                            outer.getTop() + window.rounding,
                            outer.getRight() + half,
                            outer.getBottom() - window.rounding,
                            color);
            case 2 ->
                    window.drawList.addRectFilled(
                            outer.getLeft() + window.rounding,
                            outer.getTop() - half,
                            outer.getRight() - window.rounding,
                            outer.getTop() + half,
                            color);
            default ->
                    window.drawList.addRectFilled(
                            outer.getLeft() + window.rounding,
                            outer.getBottom() - half,
                            outer.getRight() - window.rounding,
                            outer.getBottom() + half,
                            color);
        }
    }

    /**
     * Render the outer borders of the window, including resize border highlights.
     *
     * @param window The window.
     */
    static void renderWindowOuterBorders(@NonNull Window window) {
        final float borderSize = window.borderSize;
        final int borderColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER);
        if (borderSize > 0.0f && (window.flags & WindowFlags.NO_BACKGROUND) == 0) {
            window.drawList.addRect(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.size.x,
                    window.position.y + window.size.y,
                    borderColor,
                    window.rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    borderSize);
        } else if (borderSize > 0.0f) {
            if ((window.flagsAsChildWindow & ChildFlags.RESIZE_X) != 0) {
                renderWindowOuterSingleBorder(window, 1, borderColor, borderSize);
            }
            if ((window.flagsAsChildWindow & ChildFlags.RESIZE_Y) != 0) {
                renderWindowOuterSingleBorder(window, 3, borderColor, borderSize);
            }
        }
        if (window.borderBeingHovered != Direction.NONE
                || window.borderBeingDragged != Direction.NONE) {
            final Direction border =
                    window.borderBeingDragged != Direction.NONE
                            ? window.borderBeingDragged
                            : window.borderBeingHovered;
            final int borderIndex =
                    switch (border) {
                        case LEFT -> 0;
                        case RIGHT -> 1;
                        case UP -> 2;
                        default -> 3;
                    };
            final int resizingColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            window.borderBeingDragged != Direction.NONE
                                    ? ColorType.SEPARATOR_ACTIVE
                                    : ColorType.SEPARATOR_HOVERED);
            // Thicker than usual
            renderWindowOuterSingleBorder(
                    window, borderIndex, resizingColor, Math.max(2.0f, window.borderSize));
        }
        final float frameBorderSize = context.style.variable.frameBorderSize;
        if (frameBorderSize > 0
                && (window.flags & WindowFlags.NO_TITLE_BAR) == 0
                && !window.dockIsActive) {
            final float y = window.position.y + window.titleBarHeight - 1;
            window.drawList.addRectFilled(
                    window.position.x + borderSize * 0.5f,
                    y,
                    window.position.x + window.size.x - borderSize * 0.5f,
                    y + frameBorderSize,
                    borderColor);
        }
    }

    /**
     * Draw the window background and borders, and handle scrollbars.
     *
     * @param window The window.
     * @param titleBarRect The rectangle of the title bar.
     * @param titleBarIsHighlight Whether the title bar should be highlighted (focused).
     * @param handleBordersAndResizeGrips Whether to draw borders and resize grips.
     * @param resizeGripCount The number of resize grips.
     * @param resizeGripColors The colors of the resize grips.
     * @param resizeGripDrawSize The size of the resize grips.
     */
    private static void renderWindowDecorations(
            @NonNull Window window,
            @NonNull RectFloat titleBarRect,
            boolean titleBarIsHighlight,
            boolean handleBordersAndResizeGrips,
            int resizeGripCount,
            int[] resizeGripColors,
            float resizeGripDrawSize) {
        final StyleVariables style = context.style.variable;
        final int flags = window.flags;

        // Ensure that scrollbar() doesn't read last frame's skip items
        window.skipItems = false;
        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;

        // Draw window + handle manual resize. As we highlight the title bar when want focus is
        // set, multiple reappearing windows will have their title bar highlighted on their
        // reappearing frame.
        final float windowRounding = window.rounding;
        final float windowBorderSize = window.borderSize;
        if (window.collapsed) {
            // Title bar only
            int titleBarColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            titleBarIsHighlight && context.navCursorVisible
                                    ? ColorType.TITLE_BACKGROUND_ACTIVE
                                    : ColorType.TITLE_BACKGROUND_COLLAPSED);
            if (window.viewportOwned) {
                // No alpha
                titleBarColor |= 0xFF;
            }
            window.drawList.addRectFilled(
                    titleBarRect.getLeft(),
                    titleBarRect.getTop(),
                    titleBarRect.getRight(),
                    titleBarRect.getBottom(),
                    titleBarColor,
                    windowRounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    window.alphaRadius);
            if (windowBorderSize > 0.0f) {
                window.drawList.addRect(
                        titleBarRect.getLeft(),
                        titleBarRect.getTop(),
                        titleBarRect.getRight(),
                        titleBarRect.getBottom(),
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                        windowRounding,
                        DrawFlags.ROUND_CORNERS_ALL,
                        windowBorderSize);
            }
        } else {
            // Window background
            if ((flags & WindowFlags.NO_BACKGROUND) == 0) {
                final boolean isDockingTransparentPayload =
                        context.dragDropActive
                                && (context.frameCount - context.dragDropAcceptFrameCount) <= 1
                                && context.io.configDockingTransparentPayload
                                && context.dragDropPayload.isDataType(
                                        IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW)
                                && context.dragDropPayload.data == window;

                int backgroundColor =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                getWindowBackgroundColorType(window));
                if (window.viewportOwned) {
                    // No alpha, the whole platform window is made transparent instead
                    backgroundColor |= 0xFF;
                    if (isDockingTransparentPayload) {
                        window.viewport.alpha *= IkGuiImplDocking.DOCKING_TRANSPARENT_PAYLOAD_ALPHA;
                    }
                } else {
                    // Adjust the alpha, for docking
                    boolean overrideAlpha = false;
                    float alpha = 1.0f;
                    if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_BACKGROUND_ALPHA)
                            != 0) {
                        alpha = context.nextWindowData.backgroundAlpha;
                        overrideAlpha = true;
                    }
                    if (isDockingTransparentPayload) {
                        alpha *= IkGuiImplDocking.DOCKING_TRANSPARENT_PAYLOAD_ALPHA;
                        overrideAlpha = true;
                    }
                    if (overrideAlpha) {
                        backgroundColor =
                                Color.multiplyAlpha(
                                        backgroundColor & 0xFFFFFF00 | 0xFF,
                                        MathUtil.clamp(alpha, 0.0f, 1.0f));
                    }
                }

                // Render, for docked windows and host windows we ensure the background goes
                // before decorations
                if (window.dockIsActive) {
                    window.dockNode.lastBackgroundColor = backgroundColor;
                }
                if ((flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) != 0) {
                    backgroundColor = Color.CLEAR;
                }
                if ((backgroundColor & 0xFF) != 0) {
                    final RectFloat backgroundRect =
                            new RectFloat(
                                    window.position.x,
                                    window.position.y + window.titleBarHeight,
                                    window.position.x + window.size.x,
                                    window.position.y + window.size.y);
                    int roundingFlags =
                            (flags & WindowFlags.NO_TITLE_BAR) != 0
                                    ? DrawFlags.ROUND_CORNERS_ALL
                                    : DrawFlags.ROUND_CORNERS_BOTTOM;
                    final DrawList backgroundDrawList;
                    if (window.dockIsActive) {
                        roundingFlags =
                                IkGuiInternal.calcRoundingFlagsForRectInRect(
                                        backgroundRect, window.dockNode.hostWindow.getRect(), 0.0f);
                        backgroundDrawList = window.dockNode.hostWindow.drawList;
                        backgroundDrawList.channelsSetCurrent(
                                IkGuiImplDocking.DOCKING_HOST_DRAW_CHANNEL_BG);
                    } else {
                        backgroundDrawList = window.drawList;
                    }
                    backgroundDrawList.addRectFilled(
                            backgroundRect.getLeft(),
                            backgroundRect.getTop(),
                            backgroundRect.getRight(),
                            backgroundRect.getBottom(),
                            backgroundColor,
                            windowRounding,
                            roundingFlags,
                            window.alphaRadius);
                    if (window.dockIsActive) {
                        backgroundDrawList.channelsSetCurrent(
                                IkGuiImplDocking.DOCKING_HOST_DRAW_CHANNEL_FG);
                    }
                }
            }
            if (window.dockIsActive) {
                window.dockNode.isBackgroundDrawnThisFrame = true;
            }

            // Title bar (when docked, dock nodes draw their own title bar)
            if ((flags & WindowFlags.NO_TITLE_BAR) == 0 && !window.dockIsActive) {
                final int titleBarColor =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                titleBarIsHighlight
                                        ? ColorType.TITLE_BACKGROUND_ACTIVE
                                        : ColorType.TITLE_BACKGROUND);
                window.drawList.addRectFilled(
                        titleBarRect.getLeft(),
                        titleBarRect.getTop(),
                        titleBarRect.getRight(),
                        titleBarRect.getBottom(),
                        titleBarColor,
                        windowRounding,
                        DrawFlags.ROUND_CORNERS_TOP,
                        window.alphaRadius);
            }

            // Menu bar
            if ((flags & WindowFlags.MENU_BAR) != 0) {
                final RectFloat menuBarRect = window.getMenuBarRect(new RectFloat());
                // Soft clipping, in particular child windows don't have a minimum size covering
                // the menu bar so this is useful for them
                menuBarRect.clipWith(window.rectOuter);
                window.drawList.addRectFilled(
                        menuBarRect.getLeft(),
                        menuBarRect.getTop(),
                        menuBarRect.getRight(),
                        menuBarRect.getBottom(),
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.MENU_BAR_BACKGROUND),
                        (flags & WindowFlags.NO_TITLE_BAR) != 0 ? windowRounding : 0.0f,
                        DrawFlags.ROUND_CORNERS_TOP);
                if (style.frameBorderSize > 0.0f
                        && menuBarRect.getBottom() < window.position.y + window.size.y) {
                    window.drawList.addRectFilled(
                            menuBarRect.getLeft() + windowBorderSize * 0.5f,
                            menuBarRect.getBottom(),
                            menuBarRect.getRight() - windowBorderSize * 0.5f,
                            menuBarRect.getBottom() + style.frameBorderSize,
                            IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER));
                }
            }

            // Docking: unhide the tab bar (small triangle in the corner), drag from the small
            // triangle to quickly undock
            final DockNode node = window.dockNode;
            if (window.dockIsActive && node.isHiddenTabBar() && !node.isNoTabBar()) {
                final float fontSize = IkGuiInternal.getFontSize();
                final float unhideSizeDraw = IkGuiInternal.truncate(fontSize * 0.70f);
                final float unhideSizeHit = IkGuiInternal.truncate(fontSize * 0.55f);
                final float px = node.position.x;
                final float py = node.position.y;
                final RectFloat r = new RectFloat(px, py, px + unhideSizeHit, py + unhideSizeHit);
                final int unhideID = window.getID("#UNHIDE");
                IkGuiInternal.keepAliveID(unhideID);
                final IkBoolean hovered = new IkBoolean();
                final IkBoolean held = new IkBoolean();
                if (IkGuiInternal.buttonBehavior(
                        r, unhideID, hovered, held, ButtonFlags.INTERNAL_FLATTEN_CHILDREN)) {
                    node.wantHiddenTabBarToggle = true;
                } else if (held.get() && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)) {
                    // Undock from the tab bar triangle, the same as the window menu button
                    IkGuiInternal.startMouseMovingWindowOrNode(window, node, true);
                }

                final int color =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                ((held.get() && hovered.get())
                                                || (node.isFocused && !hovered.get()))
                                        ? ColorType.BUTTON_ACTIVE
                                        : hovered.get()
                                                ? ColorType.BUTTON_HOVERED
                                                : ColorType.BUTTON);
                window.drawList.addTriangleFilled(
                        px, py, px + unhideSizeDraw, py, px, py + unhideSizeDraw, color);
            }

            // Scrollbars
            if (window.scrollbarX) {
                scrollbar(window, Axis.X);
            }
            if (window.scrollbarY) {
                scrollbar(window, Axis.Y);
            }

            // Render resize grips (after their input handling so we don't have a frame of
            // latency)
            if (handleBordersAndResizeGrips && (flags & WindowFlags.NO_RESIZE) == 0) {
                final float borderInner = Math.round(windowBorderSize * 0.5f);
                for (int gripIndex = 0; gripIndex < resizeGripCount; ++gripIndex) {
                    final int color = resizeGripColors[gripIndex];
                    if ((color & 0xFF) == 0) {
                        continue;
                    }
                    final float[] def = RESIZE_GRIP_DEF[gripIndex];
                    final float cornerX =
                            lerp(window.position.x, window.position.x + window.size.x, def[0]);
                    final float cornerY =
                            lerp(window.position.y, window.position.y + window.size.y, def[1]);
                    final float cornerInnerX = cornerX + def[2] * borderInner;
                    final float cornerInnerY = cornerY + def[3] * borderInner;
                    window.drawList.addTriangleFilled(
                            cornerInnerX,
                            cornerInnerY,
                            cornerInnerX + def[2] * resizeGripDrawSize,
                            cornerInnerY,
                            cornerInnerX,
                            cornerInnerY + def[3] * resizeGripDrawSize,
                            color);
                }
            }

            // Borders (for dock node hosts they will be rendered over after the tab bar)
            if (handleBordersAndResizeGrips && window.dockNodeAsHost == null) {
                renderWindowOuterBorders(window);
            }
        }
        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;
    }

    /**
     * Render the title text, collapse button, and close button.
     *
     * @param window The window.
     * @param titleBarRect The title bar rectangle, excluding the border.
     * @param name The name of the window.
     * @param open The open state, which will be set to false if the close button is clicked.
     */
    private static void renderWindowTitleBarContents(
            @NonNull Window window,
            @NonNull RectFloat titleBarRect,
            @NonNull String name,
            IkBoolean open) {
        final StyleVariables style = context.style.variable;
        final int flags = window.flags;

        final boolean hasCloseButton = open != null;
        final boolean hasCollapseButton =
                (flags & WindowFlags.NO_COLLAPSE) == 0
                        && style.windowMenuButtonPosition != WindowMenuButtonPosition.NONE;

        // Close & collapse buttons are on the menu layer and don't default focus (unless there's
        // nothing else on that layer)
        final int itemFlagsBackup = context.currentItemFlags;
        context.currentItemFlags |= ItemFlags.NO_NAV_DEFAULT_FOCUS;
        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MENU;

        // Layout buttons
        float padLeft = style.framePadding.x;
        float padRight = style.framePadding.x;
        final float buttonSize = IkGuiInternal.getFontSize();
        float closeButtonX = 0;
        float collapseButtonX = 0;
        final float buttonY = titleBarRect.getTop() + style.framePadding.y;
        if (hasCloseButton) {
            closeButtonX = titleBarRect.getRight() - padRight - buttonSize;
            padRight += buttonSize + style.itemInnerSpacing.x;
        }
        if (hasCollapseButton && style.windowMenuButtonPosition == WindowMenuButtonPosition.RIGHT) {
            collapseButtonX = titleBarRect.getRight() - padRight - buttonSize;
            padRight += buttonSize + style.itemInnerSpacing.x;
        }
        if (hasCollapseButton && style.windowMenuButtonPosition == WindowMenuButtonPosition.LEFT) {
            collapseButtonX = titleBarRect.getLeft() + padLeft;
            padLeft += buttonSize + style.itemInnerSpacing.x;
        }

        // Collapse button (submitting first so it gets priority when choosing a navigation init
        // fallback)
        if (hasCollapseButton
                && collapseButton(window.getID("#COLLAPSE"), collapseButtonX, buttonY, null)) {
            // Defer actual collapsing to the next frame as we are too far in the begin() function
            window.collapseToggleRequested = true;
        }

        // Close button
        if (hasCloseButton) {
            final int backupItemFlags = context.currentItemFlags;
            context.currentItemFlags |= ItemFlags.NO_FOCUS;
            if (closeButton(window.getID("#CLOSE"), closeButtonX, buttonY)) {
                open.set(false);
            }
            context.currentItemFlags = backupItemFlags;
        }

        context.currentItemFlags = itemFlagsBackup;
        window.navLayerCurrent = IkGuiImplNav.NAV_LAYER_MAIN;

        // Title bar text (with horizontal alignment, avoiding collapse/close buttons, and
        // optional "unsaved document" marker)
        final float markerSizeX =
                (flags & WindowFlags.UNSAVED_DOCUMENT) != 0 ? buttonSize * 0.80f : 0.0f;
        final Vector2f textSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(textSize, name, true, -1.0f);
        textSize.x += markerSizeX;

        // As a nice touch we try to ensure that centered title text doesn't get affected by
        // visibility of close/collapse buttons, while uncentered title text will still reach edges
        // correctly
        if (padLeft > style.framePadding.x) {
            padLeft += style.itemInnerSpacing.x;
        }
        if (padRight > style.framePadding.x) {
            padRight += style.itemInnerSpacing.x;
        }
        if (style.windowTitleAlign.x > 0.0f && style.windowTitleAlign.x < 1.0f) {
            // 0 on either edge, 1 in the center
            final float centerness =
                    MathUtil.clamp(
                            1.0f - Math.abs(style.windowTitleAlign.x - 0.5f) * 2.0f, 0.0f, 1.0f);
            final float padExtend =
                    Math.min(
                            Math.max(padLeft, padRight),
                            titleBarRect.getWidth() - padLeft - padRight - textSize.x);
            padLeft = Math.max(padLeft, padExtend * centerness);
            padRight = Math.max(padRight, padExtend * centerness);
        }

        final RectFloat layoutRect =
                new RectFloat(
                        titleBarRect.getLeft() + padLeft,
                        titleBarRect.getTop(),
                        titleBarRect.getRight() - padRight,
                        titleBarRect.getBottom());
        final RectFloat clipRect =
                new RectFloat(
                        layoutRect.getLeft(),
                        layoutRect.getTop(),
                        Math.min(
                                layoutRect.getRight() + style.itemInnerSpacing.x,
                                titleBarRect.getRight()),
                        layoutRect.getBottom());
        if ((flags & WindowFlags.UNSAVED_DOCUMENT) != 0) {
            final float markerX =
                    MathUtil.clamp(
                            layoutRect.getLeft()
                                    + (layoutRect.getWidth() - textSize.x)
                                            * style.windowTitleAlign.x
                                    + textSize.x,
                            layoutRect.getLeft(),
                            Math.max(layoutRect.getLeft(), layoutRect.getRight()));
            final float markerY = (layoutRect.getTop() + layoutRect.getBottom()) * 0.5f;
            if (markerX > layoutRect.getLeft()) {
                IkGuiInternal.renderBullet(
                        window.drawList,
                        markerX,
                        markerY,
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT));
                clipRect.setRight(
                        Math.min(clipRect.getRight(), markerX - (int) (markerSizeX * 0.5f)));
            }
        }
        IkGuiInternal.renderTextClipped(
                layoutRect.getLeft(),
                layoutRect.getTop(),
                layoutRect.getRight(),
                layoutRect.getBottom(),
                name,
                textSize,
                style.windowTitleAlign.x,
                style.windowTitleAlign.y,
                clipRect);
    }

    /**
     * A button that collapses the window, shown in the title bar.
     *
     * @param id The ID of the button.
     * @param posX The left of the button.
     * @param posY The top of the button.
     * @param dockNode The dock node, if this is the window menu button of a dock node, else null.
     * @return True if pressed.
     */
    static boolean collapseButton(int id, float posX, float posY, DockNode dockNode) {
        final Window window = context.windowCurrent;
        final float size = IkGuiInternal.getFontSize();
        final RectFloat bb = new RectFloat(posX, posY, posX + size, posY + size);
        final boolean isClipped = !IkGuiInternal.itemAdd(bb, id);
        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bb, id, hovered, held, ButtonFlags.NONE);
        if (isClipped) {
            return pressed;
        }

        // Render
        final int backgroundColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        (held.get() && hovered.get())
                                ? ColorType.BUTTON_ACTIVE
                                : hovered.get() ? ColorType.BUTTON_HOVERED : ColorType.BUTTON);
        final int textColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.COMPACT, -1.0f);
        if (hovered.get() || held.get()) {
            window.drawList.addRectFilled(
                    bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), backgroundColor);
        }
        if (dockNode != null) {
            IkGuiInternal.renderArrowDockMenu(
                    window.drawList, bb.getLeft(), bb.getTop(), size, textColor);
        } else {
            IkGuiInternal.renderArrow(
                    window.drawList,
                    bb.getLeft(),
                    bb.getTop(),
                    textColor,
                    window.collapsed ? Direction.RIGHT : Direction.DOWN,
                    1.0f);
        }

        // Switch to moving the window after the mouse is moved beyond the initial drag threshold.
        // Undock from the window/collapse menu button.
        if (IkGuiImplUtils.isItemActive()
                && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)) {
            IkGuiInternal.startMouseMovingWindowOrNode(window, dockNode, true);
        }

        return pressed;
    }

    /**
     * A button that closes the window, shown in the title bar.
     *
     * @param id The ID of the button.
     * @param posX The left of the button.
     * @param posY The top of the button.
     * @return True if pressed.
     */
    static boolean closeButton(int id, float posX, float posY) {
        final Window window = context.windowCurrent;
        final float size = IkGuiInternal.getFontSize();

        // Shrink hit-testing area if the button covers an abnormally large proportion of the
        // visible region, to make it easier to move the window away
        final RectFloat bb = new RectFloat(posX, posY, posX + size, posY + size);
        final RectFloat bbInteract = new RectFloat(posX, posY, posX + size, posY + size);
        final float areaToVisibleRatio = window.rectOuterClipped.getArea() / bb.getArea();
        if (areaToVisibleRatio < 1.5f) {
            final float shrink = IkGuiInternal.truncate(size * -0.25f);
            bbInteract.expand(shrink, shrink);
        }

        // We intentionally allow interaction when clipped so that a mechanical Alt, Right,
        // Activate sequence can always close a window
        final boolean isClipped = !IkGuiInternal.itemAdd(bbInteract, id);

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bbInteract, id, hovered, held, ButtonFlags.NONE);
        if (isClipped) {
            return pressed;
        }

        // Render
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.COMPACT, -1.0f);
        if (hovered.get()) {
            final int backgroundColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            held.get() ? ColorType.BUTTON_ACTIVE : ColorType.BUTTON_HOVERED);
            window.drawList.addRectFilled(
                    bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), backgroundColor);
        }
        final int crossColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
        final float centerX = (bb.getLeft() + bb.getRight()) * 0.5f - 0.5f;
        final float centerY = (bb.getTop() + bb.getBottom()) * 0.5f - 0.5f;
        final float crossExtent = size * 0.5f * 0.7071f - 1.0f;
        window.drawList.addLine(
                centerX + crossExtent,
                centerY + crossExtent,
                centerX - crossExtent,
                centerY - crossExtent,
                crossColor,
                1.0f);
        window.drawList.addLine(
                centerX + crossExtent,
                centerY - crossExtent,
                centerX - crossExtent,
                centerY + crossExtent,
                crossColor,
                1.0f);

        return pressed;
    }

    /**
     * Calculate the rectangle for a window scrollbar.
     *
     * @param window The window.
     * @param axis The axis of the scrollbar.
     * @return The scrollbar rectangle.
     */
    private static RectFloat getWindowScrollbarRect(@NonNull Window window, @NonNull Axis axis) {
        final RectFloat outer = window.updateRectOuter();
        final RectFloat inner = window.rectInner;
        // (scrollbarSizes.x = width of Y scrollbar; scrollbarSizes.y = height of X scrollbar)
        final float scrollbarSize =
                axis == Axis.X ? window.scrollbarSizes.y : window.scrollbarSizes.x;
        final float borderSize = Math.round(window.borderSize * 0.5f);
        final float borderTop =
                (window.flags & WindowFlags.MENU_BAR) != 0
                        ? Math.round(context.style.variable.frameBorderSize * 0.5f)
                        : (window.flags & WindowFlags.NO_TITLE_BAR) != 0 ? borderSize : 0;
        if (axis == Axis.X) {
            return new RectFloat(
                    inner.getLeft() + borderSize,
                    Math.max(
                            outer.getTop() + borderSize,
                            outer.getBottom() - borderSize - scrollbarSize),
                    inner.getRight() - borderSize,
                    outer.getBottom() - borderSize);
        }
        return new RectFloat(
                Math.max(outer.getLeft(), outer.getRight() - borderSize - scrollbarSize),
                inner.getTop() + borderTop,
                outer.getRight() - borderSize,
                inner.getBottom() - borderSize);
    }

    /**
     * Process and draw a window scrollbar.
     *
     * @param window The window.
     * @param axis The axis.
     */
    private static void scrollbar(@NonNull Window window, @NonNull Axis axis) {
        final int id = window.getID(axis == Axis.X ? "#SCROLLX" : "#SCROLLY");

        // Calculate the scrollbar bounding box
        final RectFloat bb = getWindowScrollbarRect(window, axis);
        final RectFloat hostRect =
                (window.dockIsActive ? window.dockNode.hostWindow : window).getRect();
        final int roundingCorners =
                IkGuiInternal.calcRoundingFlagsForRectInRect(
                        bb, hostRect, context.style.variable.windowBorderSize);
        final float sizeVisible =
                axis == Axis.X ? window.rectInner.getWidth() : window.rectInner.getHeight();
        final float sizeContents =
                axis == Axis.X
                        ? window.contentSize.x + window.padding.x * 2.0f
                        : window.contentSize.y + window.padding.y * 2.0f;
        final float scroll = axis == Axis.X ? window.scrollPosition.x : window.scrollPosition.y;
        final float newScroll =
                scrollbarEx(bb, id, axis, scroll, sizeVisible, sizeContents, roundingCorners);
        if (axis == Axis.X) {
            window.scrollPosition.x = newScroll;
        } else {
            window.scrollPosition.y = newScroll;
        }
    }

    /**
     * A generic scrollbar.
     *
     * @param bbFrame The bounding box of the scrollbar.
     * @param id The ID.
     * @param axis The axis.
     * @param scrollValue The current scroll value.
     * @param sizeVisible The visible size of the contents.
     * @param sizeContents The full size of the contents.
     * @param roundingCorners Which corners should be rounded.
     * @return The new scroll value.
     */
    private static float scrollbarEx(
            @NonNull RectFloat bbFrame,
            int id,
            @NonNull Axis axis,
            float scrollValue,
            float sizeVisible,
            float sizeContents,
            int roundingCorners) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return scrollValue;
        }

        final float bbFrameWidth = bbFrame.getWidth();
        final float bbFrameHeight = bbFrame.getHeight();
        if (bbFrameWidth <= 0.0f || bbFrameHeight <= 0.0f) {
            return scrollValue;
        }

        // When we are too small, start hiding and disabling the grab (this reduces visual noise on
        // very small windows and makes it easier to use the window resize grab)
        float alpha = 1.0f;
        if (axis == Axis.Y && bbFrameHeight < bbFrameWidth) {
            alpha = MathUtil.clamp(bbFrameHeight / Math.max(bbFrameWidth * 2.0f, 1.0f), 0.0f, 1.0f);
        }
        if (alpha <= 0.0f) {
            return scrollValue;
        }

        final StyleVariables style = context.style.variable;
        final boolean allowInteraction = alpha >= 1.0f;

        final RectFloat bb = new RectFloat();
        bb.set(bbFrame);
        final float padding =
                IkGuiInternal.truncate(
                        Math.min(
                                style.scrollbarPadding,
                                Math.min(bbFrameWidth, bbFrameHeight) * 0.5f));
        bb.expand(-padding, -padding);

        // V denotes the main, longer axis of the scrollbar (= height for a vertical scrollbar)
        final float scrollbarSizeV = axis == Axis.X ? bb.getWidth() : bb.getHeight();
        if (scrollbarSizeV < 1.0f) {
            return scrollValue;
        }

        // Calculate the height of our grabbable box. It generally represents the amount visible
        // (vs the total scrollable amount), but we maintain a minimum size in pixels to allow for
        // the user to still aim inside.
        final float windowSizeV = Math.max(Math.max(sizeContents, sizeVisible), 1.0f);
        final float grabHMinSize =
                Math.min(axis == Axis.X ? bb.getWidth() : bb.getHeight(), style.grabMinSize);
        final float grabHPixels =
                (int)
                        MathUtil.clamp(
                                scrollbarSizeV * (sizeVisible / windowSizeV),
                                grabHMinSize,
                                Math.max(grabHMinSize, scrollbarSizeV));
        final float grabHNorm = grabHPixels / scrollbarSizeV;

        // Handle input right away. None of the code of begin() is relying on scrolling position
        // before calling scrollbar().
        final IkBoolean held = new IkBoolean();
        final IkBoolean hovered = new IkBoolean();
        IkGuiInternal.itemAdd(bbFrame, id, null, ItemFlags.NO_NAV);
        IkGuiInternal.buttonBehavior(bbFrame, id, hovered, held, ButtonFlags.INTERNAL_NO_NAV_FOCUS);

        final float scrollMax = Math.max(1.0f, sizeContents - sizeVisible);
        float scroll = scrollValue;
        float scrollRatio = MathUtil.clamp(scroll / scrollMax, 0.0f, 1.0f);
        // Grab position in normalized space
        float grabVNorm = scrollRatio * (scrollbarSizeV - grabHPixels) / scrollbarSizeV;
        if (held.get() && allowInteraction && grabHNorm < 1.0f) {
            final float scrollbarPosV = axis == Axis.X ? bb.getLeft() : bb.getTop();
            final float mousePosV =
                    axis == Axis.X ? context.io.mousePosition.x : context.io.mousePosition.y;

            // Click position in scrollbar normalized space (0.0f->1.0f)
            final float clickedVNorm =
                    MathUtil.clamp((mousePosV - scrollbarPosV) / scrollbarSizeV, 0.0f, 1.0f);

            final int heldDirection =
                    clickedVNorm < grabVNorm ? -1 : clickedVNorm > grabVNorm + grabHNorm ? +1 : 0;
            if (context.activeIDIsJustActivated) {
                // On initial click when held direction is 0 (clicked over grab), calculate the
                // distance between the mouse and the center of the grab
                final boolean scrollToClickedLocation =
                        !context.io.configScrollbarScrollByPage
                                || context.io.keyShift
                                || heldDirection == 0;
                context.scrollbarSeekMode = scrollToClickedLocation ? 0 : (byte) heldDirection;
                context.scrollbarClickDistanceToCenter =
                        (heldDirection == 0 && !context.io.keyShift)
                                ? clickedVNorm - grabVNorm - grabHNorm * 0.5f
                                : 0.0f;
            }

            if (context.scrollbarSeekMode == 0) {
                // Absolute seeking
                final float scrollVNorm =
                        MathUtil.clamp(
                                (clickedVNorm
                                                - context.scrollbarClickDistanceToCenter
                                                - grabHNorm * 0.5f)
                                        / (1.0f - grabHNorm),
                                0.0f,
                                1.0f);
                scroll = (long) (scrollVNorm * scrollMax);
            } else if (IkGuiImplUtils.isMouseClicked(MouseButton.LEFT, true)
                    && heldDirection == context.scrollbarSeekMode) {
                // Page by page
                final float pageDirection = context.scrollbarSeekMode > 0 ? 1.0f : -1.0f;
                scroll =
                        MathUtil.clamp(
                                (long) (scroll + pageDirection * sizeVisible), 0, (long) scrollMax);
            }

            // Update values for rendering
            scrollRatio = MathUtil.clamp(scroll / scrollMax, 0.0f, 1.0f);
            grabVNorm = scrollRatio * (scrollbarSizeV - grabHPixels) / scrollbarSizeV;
        }

        // Render
        final int backgroundColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.SCROLLBAR_BACKGROUND);
        final int grabColor =
                Color.multiplyAlpha(
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                held.get()
                                        ? ColorType.SCROLLBAR_GRAB_ACTIVE
                                        : hovered.get()
                                                ? ColorType.SCROLLBAR_GRAB_HOVERED
                                                : ColorType.SCROLLBAR_GRAB),
                        alpha);
        window.drawList.addRectFilled(
                bbFrame.getLeft(),
                bbFrame.getTop(),
                bbFrame.getRight(),
                bbFrame.getBottom(),
                backgroundColor,
                window.rounding,
                roundingCorners);
        if (axis == Axis.X) {
            final float grabMin = lerp(bb.getLeft(), bb.getRight(), grabVNorm);
            window.drawList.addRectFilled(
                    grabMin,
                    bb.getTop(),
                    grabMin + grabHPixels,
                    bb.getBottom(),
                    grabColor,
                    style.scrollbarRounding);
        } else {
            final float grabMin = lerp(bb.getTop(), bb.getBottom(), grabVNorm);
            window.drawList.addRectFilled(
                    bb.getLeft(),
                    grabMin,
                    bb.getRight(),
                    grabMin + grabHPixels,
                    grabColor,
                    style.scrollbarRounding);
        }

        return scroll;
    }

    /**
     * Push a clip rect for the window, updating the window's current clip rect to match.
     *
     * @param window The window.
     * @param rect The clip rect.
     * @param intersectWithCurrentClipRect Whether to intersect with the current clip rect.
     */
    static void pushWindowClipRect(
            @NonNull Window window, @NonNull RectFloat rect, boolean intersectWithCurrentClipRect) {
        window.drawList.pushClipRect(
                rect.getLeft(),
                rect.getTop(),
                rect.getRight(),
                rect.getBottom(),
                intersectWithCurrentClipRect);
        window.rectCurrentClip.set(
                window.drawList.getClipRectMinX(),
                window.drawList.getClipRectMinY(),
                window.drawList.getClipRectMaxX(),
                window.drawList.getClipRectMaxY());
    }

    /**
     * Pop a clip rect for the window, updating the window's current clip rect to match.
     *
     * @param window The window.
     */
    static void popWindowClipRect(@NonNull Window window) {
        window.drawList.popClipRect();
        window.rectCurrentClip.set(
                window.drawList.getClipRectMinX(),
                window.drawList.getClipRectMinY(),
                window.drawList.getClipRectMaxX(),
                window.drawList.getClipRectMaxY());
    }

    /**
     * Temporarily re-enable items while inside a disabled block, used for tooltips over disabled
     * items so they don't appear disabled.
     */
    private static void beginDisabledOverrideReenable() {
        if ((context.currentItemFlags & ItemFlags.DISABLED) == 0) {
            IkGuiImplDebugTools.reportError(log, "Re-enabling items when they are not disabled");
            return;
        }
        context.windowStack.getLast().disabledOverrideReenableBackup = context.style.variable.alpha;
        context.style.variable.alpha = context.disabledAlphaBackup;
        context.currentItemFlags &= ~ItemFlags.DISABLED;
        context.itemFlagsStack.push(context.currentItemFlags);
        context.disabledStackSize++;
    }

    /** End re-enabling items, matching beginDisabledOverrideReenable(). */
    static void endDisabledOverrideReenable() {
        if (context.disabledStackSize <= 0) {
            IkGuiImplDebugTools.reportError(log, "Mismatched disabled override re-enable calls");
            return;
        }
        context.disabledStackSize--;
        context.itemFlagsStack.pop();
        context.currentItemFlags = context.itemFlagsStack.peek();
        context.style.variable.alpha = context.windowStack.getLast().disabledOverrideReenableBackup;
    }

    /**
     * Set the last item data to represent the window title bar.
     *
     * @param window The window.
     * @param rect The title bar rect.
     */
    private static void setLastItemDataForWindow(@NonNull Window window, @NonNull RectFloat rect) {
        if (window.dockIsActive) {
            IkGuiInternal.setLastItemData(
                    window.idMove,
                    context.currentItemFlags,
                    window.dockTabItemStatusFlags,
                    window.dockTabItemRect);
        } else {
            IkGuiInternal.setLastItemData(
                    window.idMove, context.currentItemFlags, window.windowItemStatusFlags, rect);
        }
    }

    /**
     * Add or remove the window from the focus order list, as appropriate.
     *
     * @param window The window.
     * @param justCreated Whether the window was just created.
     * @param newFlags The new window flags.
     */
    private static void updateWindowInFocusOrderList(
            @NonNull Window window, boolean justCreated, int newFlags) {
        final boolean newIsExplicitChild =
                (newFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                        && ((newFlags & WindowFlags.INTERNAL_POPUP) == 0
                                || (newFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0);
        final boolean childFlagChanged = newIsExplicitChild != window.isExplicitChild;
        if ((justCreated || childFlagChanged) && !newIsExplicitChild) {
            if (context.windowFocusOrder.contains(window)) {
                IkGuiImplDebugTools.reportError(
                        log, "Window {} already exists in focus order!", window.name);
            } else {
                context.windowFocusOrder.add(window);
                window.focusOrder = (short) (context.windowFocusOrder.size() - 1);
            }
        } else if (!justCreated && childFlagChanged && newIsExplicitChild) {
            context.windowFocusOrder.remove(window);
            for (int i = 0; i < context.windowFocusOrder.size(); ++i) {
                context.windowFocusOrder.get(i).focusOrder = (short) i;
            }
            window.focusOrder = -1;
        }
        window.isExplicitChild = newIsExplicitChild;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplWindows() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
