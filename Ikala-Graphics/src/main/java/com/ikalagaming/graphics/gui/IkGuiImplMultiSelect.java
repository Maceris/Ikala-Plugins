package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.BoxSelectState;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.MultiSelectIO;
import com.ikalagaming.graphics.gui.data.MultiSelectState;
import com.ikalagaming.graphics.gui.data.MultiSelectTempData;
import com.ikalagaming.graphics.gui.data.SelectionRequest;
import com.ikalagaming.graphics.gui.data.SelectionUserData;
import com.ikalagaming.graphics.gui.data.Table;
import com.ikalagaming.graphics.gui.data.TableColumn;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.SelectionRequestType;
import com.ikalagaming.graphics.gui.flags.ActivateFlags;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.gui.flags.InputFlags;
import com.ikalagaming.graphics.gui.flags.ItemFlags;
import com.ikalagaming.graphics.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.gui.flags.NavMoveFlags;
import com.ikalagaming.graphics.gui.flags.WindowFocusRequestFlags;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

/**
 * Multi-select and box-select support. Multi-select implements the standard selection idioms
 * (Ctrl+Mouse/Keyboard, Shift+Mouse/Keyboard, etc.) with support for clipping and box-selection.
 * The application owns the selection data, and applies the requests returned by beginMultiSelect()
 * and endMultiSelect().
 */
@Slf4j
class IkGuiImplMultiSelect {
    /** The shared context. */
    static Context context;

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMultiSelect() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    // ---------------------------------------------------------------------------------------------
    // Box-select
    // ---------------------------------------------------------------------------------------------

    /**
     * Fetch the box-select state, if the given box-select is active.
     *
     * @param id The box-select ID.
     * @return The box-select state, or null if it's not the active box-select.
     */
    static BoxSelectState getBoxSelectState(int id) {
        final BoxSelectState state = context.boxSelectState;
        return (id != 0 && state.id == id && state.isActive) ? state : null;
    }

    /**
     * Fetch the persistent state of a multi-select scope.
     *
     * @param id The ID of the scope.
     * @return The state, or null if there is none.
     */
    static MultiSelectState getMultiSelectState(int id) {
        return context.multiSelectStorage.get(id);
    }

    /**
     * Called on the initial click, to consider starting a box-select.
     *
     * @param id The box-select ID.
     * @param clickedItem The selection user data of the clicked item, or invalid if the click was
     *     on empty space.
     */
    private static void boxSelectPreStartDrag(int id, long clickedItem) {
        final BoxSelectState bs = context.boxSelectState;
        bs.id = id;
        bs.isStarting = true;
        bs.isStartedFromVoid = clickedItem == SelectionUserData.INVALID;
        bs.isStartedSetNavIDOnce = bs.isStartedFromVoid;
        bs.keyMods = context.io.keyMods;
        windowPosAbsToRel(
                context.windowCurrent,
                context.io.mousePosition.x,
                context.io.mousePosition.y,
                bs.startPositionRelative);
        bs.endPositionRelative.set(bs.startPositionRelative);
        bs.scrollAccumulator.set(0.0f, 0.0f);
    }

