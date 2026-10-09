package com.ikalagaming.graphics.gui;

import static com.ikalagaming.graphics.gui.data.KeyRoutingData.KEY_OWNER_NO_OWNER;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

/**
 * Keyboard and gamepad navigation, focus, and the focus API (setKeyboardFocusHere(),
 * setItemDefaultFocus(), etc).
 *
 * <p>Navigation works by scoring every item submitted to the navigation window (in itemAdd()) when
 * a move request is active, and applying the best result at the start of the next frame.
 *
 * <p>Gamepad support is limited to what can be done with digital buttons, as analog values are not
 * tracked yet.
 */
@Slf4j
class IkGuiImplNav {
    /** The main navigation layer. */
    static final int NAV_LAYER_MAIN = 0;

    /** The menu navigation layer (menu bar, title bar buttons). */
    static final int NAV_LAYER_MENU = 1;

    /** How long the activation highlight is displayed, in milliseconds. */
    private static final long NAV_ACTIVATE_HIGHLIGHT_TIMER = 100;

    /** Time before the highlight and screen dimming starts fading in, in milliseconds. */
    private static final long NAV_WINDOWING_HIGHLIGHT_DELAY = 200;

    /** Time before the window list starts to appear, in milliseconds. */
    private static final long NAV_WINDOWING_LIST_APPEAR_DELAY = 150;

    /** Ownership ID used by navigation windowing (Ctrl+Tab). */
    private static final int NAV_WINDOWING_OWNER_ID = Hash.getID("##NavUpdateWindowing");

    /**
     * The gamepad button for activating items, which depends on io.configNavSwapGamepadButtons.
     *
     * @return The button.
     */
    static Key navGamepadActivateKey() {
        return context.io.configNavSwapGamepadButtons
                ? Key.GAMEPAD_FACE_RIGHT
                : Key.GAMEPAD_FACE_DOWN;
    }

    /**
     * The gamepad button for canceling, closing popups and leaving child windows, which depends on
     * io.configNavSwapGamepadButtons.
     *
     * @return The button.
     */
    static Key navGamepadCancelKey() {
        return context.io.configNavSwapGamepadButtons
                ? Key.GAMEPAD_FACE_DOWN
                : Key.GAMEPAD_FACE_RIGHT;
    }

    /** Gamepad button for toggling the menu layer, or holding for window switching. */
    private static final Key NAV_GAMEPAD_MENU = Key.GAMEPAD_FACE_LEFT;

    /** Gamepad button for text input. */
    private static final Key NAV_GAMEPAD_INPUT = Key.GAMEPAD_FACE_UP;

    static Context context;

    // ---------------------------------------------------------------------------------------
    // Navigation state helpers
    // ---------------------------------------------------------------------------------------

    /**
     * Whether keyboard navigation is enabled.
     *
     * @return True if keyboard navigation is enabled.
     */
    private static boolean isNavKeyboardActive() {
        return (context.io.configFlags & ConfigFlags.NAV_ENABLE_KEYBOARD) != 0;
    }

    /**
     * Whether gamepad navigation is enabled and the backend has a gamepad.
     *
     * @return True if gamepad navigation is enabled.
     */
    private static boolean isNavGamepadActive() {
        return (context.io.configFlags & ConfigFlags.NAV_ENABLE_GAMEPAD) != 0
                && (context.io.backendFlags & BackendFlags.HAS_GAMEPAD) != 0;
    }

    /**
     * Whether the navigation window accepts navigation inputs.
     *
     * @return True if there is a navigation window that accepts navigation inputs.
     */
    private static boolean navWindowAcceptsInputs() {
        return context.navFocusedWindow != null
                && (context.navFocusedWindow.flags & WindowFlags.NO_NAV_INPUTS) == 0;
    }

    /**
     * Set whether the navigation cursor is visible.
     *
     * @param visible Whether the cursor should be visible.
     */
    static void setNavCursorVisible(boolean visible) {
        if (context.navFocusedWindow != null
                && (context.navFocusedWindow.flags & WindowFlags.NO_NAV_INPUTS) != 0) {
            visible = false;
        } else if (context.io.configNavCursorVisibleAlways) {
            visible = true;
        }
        context.navCursorVisible = visible;
    }

    /** Make the navigation cursor visible after a navigation move, if allowed. */
    static void setNavCursorVisibleAfterMove() {
        if (context.navFocusedWindow != null
                && (context.navFocusedWindow.flags & WindowFlags.NO_NAV_INPUTS) != 0) {
            context.navCursorVisible = false;
        } else if (context.navInputSource == GuiInputSource.KEYBOARD && !isNavKeyboardActive()) {
            context.navCursorVisible = false;
        } else if (context.navInputSource == GuiInputSource.GAMEPAD
                && (context.io.configFlags & ConfigFlags.NAV_ENABLE_GAMEPAD) == 0) {
            context.navCursorVisible = false;
        } else if (context.io.configNavCursorVisibleAuto) {
            context.navCursorVisible = true;
        }
        context.navHighlightItemUnderNav = context.navMousePositionDirty = true;
    }

