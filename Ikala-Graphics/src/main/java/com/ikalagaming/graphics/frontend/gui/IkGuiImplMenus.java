package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

/** Menu bars, menus and menu items. */
@Slf4j
class IkGuiImplMenus {

    /** The navigation layer for regular window contents. */
    static final int NAV_LAYER_MAIN = IkGuiImplNav.NAV_LAYER_MAIN;

    /** The navigation layer for title bars and menu bars. */
    static final int NAV_LAYER_MENU = IkGuiImplNav.NAV_LAYER_MENU;

    /**
     * How long the mouse needs to hover over a menu before it opens even when it looks like the
     * mouse is moving toward a child menu, in milliseconds.
     */
    private static final long MENU_HOVER_OPEN_DELAY = 300;

    static Context context;

    /**
     * Append to the menu bar of the current window, which requires the {@link WindowFlags#MENU_BAR}
     * flag to be set on the window.
     *
     * @return True if the menu bar is visible, and endMenuBar() needs to be called.
     */
    public static boolean beginMenuBar() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if ((window.flags & WindowFlags.MENU_BAR) == 0) {
            return false;
        }
        if (window.menuBarAppending) {
            IkGuiImplDebugTools.reportError(
                    log, "Already appending to the menu bar, missing endMenuBar()?");
            return false;
        }

        // Backup position on layer 0
        IkGuiImplLayout.beginGroup();
        IkGuiImplUtils.pushID("##MenuBar");

        // We don't clip with the current window clipping rectangle as it is already set to the
        // area below. However we clip with the window full rect. We remove 1 worth of rounding
        // from the right so text in long menus and small windows doesn't tend to display over the
        // lower-right rounded area.
        final float borderTop =
                Math.max(round(window.borderSize * 0.5f - window.titleBarHeight), 0.0f);
        final float borderHalf = round(window.borderSize * 0.5f);
        final RectFloat barRect = window.getMenuBarRect(new RectFloat());
        final RectFloat clipRect =
                new RectFloat(
                        (float) Math.floor(barRect.getLeft() + borderHalf),
                        (float) Math.floor(barRect.getTop() + borderTop),
                        (float)
                                Math.floor(
                                        Math.max(
                                                barRect.getLeft(),
                                                barRect.getRight()
                                                        - Math.max(window.rounding, borderHalf))),
                        (float) Math.floor(barRect.getBottom()));
        clipRect.clipWith(window.rectOuterClipped);
        IkGuiImplLayout.pushClipRect(
                clipRect.getLeft(),
                clipRect.getTop(),
                clipRect.getRight(),
                clipRect.getBottom(),
                false);