    /**
     * Start dragging the box-select.
     *
     * @param bs The box-select state.
     * @param window The window being box-selected in.
     */
    private static void boxSelectActivateDrag(@NonNull BoxSelectState bs, @NonNull Window window) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_SELECTION,
                "[selection] beginBoxSelect() 0x%08X: Activate",
                bs.id);
        bs.isActive = true;
        bs.window = window;
        bs.isStarting = false;
        IkGuiInternal.setActiveID(bs.id, window);
        IkGuiImplKeys.setActiveIDUsingAllKeyboardKeys();
        if (bs.isStartedFromVoid && (bs.keyMods & (KeyModFlags.CTRL | KeyModFlags.SHIFT)) == 0) {
            bs.requestClear = true;
        }
    }

    /**
     * Stop the box-select.
     *
     * @param bs The box-select state.
     */
    private static void boxSelectDeactivateDrag(@NonNull BoxSelectState bs) {
        bs.isActive = false;
        bs.isStarting = false;
        if (context.activeID == bs.id) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_SELECTION,
                    "[selection] beginBoxSelect() 0x%08X: Deactivate",
                    bs.id);
            IkGuiInternal.clearActiveID();
        }
        bs.id = 0;
    }

    /**
     * Scroll the window when the mouse is dragged outside the inner rectangle.
     *
     * @param bs The box-select state.
     * @param window The window to scroll.
     * @param innerRect The rectangle outside which we scroll.
     */
    private static void boxSelectScrollWithMouseDrag(
            @NonNull BoxSelectState bs, @NonNull Window window, @NonNull RectFloat innerRect) {
        if (bs.window != window) {
            return;
        }
        for (int axis = 0; axis < 2; ++axis) {
            final float mousePos =
                    axis == 0 ? context.io.mousePosition.x : context.io.mousePosition.y;
            final float innerMin = axis == 0 ? innerRect.getLeft() : innerRect.getTop();
            final float innerMax = axis == 0 ? innerRect.getRight() : innerRect.getBottom();
            final float dist;
            if (mousePos > innerMax) {
                dist = mousePos - innerMax;
            } else if (mousePos < innerMin) {
                dist = mousePos - innerMin;
            } else {
                dist = 0.0f;
            }
            final float scrollCurrent =
                    axis == 0 ? window.scrollPosition.x : window.scrollPosition.y;
            final float scrollMax = axis == 0 ? window.scrollMax.x : window.scrollMax.y;
            if (dist == 0.0f
                    || (dist < 0.0f && scrollCurrent < 0.0f)
                    || (dist > 0.0f && scrollCurrent >= scrollMax)) {
                continue;
            }

            // x1 to x4 depending on distance
            final float fontSize = IkGuiInternal.getFontSize();
            final float t =
                    MathUtil.clamp(
                            (Math.abs(dist) - fontSize) / (fontSize * 5.0f - fontSize), 0, 1);
            final float speedMultiplier = 1.0f + t * 3.0f;
            final float scrollStep =
                    fontSize
                            * 35.0f
                            * speedMultiplier
                            * Math.signum(dist)
                            * (context.io.deltaTime / 1000.0f);

            // Accumulate into a stored value so we can handle high frame rates
            final float accumulated =
                    (axis == 0 ? bs.scrollAccumulator.x : bs.scrollAccumulator.y) + scrollStep;
            final float scrollStepInt = (float) Math.floor(accumulated);
            if (axis == 0) {
                bs.scrollAccumulator.x = accumulated;
            } else {
                bs.scrollAccumulator.y = accumulated;
            }
            if (scrollStepInt == 0.0f) {
                continue;
            }
            if (axis == 0) {
                IkGuiInternal.setScrollX(window, scrollCurrent + scrollStepInt);
                bs.scrollAccumulator.x -= scrollStepInt;
            } else {
                IkGuiInternal.setScrollY(window, scrollCurrent + scrollStepInt);
                bs.scrollAccumulator.y -= scrollStepInt;
            }
        }
    }

    /**
     * Check if a point is in a rectangle, excluding the right and bottom edges, like upstream's
     * ImRect::Contains(ImVec2).
     *
     * @param rect The rectangle.
     * @param x The x coordinate of the point.
     * @param y The y coordinate of the point.
     * @return True if the point is inside.
     */
    private static boolean containsExclusive(@NonNull RectFloat rect, float x, float y) {
        return x >= rect.getLeft()
                && y >= rect.getTop()
                && x < rect.getRight()
                && y < rect.getBottom();
    }

    /**
     * Update the box-select at the start of a multi-select scope.
     *
     * @param scopeRect The rectangle of the scope.
     * @param window The window.
     * @param boxSelectID The ID of the box-select.
     * @param multiSelectFlags The multi-select flags.
     * @return True if the box-select is active.
     */
    static boolean beginBoxSelect(
            @NonNull RectFloat scopeRect,
            @NonNull Window window,
            int boxSelectID,
            int multiSelectFlags) {
        final BoxSelectState bs = context.boxSelectState;
        IkGuiInternal.keepAliveID(boxSelectID);
        if (bs.id != boxSelectID) {
            return false;
        }

        // isStarting is set by multiSelectItemFooter() when considering a possible box-select. We
        // validate it here and lock the geometry.
        bs.unclipMode = false;
        bs.requestClear = false;
        if (bs.isStarting && IkGuiInternal.isMouseDragPastThreshold(MouseButton.LEFT, -1.0f)) {
            boxSelectActivateDrag(bs, window);
        } else if ((bs.isStarting || bs.isActive)
                && !context.io.mouseDown[MouseButton.LEFT.index]) {
            boxSelectDeactivateDrag(bs);
        }
        if (!bs.isActive) {
            return false;
        }

        // The current frame's absolute previous/current rectangles are used to toggle selection.
        // They are derived from positions relative to the scrolling space, so the "previous"
        // rectangle is reprojected into current frame coordinates.
        final float startX = bs.startPositionRelative.x + window.cursorStartPosition.x;
        final float startY = bs.startPositionRelative.y + window.cursorStartPosition.y;
        final float prevEndX = bs.endPositionRelative.x + window.cursorStartPosition.x;
        final float prevEndY = bs.endPositionRelative.y + window.cursorStartPosition.y;
        float currEndX = context.io.mousePosition.x;
        float currEndY = context.io.mousePosition.y;
        if ((multiSelectFlags & MultiSelectFlags.SCOPE_WINDOW) != 0) {
            // Box-select scrolling only happens with SCOPE_WINDOW
            currEndX = MathUtil.clamp(currEndX, scopeRect.getLeft(), scopeRect.getRight());
            currEndY = MathUtil.clamp(currEndY, scopeRect.getTop(), scopeRect.getBottom());
        }
        final RectFloat prev = bs.boxSelectRectPrevious;
        final RectFloat curr = bs.boxSelectRectCurrent;
        prev.set(
                Math.min(startX, prevEndX),
                Math.min(startY, prevEndY),
                Math.max(startX, prevEndX),
                Math.max(startY, prevEndY));
        curr.set(
                Math.min(startX, currEndX),
                Math.min(startY, currEndY),
                Math.max(startX, currEndX),
                Math.max(startY, currEndY));

        // Box-select 2D mode detects changes of the rectangle. We store unclip rects which are
        // tested by widgets supporting box-select. Always update the rectangles when active.
        if ((multiSelectFlags & (MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.BOX_SELECT_2D))
                != 0) {
            // For both sides, compute the area that differs between the previous and current
            // rectangles
            final RectFloat unclipX = bs.unclipRects[0];
            final RectFloat unclipY = bs.unclipRects[1];
            setInverted(unclipX);
            setInverted(unclipY);
            for (int side = 0; side < 2; ++side) {
                final float minX =
                        side == 0
                                ? Math.min(curr.getLeft(), prev.getLeft())
                                : Math.min(curr.getRight(), prev.getRight());
                final float minY =
                        side == 0
                                ? Math.min(curr.getTop(), prev.getTop())
                                : Math.min(curr.getBottom(), prev.getBottom());
                final float maxX =
                        side == 0
                                ? Math.max(curr.getLeft(), prev.getLeft())
                                : Math.max(curr.getRight(), prev.getRight());
                final float maxY =
                        side == 0
                                ? Math.max(curr.getTop(), prev.getTop())
                                : Math.max(curr.getBottom(), prev.getBottom());
                if (minX != maxX) {
                    addX(unclipX, minX);
                    addX(unclipX, maxX);
                }
                if (minY != maxY) {
                    addY(unclipY, minY);
                    addY(unclipY, maxY);
                }
            }

            final RectFloat intersection = new RectFloat(prev);
            intersection.add(curr);
            if ((multiSelectFlags & MultiSelectFlags.BOX_SELECT_2D) != 0
                    && (prev.getLeft() != curr.getLeft() || prev.getRight() != curr.getRight())) {
                addY(unclipX, intersection.getTop());
                addY(unclipX, intersection.getBottom());
            }
            if (prev.getTop() != curr.getTop() || prev.getBottom() != curr.getBottom()) {
                addX(unclipY, intersection.getLeft());
                addX(unclipY, intersection.getRight());
            }

            // Merge both rectangles into one
            bs.unclipRect.set(unclipX);
            addRect(bs.unclipRect, unclipY);
            // Like upstream, the points are tested with an exclusive max edge, so a box clamped to
            // the edge of the clip rect still unclips the items along that edge
            final RectFloat clip = window.rectCurrentClip;
            if (!bs.unclipRect.isInverted()
                    && (!containsExclusive(clip, bs.unclipRect.getLeft(), bs.unclipRect.getTop())
                            || !containsExclusive(
                                    clip, bs.unclipRect.getRight(), bs.unclipRect.getBottom()))) {
                bs.unclipMode = true;
            }
            if (bs.unclipMode && context.currentTable != null) {
                IkGuiImplTables.tableApplyExternalUnclipRect(context.currentTable, bs.unclipRect);
            }
        }
        return true;
    }

    /**
     * Render the box-select rectangle and scroll, at the end of a multi-select scope.
     *
     * @param scopeRect The rectangle of the scope.
     * @param multiSelectFlags The multi-select flags.
     */
    static void endBoxSelect(@NonNull RectFloat scopeRect, int multiSelectFlags) {
        final Window window = context.windowCurrent;
        final BoxSelectState bs = context.boxSelectState;
        bs.unclipMode = false;

        // Clamp the stored position according to the current scrolling view
        windowPosAbsToRel(
                window,
                MathUtil.clamp(
                        context.io.mousePosition.x, scopeRect.getLeft(), scopeRect.getRight()),
                MathUtil.clamp(
                        context.io.mousePosition.y, scopeRect.getTop(), scopeRect.getBottom()),
                bs.endPositionRelative);

        // Render the selection rectangle
        final RectFloat boxSelectRect = new RectFloat(bs.boxSelectRectCurrent);
        boxSelectRect.clipWith(scopeRect);
        final Window drawWindow = IkGuiImplUtils.findFrontMostVisibleChildWindow(window);
        drawWindow.drawList.addRectFilled(
                boxSelectRect.getLeft(),
                boxSelectRect.getTop(),
                boxSelectRect.getRight(),
                boxSelectRect.getBottom(),
                IkGuiImplUtils.getColor(ColorType.SEPARATOR_HOVERED, 0.30f));
        drawWindow.drawList.addRect(
                boxSelectRect.getLeft(),
                boxSelectRect.getTop(),
                boxSelectRect.getRight(),
                boxSelectRect.getBottom(),
                IkGuiImplUtils.getColor(ColorType.NAV_CURSOR));

        // Scroll
        final boolean enableScroll =
                (multiSelectFlags & MultiSelectFlags.SCOPE_WINDOW) != 0
                        && (multiSelectFlags & MultiSelectFlags.BOX_SELECT_NO_SCROLL) == 0;
        if (enableScroll) {
            final RectFloat scrollRect = new RectFloat(scopeRect);
            scrollRect.expand(-IkGuiInternal.getFontSize(), -IkGuiInternal.getFontSize());
            if (!scrollRect.contains(context.io.mousePosition)) {
                boxSelectScrollWithMouseDrag(bs, window, scrollRect);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Multi-select
    // ---------------------------------------------------------------------------------------------

    /**
     * Log the requests in the IO to the debug log.
     *
     * @param function The function name.
     * @param io The IO.
     */
    private static void debugLogMultiSelectRequests(
            @NonNull String function, @NonNull MultiSelectIO io) {
        for (SelectionRequest request : io.requests) {
            if (request.type() == SelectionRequestType.SET_ALL) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_SELECTION,
                        "[selection] %s: Request: SetAll %d (= %s)",
                        function,
                        request.selected() ? 1 : 0,
                        request.selected() ? "SelectAll" : "Clear");
            } else if (request.type() == SelectionRequestType.SET_RANGE) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_SELECTION,
                        "[selection] %s: Request: SetRange %d..%d (0x%X..0x%X) = %d (dir %d)",
                        function,
                        request.rangeFirstItem(),
                        request.rangeLastItem(),
                        request.rangeFirstItem(),
                        request.rangeLastItem(),
                        request.selected() ? 1 : 0,
                        request.rangeDirection());
            }
        }
    }

    /**
     * Calculate the rectangle of a multi-select scope.
     *
     * @param ms The multi-select data.
     * @param window The window.
     * @return The scope rectangle.
     */
    private static RectFloat calcScopeRect(
            @NonNull MultiSelectTempData ms, @NonNull Window window) {
        if ((ms.flags & MultiSelectFlags.SCOPE_RECT) != 0) {
            // This depends on the cursor max position, so it's meant to be called by
            // endMultiSelect() only
            return new RectFloat(
                    ms.scopeRectMin.x,
                    ms.scopeRectMin.y,
                    Math.max(window.cursorMaxPosition.x, ms.scopeRectMin.x),
                    Math.max(window.cursorMaxPosition.y, ms.scopeRectMin.y));
        }
        // Add the inner table decoration
        final RectFloat scopeRect = new RectFloat(window.rectInnerClip);
        scopeRect.set(
                Math.min(scopeRect.getLeft() + window.decoInnerSizeX1, scopeRect.getRight()),
                Math.min(scopeRect.getTop() + window.decoInnerSizeY1, scopeRect.getBottom()),
                scopeRect.getRight(),
                scopeRect.getBottom());
        return scopeRect;
    }

    /**
     * Start a multi-select scope.
     *
     * @param flags The multi-select flags.
     * @param selectionSize The size of the selection, 0/1 if you can only tell if it's empty, or -1
     *     if unknown. This is used to avoid claiming the Escape key when the selection is empty.
     * @param itemsCount The number of items, stored in the IO for convenience, or -1 if unknown.
     * @return The IO, with requests to apply to your selection.
     * @see MultiSelectFlags
     */
    static MultiSelectIO beginMultiSelect(int flags, int selectionSize, int itemsCount) {
        final Window window = context.windowCurrent;

        if (++context.multiSelectTempDataStackSize > context.multiSelectTempData.size()) {
            context.multiSelectTempData.add(new MultiSelectTempData());
        }
        final MultiSelectTempData ms =
                context.multiSelectTempData.get(context.multiSelectTempDataStackSize - 1);
        context.currentMultiSelect = ms;
        if ((flags & (MultiSelectFlags.SCOPE_WINDOW | MultiSelectFlags.SCOPE_RECT)) == 0) {
            flags |= MultiSelectFlags.SCOPE_WINDOW;
        }
        if ((flags & MultiSelectFlags.SINGLE_SELECT) != 0) {
            flags &= ~(MultiSelectFlags.BOX_SELECT_2D | MultiSelectFlags.BOX_SELECT_1D);
        }
        if ((flags & MultiSelectFlags.BOX_SELECT_2D) != 0) {
            flags &= ~MultiSelectFlags.BOX_SELECT_1D;
        }

        // We override the cursor max position, so make sure table size measurements aren't lost
        final Table table = context.currentTable;
        if (table != null) {
            if (!table.isLayoutLocked) {
                IkGuiImplTables.tableUpdateLayout(table);
            } else if (table.currentColumn != -1) {
                // This is currently safe to call multiple times
                IkGuiImplTables.tableEndCell(table);
            }
        }

        final int id = window.idStack.peek();
        ms.clear();
        ms.focusScopeID = id;
        ms.flags = flags;
        ms.backupCursorMaxPos.set(window.cursorMaxPosition);
        ms.scopeRectMin.set(window.cursorPosition);
        if ((flags & MultiSelectFlags.SCOPE_RECT) != 0) {
            // calcScopeRect() for SCOPE_RECT will measure in endMultiSelect()
            window.cursorMaxPosition.set(ms.scopeRectMin);
        }
        IkGuiImplNav.pushFocusScope(ms.focusScopeID);
        ms.isFocused = IkGuiImplKeys.isInNavFocusRoute(context.currentFocusScopeID);
        if ((flags & MultiSelectFlags.SCOPE_WINDOW) != 0) {
            // Mark the parent child window as navigable into, with highlight. Assume the user will
            // always submit interactive items.
            window.navLayersActiveMask |= 1 << IkGuiImplNav.NAV_LAYER_MAIN;
        }

        // Use a copy of the keyboard mods at the time of the request, otherwise we would require
        // mods to be held for an extra frame
        ms.keyMods =
                context.navJustMovedToID != 0
                        ? (context.navJustMovedToIsTabbing ? 0 : context.navJustMovedToKeyMods)
                        : context.io.keyMods;
        if ((flags & MultiSelectFlags.NO_RANGE_SELECT) != 0) {
            ms.keyMods &= ~KeyModFlags.SHIFT;
        }

        // Bind storage
        final MultiSelectState storage =
                context.multiSelectStorage.computeIfAbsent(id, k -> new MultiSelectState());
        storage.id = id;
        storage.lastFrameActive = context.frameCount;
        storage.lastSelectionSize = selectionSize;
        storage.window = window;
        ms.storage = storage;

        // Output to user
        ms.io.requests.clear();
        ms.io.rangeSourceItem = storage.rangeSourceItem;
        ms.io.navIDItem = storage.navIDItem;
        ms.io.navIDSelected = storage.navIDSelected == 1;
        ms.io.itemsCount = itemsCount;

        // Clear when using navigation to move within the scope (we compare focus scopes so it's
        // possible to use multiple selections inside the same window)
        boolean requestClear = false;
        boolean requestSelectAll = false;
        final boolean noAutoClearOrSelect =
                (flags & (MultiSelectFlags.NO_AUTO_CLEAR | MultiSelectFlags.NO_AUTO_SELECT)) != 0;
        final boolean ctrlOrShift = (ms.keyMods & (KeyModFlags.CTRL | KeyModFlags.SHIFT)) != 0;
        if (context.navJustMovedToID != 0
                && context.navJustMovedToFocusScopeID == ms.focusScopeID
                && context.navJustMovedToHasSelectionData) {
            if ((ms.keyMods & KeyModFlags.SHIFT) != 0) {
                ms.isKeyboardSetRange = true;
            }
            if (!ctrlOrShift && !noAutoClearOrSelect) {
                requestClear = true;
            }
        } else if (context.navJustMovedFromFocusScopeID == ms.focusScopeID) {
            // Also clear on leaving the scope
            if (!ctrlOrShift && !noAutoClearOrSelect) {
                requestClear = true;
            }
        }

        // Box-select handling: update the active state
        final BoxSelectState bs = context.boxSelectState;
        if ((flags & (MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.BOX_SELECT_2D)) != 0) {
            ms.boxSelectID = window.getID("##BoxSelect");
            if (beginBoxSelect(calcScopeRect(ms, window), window, ms.boxSelectID, flags)) {
                requestClear |= bs.requestClear;
            }
        }

        if (ms.isFocused) {
            // Shortcut: clear the selection (Escape)
            // - Only claim the shortcut if the selection is not empty, allowing further presses
            //   of Escape to e.g. leave the current child window.
            // - Box-select also handles Escape, and needs to pass an ID to bypass the
            //   activeIDUsingAllKeyboardKeys lock.
            if ((flags & MultiSelectFlags.CLEAR_ON_ESCAPE) != 0
                    && (selectionSize != 0 || bs.isActive)
                    && IkGuiImplKeys.shortcut(
                            KeyChord.of(Key.ESCAPE), InputFlags.NONE, bs.isActive ? bs.id : 0)) {
                requestClear = true;
                if (bs.isActive) {
                    boxSelectDeactivateDrag(bs);
                }
            }

            // Shortcut: select all (Ctrl+A)
            if ((flags & MultiSelectFlags.SINGLE_SELECT) == 0
                    && (flags & MultiSelectFlags.NO_SELECT_ALL) == 0
                    && IkGuiImplKeys.shortcut(
                            KeyChord.of(KeyModFlags.CTRL, Key.A), InputFlags.NONE, 0)) {
                requestSelectAll = true;
            }
        }

        if (requestClear || requestSelectAll) {
            multiSelectAddSetAll(ms, requestSelectAll);
            if (!requestSelectAll) {
                storage.lastSelectionSize = 0;
            }
        }
        ms.loopRequestSetAll = requestSelectAll ? 1 : requestClear ? 0 : -1;
        ms.isSoleOrUnknownSelectionSize =
                storage.lastSelectionSize == 1 || storage.lastSelectionSize == -1;

        if (IkGuiImplDebugTools.isDebugLogEnabled(DebugLogFlags.EVENT_SELECTION)) {
            debugLogMultiSelectRequests("beginMultiSelect", ms.io);
        }

        return ms.io;
    }

    /**
     * End a multi-select scope.
     *
     * @return The IO, with requests to apply to your selection.
     */
    static MultiSelectIO endMultiSelect() {
        final MultiSelectTempData ms = context.currentMultiSelect;
        final Window window = context.windowCurrent;
        if (ms == null || ms.storage.window != window) {
            IkGuiImplDebugTools.reportError(
                    log, "Calling endMultiSelect() without a matching beginMultiSelect()");
            return null;
        }
        final MultiSelectState storage = ms.storage;
        if (ms.focusScopeID != context.currentFocusScopeID) {
            IkGuiImplDebugTools.reportError(log, "endMultiSelect() FocusScope mismatch!");
        }

        final RectFloat scopeRect = calcScopeRect(ms, window);
        if (ms.isFocused) {
            // We can't read storage.rangeSourceItem here, we want the state at the beginning of the
            // scope
            if (ms.io.rangeSourceReset
                    || (!ms.rangeSourcePassedBy
                            && ms.io.rangeSourceItem != SelectionUserData.INVALID)) {
                // Will be set to the nav ID
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_SELECTION,
                        "[selection] endMultiSelect: Reset RangeSrcItem.");
                storage.rangeSourceItem = SelectionUserData.INVALID;
            }
            if (!ms.navIDPassedBy && storage.navIDItem != SelectionUserData.INVALID) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_SELECTION,
                        "[selection] endMultiSelect: Reset NavIdItem.");
                storage.navIDItem = SelectionUserData.INVALID;
                storage.navIDSelected = -1;
            }

            if ((ms.flags & (MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.BOX_SELECT_2D)) != 0
                    && getBoxSelectState(ms.boxSelectID) != null) {
                endBoxSelect(scopeRect, ms.flags);
            }
        }

        if (!ms.isEndIO) {
            ms.io.requests.clear();
        }

        // Clear the selection when clicking the void? We specifically test that the mouse was not
        // dragged past the threshold to allow box-selection. The inner rect test is necessary for
        // non-child/decorated windows.
        boolean scopeHovered =
                window.rectInner.contains(context.io.mousePosition)
                        && IkGuiImplUtils.isWindowHovered(HoveredFlags.CHILD_WINDOWS);
        if (scopeHovered && (ms.flags & MultiSelectFlags.SCOPE_RECT) != 0) {
            scopeHovered = scopeRect.contains(context.io.mousePosition);
        }
        if (scopeHovered && context.hoveredID == 0 && context.activeID == 0) {
            if ((ms.flags & (MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.BOX_SELECT_2D)) != 0
                    && !context.boxSelectState.isActive
                    && !context.boxSelectState.isStarting
                    && context.io.getMouseClickedCount(MouseButton.LEFT) == 1) {
                boxSelectPreStartDrag(ms.boxSelectID, SelectionUserData.INVALID);
                IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.UNLESS_BELOW_MODAL);
                IkGuiInternal.setHoveredID(ms.boxSelectID);
                if ((ms.flags & MultiSelectFlags.SCOPE_RECT) != 0) {
                    // Automatically switch the focus scope for the initial click from the void to
                    // box-select
                    final float mouseX = context.io.mousePosition.x;
                    final float mouseY = context.io.mousePosition.y;
                    IkGuiImplNav.setNavID(
                            0,
                            IkGuiImplNav.NAV_LAYER_MAIN,
                            ms.focusScopeID,
                            IkGuiImplNav.windowRectAbsToRel(
                                    window,
                                    new RectFloat(mouseX, mouseY, mouseX, mouseY),
                                    new RectFloat(0, 0, 0, 0)));
                }
            }

            if ((ms.flags & MultiSelectFlags.CLEAR_ON_CLICK_VOID) != 0
                    && IkGuiImplUtils.isMouseReleased(MouseButton.LEFT)
                    && !IkGuiInternal.isMouseDragPastThreshold(MouseButton.LEFT, -1.0f)
                    && context.io.keyMods == KeyModFlags.NONE) {
                multiSelectAddSetAll(ms, false);
            }
        }

        // Courtesy navigation wrapping helper flag
        if ((ms.flags & MultiSelectFlags.NAV_WRAP_X) != 0) {
            if ((ms.flags & MultiSelectFlags.SCOPE_WINDOW) == 0) {
                IkGuiImplDebugTools.reportError(
                        log, "MultiSelectFlags.NAV_WRAP_X is only supported with SCOPE_WINDOW");
            } else {
                IkGuiImplNav.navMoveRequestTryWrapping(window, NavMoveFlags.WRAP_X);
            }
        }

        // Unwind
        final Table table = context.currentTable;
        if (table != null && table.isInsideRow) {
            IkGuiImplTables.tableEndRow(table);
        }
        window.cursorMaxPosition.max(ms.backupCursorMaxPos);
        IkGuiImplNav.popFocusScope();

        if (IkGuiImplDebugTools.isDebugLogEnabled(DebugLogFlags.EVENT_SELECTION)) {
            debugLogMultiSelectRequests("endMultiSelect", ms.io);
        }

        ms.focusScopeID = 0;
        ms.flags = MultiSelectFlags.NONE;
        context.currentMultiSelect =
                (--context.multiSelectTempDataStackSize > 0)
                        ? context.multiSelectTempData.get(context.multiSelectTempDataStackSize - 1)
                        : null;

        return ms.io;
    }

    /**
     * Set the selection user data for the next item, which is required for each item inside a
     * multi-select scope. This is most likely an index into your data set.
     *
     * @param selectionUserData The value to identify the item with in selection requests.
     */
    static void setNextItemSelectionUserData(long selectionUserData) {
        // Note that the flags will be cleared by itemAdd(), so this is only useful for navigation
        // code. This is designed so widgets can also cheaply set this before calling itemAdd(),
        // so we are not tied to the multi-select API.
        context.nextItemData.selectionUserData = selectionUserData;
        context.nextItemData.focusScopeID = context.currentFocusScopeID;

        final MultiSelectTempData ms = context.currentMultiSelect;
        if (ms != null) {
            // Auto update rangeSourcePassedBy for cases where the clipper is not used (done before
            // the itemAdd() clipping)
            context.nextItemData.itemFlags |=
                    ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA | ItemFlags.INTERNAL_IS_MULTI_SELECT;
            if (ms.io.rangeSourceItem == selectionUserData) {
                ms.rangeSourcePassedBy = true;
            }
        } else {
            context.nextItemData.itemFlags |= ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA;
        }
    }

    /**
     * Check if the last item's selection state was toggled. This is useful if you need the per-item
     * information before reaching endMultiSelect(). Only toggle events are reported, in order to
     * handle clipping correctly.
     *
     * @return True if the last item's selection was toggled.
     */
    static boolean isItemToggledSelection() {
        if (context.currentMultiSelect == null) {
            IkGuiImplDebugTools.reportError(
                    log, "isItemToggledSelection() can only be used inside a multi-select scope");
            return false;
        }
        return (context.lastItemData.statusFlags & ItemStatusFlags.TOGGLED_SELECTION) != 0;
    }

    /**
     * Called by items supporting multi-select before their button behavior. This applies set-all
     * requests and keyboard set-range requests, and alters button flags to allow drag and drop of
     * multiple items.
     *
     * @param id The item ID.
     * @param selected The selection state, which may be modified.
     * @param buttonFlags The button flags, which may be modified. May be null.
     */
    static void multiSelectItemHeader(int id, @NonNull IkBoolean selected, IkInt buttonFlags) {
        final MultiSelectTempData ms = context.currentMultiSelect;

        boolean isSelected = selected.get();
        if (ms.isFocused) {
            final MultiSelectState storage = ms.storage;
            final long itemData = context.nextItemData.selectionUserData;
            if (context.nextItemData.focusScopeID != context.currentFocusScopeID) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Forgot to call setNextItemSelectionUserData() prior to item, required in"
                                + " beginMultiSelect()/endMultiSelect() scope");
            }

            // Apply set-all (clear/select all) requests from beginMultiSelect(). This is only
            // useful if the user hasn't processed them already, and only works if the user isn't
            // using the clipper. With a clipper you need to process the set-all request after
            // calling beginMultiSelect().
            if (ms.loopRequestSetAll != -1) {
                isSelected = ms.loopRequestSetAll == 1;
            }

            // When using Shift+Nav, scrolling may happen, so we can't afford a frame of lag with
            // the selection highlight
            if (ms.isKeyboardSetRange) {
                final boolean isRangeDestination =
                        !ms.rangeDestinationPassedBy && context.navJustMovedToID == id;
                if (isRangeDestination) {
                    ms.rangeDestinationPassedBy = true;
                }
                if (isRangeDestination && storage.rangeSourceItem == SelectionUserData.INVALID) {
                    // If we don't have a range source, the destination is the source
                    storage.rangeSourceItem = itemData;
                    storage.rangeSelected = isSelected ? 1 : 0;
                }
                final boolean isRangeSource = storage.rangeSourceItem == itemData;
                if (isRangeSource
                        || isRangeDestination
                        || ms.rangeSourcePassedBy != ms.rangeDestinationPassedBy) {
                    // Apply the range-select value to visible items
                    isSelected = storage.rangeSelected != 0;
                } else if ((ms.keyMods & KeyModFlags.CTRL) == 0
                        && (ms.flags & MultiSelectFlags.NO_AUTO_CLEAR) == 0) {
                    // Clear other items
                    isSelected = false;
                }
            }
            selected.set(isSelected);
        }

        // Alter the button behavior flags. To handle drag and drop of multiple items we need to
        // avoid clearing the selection on click. This makes actions using Ctrl+Shift delay their
        // effect until mouse up, but it allows drag and drop of multiple items.
        if (buttonFlags != null) {
            int flags = buttonFlags.get();
            flags |= ButtonFlags.INTERNAL_NO_HOVERED_ON_FOCUS;
            flags &=
                    ~(ButtonFlags.INTERNAL_PRESSED_ON_CLICK
                            | ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE);
            if ((ms.flags & MultiSelectFlags.SELECT_ON_CLICK_ALWAYS) != 0) {
                flags |= ButtonFlags.INTERNAL_PRESSED_ON_CLICK;
            } else if ((ms.flags & MultiSelectFlags.SELECT_ON_CLICK_RELEASE) != 0) {
                flags |= ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE;
            } else {
                // SELECT_ON_AUTO
                flags |=
                        (!isSelected
                                        || (context.activeID == id
                                                && context.activeIDHasBeenPressedBefore))
                                ? ButtonFlags.INTERNAL_PRESSED_ON_CLICK
                                : ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE;
            }
            buttonFlags.set(flags);
        }
    }

    /**
     * Called by items supporting multi-select after their button behavior. In charge of:
     *
     * <ul>
     *   <li>Auto-select on navigation.
     *   <li>Box-select toggle handling.
     *   <li>Right-click handling.
     *   <li>Altering the selection based on Ctrl/Shift modifiers, for both keyboard and mouse.
     *   <li>Recording the current selection state for the range source.
     * </ul>
     *
     * @param id The item ID.
     * @param selected The selection state, which may be modified.
     * @param pressed The pressed state, which may be modified.
     * @param extraFlags Extra multi-select flags, like {@link
     *     MultiSelectFlags#INTERNAL_CHECKBOX_MODE}.
     */
    static void multiSelectItemFooter(
            int id, @NonNull IkBoolean selected, @NonNull IkBoolean pressed, int extraFlags) {
        final Window window = context.windowCurrent;

        boolean isSelected = selected.get();
        boolean isPressed = pressed.get();
        final MultiSelectTempData ms = context.currentMultiSelect;
        final MultiSelectState storage = ms.storage;
        if (isPressed) {
            ms.isFocused = true;
        }

        boolean hovered = false;
        if ((context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_RECT) != 0) {
            hovered = IkGuiImplUtils.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP);
        }
        if (!ms.isFocused && !hovered) {
            return;
        }

        final long itemData = context.nextItemData.selectionUserData;

        final int flags = ms.flags | extraFlags;
        final boolean isSingleSelect = (flags & MultiSelectFlags.SINGLE_SELECT) != 0;
        boolean isCtrl = (ms.keyMods & KeyModFlags.CTRL) != 0;
        boolean isShift = (ms.keyMods & KeyModFlags.SHIFT) != 0;

        boolean applyToRangeSource =
                context.navID == id && storage.rangeSourceItem == SelectionUserData.INVALID;
        if (!ms.isEndIO) {
            ms.io.requests.clear();
            ms.isEndIO = true;
        }

        // Auto-select as you navigate a list
        if (context.navJustMovedToID == id) {
            if ((flags & MultiSelectFlags.NO_AUTO_SELECT) == 0) {
                if (isCtrl && isShift) {
                    isPressed = true;
                } else if (!isCtrl) {
                    isSelected = true;
                    isPressed = true;
                }
            } else {
                // With NO_AUTO_SELECT, using Shift+keyboard performs a write/copy
                if (isShift) {
                    isPressed = true;
                } else if (!isCtrl) {
                    // Since the pressed block below is not running, we update this
                    applyToRangeSource = true;
                }
            }
        }

        if (applyToRangeSource) {
            storage.rangeSourceItem = itemData;
            // Will be updated at the end of this function anyway
            storage.rangeSelected = isSelected ? 1 : 0;
        }

        // Box-select toggle handling
        final BoxSelectState bs = ms.boxSelectID != 0 ? getBoxSelectState(ms.boxSelectID) : null;
        if (bs != null) {
            final RectFloat itemRect = new RectFloat(context.lastItemData.rect);
            final Table table = context.currentTable;
            if (!window.navIsScrollPushableX && table != null && table.currentColumn != -1) {
                // We can't solely use the current clip rect as it includes the host clip rect.
                // However we account for the clip rect being larger than the current column (e.g.
                // when using SPAN_ALL_COLUMNS).
                final TableColumn column = table.columns[table.currentColumn];
                final boolean hasClipRect =
                        (context.lastItemData.statusFlags & ItemStatusFlags.HAS_CLIP_RECT) != 0;
                final float clipMinX =
                        hasClipRect
                                ? context.lastItemData.clipRect.getLeft()
                                : window.rectCurrentClip.getLeft();
                final float clipMaxX =
                        hasClipRect
                                ? context.lastItemData.clipRect.getRight()
                                : window.rectCurrentClip.getRight();
                if (clipMinX != clipMaxX) {
                    itemRect.set(
                            Math.max(itemRect.getLeft(), Math.min(column.minX, clipMinX)),
                            itemRect.getTop(),
                            Math.min(itemRect.getRight(), Math.max(column.maxX, clipMaxX)),
                            itemRect.getBottom());
                } else {
                    // When zero sized we expect the bounds have been clamped and are unreliable
                    itemRect.set(
                            Math.max(itemRect.getLeft(), column.minX),
                            itemRect.getTop(),
                            Math.min(itemRect.getRight(), column.maxX),
                            itemRect.getBottom());
                }
            }
            final boolean overlapCurrent = bs.boxSelectRectCurrent.overlaps(itemRect);
            final boolean overlapPrevious = bs.boxSelectRectPrevious.overlaps(itemRect);
            if ((overlapCurrent && !overlapPrevious && !isSelected)
                    || (overlapPrevious && !overlapCurrent)) {
                if (storage.lastSelectionSize <= 0 && bs.isStartedSetNavIDOnce) {
                    // The first item acts as pressed: the code below will emit a selection request
                    // and set the nav ID
                    isPressed = true;
                    bs.isStartedSetNavIDOnce = false;
                } else {
                    isSelected = !isSelected;
                    multiSelectAddSetRange(ms, isSelected, +1, itemData, itemData);
                }
                storage.lastSelectionSize = Math.max(storage.lastSelectionSize + 1, 1);
            }
        }

        // Right-click handling, designed for context menus
        if (hovered
                && IkGuiImplUtils.isMouseClicked(MouseButton.RIGHT, false)
                && (flags
                                & (MultiSelectFlags.NO_AUTO_SELECT
                                        | MultiSelectFlags.NO_SELECT_ON_RIGHT_CLICK))
                        == 0) {
            if (context.activeID != 0 && context.activeID != id) {
                IkGuiInternal.clearActiveID();
            }
            IkGuiImplNav.setFocusID(id, window);
            if (!isPressed && !isSelected) {
                isPressed = true;
                isCtrl = false;
                isShift = false;
            }
        }

        // Unlike Space, Enter doesn't alter the selection (but can still return a press) unless
        // the current item is not selected
        final boolean enterPressed =
                isPressed
                        && context.navActivateID == id
                        && (context.navActivateFlags & ActivateFlags.PREFER_INPUT) != 0;

        // Alter the selection
        if (isPressed && (!enterPressed || !isSelected)) {
            // Box-select
            final GuiInputSource inputSource =
                    (context.navJustMovedToID == id || context.navActivateID == id)
                            ? context.navInputSource
                            : GuiInputSource.MOUSE;
            if ((flags & (MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.BOX_SELECT_2D)) != 0
                    && !context.boxSelectState.isActive
                    && !context.boxSelectState.isStarting
                    && inputSource == GuiInputSource.MOUSE
                    && context.io.getMouseClickedCount(MouseButton.LEFT) == 1) {
                boxSelectPreStartDrag(ms.boxSelectID, itemData);
            }

            // ACTION                    | Begin | Pressed/Activated  | End
            // --------------------------+-------+--------------------+---------------------------
            // Keys Navigated:           | Clear | Src=item, Sel=1    |         SetRange 1
            // Keys Navigated: Ctrl      | n/a   | n/a                |
            // Keys Navigated: Shift     | n/a   | Dst=item, Sel=1    | => Clear + SetRange 1
            // Keys Navigated: Ctrl+Shift| n/a   | Dst=item, Sel=Src  | => Clear + SetRange Src-Dst
            // Keys Activated:           | n/a   | Src=item, Sel=1    | => Clear + SetRange 1
            // Keys Activated: Ctrl      | n/a   | Src=item, Sel=!Sel | =>         SetRange 1
            // Keys Activated: Shift     | n/a   | Dst=item, Sel=1    | => Clear + SetRange 1
            // --------------------------+-------+--------------------+---------------------------
            // Mouse Pressed:            | n/a   | Src=item, Sel=1    | => Clear + SetRange 1
            // Mouse Pressed: Ctrl       | n/a   | Src=item, Sel=!Sel | =>         SetRange 1
            // Mouse Pressed: Shift      | n/a   | Dst=item, Sel=1    | => Clear + SetRange 1
            // Mouse Pressed: Ctrl+Shift | n/a   | Dst=item, Sel=!Sel | =>         SetRange Src-Dst

            if ((flags & MultiSelectFlags.NO_AUTO_CLEAR) == 0) {
                boolean requestClear = false;
                if (isSingleSelect) {
                    requestClear = true;
                } else if ((inputSource == GuiInputSource.MOUSE || context.navActivateID == id)
                        && !isCtrl) {
                    requestClear =
                            (flags & MultiSelectFlags.NO_AUTO_CLEAR_ON_RESELECT) == 0
                                    || !isSelected;
                } else if ((inputSource == GuiInputSource.KEYBOARD
                                || inputSource == GuiInputSource.GAMEPAD)
                        && isShift
                        && !isCtrl) {
                    // Without shift, the clear request was done in beginMultiSelect()
                    requestClear = true;
                }
                if (requestClear) {
                    multiSelectAddSetAll(ms, false);
                }
            }

            final int rangeDirection;
            final boolean rangeSelected;
            if (isShift && !isSingleSelect) {
                if (storage.rangeSourceItem == SelectionUserData.INVALID) {
                    storage.rangeSourceItem = itemData;
                }
                if ((flags & MultiSelectFlags.INTERNAL_CHECKBOX_MODE) == 0) {
                    // Shift+Arrow always selects, Ctrl+Shift+Arrow copies the source selection
                    // state (already stored by beginMultiSelect() in storage.rangeSelected)
                    rangeSelected = !isCtrl || storage.rangeSelected != 0;
                } else {
                    // Shift+Arrow copies the source selection state, Shift+Click always copies
                    // from the target selection state
                    if (ms.isKeyboardSetRange) {
                        rangeSelected = storage.rangeSelected != 0;
                    } else {
                        rangeSelected = !isSelected;
                    }
                }
                rangeDirection = ms.rangeSourcePassedBy ? +1 : -1;
            } else {
                // Ctrl inverts the selection, otherwise always select
                if ((flags & MultiSelectFlags.INTERNAL_CHECKBOX_MODE) == 0) {
                    isSelected = !isCtrl || !isSelected;
                } else {
                    isSelected = !isSelected;
                }
                storage.rangeSourceItem = itemData;
                rangeSelected = isSelected;
                rangeDirection = +1;
            }
            multiSelectAddSetRange(
                    ms, rangeSelected, rangeDirection, storage.rangeSourceItem, itemData);
        }

        // Update/store the selection state of the source item (used by Ctrl+Shift, when the
        // source is unselected we perform a range unselect)
        if (storage.rangeSourceItem == itemData) {
            storage.rangeSelected = isSelected ? 1 : 0;
        }

        // Update/store the selection state of the focused item
        if (context.navID == id) {
            storage.navIDItem = itemData;
            storage.navIDSelected = isSelected ? 1 : 0;
        }
        if (storage.navIDItem == itemData) {
            ms.navIDPassedBy = true;
        }

        selected.set(isSelected);
        pressed.set(isPressed);
    }

    /**
     * Add a request to select or clear all items, replacing any previous requests.
     *
     * @param ms The multi-select data.
     * @param selected True to select all, false to clear.
     */
    static void multiSelectAddSetAll(@NonNull MultiSelectTempData ms, boolean selected) {
        ms.io.requests.clear();
        ms.io.requests.add(
                new SelectionRequest(
                        SelectionRequestType.SET_ALL,
                        selected,
                        0,
                        SelectionUserData.INVALID,
                        SelectionUserData.INVALID));
    }

    /**
     * Add a request to select or unselect a range of items.
     *
     * @param ms The multi-select data.
     * @param selected Whether to select or unselect.
     * @param rangeDirection +1 if the first item comes before the last item, -1 otherwise.
     * @param firstItem The first item in the range.
     * @param lastItem The last item in the range.
     */
    static void multiSelectAddSetRange(
            @NonNull MultiSelectTempData ms,
            boolean selected,
            int rangeDirection,
            long firstItem,
            long lastItem) {
        // Contiguous spans are not merged, since that would break with any form of coarse clipping
        // that we don't know about
        ms.io.requests.add(
                new SelectionRequest(
                        SelectionRequestType.SET_RANGE,
                        selected,
                        rangeDirection,
                        rangeDirection > 0 ? firstItem : lastItem,
                        rangeDirection > 0 ? lastItem : firstItem));
    }

    /**
     * Check if a clipped multi-select item should still be processed, because it's in the
     * box-select unclip rect.
     *
     * @param bb The item bounding box.
     * @return True if the item should be processed even though it is clipped.
     */
    static boolean isUnclippedByBoxSelect(@NonNull RectFloat bb) {
        final BoxSelectState bs = context.boxSelectState;
        return (context.lastItemData.itemFlags & ItemFlags.INTERNAL_IS_MULTI_SELECT) != 0
                && bs.unclipMode
                && bs.unclipRect.overlaps(bb);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Convert a screen position into a position relative to the window contents.
     *
     * @param window The window.
     * @param x The x position in screen space.
     * @param y The y position in screen space.
     * @param output Where to store the relative position.
     */
    private static void windowPosAbsToRel(
            @NonNull Window window, float x, float y, @NonNull Vector2f output) {
        output.set(x - window.cursorStartPosition.x, y - window.cursorStartPosition.y);
    }

    /**
     * Set a rectangle to be inverted (min = +max float, max = -max float), so adding to it starts
     * fresh.
     *
     * @param rect The rectangle.
     */
    private static void setInverted(@NonNull RectFloat rect) {
        rect.set(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
    }

    /**
     * Expand a rectangle on the x axis to include a value.
     *
     * @param rect The rectangle.
     * @param x The value to include.
     */
    private static void addX(@NonNull RectFloat rect, float x) {
        rect.set(
                Math.min(rect.getLeft(), x),
                rect.getTop(),
                Math.max(rect.getRight(), x),
                rect.getBottom());
    }

    /**
     * Expand a rectangle on the y axis to include a value.
     *
     * @param rect The rectangle.
     * @param y The value to include.
     */
    private static void addY(@NonNull RectFloat rect, float y) {
        rect.set(
                rect.getLeft(),
                Math.min(rect.getTop(), y),
                rect.getRight(),
                Math.max(rect.getBottom(), y));
    }

    /**
     * Expand a rectangle to include another, without assuming either is valid.
     *
     * @param rect The rectangle to expand.
     * @param other The rectangle to include.
     */
    private static void addRect(@NonNull RectFloat rect, @NonNull RectFloat other) {
        rect.set(
                Math.min(rect.getLeft(), other.getLeft()),
                Math.min(rect.getTop(), other.getTop()),
                Math.max(rect.getRight(), other.getRight()),
                Math.max(rect.getBottom(), other.getBottom()));
    }
}
