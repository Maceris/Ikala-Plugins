package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.PopupData;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Optional;

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
                stopMovingWindow();
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
    public static void startMovingWindow(@NonNull Window window) {
        focusWindow(window, WindowFocusRequestFlags.NONE);

        setActiveID(window.idMove, window);
        context.activeIDClickOffset.set(context.io.mouseClickedPosition[MouseButton.LEFT.index]);
        context.activeIDClickOffset.sub(
                Optional.ofNullable(window.rootWindow).orElse(window).position);
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
    public static void stopMovingWindow() {
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
                startMovingWindow(hoveredRoot);
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
                            && (modal != null || isWindowAbove(context.windowHovered, modal));
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
                IkGui.setWindowPos(context.windowMoving, pos, Condition.ALWAYS);
                focusWindow(context.windowMoving, WindowFocusRequestFlags.NONE);
            } else {
                stopMovingWindow();
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
}
