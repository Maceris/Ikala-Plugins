package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.List;

/** Popups, modals, context menus and tooltips. */
@Slf4j
class IkGuiImplPopups {

    /** How far tooltips are offset from the mouse cursor, scaled by the mouse cursor scale. */
    private static final float TOOLTIP_DEFAULT_OFFSET_MOUSE_X = 16;

    /** How far tooltips are offset from the mouse cursor, scaled by the mouse cursor scale. */
    private static final float TOOLTIP_DEFAULT_OFFSET_MOUSE_Y = 10;

    /** How far tooltips are offset from the touch position, scaled by the mouse cursor scale. */
    private static final float TOOLTIP_DEFAULT_OFFSET_TOUCH_X = 0;

    /** How far tooltips are offset from the touch position, scaled by the mouse cursor scale. */
    private static final float TOOLTIP_DEFAULT_OFFSET_TOUCH_Y = -20;

    /** The pivot used for tooltips when using a touch screen. */
    private static final float TOOLTIP_DEFAULT_PIVOT_TOUCH_X = 0.5f;

    /** The pivot used for tooltips when using a touch screen. */
    private static final float TOOLTIP_DEFAULT_PIVOT_TOUCH_Y = 1.0f;

    /** The order we try to place combo box popups in. */
    private static final Direction[] COMBO_DIRECTION_ORDER = {
        Direction.DOWN, Direction.RIGHT, Direction.LEFT, Direction.UP
    };

    /** The order we try to place tooltips and regular popups in. */
    private static final Direction[] DEFAULT_DIRECTION_ORDER = {
        Direction.RIGHT, Direction.DOWN, Direction.UP, Direction.LEFT
    };

    static Context context;

    /** Different ways to position popups relative to a reference point/rectangle. */
    enum PopupPositionPolicy {
        /** Regular popups. */
        DEFAULT,
        /** Combo boxes, where we want a connecting edge to the combo. */
        COMBO_BOX,
        /** Tooltips, which try hard to avoid the mouse cursor. */
        TOOLTIP
    }

    // ---------------------------------------------------------------------------------------
    // Tooltips
    // ---------------------------------------------------------------------------------------

    /**
     * Begin/append a tooltip window.
     *
     * @return True, currently always.
     */
    public static boolean beginTooltip() {
        return beginTooltipEx(false, WindowFlags.NONE);
    }

    /**
     * Begin/append a tooltip window if the preceding item was hovered.
     *
     * @return True if the tooltip was started, and endTooltip() needs to be called.
     */
    public static boolean beginItemTooltip() {
        if (!IkGuiImplUtils.isItemHovered(HoveredFlags.FOR_TOOLTIP)) {
            return false;
        }
        return beginTooltipEx(false, WindowFlags.NONE);
    }

    /**
     * Begin a tooltip window.
     *
     * @param overridePrevious If true, hide any tooltip that was already submitted this frame and
     *     start a new one.
     * @param extraWindowFlags Additional window flags.
     * @return True, currently always.
     */
    static boolean beginTooltipEx(boolean overridePrevious, int extraWindowFlags) {
        final boolean isDragDropTooltip =
                context.dragDropWithinSource || context.dragDropWithinTarget;
        if (isDragDropTooltip) {
            // Drag and drop tooltips are positioned differently than other tooltips. We offset
            // them more to increase visibility around the mouse, and never clamp within the outer
            // viewport boundary.
            final boolean isTouchScreen = context.io.mouseSource == MouseSource.TOUCH_SCREEN;
            if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_POSITION) == 0) {
                final float scale = context.style.variable.mouseCursorScale;
                final Vector2f mouse = context.io.mousePosition;
                if (isTouchScreen) {
                    IkGuiImplWindows.setNextWindowPos(
                            mouse.x + TOOLTIP_DEFAULT_OFFSET_TOUCH_X * scale,
                            mouse.y + TOOLTIP_DEFAULT_OFFSET_TOUCH_Y * scale,
                            Condition.NONE,
                            TOOLTIP_DEFAULT_PIVOT_TOUCH_X,
                            TOOLTIP_DEFAULT_PIVOT_TOUCH_Y);
                } else {
                    IkGuiImplWindows.setNextWindowPos(
                            mouse.x + TOOLTIP_DEFAULT_OFFSET_MOUSE_X * scale,
                            mouse.y + TOOLTIP_DEFAULT_OFFSET_MOUSE_Y * scale,
                            Condition.NONE,
                            0.0f,
                            0.0f);
                }
            }