    /**
     * Set the window that navigation operates in.
     *
     * @param window The window, may be null.
     */
    static void setNavWindow(Window window) {
        if (context.navFocusedWindow != window) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_FOCUS,
                    "[focus] SetNavWindow(\"%s\")",
                    IkGuiImplDebugTools.nameOf(window));
        }
        if (context.navFocusedWindow != window) {
            context.navFocusedWindow = window;
            context.navLastValidSelectionUserData = -1;
        }
        context.navInitRequest = context.navMoveSubmitted = context.navMoveScoringItems = false;
        navUpdateAnyRequestFlag();
    }

    /**
     * Briefly highlight an item that was activated through navigation.
     *
     * @param id The item ID.
     */
    static void navHighlightActivated(int id) {
        context.navHighlightActivatedID = id;
        context.navHighlightActivatedTimer = NAV_ACTIVATE_HIGHLIGHT_TIMER;
    }

    /**
     * Clear the preferred position for scoring navigation moves on one axis.
     *
     * @param axis The axis.
     */
    static void navClearPreferredPosForAxis(@NonNull Axis axis) {
        final Vector2f preferred =
                context.navFocusedWindow
                        .rootWindowForNavigation
                        .navPreferredScoringPositionRelative[context.navLayer];
        if (axis == Axis.X) {
            preferred.x = Float.MAX_VALUE;
        } else {
            preferred.y = Float.MAX_VALUE;
        }
    }

    /**
     * Set the navigation ID, in the navigation window.
     *
     * @param id The item ID.
     * @param navLayer The navigation layer.
     * @param focusScopeID The focus scope ID.
     * @param rectRelative The navigation rectangle, relative to the window.
     */
    static void setNavID(int id, int navLayer, int focusScopeID, @NonNull RectFloat rectRelative) {
        if (context.navFocusedWindow == null) {
            IkGuiImplDebugTools.reportError(log, "setNavID() requires a navigation window");
            return;
        }
        context.navID = id;
        context.navLayer = navLayer;
        setNavFocusScope(focusScopeID);
        context.navFocusedWindow.navLastIDs[navLayer] = id;
        context.navFocusedWindow.navRectRelative[navLayer].set(rectRelative);

        // Clear the preferred scoring position (navMoveRequestApplyResult() tends to restore it)
        navClearPreferredPosForAxis(Axis.X);
        navClearPreferredPosForAxis(Axis.Y);
    }

    /**
     * Focus an item, which moves the navigation cursor to it.
     *
     * @param id The item ID.
     * @param window The window, which may be a child window of the current window.
     */
    static void setFocusID(int id, @NonNull Window window) {
        if (id == 0) {
            IkGuiImplDebugTools.reportError(log, "setFocusID() requires a non-zero ID");
            return;
        }
        if (context.navFocusedWindow != window) {
            setNavWindow(window);
        }

        // Assume that setFocusID() is called in the context where the window's current nav layer
        // and the current focus scope are valid
        final int navLayer = window.navLayerCurrent;
        context.navID = id;
        context.navLayer = navLayer;
        setNavFocusScope(context.currentFocusScopeID);
        window.navLastIDs[navLayer] = id;
        if (context.lastItemData.id == id) {
            windowRectAbsToRel(
                    window, context.lastItemData.navRect, window.navRectRelative[navLayer]);
        }
        context.navIDItemFlags =
                context.lastItemData.id == id ? context.lastItemData.itemFlags : ItemFlags.NONE;
        if (id == context.activeIDIsAlive) {
            context.navIDAlive = true;
        }

        if (context.activeIDSource == GuiInputSource.KEYBOARD
                || context.activeIDSource == GuiInputSource.GAMEPAD) {
            context.navHighlightItemUnderNav = true;
        } else if (context.io.configNavCursorVisibleAuto) {
            context.navCursorVisible = false;
        }

        navClearPreferredPosForAxis(Axis.X);
        navClearPreferredPosForAxis(Axis.Y);
    }

    /**
     * Convert a rectangle from screen space to be relative to a window.
     *
     * @param window The window.
     * @param rect The rectangle in screen space.
     * @param output Where to store the relative rectangle.
     * @return The output, for convenience.
     */
    static RectFloat windowRectAbsToRel(
            @NonNull Window window, @NonNull RectFloat rect, @NonNull RectFloat output) {
        final float offsetX = window.cursorStartPosition.x;
        final float offsetY = window.cursorStartPosition.y;
        output.set(
                rect.getLeft() - offsetX,
                rect.getTop() - offsetY,
                rect.getRight() - offsetX,
                rect.getBottom() - offsetY);
        return output;
    }

    /**
     * Convert a rectangle relative to a window into screen space.
     *
     * @param window The window.
     * @param rect The relative rectangle.
     * @param output Where to store the rectangle in screen space.
     * @return The output, for convenience.
     */
    static RectFloat windowRectRelToAbs(
            @NonNull Window window, @NonNull RectFloat rect, @NonNull RectFloat output) {
        final float offsetX = window.cursorStartPosition.x;
        final float offsetY = window.cursorStartPosition.y;
        output.set(
                rect.getLeft() + offsetX,
                rect.getTop() + offsetY,
                rect.getRight() + offsetX,
                rect.getBottom() + offsetY);
        return output;
    }

    // ---------------------------------------------------------------------------------------
    // Focus scopes
    // ---------------------------------------------------------------------------------------

    /**
     * Push a focus scope, which groups items for tabbing and shortcut routing.
     *
     * @param id The ID of the focus scope.
     */
    static void pushFocusScope(int id) {
        context.focusScopeStack.add(new FocusScopeData(id, context.windowCurrent.id));
        context.currentFocusScopeID = id;
    }

    /** Pop a focus scope. */
    static void popFocusScope() {
        if (context.focusScopeStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Calling popFocusScope() too many times!");
            return;
        }
        context.focusScopeStack.removeLast();
        context.currentFocusScopeID =
                context.focusScopeStack.isEmpty() ? 0 : context.focusScopeStack.getLast().id;
    }

    /**
     * Set the focus scope of the navigation item, and store the path of focus scopes to it.
     *
     * @param focusScopeID The focus scope ID.
     */
    static void setNavFocusScope(int focusScopeID) {
        context.navFocusScopeID = focusScopeID;
        context.navFocusRoute.clear();
        if (focusScopeID == 0) {
            return;
        }
        final Window navWindow = context.navFocusedWindow;
        if (navWindow == null) {
            return;
        }

        // Store the current path (in reverse order)
        if (focusScopeID == context.currentFocusScopeID && context.windowCurrent != null) {
            // The top of the focus stack contains local focus scopes inside the current window
            for (int i = context.focusScopeStack.size() - 1; i >= 0; --i) {
                final FocusScopeData scope = context.focusScopeStack.get(i);
                if (scope.windowID != context.windowCurrent.id) {
                    break;
                }
                context.navFocusRoute.add(scope);
            }
        } else if (focusScopeID == navWindow.navRootFocusScopeID) {
            context.navFocusRoute.add(new FocusScopeData(focusScopeID, navWindow.id));
        } else {
            return;
        }

        // Then follow the parent windows for the focus route
        for (Window window = navWindow.parentWindowForFocusRoute;
                window != null;
                window = window.parentWindowForFocusRoute) {
            context.navFocusRoute.add(new FocusScopeData(window.navRootFocusScopeID, window.id));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Scoring and item processing
    // ---------------------------------------------------------------------------------------

    /**
     * Figure out which direction a delta points in.
     *
     * @param dx The x delta.
     * @param dy The y delta.
     * @return The direction.
     */
    static Direction getDirQuadrantFromDelta(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0.0f ? Direction.RIGHT : Direction.LEFT;
        }
        return dy > 0.0f ? Direction.DOWN : Direction.UP;
    }

    /**
     * Distance between two intervals, 0 if they overlap.
     *
     * @param candidateMin The min of the candidate interval.
     * @param candidateMax The max of the candidate interval.
     * @param currentMin The min of the current interval.
     * @param currentMax The max of the current interval.
     * @return The signed distance.
     */
    private static float navScoreItemDistInterval(
            float candidateMin, float candidateMax, float currentMin, float currentMax) {
        if (candidateMax < currentMin) {
            return candidateMax - currentMin;
        }
        if (currentMax < candidateMin) {
            return candidateMin - currentMax;
        }
        return 0.0f;
    }

    /**
     * Linear interpolation.
     *
     * @param a The start.
     * @param b The end.
     * @param t The ratio.
     * @return The interpolated value.
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Score an item for a directional navigation move.
     *
     * @param result The best result so far, updated with the distances of a new best result.
     * @param navBB The navigation rectangle of the item.
     * @return True if the item is the new best result.
     */
    private static boolean navScoreItem(@NonNull NavItemData result, @NonNull RectFloat navBB) {
        final Window window = context.windowCurrent;
        if (context.navLayer != window.navLayerCurrent) {
            return false;
        }

        // Current item nav rectangle, and current modified source rect
        final RectFloat candidate = new RectFloat(0, 0, 0, 0);
        candidate.set(navBB);
        final RectFloat current = context.navScoringRect;
        context.navScoringDebugCount++;

        // When entering through a flattened border, we consider child window items as fully
        // clipped for scoring
        if (window.parentWindow == context.navFocusedWindow) {
            if (!window.rectCurrentClip.overlaps(candidate)) {
                return false;
            }
            // This allows the scored item to not overlap other candidates in the parent window
            candidate.clipWithFull(window.rectCurrentClip);
        }

        // Compute the distance between boxes
        float dbx =
                navScoreItemDistInterval(
                        candidate.getLeft(),
                        candidate.getRight(),
                        current.getLeft(),
                        current.getRight());
        // Scale down on Y to keep using box distance for vertically touching items
        final float dby =
                navScoreItemDistInterval(
                        lerp(candidate.getTop(), candidate.getBottom(), 0.2f),
                        lerp(candidate.getTop(), candidate.getBottom(), 0.8f),
                        lerp(current.getTop(), current.getBottom(), 0.2f),
                        lerp(current.getTop(), current.getBottom(), 0.8f));
        if (dby != 0.0f && dbx != 0.0f) {
            dbx = (dbx / 1000.0f) + (dbx > 0.0f ? 1.0f : -1.0f);
        }
        final float distBox = Math.abs(dbx) + Math.abs(dby);

        // Compute the distance between centers (off by a factor of 2, but we only compare center
        // distances with each other so it doesn't matter)
        final float dcx =
                (candidate.getLeft() + candidate.getRight())
                        - (current.getLeft() + current.getRight());
        final float dcy =
                (candidate.getTop() + candidate.getBottom())
                        - (current.getTop() + current.getBottom());
        final float distCenter = Math.abs(dcx) + Math.abs(dcy);

        // Determine which quadrant of the current rect the candidate lies in
        final Direction quadrant;
        float dax = 0.0f;
        float day = 0.0f;
        float distAxial = 0.0f;
        if (dbx != 0.0f || dby != 0.0f) {
            // For non-overlapping boxes, use the distance between boxes
            dax = dbx;
            day = dby;
            distAxial = distBox;
            quadrant = getDirQuadrantFromDelta(dbx, dby);
        } else if (dcx != 0.0f || dcy != 0.0f) {
            // For overlapping boxes with different centers, use the distance between centers
            dax = dcx;
            day = dcy;
            distAxial = distCenter;
            quadrant = getDirQuadrantFromDelta(dcx, dcy);
        } else {
            // Degenerate case: two overlapping buttons with the same center, break ties
            // arbitrarily
            quadrant =
                    Integer.compareUnsigned(context.lastItemData.id, context.navID) < 0
                            ? Direction.LEFT
                            : Direction.RIGHT;
        }

        final Direction moveDirection = context.navMoveDirection;

        // Is it in the quadrant we're interested in moving to?
        boolean newBest = false;
        if (quadrant == moveDirection) {
            // Does it beat the current best candidate?
            if (distBox < result.distanceBox) {
                result.distanceBox = distBox;
                result.distanceCenter = distCenter;
                return true;
            }
            if (distBox == result.distanceBox) {
                // Try using the distance between center points to break ties
                if (distCenter < result.distanceCenter) {
                    result.distanceCenter = distCenter;
                    newBest = true;
                } else if (distCenter == result.distanceCenter) {
                    // Still tied! We consistently break ties by symbolically moving later items
                    // to the right/downwards by an infinitesimal amount
                    final boolean vertical =
                            moveDirection == Direction.UP || moveDirection == Direction.DOWN;
                    if ((vertical ? dby : dbx) < 0.0f) {
                        newBest = true;
                    }
                }
            }
        }

        // Axial check: if the current rect has no link at all in some direction and the
        // candidate lies roughly in that direction, add a tentative link. This is only enabled
        // inside menu bars.
        if (result.distanceBox == Float.MAX_VALUE
                && distAxial < result.distanceAxial
                && context.navLayer == NAV_LAYER_MENU
                && (context.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) == 0
                && ((moveDirection == Direction.LEFT && dax < 0.0f)
                        || (moveDirection == Direction.RIGHT && dax > 0.0f)
                        || (moveDirection == Direction.UP && day < 0.0f)
                        || (moveDirection == Direction.DOWN && day > 0.0f))) {
            result.distanceAxial = distAxial;
            newBest = true;
        }

        return newBest;
    }

    /**
     * Store the last item as a navigation result.
     *
     * @param result Where to store the result.
     */
    private static void navApplyItemToResult(@NonNull NavItemData result) {
        final Window window = context.windowCurrent;
        result.window = window;
        result.id = context.lastItemData.id;
        result.focusScopeID = context.currentFocusScopeID;
        result.itemFlags = context.lastItemData.itemFlags;
        windowRectAbsToRel(window, context.lastItemData.navRect, result.rectRelative);
        result.selectionUserData = context.nextItemData.selectionUserData;
    }

    /**
     * Process the last item for navigation, called from itemAdd() when the item is the navigation
     * item or there is a navigation request.
     */
    static void navProcessItem() {
        final Window window = context.windowCurrent;
        final int id = context.lastItemData.id;
        final int itemFlags = context.lastItemData.itemFlags;

        // When inside a container that isn't scrollable with left/right, clip the nav rect
        final RectFloat navBB = new RectFloat(0, 0, 0, 0);
        navBB.set(context.lastItemData.navRect);
        if (!window.navIsScrollPushableX) {
            final RectFloat clip = window.rectCurrentClip;
            navBB.setLeft(MathUtil.clamp(navBB.getLeft(), clip.getLeft(), clip.getRight()));
            navBB.setRight(MathUtil.clamp(navBB.getRight(), clip.getLeft(), clip.getRight()));
        }

        // Process an init request
        if (context.navInitRequest
                && context.navLayer == window.navLayerCurrent
                && (itemFlags & ItemFlags.DISABLED) == 0) {
            // Even if NO_NAV_DEFAULT_FOCUS is on (typically collapse/close button) we record the
            // first result so it can be used as a fallback
            final boolean candidateForNavDefaultFocus =
                    (itemFlags & ItemFlags.NO_NAV_DEFAULT_FOCUS) == 0;
            if (candidateForNavDefaultFocus || context.navInitResult.id == 0) {
                navApplyItemToResult(context.navInitResult);
            }
            if (candidateForNavDefaultFocus) {
                // Found a match, clear the request
                context.navInitRequest = false;
                navUpdateAnyRequestFlag();
            }
        }

        // Process a move request (scoring for navigation)
        if (context.navMoveScoringItems && (itemFlags & ItemFlags.DISABLED) == 0) {
            if ((context.navMoveFlags & NavMoveFlags.FOCUS_API) != 0
                    || (window.flags & WindowFlags.NO_NAV_INPUTS) == 0) {
                final boolean isTabbing = (context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0;
                if (isTabbing) {
                    navProcessItemForTabbingRequest(id, itemFlags, context.navMoveFlags);
                } else if (context.navID != id
                        || (context.navMoveFlags & NavMoveFlags.ALLOW_CURRENT_NAV_ID) != 0) {
                    final NavItemData result =
                            window == context.navFocusedWindow
                                    ? context.navMoveResultLocal
                                    : context.navMoveResultOther;
                    if (navScoreItem(result, navBB)) {
                        navApplyItemToResult(result);
                    }

                    // Page up/down need to maintain a separate score for the visible items
                    final float visibleRatio = 0.70f;
                    if ((context.navMoveFlags & NavMoveFlags.ALSO_SCORE_VISIBLE_SET) != 0) {
                        final RectFloat inner = window.rectInner;
                        if (inner.overlaps(navBB)) {
                            final float visibleHeight =
                                    MathUtil.clamp(
                                                    navBB.getBottom(),
                                                    inner.getTop(),
                                                    inner.getBottom())
                                            - MathUtil.clamp(
                                                    navBB.getTop(),
                                                    inner.getTop(),
                                                    inner.getBottom());
                            if (visibleHeight >= navBB.getHeight() * visibleRatio
                                    && navScoreItem(context.navMoveResultLocalVisible, navBB)) {
                                navApplyItemToResult(context.navMoveResultLocalVisible);
                            }
                        }
                    }
                }
            }
        }

        // Update information for the current navigation item
        if (context.navID == id) {
            if (context.navFocusedWindow != window) {
                // Always refresh the navigation window, because some operations such as
                // focusItem() may not have a window
                setNavWindow(window);
            }
            context.navLayer = window.navLayerCurrent;
            setNavFocusScope(context.currentFocusScopeID);
            context.navIDAlive = true;
            context.navIDItemFlags = itemFlags;
            if ((itemFlags & ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA) != 0) {
                context.navLastValidSelectionUserData = context.nextItemData.selectionUserData;
            }
            // Store the item bounding box (relative to the window position)
            windowRectAbsToRel(window, navBB, window.navRectRelative[window.navLayerCurrent]);
        }
    }

    /**
     * Handle the "scoring" of an item for a tabbing/focusing request.
     *
     * <ul>
     *   <li>No nav/active ID: set the result to the first eligible item, stop storing.
     *   <li>Tab forward: on the reference ID set a counter, store the result when it elapses.
     *   <li>Tab forward wrap: set the result to the first eligible item preemptively, on the
     *       reference ID set the counter, on the next frame if the counter hasn't elapsed store the
     *       result.
     *   <li>Tab backward: store all results, on the reference ID pick the previous one.
     *   <li>Tab backward wrap: store all results, on the reference ID if there is no result keep
     *       storing until the last one.
     * </ul>
     *
     * @param id The item ID.
     * @param itemFlags The item flags.
     * @param moveFlags The move flags.
     */
    private static void navProcessItemForTabbingRequest(int id, int itemFlags, int moveFlags) {
        if ((moveFlags & NavMoveFlags.FOCUS_API) == 0) {
            if (context.navLayer != context.windowCurrent.navLayerCurrent) {
                return;
            }
            if (context.navFocusScopeID != context.currentFocusScopeID) {
                return;
            }
        }

        // We can always land on an item when using the API. Tabbing with keyboard navigation
        // goes through every item, without it only through inputable items.
        final boolean canStop;
        if ((moveFlags & NavMoveFlags.FOCUS_API) != 0) {
            canStop = true;
        } else {
            canStop =
                    (itemFlags & ItemFlags.NO_TAB_STOP) == 0
                            && (isNavKeyboardActive()
                                    || (itemFlags & ItemFlags.INTERNAL_INPUTABLE) != 0);
        }

        // Always store in the local result (unlike directional requests)
        final NavItemData result = context.navMoveResultLocal;
        if (context.navTabbingDirection == 1) {
            // Tab forward, or setKeyboardFocusHere() with offset >= 0
            if (canStop && context.navTabbingResultFirst.id == 0) {
                navApplyItemToResult(context.navTabbingResultFirst);
            }
            if (canStop && context.navTabbingCounter > 0 && --context.navTabbingCounter == 0) {
                navMoveRequestResolveWithLastItem(result);
            } else if (context.navID == id) {
                context.navTabbingCounter = 1;
            }
        } else if (context.navTabbingDirection == -1) {
            // Tab backward
            if (context.navID == id) {
                if (result.id != 0) {
                    context.navMoveScoringItems = false;
                    navUpdateAnyRequestFlag();
                }
            } else if (canStop) {
                // Keep applying until reaching the nav ID
                navApplyItemToResult(result);
            }
        } else if (context.navTabbingDirection == 0) {
            if (canStop && context.navID == id) {
                navMoveRequestResolveWithLastItem(result);
            }
            if (canStop && context.navTabbingResultFirst.id == 0) {
                navApplyItemToResult(context.navTabbingResultFirst);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Move requests
    // ---------------------------------------------------------------------------------------

    /**
     * Whether there is an active move request that hasn't found a result yet.
     *
     * @return True if a move request has no result yet.
     */
    static boolean navMoveRequestButNoResultYet() {
        return context.navMoveScoringItems
                && context.navMoveResultLocal.id == 0
                && context.navMoveResultOther.id == 0;
    }

    /**
     * Submit a navigation move request.
     *
     * @param moveDirection The direction to move.
     * @param clipDirection The direction used for clipping.
     * @param moveFlags Move flags.
     * @param scrollFlags Scroll flags for the result.
     */
    static void navMoveRequestSubmit(
            @NonNull Direction moveDirection,
            @NonNull Direction clipDirection,
            int moveFlags,
            int scrollFlags) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_NAV,
                "[nav] NavMoveRequestSubmit: dir %s, window \"%s\"",
                moveDirection,
                IkGuiImplDebugTools.nameOf(context.navFocusedWindow));
        if (context.navFocusedWindow == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Navigation move requests require a navigation window");
            return;
        }
        if ((moveFlags & NavMoveFlags.IS_TABBING) != 0) {
            moveFlags |= NavMoveFlags.ALLOW_CURRENT_NAV_ID;
        }

        context.navMoveSubmitted = context.navMoveScoringItems = true;
        context.navMoveDirection = moveDirection;
        context.navMoveDirectionForDebug = moveDirection;
        context.navMoveClipDirection = clipDirection;
        context.navMoveFlags = moveFlags;
        context.navMoveScrollFlags = scrollFlags;
        context.navMoveForwardToNextFrame = false;
        context.navMoveKeyMods = (moveFlags & NavMoveFlags.FOCUS_API) != 0 ? 0 : context.io.keyMods;
        context.navMoveResultLocal.clear();
        context.navMoveResultLocalVisible.clear();
        context.navMoveResultOther.clear();
        context.navTabbingCounter = 0;
        context.navTabbingResultFirst.clear();
        navUpdateAnyRequestFlag();
    }

    /**
     * Resolve the move request with the last item.
     *
     * @param result Where to store the result.
     */
    static void navMoveRequestResolveWithLastItem(@NonNull NavItemData result) {
        // Ensure the request doesn't need more processing
        context.navMoveScoringItems = false;
        navApplyItemToResult(result);
        navUpdateAnyRequestFlag();
    }

    /**
     * Resolve the move request with a tree node that was already submitted, for
     * TreeNodeFlags.NAV_LEFT_JUMPS_TO_PARENT.
     *
     * @param result Where to store the result.
     * @param treeNodeData The tree node data.
     */
    static void navMoveRequestResolveWithPastTreeNode(
            @NonNull NavItemData result, @NonNull TreeNodeStackData treeNodeData) {
        context.navMoveScoringItems = false;
        context.lastItemData.id = treeNodeData.id;
        context.lastItemData.itemFlags =
                treeNodeData.itemFlags & ~ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA;
        context.lastItemData.navRect.set(treeNodeData.navRect);
        navApplyItemToResult(result);
        navClearPreferredPosForAxis(Axis.Y);
        navUpdateAnyRequestFlag();
    }

    /** Cancel the current move request. */
    static void navMoveRequestCancel() {
        context.navMoveSubmitted = context.navMoveScoringItems = false;
        navUpdateAnyRequestFlag();
    }

    /**
     * Forward the move request to the next frame (generally with modifications done to it).
     *
     * @param moveDirection The direction to move.
     * @param clipDirection The direction used for clipping.
     * @param moveFlags Move flags.
     * @param scrollFlags Scroll flags for the result.
     */
    static void navMoveRequestForward(
            @NonNull Direction moveDirection,
            @NonNull Direction clipDirection,
            int moveFlags,
            int scrollFlags) {
        navMoveRequestCancel();
        context.navMoveForwardToNextFrame = true;
        context.navMoveDirection = moveDirection;
        context.navMoveClipDirection = clipDirection;
        context.navMoveFlags = moveFlags | NavMoveFlags.FORWARDED;
        context.navMoveScrollFlags = scrollFlags;
    }

    /**
     * Request wrapping around if the move request fails in a window. This is delayed to the end of
     * the frame, since it's only valid after the entire window is submitted.
     *
     * @param window The window.
     * @param wrapFlags One of the LOOP_X/LOOP_Y/WRAP_X/WRAP_Y move flags.
     */
    static void navMoveRequestTryWrapping(@NonNull Window window, int wrapFlags) {
        if (context.navFocusedWindow == window
                && context.navMoveScoringItems
                && context.navLayer == window.navLayerCurrent) {
            context.navMoveFlags = (context.navMoveFlags & ~NavMoveFlags.WRAP_MASK) | wrapFlags;
        }
    }

    /**
     * Remember the last focused child window in its parent, for returning from the menu layer.
     *
     * @param navWindow The navigation window.
     */
    private static void navSaveLastChildNavWindowIntoParent(@NonNull Window navWindow) {
        Window parent = navWindow;
        while (parent != null
                && parent.rootWindow != parent
                && (parent.flags & (WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_CHILD_MENU))
                        == 0) {
            parent = parent.parentWindow;
        }
        if (parent != null && parent != navWindow) {
            parent.navLastChildNavWindow = navWindow;
        }
    }

    /**
     * Find the last focused child of a window.
     *
     * @param window The window.
     * @return The last focused child, or the window itself.
     */
    static Window navRestoreLastChildNavWindow(@NonNull Window window) {
        if (window.navLastChildNavWindow != null && window.navLastChildNavWindow.wasActive) {
            return window.navLastChildNavWindow;
        }
        if (window.dockNodeAsHost != null && window.dockNodeAsHost.tabBar != null) {
            final TabItem tab =
                    IkGuiImplTabs.tabBarFindMostRecentlySelectedTabForActiveWindow(
                            window.dockNodeAsHost.tabBar);
            if (tab != null) {
                return tab.window;
            }
        }
        return window;
    }

    /**
     * Restore navigation to a layer.
     *
     * @param layer The layer.
     */
    static void navRestoreLayer(int layer) {
        if (layer == NAV_LAYER_MAIN) {
            context.navFocusedWindow = navRestoreLastChildNavWindow(context.navFocusedWindow);
            context.navLastValidSelectionUserData = -1;
        }
        final Window window = context.navFocusedWindow;
        if (window.navLastIDs[layer] != 0) {
            setNavID(window.navLastIDs[layer], layer, 0, window.navRectRelative[layer]);
        } else {
            context.navLayer = layer;
            navInitWindow(window, true);
        }
    }

    /** Update whether any navigation request needs items to be processed. */
    static void navUpdateAnyRequestFlag() {
        context.navAnyRequest = context.navMoveScoringItems || context.navInitRequest;
        if (context.navAnyRequest && context.navFocusedWindow == null) {
            IkGuiImplDebugTools.reportError(log, "Navigation requests require a navigation window");
            context.navAnyRequest = false;
        }
    }

    /**
     * Initialize navigation for a window, selecting the first/default item. This needs to be called
     * before any items are submitted (in or before begin()).
     *
     * @param window The window, which must be the navigation window.
     * @param forceReinit Whether to reinitialize even if there is a previous navigation item.
     */
    static void navInitWindow(@NonNull Window window, boolean forceReinit) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_NAV,
                "[nav] NavInitWindow: window=\"%s\", layer=%d",
                window.name,
                context.navLayer);
        if (window != context.navFocusedWindow) {
            IkGuiImplDebugTools.reportError(
                    log, "navInitWindow() must be called on the navigation window");
            return;
        }

        if ((window.flags & WindowFlags.NO_NAV_INPUTS) != 0) {
            context.navID = 0;
            setNavFocusScope(window.navRootFocusScopeID);
            return;
        }

        boolean initForNav =
                window == window.rootWindow
                        || (window.flags & WindowFlags.INTERNAL_POPUP) != 0
                        || window.navLastIDs[0] == 0
                        || forceReinit;
        if (initForNav) {
            setNavID(0, context.navLayer, window.navRootFocusScopeID, new RectFloat(0, 0, 0, 0));
            context.navInitRequest = true;
            context.navInitRequestFromMove = false;
            context.navInitResult.id = 0;
            navUpdateAnyRequestFlag();
        } else {
            context.navID = window.navLastIDs[0];
            setNavFocusScope(window.navRootFocusScopeID);
        }
    }

    /**
     * Whether the preferred reference position for popups should come from the navigation cursor
     * rather than the mouse.
     *
     * @param popupWindow Whether this is for a popup window.
     * @return The input source to use.
     */
    private static GuiInputSource navCalcPreferredRefPosSource(boolean popupWindow) {
        final boolean activatedShortcut =
                context.activeID != 0
                        && context.activeIDFromShortcut
                        && context.activeID == context.lastItemData.id;
        if (popupWindow && activatedShortcut) {
            return GuiInputSource.KEYBOARD;
        }
        if (!context.navCursorVisible
                || !context.navHighlightItemUnderNav
                || context.navFocusedWindow == null) {
            return GuiInputSource.MOUSE;
        }
        return GuiInputSource.KEYBOARD;
    }

    /**
     * Calculate the reference position for positioning popups and tooltips, which is the mouse or
     * the navigation cursor.
     *
     * @param popupWindow Whether this is for a popup window.
     * @param output Where to store the position.
     */
    static void navCalcPreferredRefPos(boolean popupWindow, @NonNull Vector2f output) {
        final Window window = context.navFocusedWindow;
        final GuiInputSource source = navCalcPreferredRefPosSource(popupWindow);

        if (source == GuiInputSource.MOUSE) {
            // We need a fallback in case the mouse becomes invalid after being used. The +1
            // offset allows reopening a popup while not moving the mouse.
            final Vector2f p =
                    IkGuiImplUtils.isMousePosValid(
                                    context.io.mousePosition.x, context.io.mousePosition.y)
                            ? context.io.mousePosition
                            : context.mouseLastValidPosition;
            output.set(p.x + 1.0f, p.y);
            return;
        }

        // When navigation is active, pick a position around the bottom left of the navigation item
        final boolean activatedShortcut =
                context.activeID != 0
                        && context.activeIDFromShortcut
                        && context.activeID == context.lastItemData.id;
        final RectFloat refRect = new RectFloat(0, 0, 0, 0);
        if (activatedShortcut && popupWindow) {
            refRect.set(context.lastItemData.navRect);
        } else if (window != null) {
            windowRectRelToAbs(window, window.navRectRelative[context.navLayer], refRect);
        }

        // Take account of upcoming scrolling
        if (window != null
                && window.lastFrameActive != context.frameCount
                && (window.scrollTarget.x != Float.MAX_VALUE
                        || window.scrollTarget.y != Float.MAX_VALUE)) {
            final Vector2f nextScroll = new Vector2f();
            IkGuiImplWindows.calcNextScrollFromScrollTargetAndClamp(window, nextScroll);
            refRect.translate(
                    window.scrollPosition.x - nextScroll.x, window.scrollPosition.y - nextScroll.y);
        }
        final StyleVariables style = context.style.variable;
        float x = refRect.getLeft() + Math.min(style.framePadding.x * 4, refRect.getWidth());
        float y = refRect.getBottom() - Math.min(style.framePadding.y, refRect.getHeight());
        if (window != null && window.viewport != null) {
            final Viewport viewport = window.viewport;
            x = MathUtil.clamp(x, viewport.position.x, viewport.position.x + viewport.size.x);
            y = MathUtil.clamp(y, viewport.position.y, viewport.position.y + viewport.size.y);
        }
        // Truncating is important because a non-integer mouse position may be lossy
        output.set(IkGuiInternal.truncate(x), IkGuiInternal.truncate(y));
    }

    /**
     * The amount to tweak a value by with keyboard/gamepad navigation (e.g. arrows on a slider).
     *
     * @param axis The axis.
     * @return The tweak amount, positive for right/down.
     */
    static float getNavTweakPressedAmount(@NonNull Axis axis) {
        final long[] repeat =
                IkGuiImplKeys.getTypematicRepeatRate(InputFlags.INTERNAL_REPEAT_RATE_NAV_TWEAK);

        final Key keyLess;
        final Key keyMore;
        if (context.navInputSource == GuiInputSource.GAMEPAD) {
            keyLess = axis == Axis.X ? Key.GAMEPAD_DPAD_LEFT : Key.GAMEPAD_DPAD_UP;
            keyMore = axis == Axis.X ? Key.GAMEPAD_DPAD_RIGHT : Key.GAMEPAD_DPAD_DOWN;
        } else {
            keyLess = axis == Axis.X ? Key.ARROW_LEFT : Key.ARROW_UP;
            keyMore = axis == Axis.X ? Key.ARROW_RIGHT : Key.ARROW_DOWN;
        }
        float amount =
                (float) IkGuiImplUtils.getKeyPressedAmount(keyMore, repeat[0], repeat[1])
                        - (float) IkGuiImplUtils.getKeyPressedAmount(keyLess, repeat[0], repeat[1]);
        // Cancel when opposite directions are held, regardless of repeat phase
        if (amount != 0.0f && context.io.getKeyDown(keyLess) && context.io.getKeyDown(keyMore)) {
            amount = 0.0f;
        }
        return amount;
    }

    // ---------------------------------------------------------------------------------------
    // Frame update
    // ---------------------------------------------------------------------------------------

    /** Update navigation at the start of the frame. */
    static void navUpdate() {
        final IkIO io = context.io;
        io.wantSetMousePosition = false;

        // Set the input source based on which keys were last pressed
        final boolean navGamepadActive = isNavGamepadActive();
        final Key[] navGamepadKeysToChangeSource = {
            Key.GAMEPAD_FACE_RIGHT,
            Key.GAMEPAD_FACE_LEFT,
            Key.GAMEPAD_FACE_UP,
            Key.GAMEPAD_FACE_DOWN,
            Key.GAMEPAD_DPAD_RIGHT,
            Key.GAMEPAD_DPAD_LEFT,
            Key.GAMEPAD_DPAD_UP,
            Key.GAMEPAD_DPAD_DOWN
        };
        if (navGamepadActive && context.navInputSource != GuiInputSource.GAMEPAD) {
            for (Key key : navGamepadKeysToChangeSource) {
                if (io.getKeyDown(key)) {
                    context.navInputSource = GuiInputSource.GAMEPAD;
                }
            }
        }
        final boolean navKeyboardActive = isNavKeyboardActive();
        final Key[] navKeyboardKeysToChangeSource = {
            Key.SPACE,
            Key.ENTER,
            Key.ESCAPE,
            Key.ARROW_RIGHT,
            Key.ARROW_LEFT,
            Key.ARROW_UP,
            Key.ARROW_DOWN
        };
        if (navKeyboardActive && context.navInputSource != GuiInputSource.KEYBOARD) {
            for (Key key : navKeyboardKeysToChangeSource) {
                if (io.getKeyDown(key)) {
                    context.navInputSource = GuiInputSource.KEYBOARD;
                }
            }
        }

        // Process the navigation init request (select the first/default item)
        context.navJustMovedToID = 0;
        context.navJustMovedToFocusScopeID = context.navJustMovedFromFocusScopeID = 0;
        if (context.navInitResult.id != 0) {
            navInitRequestApplyResult();
        }
        context.navInitRequest = false;
        context.navInitRequestFromMove = false;
        context.navInitResult.id = 0;

        // Process the navigation move request
        if (context.navMoveSubmitted) {
            navMoveRequestApplyResult();
        }
        context.navTabbingCounter = 0;
        context.navMoveSubmitted = context.navMoveScoringItems = false;
        if (context.navCursorHideFrames > 0 && --context.navCursorHideFrames == 0) {
            context.navCursorVisible = true;
        }

        // Schedule a mouse position update (done at the bottom of this function, after
        // processing all move requests and updating scrolling)
        boolean setMousePos =
                context.navMousePositionDirty
                        && context.navIDAlive
                        && context.navCursorVisible
                        && context.navHighlightItemUnderNav
                        && context.navFocusedWindow != null;
        context.navMousePositionDirty = false;

        // Store our return window (for returning from the menu layer to the main layer) and clear
        // it as soon as we step back in our own main layer
        if (context.navFocusedWindow != null) {
            navSaveLastChildNavWindowIntoParent(context.navFocusedWindow);
        }
        if (context.navFocusedWindow != null
                && context.navFocusedWindow.navLastChildNavWindow != null
                && context.navLayer == NAV_LAYER_MAIN) {
            context.navFocusedWindow.navLastChildNavWindow = null;
        }

        // Update Ctrl+Tab and windowing features
        navUpdateWindowing();

        // Set output flags for the user application
        io.navActive = (navKeyboardActive || navGamepadActive) && navWindowAcceptsInputs();
        io.navVisible =
                (io.navActive && context.navID != 0 && context.navCursorVisible)
                        || context.navWindowingTarget != null;

        // Process the cancel input (to close a popup, get back to the parent, clear focus)
        navUpdateCancelRequest();
        navUpdateContextMenuRequest();

        // Process a manual activation request
        context.navActivateID = context.navActivateDownID = context.navActivatePressedID = 0;
        context.navActivateFlags = ActivateFlags.NONE;
        if (context.navID != 0
                && context.navCursorVisible
                && context.navWindowingTarget == null
                && navWindowAcceptsInputs()) {
            final boolean activateDown =
                    (navKeyboardActive && IkGuiImplKeys.isKeyDown(Key.SPACE, KEY_OWNER_NO_OWNER))
                            || (navGamepadActive
                                    && IkGuiImplKeys.isKeyDown(
                                            navGamepadActivateKey(), KEY_OWNER_NO_OWNER));
            final boolean activatePressed =
                    activateDown
                            && ((navKeyboardActive
                                            && IkGuiImplKeys.isKeyPressed(
                                                    Key.SPACE, 0, KEY_OWNER_NO_OWNER))
                                    || (navGamepadActive
                                            && IkGuiImplKeys.isKeyPressed(
                                                    navGamepadActivateKey(),
                                                    0,
                                                    KEY_OWNER_NO_OWNER)));
            final boolean inputPressedKeyboard =
                    navKeyboardActive
                            && (IkGuiImplKeys.isKeyPressed(Key.ENTER, 0, KEY_OWNER_NO_OWNER)
                                    || IkGuiImplKeys.isKeyPressed(
                                            Key.NUMPAD_ENTER, 0, KEY_OWNER_NO_OWNER));
            final boolean inputPressedGamepad =
                    navGamepadActive
                            && (context.navIDItemFlags & ItemFlags.INTERNAL_INPUTABLE) != 0
                            && IkGuiImplKeys.isKeyPressed(NAV_GAMEPAD_INPUT, 0, KEY_OWNER_NO_OWNER);
            final boolean inputPressed = inputPressedKeyboard || inputPressedGamepad;
            final boolean activeIsNav = context.activeID == 0 || context.activeID == context.navID;

            if (context.activeID == 0 && activatePressed) {
                context.navActivateID = context.navID;
                context.navActivateFlags = ActivateFlags.PREFER_TWEAK;
            }
            if (activeIsNav && inputPressed) {
                context.navActivateID = context.navID;
                context.navActivateFlags = ActivateFlags.PREFER_INPUT;
            }
            if (activeIsNav && (activateDown || inputPressed)) {
                context.navActivateDownID = context.navID;
            }
            if (activeIsNav && (activatePressed || inputPressed)) {
                context.navActivatePressedID = context.navID;
                navHighlightActivated(context.navID);
            }
        }
        if (context.navFocusedWindow != null
                && (context.navFocusedWindow.flags & WindowFlags.NO_NAV_INPUTS) != 0) {
            context.navCursorVisible = false;
        } else if (io.configNavCursorVisibleAlways && context.navCursorHideFrames == 0) {
            context.navCursorVisible = true;
        }

        // Highlight
        if (context.navHighlightActivatedTimer > 0) {
            context.navHighlightActivatedTimer =
                    Math.max(0, context.navHighlightActivatedTimer - io.deltaTime);
        }
        if (context.navHighlightActivatedTimer == 0) {
            context.navHighlightActivatedID = 0;
        }

        // Process a programmatic activation request
        if (context.navNextActivateID != 0) {
            context.navActivateID =
                    context.navActivateDownID =
                            context.navActivatePressedID = context.navNextActivateID;
            context.navActivateFlags = context.navNextActivateFlags;
        }
        context.navNextActivateID = 0;

        // Process move requests
        navUpdateCreateMoveRequest();
        if (context.navMoveDirection == Direction.NONE) {
            navUpdateCreateTabbingRequest();
        }
        navUpdateAnyRequestFlag();
        context.navIDAlive = false;

        // Scrolling
        if (navWindowAcceptsInputs() && context.navWindowingTarget == null) {
            // Fallback manual scroll with directional keys when the window has no navigable item
            final Window window = context.navFocusedWindow;
            final float scrollSpeed =
                    Math.round(IkGuiInternal.getFontSize() * 100 * io.deltaTime / 1000.0f);
            final Direction moveDirection = context.navMoveDirection;
            if (window.navLayersActiveMask == 0
                    && window.navWindowHasScrollY
                    && moveDirection != Direction.NONE) {
                if (moveDirection == Direction.LEFT || moveDirection == Direction.RIGHT) {
                    IkGuiInternal.setScrollX(
                            window,
                            IkGuiInternal.truncate(
                                    window.scrollPosition.x
                                            + (moveDirection == Direction.LEFT ? -1.0f : 1.0f)
                                                    * scrollSpeed));
                }
                if (moveDirection == Direction.UP || moveDirection == Direction.DOWN) {
                    IkGuiInternal.setScrollY(
                            window,
                            IkGuiInternal.truncate(
                                    window.scrollPosition.y
                                            + (moveDirection == Direction.UP ? -1.0f : 1.0f)
                                                    * scrollSpeed));
                }
            }
            // TODO(ches) scrolling with the gamepad left stick once analog values are tracked
        }

        // Always prioritize mouse highlight if navigation is disabled
        if (!navKeyboardActive && !navGamepadActive) {
            context.navCursorVisible = false;
            context.navHighlightItemUnderNav = setMousePos = false;
        }

        // Update the mouse position if requested
        if (setMousePos
                && io.configNavMoveSetMousePosition
                && (io.backendFlags & BackendFlags.HAS_SET_MOUSE_POS) != 0) {
            final Vector2f position = new Vector2f();
            navCalcPreferredRefPos(true, position);
            teleportMousePos(position.x, position.y);
        }

        context.navScoringDebugCount = 0;
    }

    /**
     * Move the mouse, and ask the backend to move the OS cursor.
     *
     * @param x The new x position.
     * @param y The new y position.
     */
    private static void teleportMousePos(float x, float y) {
        IkGuiImplDebugTools.debugLog(DebugLogFlags.EVENT_IO, "TeleportMousePos: (%.1f,%.1f)", x, y);
        context.io.mousePosition.set(x, y);
        context.io.mousePositionPrevious.set(x, y);
        context.io.wantSetMousePosition = true;
    }

    /** Apply the result of the previous frame's navigation init request. */
    private static void navInitRequestApplyResult() {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_NAV,
                "[nav] NavInitRequest: ApplyResult: NavID 0x%08X in Layer %d Window \"%s\"",
                context.navInitResult.id,
                context.navLayer,
                IkGuiImplDebugTools.nameOf(context.navFocusedWindow));
        // In very rare cases the navigation window may be null (e.g. clearing focus after
        // requesting an init request)
        if (context.navFocusedWindow == null) {
            return;
        }

        final NavItemData result = context.navInitResult;
        if (context.navID != result.id) {
            context.navJustMovedFromFocusScopeID = context.navFocusScopeID;
            context.navJustMovedToID = result.id;
            context.navJustMovedToFocusScopeID = result.focusScopeID;
            context.navJustMovedToKeyMods = 0;
            context.navJustMovedToIsTabbing = false;
            context.navJustMovedToIsInit = true;
            context.navJustMovedToHasSelectionData =
                    (result.itemFlags & ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA) != 0;
        }

        // Apply the result (will typically select the first item, unless
        // setItemDefaultFocus() has been called)
        setNavID(result.id, context.navLayer, result.focusScopeID, result.rectRelative);
        // Mark as alive from the previous frame as we got a result
        context.navIDAlive = true;
        if (result.selectionUserData != -1) {
            context.navLastValidSelectionUserData = result.selectionUserData;
        }
        if (context.navInitRequestFromMove) {
            setNavCursorVisibleAfterMove();
        }
    }

    /**
     * Bias the scoring rectangle ahead of scoring, and update the preferred position (if missing)
     * using the source position.
     *
     * @param rect The scoring rectangle.
     * @param preferredPosRel The preferred position relative to the window.
     * @param moveDirection The move direction.
     * @param moveFlags The move flags.
     */
    private static void navBiasScoringRect(
            @NonNull RectFloat rect,
            @NonNull Vector2f preferredPosRel,
            @NonNull Direction moveDirection,
            int moveFlags) {
        final Vector2f relToAbsOffset = context.navFocusedWindow.cursorStartPosition;

        // Initialize the bias on departure if we don't have any. So a mouse click + arrow will
        // record the bias. We default to left/up bias, so moving down from a large item into
        // several columns will land on the leftmost column.
        if ((moveFlags & NavMoveFlags.FORWARDED) == 0) {
            if (preferredPosRel.x == Float.MAX_VALUE) {
                preferredPosRel.x =
                        Math.min(rect.getLeft() + 1.0f, rect.getRight()) - relToAbsOffset.x;
            }
            if (preferredPosRel.y == Float.MAX_VALUE) {
                preferredPosRel.y = (rect.getTop() + rect.getBottom()) * 0.5f - relToAbsOffset.y;
            }
        }

        // Apply the general bias on the other axis
        if ((moveDirection == Direction.UP || moveDirection == Direction.DOWN)
                && preferredPosRel.x != Float.MAX_VALUE) {
            final float x = preferredPosRel.x + relToAbsOffset.x;
            rect.setLeft(x);
            rect.setRight(x);
        } else if ((moveDirection == Direction.LEFT || moveDirection == Direction.RIGHT)
                && preferredPosRel.y != Float.MAX_VALUE) {
            final float y = preferredPosRel.y + relToAbsOffset.y;
            rect.setTop(y);
            rect.setBottom(y);
        }
    }

    /** Create a move request from the directional inputs. */
    private static void navUpdateCreateMoveRequest() {
        final Window window = context.navFocusedWindow;
        final boolean navGamepadActive = isNavGamepadActive();
        final boolean navKeyboardActive = isNavKeyboardActive();

        if (context.navMoveForwardToNextFrame && window != null) {
            // Forwarding the previous request (which has been modified, e.g. wrap around menus
            // rewrite the requests with a starting rectangle at the other side of the window).
            // Preserve most state, which was already set by navMoveRequestForward().
        } else {
            // Initiate a directional inputs request
            context.navMoveDirection = Direction.NONE;
            context.navMoveFlags = NavMoveFlags.NONE;
            context.navMoveScrollFlags = ScrollFlags.NONE;
            if (window != null
                    && context.navWindowingTarget == null
                    && (window.flags & WindowFlags.NO_NAV_INPUTS) == 0) {
                final int repeatMode = InputFlags.REPEAT | InputFlags.INTERNAL_REPEAT_RATE_NAV_MOVE;
                if (navDirectionPressed(
                        Direction.LEFT,
                        Key.GAMEPAD_DPAD_LEFT,
                        Key.ARROW_LEFT,
                        repeatMode,
                        navGamepadActive,
                        navKeyboardActive)) {
                    context.navMoveDirection = Direction.LEFT;
                }
                if (navDirectionPressed(
                        Direction.RIGHT,
                        Key.GAMEPAD_DPAD_RIGHT,
                        Key.ARROW_RIGHT,
                        repeatMode,
                        navGamepadActive,
                        navKeyboardActive)) {
                    context.navMoveDirection = Direction.RIGHT;
                }
                if (navDirectionPressed(
                        Direction.UP,
                        Key.GAMEPAD_DPAD_UP,
                        Key.ARROW_UP,
                        repeatMode,
                        navGamepadActive,
                        navKeyboardActive)) {
                    context.navMoveDirection = Direction.UP;
                }
                if (navDirectionPressed(
                        Direction.DOWN,
                        Key.GAMEPAD_DPAD_DOWN,
                        Key.ARROW_DOWN,
                        repeatMode,
                        navGamepadActive,
                        navKeyboardActive)) {
                    context.navMoveDirection = Direction.DOWN;
                }
            }
            context.navMoveClipDirection = context.navMoveDirection;
            context.navScoringNoClipRect.set(
                    Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        }

        // Update page up/page down/home/end scroll
        float scoringPageOffsetY = 0.0f;
        if (window != null && context.navMoveDirection == Direction.NONE && navKeyboardActive) {
            scoringPageOffsetY = navUpdatePageUpPageDown();
        }

        // Submit
        context.navMoveForwardToNextFrame = false;
        if (context.navMoveDirection != Direction.NONE) {
            navMoveRequestSubmit(
                    context.navMoveDirection,
                    context.navMoveClipDirection,
                    context.navMoveFlags,
                    context.navMoveScrollFlags);
        }

        // Moving with no reference triggers an init request (which will be used as a fallback if
        // the direction fails to find a match)
        if (context.navMoveSubmitted && context.navID == 0) {
            context.navInitRequest = context.navInitRequestFromMove = true;
            context.navInitResult.id = 0;
            if (context.io.configNavCursorVisibleAuto) {
                context.navCursorVisible = true;
            }
        }

        // When using a gamepad, we project the reference nav bounding box into the visible area of
        // the window. This allows resuming navigation inside the visible area after scrolling a
        // lot, since with a gamepad all movements are relative (we can't focus a visible item like
        // with the mouse).
        if (context.navMoveSubmitted
                && context.navInputSource == GuiInputSource.GAMEPAD
                && context.navLayer == NAV_LAYER_MAIN
                && window != null) {
            final boolean clampX =
                    (context.navMoveFlags & (NavMoveFlags.LOOP_X | NavMoveFlags.WRAP_X)) == 0;
            final boolean clampY =
                    (context.navMoveFlags & (NavMoveFlags.LOOP_Y | NavMoveFlags.WRAP_Y)) == 0;
            final RectFloat innerRectRel =
                    windowRectAbsToRel(
                            window,
                            new RectFloat(
                                    window.rectInner.getLeft() - 1,
                                    window.rectInner.getTop() - 1,
                                    window.rectInner.getRight() + 1,
                                    window.rectInner.getBottom() + 1),
                            new RectFloat());

            // Take account of a changing scroll, to handle triggering a new move request on a
            // scrolling frame. Otherwise the inner rect would be off on the move result frame.
            final Vector2f nextScroll = new Vector2f();
            IkGuiImplWindows.calcNextScrollFromScrollTargetAndClamp(window, nextScroll);
            innerRectRel.translate(
                    nextScroll.x - window.scrollPosition.x, nextScroll.y - window.scrollPosition.y);

            final RectFloat navRectRel = window.navRectRelative[context.navLayer];
            if ((clampX || clampY) && !innerRectRel.contains(navRectRel)) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_NAV,
                        "[nav] NavMoveRequest: clamp NavRectRel for gamepad move");
                final float fontSize = IkGuiInternal.getFontSize();
                final float padX = Math.min(innerRectRel.getWidth(), fontSize * 0.5f);
                // A rough approximation of starting navigation from the first fully visible item
                final float padY = Math.min(innerRectRel.getHeight(), fontSize * 0.5f);
                innerRectRel.set(
                        clampX ? innerRectRel.getLeft() + padX : -Float.MAX_VALUE,
                        clampY ? innerRectRel.getTop() + padY : -Float.MAX_VALUE,
                        clampX ? innerRectRel.getRight() - padX : Float.MAX_VALUE,
                        clampY ? innerRectRel.getBottom() - padY : Float.MAX_VALUE);
                navRectRel.clipWithFull(innerRectRel);
                context.navID = 0;
            }
        }

        // Prepare the scoring rectangle. For scoring we use a single segment on the left side of
        // the current item bounding box.
        final RectFloat scoringRect = context.navScoringRect;
        if (window != null) {
            final RectFloat navRectRel = window.navRectRelative[context.navLayer];
            final boolean inverted =
                    navRectRel.getLeft() > navRectRel.getRight()
                            || navRectRel.getTop() > navRectRel.getBottom();
            windowRectRelToAbs(
                    window, inverted ? new RectFloat(0, 0, 0, 0) : navRectRel, scoringRect);

            if ((context.navMoveFlags & NavMoveFlags.IS_PAGE_MOVE) != 0) {
                // When we start from a visible location, score visible items and prioritize the
                // result
                if (window.rectInner.contains(scoringRect)) {
                    context.navMoveFlags |= NavMoveFlags.ALSO_SCORE_VISIBLE_SET;
                }
                context.navScoringNoClipRect.set(scoringRect);
                scoringRect.translate(0, scoringPageOffsetY);
                addRect(context.navScoringNoClipRect, scoringRect);
            }

            if (context.navMoveSubmitted) {
                navBiasScoringRect(
                        scoringRect,
                        window.rootWindowForNavigation
                                .navPreferredScoringPositionRelative[context.navLayer],
                        context.navMoveDirection,
                        context.navMoveFlags);
            }
        } else {
            scoringRect.set(0, 0, 0, 0);
        }
    }

    /**
     * Expand a rectangle to contain another.
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

    /**
     * Check if a navigation direction was pressed.
     *
     * @param direction The direction.
     * @param gamepadKey The gamepad key for the direction.
     * @param keyboardKey The keyboard key for the direction.
     * @param repeatMode The repeat flags.
     * @param navGamepadActive Whether gamepad navigation is active.
     * @param navKeyboardActive Whether keyboard navigation is active.
     * @return True if the direction was pressed.
     */
    private static boolean navDirectionPressed(
            @NonNull Direction direction,
            @NonNull Key gamepadKey,
            @NonNull Key keyboardKey,
            int repeatMode,
            boolean navGamepadActive,
            boolean navKeyboardActive) {
        if (IkGuiImplKeys.isActiveIDUsingNavDir(direction)) {
            return false;
        }
        return (navGamepadActive
                        && IkGuiImplKeys.isKeyPressed(gamepadKey, repeatMode, KEY_OWNER_NO_OWNER))
                || (navKeyboardActive
                        && IkGuiImplKeys.isKeyPressed(keyboardKey, repeatMode, KEY_OWNER_NO_OWNER));
    }

    /** Create a tabbing request from Tab/Shift+Tab. */
    private static void navUpdateCreateTabbingRequest() {
        final Window window = context.navFocusedWindow;
        if (window == null
                || context.navWindowingTarget != null
                || (window.flags & WindowFlags.NO_NAV_INPUTS) != 0
                || !context.configNavEnableTabbing) {
            return;
        }

        final boolean tabPressed =
                IkGuiImplKeys.isKeyPressed(Key.TAB, InputFlags.REPEAT, KEY_OWNER_NO_OWNER)
                        && !context.io.keyCtrl
                        && !context.io.keyAlt;
        if (!tabPressed) {
            return;
        }

        // Initiate a tabbing request (this is always enabled, regardless of keyboard navigation)
        if (isNavKeyboardActive()) {
            context.navTabbingDirection =
                    context.io.keyShift
                            ? -1
                            : (!context.navCursorVisible && context.activeID == 0) ? 0 : 1;
        } else {
            context.navTabbingDirection = context.io.keyShift ? -1 : context.activeID == 0 ? 0 : 1;
        }
        final int moveFlags = NavMoveFlags.IS_TABBING | NavMoveFlags.ACTIVATE;
        final int scrollFlags =
                window.appearing
                        ? ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y
                        : ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.KEEP_VISIBLE_EDGE_Y;
        final Direction clipDirection =
                context.navTabbingDirection < 0 ? Direction.UP : Direction.DOWN;
        navMoveRequestSubmit(Direction.NONE, clipDirection, moveFlags, scrollFlags);
        context.navTabbingCounter = -1;
    }

    /** Apply the result of the previous frame's navigation move request. */
    private static void navMoveRequestApplyResult() {
        // Select which result to use
        NavItemData result =
                context.navMoveResultLocal.id != 0
                        ? context.navMoveResultLocal
                        : context.navMoveResultOther.id != 0 ? context.navMoveResultOther : null;

        // Tabbing forward wrap
        if ((context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0
                && result == null
                && (context.navTabbingCounter == 1 || context.navTabbingDirection == 0)
                && context.navTabbingResultFirst.id != 0) {
            result = context.navTabbingResultFirst;
        }

        // When there are no results but the nav ID is set, re-enable the navigation highlight
        final Axis axis =
                context.navMoveDirection == Direction.UP
                                || context.navMoveDirection == Direction.DOWN
                        ? Axis.Y
                        : Axis.X;
        if (result == null) {
            if ((context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0) {
                context.navMoveFlags |= NavMoveFlags.NO_SET_NAV_CURSOR_VISIBLE;
            }
            if (context.navID != 0
                    && (context.navMoveFlags & NavMoveFlags.NO_SET_NAV_CURSOR_VISIBLE) == 0) {
                setNavCursorVisibleAfterMove();
            }
            // On a failed move, clear the preferred position for this axis
            if (context.navFocusedWindow != null) {
                navClearPreferredPosForAxis(axis);
            }
            return;
        }

        // Page up/down first jumps to the bottom/top mostly visible item, otherwise use the
        // result from the previous/next page
        if ((context.navMoveFlags & NavMoveFlags.ALSO_SCORE_VISIBLE_SET) != 0
                && context.navMoveResultLocalVisible.id != 0
                && context.navMoveResultLocalVisible.id != context.navID) {
            result = context.navMoveResultLocalVisible;
        }

        // Maybe entering a flattened child from the outside? In this case solve the tie using the
        // regular scoring rules.
        final NavItemData other = context.navMoveResultOther;
        if (result != other
                && other.id != 0
                && other.window.parentWindow == context.navFocusedWindow
                && (other.distanceBox < result.distanceBox
                        || (other.distanceBox == result.distanceBox
                                && other.distanceCenter < result.distanceCenter))) {
            result = other;
        }
        if (context.navFocusedWindow == null || result.window == null) {
            return;
        }

        // Scroll to keep the newly navigated item fully in view
        if (context.navLayer == NAV_LAYER_MAIN) {
            final RectFloat rectAbs =
                    windowRectRelToAbs(
                            result.window, result.rectRelative, new RectFloat(0, 0, 0, 0));
            scrollToRectEx(result.window, rectAbs, context.navMoveScrollFlags, new Vector2f());

            if ((context.navMoveFlags & NavMoveFlags.SCROLL_TO_EDGE_Y) != 0) {
                final float scrollTarget =
                        context.navMoveDirection == Direction.UP ? result.window.scrollMax.y : 0.0f;
                IkGuiInternal.setScrollY(result.window, scrollTarget);
            }
        }

        if (context.navFocusedWindow != result.window) {
            context.navFocusedWindow = result.window;
            context.navLastValidSelectionUserData = -1;
        }

        // Clear the active ID unless requested not to
        if (context.activeID != result.id
                && (context.navMoveFlags & NavMoveFlags.NO_CLEAR_ACTIVE_ID) == 0) {
            IkGuiInternal.clearActiveID();
        }

        // Don't set the just moved to ID if we just landed on the same spot (which may happen
        // with ALLOW_CURRENT_NAV_ID). Page up/down always sets it, mimicking Windows behavior.
        if ((context.navID != result.id || (context.navMoveFlags & NavMoveFlags.IS_PAGE_MOVE) != 0)
                && (context.navMoveFlags & NavMoveFlags.NO_SELECT) == 0) {
            context.navJustMovedFromFocusScopeID = context.navFocusScopeID;
            context.navJustMovedToID = result.id;
            context.navJustMovedToFocusScopeID = result.focusScopeID;
            context.navJustMovedToKeyMods = context.navMoveKeyMods;
            context.navJustMovedToIsTabbing = (context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0;
            context.navJustMovedToIsInit = false;
            context.navJustMovedToHasSelectionData =
                    (result.itemFlags & ItemFlags.INTERNAL_HAS_SELECTION_USER_DATA) != 0;
        }

        // Apply the new nav ID/focus
        final Vector2f preferredScoringPosRel =
                new Vector2f(
                        context.navFocusedWindow
                                .rootWindowForNavigation
                                .navPreferredScoringPositionRelative[context.navLayer]);
        setNavID(result.id, context.navLayer, result.focusScopeID, result.rectRelative);
        if (result.selectionUserData != -1) {
            context.navLastValidSelectionUserData = result.selectionUserData;
        }

        // Restore the last preferred position for the current axis
        if ((context.navMoveFlags & NavMoveFlags.IS_TABBING) == 0) {
            final RectFloat r = result.rectRelative;
            if (axis == Axis.X) {
                preferredScoringPosRel.x = (r.getLeft() + r.getRight()) * 0.5f;
            } else {
                preferredScoringPosRel.y = (r.getTop() + r.getBottom()) * 0.5f;
            }
            context.navFocusedWindow.rootWindowForNavigation
                    .navPreferredScoringPositionRelative[context.navLayer].set(
                    preferredScoringPosRel);
        }

        // Tabbing activates inputable items, otherwise it only focuses
        if ((context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0
                && (result.itemFlags & ItemFlags.INTERNAL_INPUTABLE) == 0) {
            context.navMoveFlags &= ~NavMoveFlags.ACTIVATE;
        }

        // Activate
        if ((context.navMoveFlags & NavMoveFlags.ACTIVATE) != 0) {
            context.navNextActivateID = result.id;
            context.navNextActivateFlags = ActivateFlags.NONE;
            if ((context.navMoveFlags & NavMoveFlags.FOCUS_API) != 0) {
                context.navNextActivateFlags |= ActivateFlags.FROM_FOCUS_API;
            }
            if ((context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0) {
                context.navNextActivateFlags |=
                        ActivateFlags.PREFER_INPUT
                                | ActivateFlags.TRY_TO_PRESERVE_STATE
                                | ActivateFlags.FROM_TABBING;
            }
        }

        // Make the navigation cursor visible
        if ((context.navMoveFlags & NavMoveFlags.NO_SET_NAV_CURSOR_VISIBLE) == 0) {
            setNavCursorVisibleAfterMove();
        }
    }

    /** Process Escape (or the gamepad cancel button) to close a popup, leave a child, etc. */
    private static void navUpdateCancelRequest() {
        if (!(isNavKeyboardActive()
                        && IkGuiImplKeys.isKeyPressed(Key.ESCAPE, 0, KEY_OWNER_NO_OWNER))
                && !(isNavGamepadActive()
                        && IkGuiImplKeys.isKeyPressed(
                                navGamepadCancelKey(), 0, KEY_OWNER_NO_OWNER))) {
            return;
        }
        IkGuiImplDebugTools.debugLog(DebugLogFlags.EVENT_NAV, "[nav] NavUpdateCancelRequest()");

        final Window navWindow = context.navFocusedWindow;
        if (context.activeID != 0) {
            IkGuiInternal.clearActiveID();
        } else if (context.navLayer != NAV_LAYER_MAIN) {
            // Leave the menu layer
            navRestoreLayer(NAV_LAYER_MAIN);
            setNavCursorVisibleAfterMove();
        } else if (navWindow != null
                && navWindow != navWindow.rootWindow
                && (navWindow.rootWindowForNavigation.flags & WindowFlags.INTERNAL_POPUP) == 0
                && navWindow.rootWindowForNavigation.parentWindow != null) {
            // Exit the child window
            final Window childWindow = navWindow.rootWindowForNavigation;
            final Window parentWindow = childWindow.parentWindow;
            IkGuiInternal.focusWindow(parentWindow, WindowFocusRequestFlags.NONE);
            final RectFloat childRect =
                    new RectFloat(
                            childWindow.position.x,
                            childWindow.position.y,
                            childWindow.position.x + childWindow.size.x,
                            childWindow.position.y + childWindow.size.y);
            setNavID(
                    childWindow.childID,
                    NAV_LAYER_MAIN,
                    0,
                    windowRectAbsToRel(parentWindow, childRect, new RectFloat(0, 0, 0, 0)));
            setNavCursorVisibleAfterMove();
        } else if (!context.openPopupStack.isEmpty()
                && context.openPopupStack.getLast().window != null
                && (context.openPopupStack.getLast().window.flags & WindowFlags.INTERNAL_MODAL)
                        == 0) {
            // Close the open popup/menu
            IkGuiImplPopups.closePopupToLevel(context.openPopupStack.size() - 1, true);
        } else {
            // Clear the last nav ID for popups, but keep it for regular child windows so we can
            // leave one and come back where we were
            final IkIO io = context.io;
            if ((io.configNavEscapeClearFocusItem || io.configNavEscapeClearFocusWindow)
                    && navWindow != null
                    && (navWindow.flags & WindowFlags.INTERNAL_POPUP) != 0) {
                navWindow.navLastIDs[0] = 0;
            }

            // Clear the nav focus
            if (io.configNavEscapeClearFocusItem || io.configNavEscapeClearFocusWindow) {
                context.navID = 0;
            }
            if (io.configNavEscapeClearFocusWindow) {
                IkGuiInternal.focusWindow(null, WindowFocusRequestFlags.NONE);
            }
        }
    }

    /** Open a context menu for the navigation item, with the Menu key or Shift+F10. */
    private static void navUpdateContextMenuRequest() {
        context.navOpenContextMenuItemID = context.navOpenContextMenuWindowID = 0;
        final boolean navKeyboardActive = isNavKeyboardActive();
        final boolean navGamepadActive = isNavGamepadActive();
        if ((!navKeyboardActive && !navGamepadActive) || context.navFocusedWindow == null) {
            return;
        }

        boolean request = false;
        request |=
                navKeyboardActive
                        && (IkGuiImplKeys.isKeyReleased(Key.MENU, KEY_OWNER_NO_OWNER)
                                || (IkGuiImplKeys.isKeyPressed(Key.F10, 0, KEY_OWNER_NO_OWNER)
                                        && context.io.keyMods == KeyModFlags.SHIFT));
        if (!request) {
            return;
        }
        final Window navWindow = context.navFocusedWindow;
        context.navOpenContextMenuItemID = context.navID;
        context.navOpenContextMenuWindowID = navWindow.id;

        // Allow triggering for begin()..beginPopupContextItem()
        if (context.navID == navWindow.getID("#CLOSE")
                || context.navID == navWindow.getID("#COLLAPSE")) {
            context.navOpenContextMenuItemID = navWindow.idMove;
        }

        context.navInputSource = GuiInputSource.KEYBOARD;
        setNavCursorVisibleAfterMove();
    }

    /**
     * Handle the page up/page down/home/end keys.
     *
     * @return The offset to apply to the scoring rectangle.
     */
    private static float navUpdatePageUpPageDown() {
        final Window window = context.navFocusedWindow;
        if ((window.flags & WindowFlags.NO_NAV_INPUTS) != 0 || context.navWindowingTarget != null) {
            return 0.0f;
        }

        final boolean pageUpHeld = IkGuiImplKeys.isKeyDown(Key.PAGE_UP, KEY_OWNER_NO_OWNER);
        final boolean pageDownHeld = IkGuiImplKeys.isKeyDown(Key.PAGE_DOWN, KEY_OWNER_NO_OWNER);
        final boolean homePressed =
                IkGuiImplKeys.isKeyPressed(Key.HOME, InputFlags.REPEAT, KEY_OWNER_NO_OWNER);
        final boolean endPressed =
                IkGuiImplKeys.isKeyPressed(Key.END, InputFlags.REPEAT, KEY_OWNER_NO_OWNER);
        // Proceed if either (not both) are pressed
        if (pageUpHeld == pageDownHeld && homePressed == endPressed) {
            return 0.0f;
        }

        if (context.navLayer != NAV_LAYER_MAIN) {
            navRestoreLayer(NAV_LAYER_MAIN);
        }

        final float innerHeight = window.rectInner.getHeight();
        if ((window.navLayersActiveMask & (1 << NAV_LAYER_MAIN)) == 0
                && window.navWindowHasScrollY) {
            // Fallback manual scroll when the window has no navigable item
            if (IkGuiImplKeys.isKeyPressed(Key.PAGE_UP, InputFlags.REPEAT, KEY_OWNER_NO_OWNER)) {
                IkGuiInternal.setScrollY(window, window.scrollPosition.y - innerHeight);
            } else if (IkGuiImplKeys.isKeyPressed(
                    Key.PAGE_DOWN, InputFlags.REPEAT, KEY_OWNER_NO_OWNER)) {
                IkGuiInternal.setScrollY(window, window.scrollPosition.y + innerHeight);
            } else if (homePressed) {
                IkGuiInternal.setScrollY(window, 0.0f);
            } else if (endPressed) {
                IkGuiInternal.setScrollY(window, window.scrollMax.y);
            }
            return 0.0f;
        }

        final RectFloat navRectRel = window.navRectRelative[context.navLayer];
        final float pageOffsetY =
                Math.max(0.0f, innerHeight - IkGuiInternal.getFontSize() + navRectRel.getHeight());
        float navScoringRectOffsetY = 0.0f;
        if (IkGuiImplUtils.isKeyPressed(Key.PAGE_UP, true)) {
            navScoringRectOffsetY = -pageOffsetY;
            // Because our scoring rect is offset up, we request the down direction (so we can
            // always land on the last item)
            context.navMoveDirection = Direction.DOWN;
            context.navMoveClipDirection = Direction.UP;
            context.navMoveFlags = NavMoveFlags.ALLOW_CURRENT_NAV_ID | NavMoveFlags.IS_PAGE_MOVE;
        } else if (IkGuiImplUtils.isKeyPressed(Key.PAGE_DOWN, true)) {
            navScoringRectOffsetY = pageOffsetY;
            context.navMoveDirection = Direction.UP;
            context.navMoveClipDirection = Direction.DOWN;
            context.navMoveFlags = NavMoveFlags.ALLOW_CURRENT_NAV_ID | NavMoveFlags.IS_PAGE_MOVE;
        } else if (homePressed) {
            // Scrolling is handled via SCROLL_TO_EDGE_Y. Preserve the current horizontal position
            // if we have any.
            navRectRel.setTop(0.0f);
            navRectRel.setBottom(0.0f);
            if (navRectRel.getLeft() > navRectRel.getRight()) {
                navRectRel.setLeft(0.0f);
                navRectRel.setRight(0.0f);
            }
            context.navMoveDirection = Direction.DOWN;
            context.navMoveFlags =
                    NavMoveFlags.ALLOW_CURRENT_NAV_ID | NavMoveFlags.SCROLL_TO_EDGE_Y;
        } else if (endPressed) {
            navRectRel.setTop(window.contentSize.y);
            navRectRel.setBottom(window.contentSize.y);
            if (navRectRel.getLeft() > navRectRel.getRight()) {
                navRectRel.setLeft(0.0f);
                navRectRel.setRight(0.0f);
            }
            context.navMoveDirection = Direction.UP;
            context.navMoveFlags =
                    NavMoveFlags.ALLOW_CURRENT_NAV_ID | NavMoveFlags.SCROLL_TO_EDGE_Y;
        }
        return navScoringRectOffsetY;
    }

    /** Navigation work at the end of the frame. */
    static void navEndFrame() {
        // Show the Ctrl+Tab list window
        if (context.navWindowingTarget != null) {
            navUpdateWindowingOverlay();
        }

        // Perform wrap around in menus
        if (context.navFocusedWindow != null
                && navMoveRequestButNoResultYet()
                && (context.navMoveFlags & NavMoveFlags.WRAP_MASK) != 0
                && (context.navMoveFlags & NavMoveFlags.FORWARDED) == 0) {
            navUpdateCreateWrappingRequest();
        }
    }

    /** Create a request to wrap around to the other side of the window. */
    private static void navUpdateCreateWrappingRequest() {
        final Window window = context.navFocusedWindow;

        boolean doForward = false;
        final RectFloat bbRel = new RectFloat(0, 0, 0, 0);
        bbRel.set(window.navRectRelative[context.navLayer]);
        Direction clipDirection = context.navMoveDirection;

        final int moveFlags = context.navMoveFlags;

        // The menu layer does not maintain scrolling/content size
        final float wrapSizeX =
                context.navLayer == NAV_LAYER_MENU
                        ? window.size.x
                        : window.contentSize.x + window.padding.x;
        final float wrapSizeY =
                context.navLayer == NAV_LAYER_MENU
                        ? window.size.y
                        : window.contentSize.y + window.padding.y;

        final boolean wrapOrLoopX = (moveFlags & (NavMoveFlags.WRAP_X | NavMoveFlags.LOOP_X)) != 0;
        final boolean wrapOrLoopY = (moveFlags & (NavMoveFlags.WRAP_Y | NavMoveFlags.LOOP_Y)) != 0;
        if (context.navMoveDirection == Direction.LEFT && wrapOrLoopX) {
            bbRel.setLeft(wrapSizeX);
            bbRel.setRight(wrapSizeX);
            if ((moveFlags & NavMoveFlags.WRAP_X) != 0) {
                // Previous row
                bbRel.translate(0, -bbRel.getHeight());
                clipDirection = Direction.UP;
            }
            doForward = true;
        }
        if (context.navMoveDirection == Direction.RIGHT && wrapOrLoopX) {
            bbRel.setLeft(-window.padding.x);
            bbRel.setRight(-window.padding.x);
            if ((moveFlags & NavMoveFlags.WRAP_X) != 0) {
                // Next row
                bbRel.translate(0, bbRel.getHeight());
                clipDirection = Direction.DOWN;
            }
            doForward = true;
        }
        if (context.navMoveDirection == Direction.UP && wrapOrLoopY) {
            bbRel.setTop(wrapSizeY);
            bbRel.setBottom(wrapSizeY);
            if ((moveFlags & NavMoveFlags.WRAP_Y) != 0) {
                // Previous column
                bbRel.translate(-bbRel.getWidth(), 0);
                clipDirection = Direction.LEFT;
            }
            doForward = true;
        }
        if (context.navMoveDirection == Direction.DOWN && wrapOrLoopY) {
            bbRel.setTop(-window.padding.y);
            bbRel.setBottom(-window.padding.y);
            if ((moveFlags & NavMoveFlags.WRAP_Y) != 0) {
                // Next column
                bbRel.translate(bbRel.getWidth(), 0);
                clipDirection = Direction.RIGHT;
            }
            doForward = true;
        }
        if (!doForward) {
            return;
        }
        window.navRectRelative[context.navLayer].set(bbRel);
        navClearPreferredPosForAxis(Axis.X);
        navClearPreferredPosForAxis(Axis.Y);
        navMoveRequestForward(
                context.navMoveDirection, clipDirection, moveFlags, context.navMoveScrollFlags);
    }

    // ---------------------------------------------------------------------------------------
    // Windowing (Ctrl+Tab, Alt to toggle the menu layer)
    // ---------------------------------------------------------------------------------------

    /**
     * Whether a window can be focused with Ctrl+Tab. Windows with NO_NAV_FOCUS can still be focused
     * with the mouse or programmatically.
     *
     * @param window The window.
     * @return True if the window can be focused with Ctrl+Tab.
     */
    static boolean isWindowNavFocusable(@NonNull Window window) {
        return window.wasActive
                && window == window.rootWindow
                && (window.flags & WindowFlags.NO_NAV_FOCUS) == 0;
    }

    /**
     * Find a window that can be focused with Ctrl+Tab.
     *
     * @param start The focus order index to start at.
     * @param stop The focus order index to stop at.
     * @param direction The direction to search in (1 or -1).
     * @return The window, or null if none was found.
     */
    private static Window findWindowNavFocusable(int start, int stop, int direction) {
        for (int i = start;
                i >= 0 && i < context.windowFocusOrder.size() && i != stop;
                i += direction) {
            if (isWindowNavFocusable(context.windowFocusOrder.get(i))) {
                return context.windowFocusOrder.get(i);
            }
        }
        return null;
    }

    /**
     * Move the Ctrl+Tab target to the next/previous window.
     *
     * @param focusChangeDirection The direction to move in the focus order.
     */
    private static void navUpdateWindowingTarget(int focusChangeDirection) {
        if ((context.navWindowingTarget.flags & WindowFlags.INTERNAL_MODAL) != 0) {
            return;
        }

        final int current = context.windowFocusOrder.indexOf(context.navWindowingTarget);
        Window target =
                findWindowNavFocusable(
                        current + focusChangeDirection, Integer.MIN_VALUE, focusChangeDirection);
        if (target == null) {
            target =
                    findWindowNavFocusable(
                            focusChangeDirection < 0 ? context.windowFocusOrder.size() - 1 : 0,
                            current,
                            focusChangeDirection);
        }
        // Don't reset the target if there's a single window in the list
        if (target != null) {
            context.navWindowingTarget = context.navWindowingTargetAnim = target;
            context.navWindowingAccumulatedDeltaPosition.set(0, 0);
            context.navWindowingAccumulatedDeltaSize.set(0, 0);
        }
        context.navWindowingToggleLayer = false;
    }

    /**
     * Apply focus from Ctrl+Tab and close the overlay.
     *
     * @param applyFocusWindow The window to focus.
     */
    private static void navUpdateWindowingApplyFocus(@NonNull Window applyFocusWindow) {
        if (context.navFocusedWindow == null
                || applyFocusWindow != context.navFocusedWindow.rootWindow) {
            final Viewport previousViewport =
                    context.navFocusedWindow != null ? context.navFocusedWindow.viewport : null;
            IkGuiInternal.clearActiveID();
            setNavCursorVisibleAfterMove();
            IkGuiInternal.closePopupsOverWindow(applyFocusWindow, false);
            IkGuiInternal.focusWindow(
                    applyFocusWindow, WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
            applyFocusWindow = context.navFocusedWindow;
            if (applyFocusWindow != null) {
                if (applyFocusWindow.navLastIDs[0] == 0) {
                    navInitWindow(applyFocusWindow, false);
                }
                // If the window has only a menu layer, select it directly
                if (applyFocusWindow.navLayersActiveMaskNext == (1 << NAV_LAYER_MENU)) {
                    context.navLayer = NAV_LAYER_MENU;
                }

                // Request OS level focus
                if (applyFocusWindow.viewport != previousViewport
                        && context.platformIO.platformSetWindowFocus != null) {
                    context.platformIO.platformSetWindowFocus.accept(applyFocusWindow.viewport);
                }
            }
        }
        context.navWindowingTarget = null;
    }

    /**
     * Window management. Keyboard: Ctrl+Tab to change focus, Alt to toggle the menu layer, Ctrl
     * Tab+arrows to move windows. Gamepad: hold the menu button to change focus with L1/R1, tap it
     * to toggle the menu layer.
     */
    private static void navUpdateWindowing() {
        final IkIO io = context.io;

        Window applyFocusWindow = null;
        boolean applyToggleLayer = false;

        final Window modalWindow = IkGuiInternal.getTopmostPopupModal();
        final boolean allowWindowing = modalWindow == null;
        if (!allowWindowing) {
            context.navWindowingTarget = null;
        }

        // Fade out
        if (context.navWindowingTargetAnim != null && context.navWindowingTarget == null) {
            context.navWindowingHighlightAlpha =
                    Math.max(
                            context.navWindowingHighlightAlpha - io.deltaTime * 10.0f / 1000.0f, 0);
            if (context.dimBackgroundRatio <= 0.0f && context.navWindowingHighlightAlpha <= 0.0f) {
                context.navWindowingTargetAnim = null;
            }
        }

        // Start Ctrl+Tab or gamepad window selection
        final boolean navGamepadActive = isNavGamepadActive();
        final boolean navKeyboardActive = isNavKeyboardActive();
        final int inputFlags = InputFlags.REPEAT | InputFlags.ROUTE_ALWAYS;
        final boolean keyboardNextWindow =
                allowWindowing
                        && context.configNavWindowingKeyNext != 0
                        && IkGuiImplKeys.shortcut(
                                context.configNavWindowingKeyNext,
                                inputFlags,
                                NAV_WINDOWING_OWNER_ID);
        final boolean keyboardPrevWindow =
                allowWindowing
                        && context.configNavWindowingKeyPrevious != 0
                        && IkGuiImplKeys.shortcut(
                                context.configNavWindowingKeyPrevious,
                                inputFlags,
                                NAV_WINDOWING_OWNER_ID);
        final boolean startTogglingWithGamepad =
                navGamepadActive
                        && context.navWindowingTarget == null
                        && IkGuiImplKeys.shortcut(
                                KeyChord.of(NAV_GAMEPAD_MENU),
                                InputFlags.ROUTE_ALWAYS,
                                NAV_WINDOWING_OWNER_ID);
        final boolean startWindowingWithGamepad = allowWindowing && startTogglingWithGamepad;
        // This is enabled even without keyboard navigation
        final boolean startWindowingWithKeyboard =
                allowWindowing
                        && context.navWindowingTarget == null
                        && (keyboardNextWindow || keyboardPrevWindow);
        boolean justStartedWindowingFromNullFocus = false;
        if (startTogglingWithGamepad) {
            // The gamepad starts toggling the layer
            context.navWindowingToggleLayer = true;
            context.navWindowingToggleKey = NAV_GAMEPAD_MENU;
            context.navWindowingInputSource = context.navInputSource = GuiInputSource.GAMEPAD;
        }
        if (startWindowingWithGamepad || startWindowingWithKeyboard) {
            final Window window =
                    context.navFocusedWindow != null
                                    && isWindowNavFocusable(context.navFocusedWindow)
                            ? context.navFocusedWindow
                            : findWindowNavFocusable(
                                    context.windowFocusOrder.size() - 1, Integer.MIN_VALUE, -1);
            if (window != null) {
                if (startWindowingWithKeyboard || context.configNavWindowingWithGamepad) {
                    // Current location
                    context.navWindowingTarget = context.navWindowingTargetAnim = window.rootWindow;
                }
                context.navWindowingTimer = 0;
                context.navWindowingHighlightAlpha = 0.0f;
                context.navWindowingAccumulatedDeltaPosition.set(0, 0);
                context.navWindowingAccumulatedDeltaSize.set(0, 0);
                context.navWindowingInputSource =
                        context.navInputSource =
                                startWindowingWithKeyboard
                                        ? GuiInputSource.KEYBOARD
                                        : GuiInputSource.GAMEPAD;
                if (context.navFocusedWindow == null) {
                    justStartedWindowingFromNullFocus = true;
                }

                // Manually register ownership of our mods
                if (keyboardNextWindow || keyboardPrevWindow) {
                    IkGuiImplKeys.setKeyOwnersForKeyChord(
                            KeyChord.ofMods(
                                    KeyChord.getMods(
                                            context.configNavWindowingKeyNext
                                                    | context.configNavWindowingKeyPrevious)),
                            NAV_WINDOWING_OWNER_ID,
                            0);
                }
            }
        }

        // Gamepad update
        if ((context.navWindowingTarget != null || context.navWindowingToggleLayer)
                && context.navWindowingInputSource == GuiInputSource.GAMEPAD) {
            if (context.navWindowingTarget != null) {
                // The highlight only appears after a brief time holding the button, so a fast
                // tap doesn't add visual noise
                context.navWindowingTimer += io.deltaTime;
                context.navWindowingHighlightAlpha =
                        Math.max(
                                context.navWindowingHighlightAlpha,
                                MathUtil.clamp(
                                        (context.navWindowingTimer - NAV_WINDOWING_HIGHLIGHT_DELAY)
                                                / 50.0f,
                                        0.0f,
                                        1.0f));

                // Select the window to focus
                final int focusChangeDirection =
                        (IkGuiImplUtils.isKeyPressed(Key.GAMEPAD_L1, true) ? 1 : 0)
                                - (IkGuiImplUtils.isKeyPressed(Key.GAMEPAD_R1, true) ? 1 : 0);
                if (focusChangeDirection != 0 && !justStartedWindowingFromNullFocus) {
                    navUpdateWindowingTarget(focusChangeDirection);
                    context.navWindowingHighlightAlpha = 1.0f;
                }
            }

            // A single press toggles the layer, a long press with L/R applies the focus on release
            if (!io.getKeyDown(NAV_GAMEPAD_MENU)) {
                // Once the button was held long enough we don't consider it a tap anymore
                context.navWindowingToggleLayer &= context.navWindowingHighlightAlpha < 1.0f;
                if (context.navWindowingToggleLayer && context.navFocusedWindow != null) {
                    applyToggleLayer = true;
                } else if (!context.navWindowingToggleLayer) {
                    applyFocusWindow = context.navWindowingTarget;
                }
                context.navWindowingTarget = null;
                context.navWindowingToggleLayer = false;
            }
        }

        // Keyboard: focus
        if (context.navWindowingTarget != null
                && context.navWindowingInputSource == GuiInputSource.KEYBOARD) {
            // Visuals only appear after a brief time after pressing Tab the first time, so a fast
            // Ctrl+Tab doesn't add visual noise
            final int nextMods =
                    context.configNavWindowingKeyNext != 0
                            ? KeyChord.getMods(context.configNavWindowingKeyNext)
                            : KeyModFlags.MASK;
            final int prevMods =
                    context.configNavWindowingKeyPrevious != 0
                            ? KeyChord.getMods(context.configNavWindowingKeyPrevious)
                            : KeyModFlags.MASK;
            final int sharedMods = nextMods & prevMods;
            context.navWindowingTimer += io.deltaTime;
            context.navWindowingHighlightAlpha =
                    Math.max(
                            context.navWindowingHighlightAlpha,
                            MathUtil.clamp(
                                    (context.navWindowingTimer - NAV_WINDOWING_HIGHLIGHT_DELAY)
                                            / 50.0f,
                                    0.0f,
                                    1.0f));
            if ((keyboardNextWindow || keyboardPrevWindow) && !justStartedWindowingFromNullFocus) {
                navUpdateWindowingTarget(keyboardNextWindow ? -1 : 1);
            } else if ((io.keyMods & sharedMods) != sharedMods) {
                applyFocusWindow = context.navWindowingTarget;
            }
        }

        // Keyboard: press and release Alt to toggle the menu layer
        boolean windowingToggleLayerStart = false;
        if (navWindowAcceptsInputs() && navKeyboardActive) {
            for (Key toggleKey : new Key[] {Key.LEFT_ALT, Key.RIGHT_ALT}) {
                if (IkGuiImplKeys.isKeyPressed(toggleKey, 0, KEY_OWNER_NO_OWNER)) {
                    windowingToggleLayerStart = true;
                    context.navWindowingToggleLayer = true;
                    context.navWindowingToggleKey = toggleKey;
                    context.navWindowingInputSource =
                            context.navInputSource = GuiInputSource.KEYBOARD;
                    break;
                }
            }
        }
        if (context.navWindowingToggleLayer
                && context.navWindowingInputSource == GuiInputSource.KEYBOARD) {
            // We cancel toggling the layer when any text has been typed (generally while holding
            // Alt), when other modifiers are pressed, or when an owner has claimed the key
            if (!io.inputQueueCharacters.isEmpty() || io.keyCtrl || io.keyShift || io.keySuper) {
                context.navWindowingToggleLayer = false;
            } else if (!windowingToggleLayerStart
                    && context.lastKeyboardKeyPressFrame == context.frameCount) {
                context.navWindowingToggleLayer = false;
            } else if (!IkGuiImplKeys.testKeyOwner(
                            context.navWindowingToggleKey, KEY_OWNER_NO_OWNER)
                    || !IkGuiImplKeys.testKeyOwner(Key.MOD_ALT, KEY_OWNER_NO_OWNER)) {
                context.navWindowingToggleLayer = false;
            }

            // Apply the layer toggle on Alt release
            if (IkGuiImplKeys.isKeyReleased(
                            context.navWindowingToggleKey,
                            com.ikalagaming.graphics.gui.data.KeyRoutingData.KEY_OWNER_ANY)
                    && context.navWindowingToggleLayer
                    && (context.activeID == 0 || context.activeIDAllowOverlap)
                    && IkGuiImplUtils.isMousePosValid(io.mousePosition.x, io.mousePosition.y)
                            == IkGuiImplUtils.isMousePosValid(
                                    io.mousePositionPrevious.x, io.mousePositionPrevious.y)) {
                applyToggleLayer = true;
            }
            if (!io.getKeyDown(context.navWindowingToggleKey)) {
                context.navWindowingToggleLayer = false;
            }
        }

        // Move the window with the arrow keys
        if (context.navWindowingTarget != null
                && (context.navWindowingTarget.flags & WindowFlags.NO_MOVE) == 0
                && context.navInputSource == GuiInputSource.KEYBOARD
                && !io.keyShift) {
            final float moveX =
                    (io.getKeyDown(Key.ARROW_RIGHT) ? 1.0f : 0.0f)
                            - (io.getKeyDown(Key.ARROW_LEFT) ? 1.0f : 0.0f);
            final float moveY =
                    (io.getKeyDown(Key.ARROW_DOWN) ? 1.0f : 0.0f)
                            - (io.getKeyDown(Key.ARROW_UP) ? 1.0f : 0.0f);
            if (moveX != 0.0f || moveY != 0.0f) {
                final float navMoveSpeed = 800.0f;
                final float moveStep = navMoveSpeed * io.deltaTime / 1000.0f;
                final Vector2f accum = context.navWindowingAccumulatedDeltaPosition;
                accum.add(moveX * moveStep, moveY * moveStep);
                context.navHighlightItemUnderNav = true;
                final float flooredX = IkGuiInternal.truncate(accum.x);
                final float flooredY = IkGuiInternal.truncate(accum.y);
                if (flooredX != 0.0f || flooredY != 0.0f) {
                    final Window movingWindow = context.navWindowingTarget.rootWindowDockTree;
                    IkGuiImplWindows.setWindowPos(
                            movingWindow,
                            movingWindow.position.x + flooredX,
                            movingWindow.position.y + flooredY,
                            Condition.ALWAYS);
                    accum.sub(flooredX, flooredY);
                }
            }
        }

        // Apply the final focus
        if (applyFocusWindow != null) {
            navUpdateWindowingApplyFocus(applyFocusWindow);
        }

        // Apply the menu/layer toggle
        if (applyToggleLayer && context.navFocusedWindow != null) {
            IkGuiInternal.clearActiveID();

            // Move to the parent menu if necessary
            Window newNavWindow = context.navFocusedWindow;
            while (newNavWindow.parentWindow != null
                    && (newNavWindow.navLayersActiveMask & (1 << NAV_LAYER_MENU)) == 0
                    && (newNavWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    && (newNavWindow.flags
                                    & (WindowFlags.INTERNAL_POPUP
                                            | WindowFlags.INTERNAL_CHILD_MENU))
                            == 0) {
                newNavWindow = newNavWindow.parentWindow;
            }
            if (newNavWindow != context.navFocusedWindow) {
                final Window oldNavWindow = context.navFocusedWindow;
                IkGuiInternal.focusWindow(newNavWindow, WindowFocusRequestFlags.NONE);
                newNavWindow.navLastChildNavWindow = oldNavWindow;
            }

            // Toggle the layer
            final int newNavLayer =
                    (context.navFocusedWindow.navLayersActiveMask & (1 << NAV_LAYER_MENU)) != 0
                            ? context.navLayer ^ 1
                            : NAV_LAYER_MAIN;
            if (newNavLayer != context.navLayer) {
                // Reinitialize navigation when entering the menu bar with the Alt key
                final boolean preserveLayer1NavID = newNavWindow.dockNodeAsHost != null;
                if (newNavLayer == NAV_LAYER_MENU && !preserveLayer1NavID) {
                    context.navFocusedWindow.navLastIDs[newNavLayer] = 0;
                }
                navRestoreLayer(newNavLayer);
                setNavCursorVisibleAfterMove();
            }
        }
    }

    /** The overlay displayed when using Ctrl+Tab, called from endFrame(). */
    private static void navUpdateWindowingOverlay() {
        if (context.navWindowingTimer < NAV_WINDOWING_LIST_APPEAR_DELAY) {
            return;
        }

        final Viewport viewport = context.mainViewport;
        IkGuiImplWindows.setNextWindowSizeConstraints(
                viewport.size.x * 0.20f, viewport.size.y * 0.20f, Float.MAX_VALUE, Float.MAX_VALUE);
        IkGuiImplWindows.setNextWindowPos(
                viewport.position.x + viewport.size.x * 0.5f,
                viewport.position.y + viewport.size.y * 0.5f,
                Condition.ALWAYS,
                0.5f,
                0.5f);
        final StyleVariables style = context.style.variable;
        IkGuiImplUtils.pushStyleVarFloat2(
                StyleVariable.WINDOW_PADDING, style.windowPadding.x * 2, style.windowPadding.y * 2);
        IkGuiImplWindows.begin(
                "##NavWindowingOverlay",
                null,
                WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_FOCUS_ON_APPEARING
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_MOVE
                        | WindowFlags.NO_INPUTS
                        | WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_SAVED_SETTINGS);
        context.navWindowingListWindow = context.windowCurrent;
        for (int i = context.windowFocusOrder.size() - 1; i >= 0; --i) {
            final Window window = context.windowFocusOrder.get(i);
            if (!isWindowNavFocusable(window)) {
                continue;
            }
            String label = Hash.getDisplayedText(window.name);
            if (label.isEmpty()) {
                if ((window.flags & WindowFlags.INTERNAL_POPUP) != 0) {
                    label = "(Popup)";
                } else if ((window.flags & WindowFlags.MENU_BAR) != 0
                        && "##MainMenuBar".equals(window.name)) {
                    label = "(Main menu bar)";
                } else if (window.dockNodeAsHost != null) {
                    // Not normally shown to the user
                    label = "(Dock node)";
                } else {
                    label = "(Untitled)";
                }
            }
            IkGuiImplMiscWidgets.selectable(
                    label + "##" + window.id,
                    context.navWindowingTarget == window,
                    SelectableFlags.NONE,
                    0,
                    0);
        }
        IkGuiImplWindows.end();
        IkGuiImplUtils.popStyleVar();
    }

    // ---------------------------------------------------------------------------------------
    // Focus API
    // ---------------------------------------------------------------------------------------

    /** Focus the last item: move the navigation cursor to it, scroll to it, focus the window. */
    static void focusItem() {
        if (context.dragDropActive) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_FOCUS, "FocusItem() ignored while DragDropActive!");
        } else if (context.windowCurrent != null) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_FOCUS,
                    "FocusItem(0x%08x) in window \"%s\"",
                    context.lastItemData.id,
                    context.windowCurrent.name);
        }
        final Window window = context.windowCurrent;
        if (context.dragDropActive || context.windowMoving != null) {
            return;
        }

        final int moveFlags =
                NavMoveFlags.IS_TABBING
                        | NavMoveFlags.FOCUS_API
                        | NavMoveFlags.NO_SET_NAV_CURSOR_VISIBLE
                        | NavMoveFlags.NO_SELECT;
        final int scrollFlags =
                window.appearing
                        ? ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y
                        : ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.KEEP_VISIBLE_EDGE_Y;
        setNavWindow(window);
        navMoveRequestSubmit(Direction.NONE, Direction.UP, moveFlags, scrollFlags);
        navMoveRequestResolveWithLastItem(context.navMoveResultLocal);
    }

    /**
     * Activate an item on the next frame, as if it was clicked or activated with navigation.
     *
     * @param id The item ID.
     */
    static void activateItemByID(int id) {
        context.navNextActivateID = id;
        context.navNextActivateFlags = ActivateFlags.NONE;
    }

    /**
     * Focus keyboard on the next widget. Use a positive offset to access sub components of a
     * multiple component widget. Use -1 to access the previous widget.
     *
     * @param offset The offset.
     */
    static void setKeyboardFocusHere(int offset) {
        if (context.dragDropActive) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_FOCUS,
                    "SetKeyboardFocusHere() ignored while DragDropActive!");
        } else if (context.windowCurrent != null) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_FOCUS,
                    "SetKeyboardFocusHere(%d) in window \"%s\"",
                    offset,
                    context.windowCurrent.name);
        }
        final Window window = context.windowCurrent;
        if (offset < -1) {
            IkGuiImplDebugTools.reportError(
                    log, "setKeyboardFocusHere() offset must be at least -1");
            return;
        }

        // It makes sense in the vast majority of cases to never interrupt a drag and drop
        if (context.dragDropActive || context.windowMoving != null) {
            return;
        }

        setNavWindow(window);

        final int moveFlags =
                NavMoveFlags.IS_TABBING
                        | NavMoveFlags.ACTIVATE
                        | NavMoveFlags.FOCUS_API
                        | NavMoveFlags.NO_SET_NAV_CURSOR_VISIBLE;
        final int scrollFlags =
                window.appearing
                        ? ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y
                        : ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.KEEP_VISIBLE_EDGE_Y;
        navMoveRequestSubmit(
                Direction.NONE, offset < 0 ? Direction.UP : Direction.DOWN, moveFlags, scrollFlags);
        if (offset == -1) {
            navMoveRequestResolveWithLastItem(context.navMoveResultLocal);
        } else {
            context.navTabbingDirection = 1;
            context.navTabbingCounter = offset + 1;
        }
    }

    /** Make the last item the default focused item of a newly appearing window. */
    static void setItemDefaultFocus() {
        final Window window = context.windowCurrent;
        if (!window.appearing) {
            return;
        }
        if (context.navFocusedWindow != window.rootWindowForNavigation
                || (!context.navInitRequest && context.navInitResult.id == 0)
                || context.navLayer != window.navLayerCurrent) {
            return;
        }

        context.navInitRequest = false;
        navApplyItemToResult(context.navInitResult);
        navUpdateAnyRequestFlag();

        // Scroll could be done in navInitRequestApplyResult() via an opt-in flag (we however
        // don't want regular init requests to scroll)
        if (!window.rectCurrentClip.contains(context.lastItemData.rect)) {
            scrollToRectEx(window, context.lastItemData.rect, ScrollFlags.NONE, new Vector2f());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Rendering and scrolling
    // ---------------------------------------------------------------------------------------

    /**
     * Render the navigation cursor around an item, if it is the navigation item.
     *
     * @param bb The item bounding box.
     * @param id The item ID.
     * @param flags NavRenderCursorFlags.
     * @param rounding The rounding, negative to use the frame rounding.
     */
    static void renderNavCursor(@NonNull RectFloat bb, int id, int flags, float rounding) {
        if (id != context.navID) {
            return;
        }
        if (!context.navCursorVisible && (flags & NavRenderCursorFlags.ALWAYS_DRAW) == 0) {
            return;
        }
        if (id == context.lastItemData.id
                && (context.lastItemData.itemFlags & ItemFlags.NO_NAV) != 0) {
            return;
        }

        final Window window = context.windowCurrent;
        if (window.navHideHighlightOneFrame) {
            return;
        }

        if (rounding < 0.0f) {
            rounding = context.style.variable.frameRounding;
        }

        final RectFloat displayRect = new RectFloat(0, 0, 0, 0);
        displayRect.set(bb);
        displayRect.clipWith(window.rectCurrentClip);
        final float thickness = 2.0f;
        final int color = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.NAV_CURSOR);
        if ((flags & NavRenderCursorFlags.COMPACT) != 0) {
            window.drawList.addRect(
                    displayRect.getLeft(),
                    displayRect.getTop(),
                    displayRect.getRight(),
                    displayRect.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    thickness);
        } else {
            final float distance = (float) (int) (3.0f + thickness * 0.5f);
            displayRect.expand(distance, distance);
            final boolean fullyVisible = window.rectCurrentClip.contains(displayRect);
            if (!fullyVisible) {
                window.drawList.pushClipRect(
                        displayRect.getLeft(),
                        displayRect.getTop(),
                        displayRect.getRight(),
                        displayRect.getBottom());
            }
            window.drawList.addRect(
                    displayRect.getLeft(),
                    displayRect.getTop(),
                    displayRect.getRight(),
                    displayRect.getBottom(),
                    color,
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    thickness);
            if (!fullyVisible) {
                window.drawList.popClipRect();
            }
        }
    }

    /**
     * Scroll so the last item is visible.
     *
     * @param scrollFlags Scroll flags.
     */
    static void scrollToItem(int scrollFlags) {
        scrollToRectEx(
                context.windowCurrent, context.lastItemData.navRect, scrollFlags, new Vector2f());
    }

    /**
     * Scroll a window (and its parents) to keep a rectangle in view.
     *
     * @param window The window.
     * @param itemRect The rectangle, in screen space.
     * @param scrollFlags Scroll flags.
     * @param output Where to store how much the scroll position will change.
     * @return The output, for convenience.
     */
    static Vector2f scrollToRectEx(
            @NonNull Window window,
            @NonNull RectFloat itemRect,
            int scrollFlags,
            @NonNull Vector2f output) {
        final StyleVariables style = context.style.variable;
        final RectFloat scrollRect =
                new RectFloat(
                        window.rectInner.getLeft() - 1,
                        window.rectInner.getTop() - 1,
                        window.rectInner.getRight() + 1,
                        window.rectInner.getBottom() + 1);
        scrollRect.setLeft(
                Math.min(scrollRect.getLeft() + window.decoInnerSizeX1, scrollRect.getRight()));
        scrollRect.setTop(
                Math.min(scrollRect.getTop() + window.decoInnerSizeY1, scrollRect.getBottom()));

        // Defaults
        int inFlags = scrollFlags;
        if ((scrollFlags & ScrollFlags.MASK_X) == 0 && window.scrollbarX) {
            scrollFlags |= ScrollFlags.KEEP_VISIBLE_EDGE_X;
        }
        if ((scrollFlags & ScrollFlags.MASK_Y) == 0) {
            scrollFlags |=
                    window.appearing
                            ? ScrollFlags.ALWAYS_CENTER_Y
                            : ScrollFlags.KEEP_VISIBLE_EDGE_Y;
        }

        final boolean fullyVisibleX =
                itemRect.getLeft() >= scrollRect.getLeft()
                        && itemRect.getRight() <= scrollRect.getRight();
        final boolean fullyVisibleY =
                itemRect.getTop() >= scrollRect.getTop()
                        && itemRect.getBottom() <= scrollRect.getBottom();
        final boolean alwaysAutoResize = (window.flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0;
        final boolean canBeFullyVisibleX =
                itemRect.getWidth() + style.itemSpacing.x * 2.0f <= scrollRect.getWidth()
                        || window.autoFitFramesX.get() > 0
                        || alwaysAutoResize;
        final boolean canBeFullyVisibleY =
                itemRect.getHeight() + style.itemSpacing.y * 2.0f <= scrollRect.getHeight()
                        || window.autoFitFramesY.get() > 0
                        || alwaysAutoResize;

        if ((scrollFlags & ScrollFlags.KEEP_VISIBLE_EDGE_X) != 0 && !fullyVisibleX) {
            if (itemRect.getLeft() < scrollRect.getLeft() || !canBeFullyVisibleX) {
                IkGuiInternal.setScrollFromPosX(
                        window, itemRect.getLeft() - style.itemSpacing.x - window.position.x, 0.0f);
            } else if (itemRect.getRight() >= scrollRect.getRight()) {
                IkGuiInternal.setScrollFromPosX(
                        window,
                        itemRect.getRight() + style.itemSpacing.x - window.position.x,
                        1.0f);
            }
        } else if (((scrollFlags & ScrollFlags.KEEP_VISIBLE_CENTER_X) != 0 && !fullyVisibleX)
                || (scrollFlags & ScrollFlags.ALWAYS_CENTER_X) != 0) {
            if (canBeFullyVisibleX) {
                IkGuiInternal.setScrollFromPosX(
                        window,
                        IkGuiInternal.truncate((itemRect.getLeft() + itemRect.getRight()) * 0.5f)
                                - window.position.x,
                        0.5f);
            } else {
                IkGuiInternal.setScrollFromPosX(
                        window, itemRect.getLeft() - window.position.x, 0.0f);
            }
        }

        if ((scrollFlags & ScrollFlags.KEEP_VISIBLE_EDGE_Y) != 0 && !fullyVisibleY) {
            if (itemRect.getTop() < scrollRect.getTop() || !canBeFullyVisibleY) {
                IkGuiInternal.setScrollFromPosY(
                        window, itemRect.getTop() - style.itemSpacing.y - window.position.y, 0.0f);
            } else if (itemRect.getBottom() >= scrollRect.getBottom()) {
                IkGuiInternal.setScrollFromPosY(
                        window,
                        itemRect.getBottom() + style.itemSpacing.y - window.position.y,
                        1.0f);
            }
        } else if (((scrollFlags & ScrollFlags.KEEP_VISIBLE_CENTER_Y) != 0 && !fullyVisibleY)
                || (scrollFlags & ScrollFlags.ALWAYS_CENTER_Y) != 0) {
            if (canBeFullyVisibleY) {
                IkGuiInternal.setScrollFromPosY(
                        window,
                        IkGuiInternal.truncate((itemRect.getTop() + itemRect.getBottom()) * 0.5f)
                                - window.position.y,
                        0.5f);
            } else {
                IkGuiInternal.setScrollFromPosY(
                        window, itemRect.getTop() - window.position.y, 0.0f);
            }
        }

        final Vector2f nextScroll = new Vector2f();
        IkGuiImplWindows.calcNextScrollFromScrollTargetAndClamp(window, nextScroll);
        output.set(nextScroll).sub(window.scrollPosition);

        // Also scroll the parent window to keep us in view if necessary
        if ((scrollFlags & ScrollFlags.NO_SCROLL_PARENT) == 0
                && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                && window.parentWindow != null) {
            if ((inFlags & (ScrollFlags.ALWAYS_CENTER_X | ScrollFlags.KEEP_VISIBLE_CENTER_X))
                    != 0) {
                inFlags = (inFlags & ~ScrollFlags.MASK_X) | ScrollFlags.KEEP_VISIBLE_EDGE_X;
            }
            if ((inFlags & (ScrollFlags.ALWAYS_CENTER_Y | ScrollFlags.KEEP_VISIBLE_CENTER_Y))
                    != 0) {
                inFlags = (inFlags & ~ScrollFlags.MASK_Y) | ScrollFlags.KEEP_VISIBLE_EDGE_Y;
            }
            final RectFloat parentRect =
                    new RectFloat(
                            itemRect.getLeft() - output.x,
                            itemRect.getTop() - output.y,
                            itemRect.getRight() - output.x,
                            itemRect.getBottom() - output.y);
            final Vector2f parentDelta = new Vector2f();
            scrollToRectEx(window.parentWindow, parentRect, inFlags, parentDelta);
            output.add(parentDelta);
        }
        return output;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplNav() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