        // We overwrite the cursor max position because beginGroup() sets it to the cursor
        // position
        window.cursorPosition.set(
                barRect.getLeft() + window.menuBarOffset.x,
                barRect.getTop() + window.menuBarOffset.y);
        window.cursorMaxPosition.set(window.cursorPosition);
        window.layoutType = LayoutType.HORIZONTAL;
        window.sameLine = false;
        window.navLayerCurrent = NAV_LAYER_MENU;
        window.menuBarAppending = true;
        IkGuiImplText.alignTextToFramePadding();
        return true;
    }

    /** End the menu bar, only call this if beginMenuBar() returned true. */
    public static void endMenuBar() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }
        if ((window.flags & WindowFlags.MENU_BAR) == 0 || !window.menuBarAppending) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginMenuBar()/endMenuBar() calls");
            return;
        }

        // When a move request within one of our child menus failed, capture the request to
        // navigate among our siblings
        if (IkGuiImplNav.navMoveRequestButNoResultYet()
                && (context.navMoveDirection == Direction.LEFT
                        || context.navMoveDirection == Direction.RIGHT)
                && (context.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            // Try to find out if the request is for one of our child menus
            Window navEarliestChild = context.navFocusedWindow;
            while (navEarliestChild.parentWindow != null
                    && (navEarliestChild.parentWindow.flags & WindowFlags.INTERNAL_CHILD_MENU)
                            != 0) {
                navEarliestChild = navEarliestChild.parentWindow;
            }
            if (navEarliestChild.parentWindow == window
                    && navEarliestChild.parentLayoutType == LayoutType.HORIZONTAL
                    && (context.navMoveFlags & NavMoveFlags.FORWARDED) == 0) {
                // To do so we claim focus back, restore the nav ID and then process the movement
                // request for yet another frame
                final int layer = NAV_LAYER_MENU;
                IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
                IkGuiImplNav.setNavID(
                        window.navLastIDs[layer], layer, 0, window.navRectRelative[layer]);
                if (context.navCursorVisible) {
                    // Hide the nav cursor for the current frame so we don't see the intermediary
                    // selection
                    context.navCursorVisible = false;
                    context.navCursorHideFrames = 2;
                }
                context.navHighlightItemUnderNav = context.navMousePositionDirty = true;
                IkGuiImplNav.navMoveRequestForward(
                        context.navMoveDirection,
                        context.navMoveClipDirection,
                        context.navMoveFlags,
                        context.navMoveScrollFlags);
            }
        } else {
            IkGuiImplNav.navMoveRequestTryWrapping(window, NavMoveFlags.WRAP_X);
        }

        IkGuiImplLayout.popClipRect();
        IkGuiImplUtils.popID();
        // Save the horizontal position so the next append can reuse it
        window.menuBarOffset.x = window.cursorPosition.x - window.position.x;

        final GroupData groupData = context.groupStack.peek();
        groupData.emitItem = false;
        final Vector2f restoreCursorMaxPosition = new Vector2f(groupData.backupCursorMaxPos);
        // Convert ideal extents for the scrolling layer equivalent
        window.cursorIdealMaxPosition.x =
                Math.max(
                        window.cursorIdealMaxPosition.x,
                        window.cursorMaxPosition.x - window.scrollPosition.x);
        // Restore the position on layer 0
        IkGuiImplLayout.endGroup();
        window.layoutType = LayoutType.VERTICAL;
        window.sameLine = false;
        window.navLayerCurrent = NAV_LAYER_MAIN;
        window.menuBarAppending = false;
        window.cursorMaxPosition.set(restoreCursorMaxPosition);
    }

    /**
     * Begin a window that is attached to one side of a viewport, claiming space from the work area
     * of the viewport (starting next frame).
     *
     * @param name The name of the window.
     * @param viewport The viewport to attach to.
     * @param direction Which side of the viewport to attach to.
     * @param axisSize The width (left/right) or height (up/down) of the bar.
     * @param windowFlags Window flags.
     * @return True if the window is visible.
     */
    static boolean beginViewportSideBar(
            @NonNull String name,
            @NonNull Viewport viewport,
            @NonNull Direction direction,
            float axisSize,
            int windowFlags) {
        if (direction == Direction.NONE) {
            IkGuiImplDebugTools.reportError(log, "A side bar requires a direction");
            return false;
        }

        final Window barWindow = IkGuiInternal.findWindowByName(name);
        if (barWindow == null || barWindow.beginCount == 0) {
            // Calculate and set window size/position
            final RectFloat available = viewport.getBuildWorkRect(new RectFloat());
            final boolean vertical = direction == Direction.UP || direction == Direction.DOWN;
            float x = available.getLeft();
            float y = available.getTop();
            float width = available.getWidth();
            float height = available.getHeight();
            if (vertical) {
                if (direction == Direction.DOWN) {
                    y = available.getBottom() - axisSize;
                }
                height = axisSize;
            } else {
                if (direction == Direction.RIGHT) {
                    x = available.getRight() - axisSize;
                }
                width = axisSize;
            }
            IkGuiImplWindows.setNextWindowPos(x, y, Condition.ALWAYS, 0.0f, 0.0f);
            IkGuiImplWindows.setNextWindowSize(width, height, Condition.ALWAYS);

            // Report our size into the work area (for next frame) using the actual window size
            switch (direction) {
                case UP -> viewport.buildWorkInsetMin.y += axisSize;
                case LEFT -> viewport.buildWorkInsetMin.x += axisSize;
                case DOWN -> viewport.buildWorkInsetMax.y += axisSize;
                default -> viewport.buildWorkInsetMax.x += axisSize;
            }
        }

        windowFlags |=
                WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_MOVE
                        | WindowFlags.NO_DOCKING;
        // Enforce the viewport, so we don't create our own viewport when
        // io.configViewportsNoAutoMerge is set
        IkGuiImplViewports.setNextWindowViewport(viewport.id);
        IkGuiImplUtils.pushStyleVarFloat(StyleVariable.WINDOW_ROUNDING, 0.0f);
        // Lift the normal size constraint
        IkGuiImplUtils.pushStyleVarFloat2(StyleVariable.WINDOW_MIN_SIZE, 0.0f, 0.0f);
        final boolean isOpen = IkGuiImplWindows.begin(name, null, windowFlags);
        IkGuiImplUtils.popStyleVar(2);
        return isOpen;
    }

    /**
     * Create and append to a full screen menu bar.
     *
     * @return True if the menu bar is visible, and endMainMenuBar() needs to be called.
     */
    public static boolean beginMainMenuBar() {
        final Viewport viewport = IkGuiImplViewports.getMainViewport();
        final StyleVariables style = context.style.variable;

        // Notify of the viewport change so getFrameHeight() can be accurate in case of a DPI
        // change
        IkGuiImplViewports.setCurrentViewport(null, viewport);

        // For the main menu bar, which cannot be moved, we honor the display safe area padding to
        // ensure text can be visible on a TV set
        context.nextWindowData.menuBarOffsetMinValue.set(
                style.displaySafeAreaPadding.x,
                Math.max(style.displaySafeAreaPadding.y - style.framePadding.y, 0.0f));
        final int windowFlags =
                WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR;
        final float height = IkGuiImplLayout.getFrameHeight();
        final boolean isOpen =
                beginViewportSideBar("##MainMenuBar", viewport, Direction.UP, height, windowFlags);
        context.nextWindowData.menuBarOffsetMinValue.set(0.0f, 0.0f);
        if (!isOpen) {
            IkGuiImplWindows.end();
            return false;
        }

        // Temporarily disable NO_SAVED_SETTINGS, in case tables or child windows submitted within
        // the menu bar want to use settings
        context.windowCurrent.flags &= ~WindowFlags.NO_SAVED_SETTINGS;
        beginMenuBar();
        return true;
    }

    /** End the main menu bar, only call this if beginMainMenuBar() returned true. */
    public static void endMainMenuBar() {
        final Window window = context.windowCurrent;
        if (window == null || !window.menuBarAppending) {
            IkGuiImplDebugTools.reportError(log, "Calling endMainMenuBar() not from a menu bar!");
            return;
        }

        endMenuBar();
        window.flags |= WindowFlags.NO_SAVED_SETTINGS;

        // When the user has left the menu (typically: closed menus through activation of an item),
        // we restore focus to the previous window.
        if (window == context.navFocusedWindow
                && context.navLayer == NAV_LAYER_MAIN
                && !context.navAnyRequest
                && context.activeID == 0) {
            IkGuiInternal.focusTopMostWindowUnderOne(
                    context.navFocusedWindow,
                    null,
                    WindowFocusRequestFlags.UNLESS_BELOW_MODAL
                            | WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
        }

        IkGuiImplWindows.end();
    }

    /**
     * Check if the current window is the root of a set of open menus, so that hovering can move
     * between menus in the same set (e.g. moving along a menu bar while one menu is open).
     *
     * @return True if the current window is the root of an open menu set.
     */
    private static boolean isRootOfOpenMenuSet() {
        final Window window = context.windowCurrent;
        if (context.openPopupStack.size() <= context.beginPopupStack.size()
                || (window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            return false;
        }

        // We don't treat popups specially (to consistently support menu features in them), so we
        // can't differentiate separate menu sets by ID. To compensate for that we at least check
        // the parent window navigation layer, which fixes the most common case of menus opening
        // on hover when moving between window content and the menu bar.
        final PopupData upperPopup = context.openPopupStack.get(context.beginPopupStack.size());
        if (window.navLayerCurrent != upperPopup.parentNavLayer) {
            return false;
        }
        return upperPopup.window != null
                && (upperPopup.window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0
                && IkGuiInternal.isWindowChildOf(upperPopup.window, window, true, false);
    }

    /**
     * Create a sub-menu entry.
     *
     * @param label The label, which is also used for the ID.
     * @param enabled Whether the menu is enabled.
     * @return True if the menu is open, and endMenu() needs to be called.
     */
    public static boolean beginMenu(@NonNull String label, boolean enabled) {
        return beginMenuEx(label, null, enabled);
    }

    /**
     * Create a sub-menu entry, with an optional icon.
     *
     * @param label The label, which is also used for the ID.
     * @param icon The icon text, may be null.
     * @param enabled Whether the menu is enabled.
     * @return True if the menu is open, and endMenu() needs to be called.
     */
    static boolean beginMenuEx(@NonNull String label, String icon, boolean enabled) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        boolean menuIsOpen = IkGuiImplPopups.isPopupOpen(id, PopupFlags.NONE);

        // Sub-menus are child windows so that the mouse can be hovering across them (otherwise the
        // top-most popup menu would steal focus and not allow hovering on the parent menu). The
        // first menu in a hierarchy isn't, so hovering doesn't get across.
        int windowFlags =
                WindowFlags.INTERNAL_CHILD_MENU
                        | WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_MOVE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS
                        | WindowFlags.NO_NAV_FOCUS;
        if ((window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            windowFlags |= WindowFlags.INTERNAL_CHILD_WINDOW;
        }

        // If a menu with the same ID was already submitted, we will append to it, matching the
        // behavior of begin()
        if (context.menuIDsSubmittedThisFrame.contains(id)) {
            if (menuIsOpen) {
                menuIsOpen = IkGuiImplPopups.beginPopupMenuEx(id, label, windowFlags);
            } else {
                // We behave like begin() and need to consume those values
                context.nextWindowData.clearFlags();
            }
            return menuIsOpen;
        }

        // Tag the menu as used. Next time beginMenu() with the same ID is called it will append
        // to the existing menu.
        context.menuIDsSubmittedThisFrame.addInt(id);

        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        // Odd hack to allow hovering across menus of the same menu set (otherwise we wouldn't be
        // able to hover the parent without always being a child window)
        final boolean menuSetIsOpen = isRootOfOpenMenuSet();
        if (menuSetIsOpen) {
            context.nextItemData.itemFlags |= ItemFlags.INTERNAL_NO_WINDOW_HOVERABLE_CHECK;
        }

        // The reference position stored in popupPosition will be used by begin() to find a
        // suitable position for the child menu. The final position will be different, it is
        // chosen by findBestWindowPosForPopup().
        final float popupPositionX;
        final float popupPositionY;
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;
        IkGuiImplUtils.pushID(label);
        if (!enabled) {
            IkGuiImplUtils.beginDisabled(true);
        }

        final boolean pressed;
        final float backupRounding = style.selectableRounding;
        style.selectableRounding = style.menuItemRounding;
        final int selectableFlags =
                SelectableFlags.NO_AUTO_CLOSE_POPUPS | SelectableFlags.INTERNAL_SELECT_ON_CLICK;
        final MenuColumns offsets = window.menuColumns;
        if (window.layoutType == LayoutType.HORIZONTAL) {
            // Menu inside a horizontal menu bar. Selectables extend their highlight by half the
            // item spacing in each direction.
            final float itemSpacingX = style.itemSpacing.x;
            window.cursorPosition.x += IkGuiInternal.truncate(itemSpacingX * 0.5f);
            IkGuiImplUtils.pushStyleVarFloat2(
                    StyleVariable.ITEM_SPACING, itemSpacingX * 2.0f, style.itemSpacing.y);
            final float textX = window.cursorPosition.x + offsets.offsetLabel;
            final float textY = posY + window.baseOffsetCurrentLine;
            pressed =
                    IkGuiImplMiscWidgets.selectable(
                            "", menuIsOpen, selectableFlags, labelSize.x, labelSize.y);
            IkGuiImplLogging.logSetNextTextDecoration("[", "]");
            IkGuiInternal.renderText(textX, textY, displayedLabel, false);
            IkGuiImplUtils.popStyleVar();
            // -1 spacing to compensate the spacing added when selectable() did a sameLine()
            window.cursorPosition.x += IkGuiInternal.truncate(itemSpacingX * (-1.0f + 0.5f));
            popupPositionX = posX - 1.0f - IkGuiInternal.truncate(itemSpacingX * 0.5f);
            popupPositionY = textY - style.framePadding.y + window.menuBarHeight;
        } else {
            // Menu inside a regular/vertical menu. In a typical menu window where all items are
            // beginMenu() or menuItem() calls, extraWidth will always be 0. Only when there are
            // other items sticking out we're going to add spacing, yet only register the minimum
            // width into the layout system.
            final float fontSize = IkGuiInternal.getFontSize();
            final float iconWidth = calcIconWidth(icon);
            final float checkMarkWidth = IkGuiInternal.truncate(fontSize * 1.20f);
            // Feedback to next frame
            final float minWidth =
                    offsets.declColumns(iconWidth, labelSize.x, 0.0f, checkMarkWidth);
            final float extraWidth =
                    Math.max(0.0f, IkGuiImplUtils.getContentRegionAvailableX() - minWidth);
            final float textX = window.cursorPosition.x;
            final float textY = posY + window.baseOffsetCurrentLine;
            pressed =
                    IkGuiImplMiscWidgets.selectable(
                            "",
                            menuIsOpen,
                            selectableFlags | SelectableFlags.INTERNAL_SPAN_AVAILABLE_WIDTH,
                            minWidth,
                            labelSize.y);
            IkGuiImplLogging.logSetNextTextDecoration("", ">");
            IkGuiInternal.renderText(textX + offsets.offsetLabel, textY, displayedLabel, false);
            if (iconWidth > 0.0f) {
                IkGuiInternal.renderText(textX + offsets.offsetIcon, textY, icon, false);
            }
            IkGuiInternal.renderArrow(
                    window.drawList,
                    textX + offsets.offsetMark + extraWidth + fontSize * 0.30f,
                    textY,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                    Direction.RIGHT,
                    1.0f);
            popupPositionX = posX;
            popupPositionY = textY - style.windowPadding.y;
        }
        style.selectableRounding = backupRounding;
        if (!enabled) {
            IkGuiImplUtils.endDisabled();
        }
        final int selectableID = context.lastItemData.id;

        // Once dragged, release the active ID. This allows the idiom of mouse down on a menu,
        // dragging elsewhere, then releasing on some other menu item.
        releaseActiveIDWhenDraggedAway(selectableID);

        final boolean hovered =
                context.hoveredID == selectableID && enabled && !context.navHighlightItemUnderNav;

        boolean wantOpen = false;
        boolean wantOpenNavInit = false;
        boolean wantClose = false;
        if (window.layoutType == LayoutType.VERTICAL) {
            // Close the menu when not hovering it anymore unless we are moving roughly in the
            // direction of the menu
            final boolean movingTowardChildMenu = isMovingTowardChildMenu(window);

            // The 'windowHovered == window' check creates an inconsistency (e.g. moving away
            // from a menu slowly tends to hit the same window, whereas moving away fast does not),
            // but we also need to not close the top menu when moving over the void.
            if (menuIsOpen
                    && !hovered
                    && context.windowHovered == window
                    && !movingTowardChildMenu
                    && !context.navHighlightItemUnderNav
                    && context.activeID == 0) {
                wantClose = true;
            }

            if (!menuIsOpen && pressed) {
                // Click/activate to open
                wantOpen = true;
            } else if (!menuIsOpen && hovered && !movingTowardChildMenu) {
                // Hover to open
                wantOpen = true;
            } else if (!menuIsOpen
                    && hovered
                    && context.hoveredIDTimer >= MENU_HOVER_OPEN_DELAY
                    && context.io.mouseStationaryTimer >= MENU_HOVER_OPEN_DELAY) {
                // Hover to open (timer fallback)
                wantOpen = true;
            }
            if (context.navID == selectableID && context.navMoveDirection == Direction.RIGHT) {
                // Navigate right to open
                wantOpen = wantOpenNavInit = true;
                IkGuiImplNav.navMoveRequestCancel();
                IkGuiImplNav.setNavCursorVisibleAfterMove();
            }
        } else {
            // Menu bar
            if (menuIsOpen && pressed && menuSetIsOpen) {
                // Click an open menu again to close it
                wantClose = true;
                menuIsOpen = false;
            } else if (pressed || (hovered && menuSetIsOpen && !menuIsOpen)) {
                // First click to open, then hover to open others
                wantOpen = true;
            } else if (context.navID == selectableID
                    && context.navMoveDirection == Direction.DOWN) {
                // Navigate down to open
                wantOpen = true;
                IkGuiImplNav.navMoveRequestCancel();
            }
        }

        // Explicitly close if an open menu becomes disabled, this facilitates users code a lot in
        // patterns like 'if (beginMenu("options", hasObject)) { ..use object.. }'
        if (!enabled) {
            wantClose = true;
        }
        if (wantClose && IkGuiImplPopups.isPopupOpen(id, PopupFlags.NONE)) {
            IkGuiImplPopups.closePopupToLevel(context.beginPopupStack.size(), true);
        }

        IkGuiInternal.testEngineItemInfo(
                id,
                label,
                context.lastItemData.statusFlags
                        | ItemStatusFlags.OPENABLE
                        | (menuIsOpen ? ItemStatusFlags.OPENED : 0));
        IkGuiImplUtils.popID();

        if (context.activeID == selectableID && wantOpen) {
            context.activeIDNoClearOnFocusLost = true;
        }

        if (wantOpen
                && !menuIsOpen
                && context.openPopupStack.size() > context.beginPopupStack.size()) {
            // Don't reopen/recycle the same menu level in the same frame if it is a different
            // menu ID, first close the other menu and yield for a frame
            IkGuiImplPopups.openPopupEx(id, PopupFlags.NONE);
        } else if (wantOpen) {
            menuIsOpen = true;
            IkGuiImplPopups.openPopupEx(id, PopupFlags.NO_REOPEN);
        }

        if (menuIsOpen) {
            final LastItemData lastItemInParent = new LastItemData();
            lastItemInParent.set(context.lastItemData);
            // The position is a reference for findBestWindowPosForPopup(), not the actual
            // position
            IkGuiImplWindows.setNextWindowPos(
                    popupPositionX, popupPositionY, Condition.ALWAYS, 0.0f, 0.0f);
            // The first level will use the popup rounding, subsequent levels the child rounding
            IkGuiImplUtils.pushStyleVarFloat(StyleVariable.CHILD_ROUNDING, style.popupRounding);
            menuIsOpen = IkGuiImplPopups.beginPopupMenuEx(id, label, windowFlags);
            IkGuiImplUtils.popStyleVar();
            if (menuIsOpen) {
                // Perform an init request in the case the popup was already open (via a previous
                // mouse hover)
                if (wantOpen && wantOpenNavInit && !context.navInitRequest) {
                    IkGuiInternal.focusWindow(
                            context.windowCurrent, WindowFocusRequestFlags.UNLESS_BELOW_MODAL);
                    IkGuiImplNav.navInitWindow(context.windowCurrent, false);
                }

                // Restore the last item data so isItem*() functions work after beginMenu()
                context.lastItemData.set(lastItemInParent);
                if (context.windowHovered == window) {
                    context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_WINDOW;
                }
            }
        } else {
            // We behave like begin() and need to consume those values
            context.nextWindowData.clearFlags();
        }

        return menuIsOpen;
    }

    /**
     * Implements the "mega dropdown" approach to avoid closing menus when the mouse is moving
     * toward a child menu, without needing timers.
     *
     * @param window The current (parent) menu window.
     * @return True if the mouse appears to be moving toward an open child menu.
     */
    private static boolean isMovingTowardChildMenu(@NonNull Window window) {
        final int beginSize = context.beginPopupStack.size();
        final PopupData childPopup =
                beginSize < context.openPopupStack.size()
                        ? context.openPopupStack.get(beginSize)
                        : null;
        final Window childMenuWindow =
                childPopup != null
                                && childPopup.window != null
                                && childPopup.window.parentWindow == window
                        ? childPopup.window
                        : null;
        if (context.windowHovered != window || childMenuWindow == null) {
            return false;
        }

        final float referenceUnit = IkGuiInternal.getFontSize();
        final float childDirection = window.position.x < childMenuWindow.position.x ? 1.0f : -1.0f;
        final Vector2f mouse = context.io.mousePosition;
        final float nextLeft = childMenuWindow.position.x;
        final float nextTop = childMenuWindow.position.y;
        final float nextRight = nextLeft + childMenuWindow.size.x;
        final float nextBottom = nextTop + childMenuWindow.size.y;

        float aX = mouse.x - context.io.mouseDelta.x;
        final float aY = mouse.y - context.io.mouseDelta.y;
        float bX = childDirection > 0.0f ? nextLeft : nextRight;
        float bY = nextTop;
        float cX = childDirection > 0.0f ? nextLeft : nextRight;
        float cY = nextBottom;
        // Add a bit of extra slack
        final float padFarmostH =
                MathUtil.clamp(
                        Math.abs(aX - bX) * 0.30f, referenceUnit * 0.5f, referenceUnit * 2.5f);
        aX += childDirection * -0.5f;
        bX += childDirection * referenceUnit;
        cX += childDirection * referenceUnit;
        // The triangle has a maximum height to limit the slope and the bias toward large
        // sub-menus
        bY = aY + Math.max((bY - padFarmostH) - aY, -referenceUnit * 8.0f);
        cY = aY + Math.min((cY + padFarmostH) - aY, referenceUnit * 8.0f);
        return triangleContainsPoint(aX, aY, bX, bY, cX, cY, mouse.x, mouse.y);
    }

    /**
     * Check if a point is inside a triangle.
     *
     * @return True if the point (pX, pY) is inside the triangle.
     */
    private static boolean triangleContainsPoint(
            float aX, float aY, float bX, float bY, float cX, float cY, float pX, float pY) {
        final boolean b1 = ((pX - bX) * (aY - bY) - (pY - bY) * (aX - bX)) < 0.0f;
        final boolean b2 = ((pX - cX) * (bY - cY) - (pY - cY) * (bX - cX)) < 0.0f;
        final boolean b3 = ((pX - aX) * (cY - aY) - (pY - aY) * (cX - aX)) < 0.0f;
        return (b1 == b2) && (b2 == b3);
    }

    /** End a menu, only call this if beginMenu() returned true. */
    public static void endMenu() {
        final Window window = context.windowCurrent;
        final int required = WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_CHILD_MENU;
        if (window == null || (window.flags & required) != required) {
            IkGuiImplDebugTools.reportError(log, "Calling endMenu() in the wrong window!");
            return;
        }
        // When a left move request in our menu failed, close ourselves
        final Window parentWindow = window.parentWindow;
        if (window.beginCount == window.beginCountPreviousFrame
                && context.navMoveDirection == Direction.LEFT
                && IkGuiImplNav.navMoveRequestButNoResultYet()
                && context.navFocusedWindow != null
                && context.navFocusedWindow.rootWindowForNavigation == window
                && parentWindow != null
                && parentWindow.layoutType == LayoutType.VERTICAL) {
            IkGuiImplPopups.closePopupToLevel(context.beginPopupStack.size() - 1, true);
            IkGuiImplNav.navMoveRequestCancel();
        }
        IkGuiImplPopups.endPopup();
    }

    /**
     * A menu item, which is activated when clicked.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut The shortcut text to display, may be null. This is only displayed, not
     *     processed.
     * @param selected Whether to display a check mark.
     * @param enabled Whether the item is enabled.
     * @return True if the item was activated.
     */
    public static boolean menuItem(
            @NonNull String label, String shortcut, boolean selected, boolean enabled) {
        return menuItemEx(label, null, shortcut, selected, enabled);
    }

    /**
     * A menu item, which is activated when clicked and toggles the selected value.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut The shortcut text to display, may be null. This is only displayed, not
     *     processed.
     * @param selected Whether to display a check mark, toggled when activated. May be null.
     * @param enabled Whether the item is enabled.
     * @return True if the item was activated.
     */
    public static boolean menuItem(
            @NonNull String label, String shortcut, IkBoolean selected, boolean enabled) {
        if (menuItemEx(label, null, shortcut, selected != null && selected.get(), enabled)) {
            if (selected != null) {
                selected.set(!selected.get());
            }
            return true;
        }
        return false;
    }

    /**
     * A menu item with an optional icon.
     *
     * @param label The label, which is also used for the ID.
     * @param icon The icon text, may be null.
     * @param shortcut The shortcut text to display, may be null.
     * @param selected Whether to display a check mark.
     * @param enabled Whether the item is enabled.
     * @return True if the item was activated.
     */
    static boolean menuItemEx(
            @NonNull String label,
            String icon,
            String shortcut,
            boolean selected,
            boolean enabled) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        // See beginMenuEx() for comments about this
        if (isRootOfOpenMenuSet()) {
            context.nextItemData.itemFlags |= ItemFlags.INTERNAL_NO_WINDOW_HOVERABLE_CHECK;
        }

        final boolean pressed;
        IkGuiImplUtils.pushID(label);
        if (!enabled) {
            IkGuiImplUtils.beginDisabled(true);
        }

        final float backupRounding = style.selectableRounding;
        style.selectableRounding = style.menuItemRounding;
        final int selectableFlags =
                SelectableFlags.INTERNAL_SELECT_ON_RELEASE
                        | SelectableFlags.INTERNAL_SET_NAV_ID_ON_HOVER;
        final MenuColumns offsets = window.menuColumns;
        if (window.layoutType == LayoutType.HORIZONTAL) {
            // Mimic the exact layout spacing of beginMenu() to allow menuItem() inside a menu bar.
            // In this situation we don't render the shortcut, and we render a highlight instead of
            // the selected check mark.
            final float itemSpacingX = style.itemSpacing.x;
            window.cursorPosition.x += IkGuiInternal.truncate(itemSpacingX * 0.5f);
            final float textX = window.cursorPosition.x + offsets.offsetLabel;
            final float textY = window.cursorPosition.y + window.baseOffsetCurrentLine;
            IkGuiImplUtils.pushStyleVarFloat2(
                    StyleVariable.ITEM_SPACING, itemSpacingX * 2.0f, style.itemSpacing.y);
            pressed =
                    IkGuiImplMiscWidgets.selectable(
                            "", selected, selectableFlags, labelSize.x, 0.0f);
            IkGuiImplUtils.popStyleVar();
            if ((context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) != 0) {
                IkGuiInternal.renderText(textX, textY, displayedLabel, false);
            }
            // -1 spacing to compensate the spacing added when selectable() did a sameLine()
            window.cursorPosition.x += IkGuiInternal.truncate(itemSpacingX * (-1.0f + 0.5f));
        } else {
            // Menu item inside a vertical menu
            final float fontSize = IkGuiInternal.getFontSize();
            final float iconWidth = calcIconWidth(icon);
            float shortcutWidth = 0.0f;
            if (shortcut != null && !shortcut.isEmpty()) {
                final Vector2f shortcutSize = new Vector2f();
                IkGuiImplUtils.calcTextSize(shortcutSize, shortcut, false, -1.0f);
                shortcutWidth = shortcutSize.x;
            }
            // Always accounted for, even in a menu with none, because 'selected' has no neutral
            // setting
            final float checkMarkWidth = IkGuiInternal.truncate(fontSize * 1.20f);
            // Feedback for next frame
            final float minWidth =
                    offsets.declColumns(iconWidth, labelSize.x, shortcutWidth, checkMarkWidth);
            final float stretchWidth =
                    Math.max(0.0f, IkGuiImplUtils.getContentRegionAvailableX() - minWidth);
            final float textX = posX;
            final float textY = posY + window.baseOffsetCurrentLine;
            pressed =
                    IkGuiImplMiscWidgets.selectable(
                            "",
                            false,
                            selectableFlags | SelectableFlags.INTERNAL_SPAN_AVAILABLE_WIDTH,
                            minWidth,
                            labelSize.y);
            if ((context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) != 0) {
                IkGuiInternal.renderText(textX + offsets.offsetLabel, textY, displayedLabel, false);
                if (iconWidth > 0.0f) {
                    IkGuiInternal.renderText(textX + offsets.offsetIcon, textY, icon, false);
                }
                if (shortcutWidth > 0.0f) {
                    IkGuiImplUtils.pushStyleColor(
                            ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
                    IkGuiImplLogging.logSetNextTextDecoration("(", ")");
                    IkGuiInternal.renderText(
                            textX + offsets.offsetShortcut + stretchWidth, textY, shortcut, false);
                    IkGuiImplUtils.popStyleColor();
                }
                if (selected) {
                    IkGuiInternal.renderCheckMark(
                            window.drawList,
                            textX + offsets.offsetMark + stretchWidth + fontSize * 0.40f,
                            textY + fontSize * 0.134f * 0.5f,
                            IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                            fontSize * 0.866f);
                }
            }
        }
        style.selectableRounding = backupRounding;

        // Once dragged, release the active ID. This allows the idiom of mouse down on a menu,
        // dragging elsewhere, then releasing on some other menu item.
        releaseActiveIDWhenDraggedAway(context.lastItemData.id);

        IkGuiInternal.testEngineItemInfo(
                context.lastItemData.id,
                label,
                context.lastItemData.statusFlags
                        | ItemStatusFlags.CHECKABLE
                        | (selected ? ItemStatusFlags.CHECKED : 0));
        if (!enabled) {
            IkGuiImplUtils.endDisabled();
        }
        IkGuiImplUtils.popID();

        return pressed;
    }

    /**
     * Release the active ID if the mouse is dragging and no longer hovering the item, which lets
     * users press on one menu item and release on another.
     *
     * @param id The ID of the item.
     */
    private static void releaseActiveIDWhenDraggedAway(int id) {
        if (id != 0
                && context.activeID == id
                && context.hoveredID != id
                && context.activeIDSource == GuiInputSource.MOUSE
                && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)) {
            IkGuiInternal.clearActiveID();
            IkGuiImplKeys.setKeyOwner(
                    Key.MOUSE_LEFT, KeyRoutingData.KEY_OWNER_NO_OWNER, InputFlags.NONE);
        }
    }

    /**
     * Calculate the width of an icon, if there is one.
     *
     * @param icon The icon text, may be null.
     * @return The width of the icon, or 0 if there is no icon.
     */
    private static float calcIconWidth(String icon) {
        if (icon == null || icon.isEmpty()) {
            return 0.0f;
        }
        final Vector2f size = new Vector2f();
        IkGuiImplUtils.calcTextSize(size, icon, false, -1.0f);
        return size.x;
    }

    /**
     * Round a value to the nearest integer.
     *
     * @param value The value.
     * @return The rounded value.
     */
    private static float round(float value) {
        return (float) Math.floor(value + 0.5f);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMenus() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