            final int popupBackground = IkGuiImplUtils.getColor(ColorType.POPUP_BACKGROUND);
            IkGuiImplWindows.setNextWindowBgAlpha((popupBackground & 0xFF) / 255.0f * 0.60f);
            overridePrevious = true;
        }

        // Hide the previous tooltip from being displayed. We can't easily "reset" the contents of
        // a window so we create a new one.
        if (overridePrevious
                && context.tooltipPreviousWindow != null
                && context.tooltipPreviousWindow.active
                && !isWindowInBeginStack(context.tooltipPreviousWindow)) {
            setWindowHiddenAndSkipItemsForCurrentFrame(context.tooltipPreviousWindow);
            context.tooltipOverrideCount++;
        }

        final String windowName =
                String.format(
                        isDragDropTooltip ? "##Tooltip_DragDrop_%02d" : "##Tooltip_%02d",
                        context.tooltipOverrideCount);
        final int flags =
                WindowFlags.INTERNAL_TOOLTIP
                        | WindowFlags.NO_INPUTS
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_MOVE
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_SAVED_SETTINGS
                        | WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_DOCKING;
        IkGuiImplWindows.begin(windowName, null, flags | extraWindowFlags);
        // If this ever returns false we need to update beginDragDropSource() accordingly
        return true;
    }

    /** End a tooltip, only call this if beginTooltip()/beginItemTooltip() returned true. */
    public static void endTooltip() {
        final Window window = context.windowCurrent;
        if (window == null || (window.flags & WindowFlags.INTERNAL_TOOLTIP) == 0) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginTooltip()/endTooltip() calls");
            return;
        }
        IkGuiImplWindows.end();
    }

    /**
     * Set a text-only tooltip, often used after an isItemHovered() check. Overrides any previous
     * call to setTooltip().
     *
     * @param text The text to display.
     */
    public static void setTooltip(@NonNull String text) {
        if (!beginTooltipEx(true, WindowFlags.NONE)) {
            return;
        }
        IkGuiImplText.textUnformatted(text);
        endTooltip();
    }

    /**
     * Set a text-only tooltip if the preceding item was hovered. Overrides any previous call to
     * setTooltip().
     *
     * @param text The text to display.
     */
    public static void setItemTooltip(@NonNull String text) {
        if (IkGuiImplUtils.isItemHovered(HoveredFlags.FOR_TOOLTIP)) {
            setTooltip(text);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Popup queries
    // ---------------------------------------------------------------------------------------

    /**
     * Check if a popup is open.
     *
     * @param id The ID of the popup, should be 0 if using {@link PopupFlags#ANY_POPUP_ID}.
     * @param popupFlags Popup flags, supports {@link PopupFlags#ANY_POPUP_ID} and {@link
     *     PopupFlags#ANY_POPUP_LEVEL}.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(int id, int popupFlags) {
        final List<PopupData> openStack = context.openPopupStack;
        final int beginSize = context.beginPopupStack.size();
        if ((popupFlags & PopupFlags.ANY_POPUP_ID) != 0) {
            // Check if any popup is open at the current beginPopup() level of the popup stack.
            // This may be used to e.g. test for another popup already opened to handle popup
            // priorities at the same level.
            if (id != 0) {
                log.warn("isPopupOpen() called with ANY_POPUP_ID and a non-zero ID");
            }
            if ((popupFlags & PopupFlags.ANY_POPUP_LEVEL) != 0) {
                return !openStack.isEmpty();
            }
            return openStack.size() > beginSize;
        }
        if ((popupFlags & PopupFlags.ANY_POPUP_LEVEL) != 0) {
            // Check if the popup is open anywhere in the popup stack
            for (PopupData data : openStack) {
                if (data.popupID == id) {
                    return true;
                }
            }
            return false;
        }
        // Check if the popup is open at the current beginPopup() level (the most common query)
        return openStack.size() > beginSize && openStack.get(beginSize).popupID == id;
    }

    /**
     * Check if a popup is open.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param popupFlags Popup flags, supports {@link PopupFlags#ANY_POPUP_ID} and {@link
     *     PopupFlags#ANY_POPUP_LEVEL}.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(@NonNull String stringID, int popupFlags) {
        final int id =
                (popupFlags & PopupFlags.ANY_POPUP_ID) != 0
                        ? 0
                        : context.windowCurrent.getID(stringID);
        if ((popupFlags & PopupFlags.ANY_POPUP_LEVEL) != 0 && id != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Cannot use isPopupOpen() with a string ID and ANY_POPUP_LEVEL");
            return false;
        }
        return isPopupOpen(id, popupFlags);
    }

    /**
     * Find the top-most modal popup that is active and visible.
     *
     * @return The modal window, or null if there are none.
     */
    static Window getTopMostAndVisiblePopupModal() {
        final List<PopupData> openStack = context.openPopupStack;
        for (int i = openStack.size() - 1; i >= 0; --i) {
            final Window popup = openStack.get(i).window;
            if (popup != null
                    && (popup.flags & WindowFlags.INTERNAL_MODAL) != 0
                    && IkGuiInternal.isWindowActiveAndVisible(popup)) {
                return popup;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Opening and closing
    // ---------------------------------------------------------------------------------------

    /**
     * Mark a popup as open. Don't call this every frame!
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param popupFlags Popup flags.
     * @return True if the popup was toggled open.
     * @see PopupFlags
     */
    public static boolean openPopup(@NonNull String stringID, int popupFlags) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_POPUP,
                "[popup] OpenPopup(\"%s\" -> 0x%08X)",
                stringID,
                context.windowCurrent != null ? context.windowCurrent.getID(stringID) : 0);
        return openPopupEx(context.windowCurrent.getID(stringID), popupFlags);
    }

    /**
     * Mark a popup as open (toggle toward the open state).
     *
     * <ul>
     *   <li>Returns true when the popup is toggled open, which allows capturing local state if
     *       needed. You may also call isWindowAppearing() inside the later beginPopup() scope if
     *       you need to prepare data for the popup.
     *   <li>Popups are closed when the user clicks outside, activates a pressable item, or
     *       closeCurrentPopup() is called within a beginPopup()/endPopup() block.
     *   <li>Popup identifiers are relative to the current ID stack (so openPopup() and beginPopup()
     *       need to be at the same level).
     *   <li>There is one open popup per level of the popup hierarchy.
     * </ul>
     *
     * @param id The ID of the popup.
     * @param popupFlags Popup flags.
     * @return True if the popup was toggled open.
     */
    static boolean openPopupEx(int id, int popupFlags) {
        IkGuiImplDebugTools.debugLog(DebugLogFlags.EVENT_POPUP, "[popup] OpenPopupEx(0x%08X)", id);
        final Window parentWindow = context.windowCurrent;
        final int currentStackSize = context.beginPopupStack.size();
        final List<PopupData> openStack = context.openPopupStack;

        if ((popupFlags & PopupFlags.NO_OPEN_OVER_EXISTING_POPUP) != 0
                && isPopupOpen(0, PopupFlags.ANY_POPUP_ID)) {
            return false;
        }

        final PopupData popupRef = new PopupData();
        popupRef.popupID = id;
        popupRef.window = null;
        popupRef.restoreNavWindow = context.navFocusedWindow;
        popupRef.openFrameCount = context.frameCount;
        popupRef.openParentID = parentWindow.idStack.peek();
        navCalcPreferredRefPos(true, popupRef.preferredPosition);
        if (IkGuiImplUtils.isMousePosValid(
                context.io.mousePosition.x, context.io.mousePosition.y)) {
            popupRef.mousePosition.set(context.io.mousePosition);
        } else {
            popupRef.mousePosition.set(popupRef.preferredPosition);
        }

        if (openStack.size() < currentStackSize + 1) {
            openStack.add(popupRef);
            return true;
        }

        // Gently handle the user mistakenly calling openPopup() every frame, which is likely a
        // programming mistake. If we ran the regular code path, the UI would become unusable
        // because the popup would always be in the hidden-while-calculating-size state while
        // claiming focus. Instead, for successive frame calls we silently avoid reopening.
        final PopupData existing = openStack.get(currentStackSize);
        final boolean keepExisting =
                existing.popupID == id
                        && (existing.openFrameCount == context.frameCount - 1
                                || (popupFlags & PopupFlags.NO_REOPEN) != 0);
        if (keepExisting) {
            // No reopen
            existing.openFrameCount = popupRef.openFrameCount;
        } else {
            // Reopen: close child popups if any, then flag the popup for open/reopen
            closePopupToLevel(currentStackSize, true);
            openStack.add(popupRef);
        }
        return !keepExisting;
    }

    /**
     * Helper to open a popup if the mouse button was released over the last item.
     *
     * @param stringID The string ID of the popup, or null to use the last item ID.
     * @param popupFlags Popup flags.
     * @return True if the popup was opened.
     */
    public static boolean openPopupOnItemClick(String stringID, int popupFlags) {
        if (isPopupOpenRequestForItem(popupFlags, context.lastItemData.id)) {
            final int id =
                    stringID != null
                            ? context.windowCurrent.getID(stringID)
                            : context.lastItemData.id;
            if (id == 0) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "openPopupOnItemClick() requires an ID if the last item has no identifier");
                return false;
            }
            return openPopupEx(id, popupFlags);
        }
        return false;
    }

    /**
     * When popups are stacked, clicking on a lower level popup puts focus back to it and closes
     * popups above it. This function closes any popups that are over the reference window.
     *
     * @param referenceWindow The reference window, generally the newly focused window. If null, all
     *     popups are closed.
     * @param restoreFocusToWindowUnderPopup Whether to restore focus to the window that was focused
     *     before the bottom-most closed popup was opened.
     */
    static void closePopupsOverWindow(
            Window referenceWindow, boolean restoreFocusToWindowUnderPopup) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_POPUP,
                "[popup] ClosePopupsOverWindow(\"%s\")",
                IkGuiImplDebugTools.nameOf(referenceWindow));
        final List<PopupData> openStack = context.openPopupStack;
        if (openStack.isEmpty()) {
            return;
        }

        // Don't close our own child popup windows
        int popupCountToKeep = 0;
        if (referenceWindow != null) {
            // Find the highest popup which is a descendant of the reference window
            for (; popupCountToKeep < openStack.size(); ++popupCountToKeep) {
                final PopupData popup = openStack.get(popupCountToKeep);
                if (popup.window == null) {
                    continue;
                }

                // Trim the stack unless the popup is a direct parent of the reference window.
                // - Clicking/Focusing Window2 won't close Popup1:
                //     Window -> Popup1 -> Window2(Ref)
                // - Clicking/focusing Popup1 will close Popup2 and Popup3:
                //     Window -> Popup1(Ref) -> Popup2 -> Popup3
                // We step through every popup from bottom to top to validate their position
                // relative to the reference window.
                boolean referenceWindowIsDescendantOfPopup = false;
                for (int i = popupCountToKeep; i < openStack.size(); ++i) {
                    final Window popupWindow = openStack.get(i).window;
                    if (popupWindow != null
                            && IkGuiInternal.isWindowWithinBeginStackOf(
                                    referenceWindow, popupWindow)) {
                        referenceWindowIsDescendantOfPopup = true;
                        break;
                    }
                }
                if (!referenceWindowIsDescendantOfPopup) {
                    break;
                }
            }
        }
        if (popupCountToKeep < openStack.size()) {
            closePopupToLevel(popupCountToKeep, restoreFocusToWindowUnderPopup);
        }
    }

    /** Close all popups except modals. */
    static void closePopupsExceptModals() {
        final List<PopupData> openStack = context.openPopupStack;
        int popupCountToKeep;
        for (popupCountToKeep = openStack.size(); popupCountToKeep > 0; --popupCountToKeep) {
            final Window window = openStack.get(popupCountToKeep - 1).window;
            if (window == null || (window.flags & WindowFlags.INTERNAL_MODAL) != 0) {
                break;
            }
        }
        if (popupCountToKeep < openStack.size()) {
            closePopupToLevel(popupCountToKeep, true);
        }
    }

    /**
     * Trim the open popup stack down to a certain size.
     *
     * @param remaining How many popups should remain open.
     * @param restoreFocusToWindowUnderPopup Whether to restore focus to the window that was focused
     *     before the bottom-most closed popup was opened.
     */
    static void closePopupToLevel(int remaining, boolean restoreFocusToWindowUnderPopup) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_POPUP,
                "[popup] ClosePopupToLevel(%d), restore_under=%b",
                remaining,
                restoreFocusToWindowUnderPopup);
        if (IkGuiImplDebugTools.isDebugLogEnabled(DebugLogFlags.EVENT_POPUP)) {
            for (int n = remaining; n < context.openPopupStack.size(); ++n) {
                final PopupData popup = context.openPopupStack.get(n);
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_POPUP,
                        "[popup] - Closing PopupID 0x%08X Window \"%s\"",
                        popup.popupID,
                        IkGuiImplDebugTools.nameOf(popup.window));
            }
        }
        final List<PopupData> openStack = context.openPopupStack;
        if (remaining < 0 || remaining >= openStack.size()) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Invalid popup level {} to close to, with {} open",
                    remaining,
                    openStack.size());
            return;
        }

        // Trim the open popup stack
        final PopupData previousPopup = openStack.get(remaining);
        openStack.subList(remaining, openStack.size()).clear();

        // Restore focus (unless the popup window was not yet submitted, and didn't have a chance
        // to take focus anyway)
        if (restoreFocusToWindowUnderPopup && previousPopup.window != null) {
            final Window popupWindow = previousPopup.window;
            final Window focusWindow =
                    (popupWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0
                            ? popupWindow.parentWindow
                            : previousPopup.restoreNavWindow;
            if (focusWindow != null && !focusWindow.wasActive) {
                // Fallback
                IkGuiInternal.focusTopMostWindowUnderOne(
                        popupWindow, null, WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
            } else {
                IkGuiInternal.focusWindow(
                        focusWindow,
                        context.navLayer == IkGuiImplNav.NAV_LAYER_MAIN
                                ? WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD
                                : WindowFocusRequestFlags.NONE);
            }
        }
    }

    /** Close the popup we have begun into. */
    public static void closeCurrentPopup() {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_POPUP,
                "[popup] CloseCurrentPopup %d",
                context.beginPopupStack.size() - 1);
        final List<PopupData> openStack = context.openPopupStack;
        final List<PopupData> beginStack = context.beginPopupStack;
        int popupIndex = beginStack.size() - 1;
        if (popupIndex < 0
                || popupIndex >= openStack.size()
                || beginStack.get(popupIndex).popupID != openStack.get(popupIndex).popupID) {
            return;
        }

        // Closing a menu closes its top-most parent popup (unless a modal)
        while (popupIndex > 0) {
            final Window popupWindow = openStack.get(popupIndex).window;
            final Window parentPopupWindow = openStack.get(popupIndex - 1).window;
            boolean closeParent =
                    popupWindow != null
                            && (popupWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0
                            && parentPopupWindow != null
                            && (parentPopupWindow.flags & WindowFlags.MENU_BAR) == 0;
            if (!closeParent) {
                break;
            }
            popupIndex--;
        }
        closePopupToLevel(popupIndex, true);
    }

    // ---------------------------------------------------------------------------------------
    // Begin/end
    // ---------------------------------------------------------------------------------------

    /**
     * Begin a popup window, if it is open. Note that this does not add the default flags that
     * beginPopup() adds.
     *
     * @param id The ID of the popup.
     * @param extraWindowFlags Window flags.
     * @return True if the popup is open and visible, and endPopup() needs to be called.
     */
    static boolean beginPopupEx(int id, int extraWindowFlags) {
        if (!isPopupOpen(id, PopupFlags.NONE)) {
            // We behave like begin() and need to consume those values
            context.nextWindowData.clearFlags();
            return false;
        }
        if ((extraWindowFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            IkGuiImplDebugTools.reportError(log, "Use beginPopupMenuEx() for menus");
            return false;
        }

        // No recycling, so we can close/open during the same frame
        final String name = String.format("##Popup_%08x", id);
        final boolean isOpen =
                IkGuiImplWindows.begin(
                        name,
                        null,
                        extraWindowFlags | WindowFlags.INTERNAL_POPUP | WindowFlags.NO_DOCKING);
        if (!isOpen) {
            // This can return false when the popup is completely clipped (e.g. zero size display)
            endPopup();
        }
        return isOpen;
    }

    /**
     * Begin a popup window for a menu, if it is open. Menu windows are recycled based on their
     * depth.
     *
     * @param id The ID of the popup.
     * @param label The label of the menu.
     * @param extraWindowFlags Window flags, which must include {@link
     *     WindowFlags#INTERNAL_CHILD_MENU}.
     * @return True if the popup is open and visible, and endPopup() needs to be called.
     */
    static boolean beginPopupMenuEx(int id, @NonNull String label, int extraWindowFlags) {
        if (!isPopupOpen(id, PopupFlags.NONE)) {
            // We behave like begin() and need to consume those values
            context.nextWindowData.clearFlags();
            return false;
        }
        if ((extraWindowFlags & WindowFlags.INTERNAL_CHILD_MENU) == 0) {
            IkGuiImplDebugTools.reportError(log, "beginPopupMenuEx() requires the child menu flag");
            return false;
        }

        // As we bypass beginChild(), set ALWAYS_AUTO_RESIZE as child flags too, since it is
        // checked independently from the window flag
        if ((extraWindowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                && (extraWindowFlags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0) {
            context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_CHILD_FLAGS;
            context.nextWindowData.childFlags |= ChildFlags.ALWAYS_AUTO_RESIZE;
        }

        // Recycle windows based on depth
        final String name = String.format("%s###Menu_%02d", label, context.beginMenuDepth);
        final boolean isOpen =
                IkGuiImplWindows.begin(name, null, extraWindowFlags | WindowFlags.INTERNAL_POPUP);
        if (!isOpen) {
            // This can return false when the popup is completely clipped (e.g. zero size display)
            endPopup();
        }
        return isOpen;
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param id The ID of the popup.
     * @param windowFlags Window flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopup(int id, int windowFlags) {
        if (context.openPopupStack.size() <= context.beginPopupStack.size()) {
            // Early out for performance. We behave like begin() and need to consume those values.
            context.nextWindowData.clearFlags();
            return false;
        }
        windowFlags |=
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS;
        return beginPopupEx(id, windowFlags);
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param windowFlags Window flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopup(@NonNull String stringID, int windowFlags) {
        return beginPopup(context.windowCurrent.getID(stringID), windowFlags);
    }

    /**
     * Begin a modal popup, if it is open. Modals block interactions with windows behind them, and
     * can't be closed by clicking outside them.
     *
     * <p>If open is provided, the modal will have a regular close button which closes the popup.
     * The value of open is set to false when the popup is not open, and if you set it to false
     * before calling this, the popup is closed.
     *
     * @param name The name of the modal, which is also the window name.
     * @param open Whether the popup is open, may be null.
     * @param windowFlags Window flags.
     * @return True if the modal is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupModal(@NonNull String name, IkBoolean open, int windowFlags) {
        final Window window = context.windowCurrent;
        final int id = window.getID(name);
        if (!isPopupOpen(id, PopupFlags.NONE)) {
            // We behave like begin() and need to consume those values
            context.nextWindowData.clearFlags();
            if (open != null && open.get()) {
                open.set(false);
            }
            return false;
        }

        // Center modal windows by default for increased visibility
        if ((context.nextWindowData.fieldFlags & NextWindowFlags.HAS_POSITION) == 0) {
            final Viewport viewport = window.wasActive ? window.viewport : context.mainViewport;
            IkGuiImplWindows.setNextWindowPos(
                    viewport.position.x + viewport.size.x * 0.5f,
                    viewport.position.y + viewport.size.y * 0.5f,
                    Condition.FIRST_USE_EVER,
                    0.5f,
                    0.5f);
        }

        windowFlags |=
                WindowFlags.INTERNAL_POPUP
                        | WindowFlags.INTERNAL_MODAL
                        | WindowFlags.NO_COLLAPSE
                        | WindowFlags.NO_DOCKING;
        final boolean isOpen = IkGuiImplWindows.begin(name, open, windowFlags);
        if (!isOpen || (open != null && !open.get())) {
            // This can be false when the popup is completely clipped (e.g. zero size display)
            endPopup();
            if (isOpen) {
                closePopupToLevel(context.beginPopupStack.size(), true);
            }
            return false;
        }
        return true;
    }

    /** End a popup, only call this if a beginPopup*() call returned true. */
    public static void endPopup() {
        final Window window = context.windowCurrent;
        if (window == null
                || (window.flags & WindowFlags.INTERNAL_POPUP) == 0
                || context.beginPopupStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Calling endPopup() in the wrong window!");
            return;
        }

        // Make all menus and popups wrap around for now, may need to expose that policy
        if (context.navFocusedWindow == window) {
            IkGuiImplNav.navMoveRequestTryWrapping(window, NavMoveFlags.LOOP_Y);
        }

        // Child popups don't need to be laid out
        final int backupWithinEndPopupID = context.withinEndPopupID;
        final int backupWithinEndChildID = context.withinEndChildID;
        context.withinEndPopupID = window.id;
        if ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            context.withinEndChildID = window.id;
        }
        IkGuiImplWindows.end();
        context.withinEndPopupID = backupWithinEndPopupID;
        context.withinEndChildID = backupWithinEndChildID;
    }

    // ---------------------------------------------------------------------------------------
    // Context popups
    // ---------------------------------------------------------------------------------------

    /**
     * Find which mouse button the popup flags indicate.
     *
     * @param popupFlags The popup flags.
     * @return The mouse button, defaulting to the right button.
     */
    static MouseButton getMouseButtonFromPopupFlags(int popupFlags) {
        if ((popupFlags & PopupFlags.MOUSE_BUTTON_LEFT) != 0) {
            return MouseButton.LEFT;
        }
        if ((popupFlags & PopupFlags.MOUSE_BUTTON_MIDDLE) != 0) {
            return MouseButton.MIDDLE;
        }
        return MouseButton.RIGHT;
    }

    /**
     * Checks if there is a request to open a popup for an item, from either the mouse or
     * navigation.
     *
     * @param popupFlags Used to decide which mouse button we are checking for.
     * @param id The ID of the item.
     * @return Whether a mouse click or navigation action would result in a popup being requested.
     */
    static boolean isPopupOpenRequestForItem(int popupFlags, int id) {
        final MouseButton button = getMouseButtonFromPopupFlags(popupFlags);
        if (IkGuiImplUtils.isMouseReleased(button, id)
                && IkGuiImplUtils.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP)) {
            return true;
        }
        return context.navOpenContextMenuItemID == id
                && (IkGuiImplUtils.isItemFocused() || id == context.windowCurrent.idMove);
    }

    /**
     * Checks if there is a request to open a popup for the current window, from either the mouse or
     * navigation.
     *
     * @param popupFlags Used to decide which mouse button we are checking for.
     * @return Whether a mouse click or navigation action would result in a popup being requested.
     */
    static boolean isPopupOpenRequestForWindow(int popupFlags) {
        final MouseButton button = getMouseButtonFromPopupFlags(popupFlags);
        if (IkGuiImplUtils.isMouseReleased(button, KeyRoutingData.KEY_OWNER_NO_OWNER)
                && IkGuiImplUtils.isWindowHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP)
                && ((popupFlags & PopupFlags.NO_OPEN_OVER_ITEMS) == 0
                        || !IkGuiImplUtils.isAnyItemHovered())) {
            return true;
        }
        return context.navOpenContextMenuWindowID != 0
                && IkGuiInternal.isWindowChildOf(
                        context.navFocusedWindow, context.windowCurrent, false, false);
    }

    /**
     * Open and begin a popup when the last item is clicked.
     *
     * @param stringID The string ID of the popup, or null to associate the popup with the last
     *     item. You need to pass an ID for items with no identifier, like text().
     * @param popupFlags Popup flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextItem(String stringID, int popupFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }
        // Using the last item ID as a popup ID won't conflict
        final int id = stringID != null ? window.getID(stringID) : context.lastItemData.id;
        return beginPopupContextItem(id, popupFlags);
    }

    /**
     * Open and begin a popup when the last item is clicked.
     *
     * @param id The ID of the popup.
     * @param popupFlags Popup flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextItem(int id, int popupFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }
        if (id == 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "beginPopupContextItem() requires an ID if the last item has no identifier");
            return false;
        }
        if (isPopupOpenRequestForItem(popupFlags, context.lastItemData.id)) {
            openPopupEx(id, popupFlags);
        }
        return beginPopupEx(
                id,
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS);
    }

    /**
     * Open and begin a popup when the current window is clicked.
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @param popupFlags Popup flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextWindow(String stringID, int popupFlags) {
        final Window window = context.windowCurrent;
        final int id = window.getID(stringID != null ? stringID : "window_context");
        if (isPopupOpenRequestForWindow(popupFlags)) {
            openPopupEx(id, popupFlags);
        }
        return beginPopupEx(
                id,
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS);
    }

    /**
     * Open and begin a popup when clicking in the void (where there are no windows).
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @param popupFlags Popup flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextVoid(String stringID, int popupFlags) {
        final Window window = context.windowCurrent;
        final int id = window.getID(stringID != null ? stringID : "void_context");
        final MouseButton button = getMouseButtonFromPopupFlags(popupFlags);
        if (IkGuiImplUtils.isMouseReleased(button, id)
                && !IkGuiImplUtils.isWindowHovered(HoveredFlags.ANY_WINDOW)
                && IkGuiInternal.getTopmostPopupModal() == null) {
            openPopupEx(id, popupFlags);
        }
        return beginPopupEx(
                id,
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS);
    }

    // ---------------------------------------------------------------------------------------
    // Positioning
    // ---------------------------------------------------------------------------------------

    /**
     * Find the best position for a popup.
     *
     * @param referenceX The preferred x position.
     * @param referenceY The preferred y position.
     * @param size The size of the popup.
     * @param window The window to use for storing the last direction we picked.
     * @param outer The visible area rectangle, minus safe area padding. If the popup won't fit
     *     because of safe area padding, we ignore it.
     * @param avoid The rectangle to avoid (e.g. for tooltips it is a rectangle around the mouse
     *     cursor which we want to avoid, for popups it's a small point around the cursor).
     * @param policy The positioning policy.
     * @param output Where to store the position.
     * @return The output vector, for convenience.
     */
    static Vector2f findBestWindowPosForPopupEx(
            float referenceX,
            float referenceY,
            @NonNull Vector2f size,
            @NonNull Window window,
            @NonNull RectFloat outer,
            @NonNull RectFloat avoid,
            @NonNull PopupPositionPolicy policy,
            @NonNull Vector2f output) {
        final float basePosClampedX =
                MathUtil.clamp(
                        referenceX,
                        outer.getLeft(),
                        Math.max(outer.getLeft(), outer.getRight() - size.x));
        final float basePosClampedY =
                MathUtil.clamp(
                        referenceY,
                        outer.getTop(),
                        Math.max(outer.getTop(), outer.getBottom() - size.y));

        // Combo box policy (we want a connecting edge)
        if (policy == PopupPositionPolicy.COMBO_BOX) {
            final Direction lastDirection = window.autoPosLastDirection;
            for (int n = lastDirection != Direction.NONE ? -1 : 0;
                    n < COMBO_DIRECTION_ORDER.length;
                    ++n) {
                final Direction direction = n == -1 ? lastDirection : COMBO_DIRECTION_ORDER[n];
                if (n != -1 && direction == lastDirection) {
                    // Already tried this direction
                    continue;
                }
                float x;
                float y;
                switch (direction) {
                    case RIGHT -> {
                        // Above, toward the right
                        x = avoid.getLeft();
                        y = avoid.getTop() - size.y;
                    }
                    case LEFT -> {
                        // Below, toward the left
                        x = avoid.getRight() - size.x;
                        y = avoid.getBottom();
                    }
                    case UP -> {
                        // Above, toward the left
                        x = avoid.getRight() - size.x;
                        y = avoid.getTop() - size.y;
                    }
                    default -> {
                        // Below, toward the right (default)
                        x = avoid.getLeft();
                        y = avoid.getBottom();
                    }
                }
                if (!outer.contains(new RectFloat(x, y, x + size.x, y + size.y))) {
                    continue;
                }
                window.autoPosLastDirection = direction;
                return output.set(x, y);
            }
        }

        // Tooltip and default popup policy (always first try the direction we used last frame)
        if (policy == PopupPositionPolicy.TOOLTIP || policy == PopupPositionPolicy.DEFAULT) {
            final Direction lastDirection = window.autoPosLastDirection;
            for (int n = lastDirection != Direction.NONE ? -1 : 0;
                    n < DEFAULT_DIRECTION_ORDER.length;
                    ++n) {
                final Direction direction = n == -1 ? lastDirection : DEFAULT_DIRECTION_ORDER[n];
                if (n != -1 && direction == lastDirection) {
                    // Already tried this direction
                    continue;
                }

                final float availableWidth =
                        (direction == Direction.LEFT ? avoid.getLeft() : outer.getRight())
                                - (direction == Direction.RIGHT
                                        ? avoid.getRight()
                                        : outer.getLeft());
                final float availableHeight =
                        (direction == Direction.UP ? avoid.getTop() : outer.getBottom())
                                - (direction == Direction.DOWN
                                        ? avoid.getBottom()
                                        : outer.getTop());

                // If there's not enough room on one axis, there's no point in positioning on a
                // side on this axis (e.g. when not enough width, use a top/bottom position to
                // maximize available width)
                if (availableWidth < size.x
                        && (direction == Direction.LEFT || direction == Direction.RIGHT)) {
                    continue;
                }
                if (availableHeight < size.y
                        && (direction == Direction.UP || direction == Direction.DOWN)) {
                    continue;
                }

                float x =
                        switch (direction) {
                            case LEFT -> avoid.getLeft() - size.x;
                            case RIGHT -> avoid.getRight();
                            default -> basePosClampedX;
                        };
                float y =
                        switch (direction) {
                            case UP -> avoid.getTop() - size.y;
                            case DOWN -> avoid.getBottom();
                            default -> basePosClampedY;
                        };

                // Clamp the top-left corner of the popup
                x = Math.max(x, outer.getLeft());
                y = Math.max(y, outer.getTop());

                window.autoPosLastDirection = direction;
                return output.set(x, y);
            }
        }

        // Fallback when there's not enough room
        window.autoPosLastDirection = Direction.NONE;

        // For tooltips we prefer avoiding the cursor at all costs, even if it means that part of
        // the tooltip won't be visible
        if (policy == PopupPositionPolicy.TOOLTIP) {
            return output.set(referenceX + 2, referenceY + 2);
        }

        // Otherwise try to keep within the display
        return output.set(
                Math.max(Math.min(referenceX + size.x, outer.getRight()) - size.x, outer.getLeft()),
                Math.max(
                        Math.min(referenceY + size.y, outer.getBottom()) - size.y, outer.getTop()));
    }

    /**
     * Calculate the area a popup is allowed to be in. Popups can overlap the non-work area of
     * viewports.
     *
     * @param window The popup window.
     * @param output Where to store the rectangle.
     * @return The output rectangle, for convenience.
     */
    static RectFloat getPopupAllowedExtentRect(@NonNull Window window, @NonNull RectFloat output) {
        final int monitorIndex = window.viewportAllowPlatformMonitorExtend;
        if (monitorIndex >= 0 && monitorIndex < context.platformIO.monitors.size()) {
            // The extent will be in the frame of reference of the given viewport (so the minimum
            // is likely to be negative here)
            context.platformIO.monitors.get(monitorIndex).getWorkRect(output);
        } else {
            // Use the full viewport area (not the work area) for popups
            window.viewport.getMainRect(output);
        }
        final Vector2f padding = context.style.variable.displaySafeAreaPadding;
        output.expand(
                output.getWidth() > padding.x * 2 ? -padding.x : 0.0f,
                output.getHeight() > padding.y * 2 ? -padding.y : 0.0f);
        return output;
    }

    /**
     * Find the best position for a popup, child menu, or tooltip window.
     *
     * @param window The window.
     * @param output Where to store the position.
     * @return The output vector, for convenience.
     */
    static Vector2f findBestWindowPosForPopup(@NonNull Window window, @NonNull Vector2f output) {
        final RectFloat outer = getPopupAllowedExtentRect(window, new RectFloat());
        if ((window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0) {
            // Child menus typically request any position within the parent menu item, and then we
            // move the new menu outside the parent bounds. This is how we end up with child menus
            // appearing (most commonly) on the right of the parent menu.
            final Window parentWindow = window.parentWindow;
            // We want some overlap to convey the relative depth of each menu
            final float horizontalOverlap = context.style.variable.itemInnerSpacing.x;
            final RectFloat avoid;
            if (parentWindow.menuBarAppending) {
                // Avoid the parent menu bar
                avoid =
                        new RectFloat(
                                -Float.MAX_VALUE,
                                parentWindow.rectCurrentClip.getTop(),
                                Float.MAX_VALUE,
                                parentWindow.rectCurrentClip.getBottom());
            } else {
                avoid =
                        new RectFloat(
                                parentWindow.position.x + horizontalOverlap,
                                -Float.MAX_VALUE,
                                parentWindow.position.x
                                        + parentWindow.size.x
                                        - horizontalOverlap
                                        - parentWindow.scrollbarSizes.x,
                                Float.MAX_VALUE);
            }
            return findBestWindowPosForPopupEx(
                    window.position.x,
                    window.position.y,
                    window.size,
                    window,
                    outer,
                    avoid,
                    PopupPositionPolicy.DEFAULT,
                    output);
        }
        if ((window.flags & WindowFlags.INTERNAL_POPUP) != 0) {
            return findBestWindowPosForPopupEx(
                    window.position.x,
                    window.position.y,
                    window.size,
                    window,
                    outer,
                    new RectFloat(
                            window.position.x,
                            window.position.y,
                            window.position.x,
                            window.position.y),
                    PopupPositionPolicy.DEFAULT,
                    output);
        }
        if ((window.flags & WindowFlags.INTERNAL_TOOLTIP) != 0) {
            // Position the tooltip (always follows the mouse and clamps within outer boundaries)
            final float scale = context.style.variable.mouseCursorScale;
            final Vector2f reference = navCalcPreferredRefPos(false, new Vector2f());

            if (context.io.mouseSource == MouseSource.TOUCH_SCREEN) {
                final float x =
                        reference.x
                                + TOOLTIP_DEFAULT_OFFSET_TOUCH_X * scale
                                - TOOLTIP_DEFAULT_PIVOT_TOUCH_X * window.size.x;
                final float y =
                        reference.y
                                + TOOLTIP_DEFAULT_OFFSET_TOUCH_Y * scale
                                - TOOLTIP_DEFAULT_PIVOT_TOUCH_Y * window.size.y;
                if (outer.contains(new RectFloat(x, y, x + window.size.x, y + window.size.y))) {
                    return output.set(x, y);
                }
            }

            // The exact dimensions are not very important, this is based on the expected shape of
            // the mouse cursor
            final RectFloat avoid =
                    new RectFloat(
                            reference.x - 16,
                            reference.y - 8,
                            reference.x + 24 * scale,
                            reference.y + 24 * scale);
            return findBestWindowPosForPopupEx(
                    reference.x + TOOLTIP_DEFAULT_OFFSET_MOUSE_X * scale,
                    reference.y + TOOLTIP_DEFAULT_OFFSET_MOUSE_Y * scale,
                    window.size,
                    window,
                    outer,
                    avoid,
                    PopupPositionPolicy.TOOLTIP,
                    output);
        }
        IkGuiImplDebugTools.reportError(
                log, "findBestWindowPosForPopup() called on a regular window {}", window.name);
        return output.set(window.position);
    }

    /**
     * Calculate the preferred reference position for opening popups and tooltips. This is the mouse
     * position, or near the navigation item when using keyboard/gamepad navigation.
     *
     * @param popupWindow True for popups, false for tooltips.
     * @param output Where to store the position.
     * @return The output vector, for convenience.
     */
    static Vector2f navCalcPreferredRefPos(boolean popupWindow, @NonNull Vector2f output) {
        IkGuiImplNav.navCalcPreferredRefPos(popupWindow, output);
        return output;
    }

    // ---------------------------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------------------------

    /**
     * Check if a window is currently on the window stack (between begin() and end()).
     *
     * @param window The window.
     * @return True if the window is in the begin stack.
     */
    static boolean isWindowInBeginStack(@NonNull Window window) {
        for (int i = context.windowStack.size() - 1; i >= 0; --i) {
            if (context.windowStack.get(i).window == window) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hide a window for the rest of the current frame, and skip items being submitted to it.
     *
     * @param window The window.
     */
    static void setWindowHiddenAndSkipItemsForCurrentFrame(@NonNull Window window) {
        window.hidden = true;
        window.skipItems = true;
        window.hiddenFramesCanSkipItems.set(1);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplPopups() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
