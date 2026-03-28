package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.PopupData;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.frontend.gui.flags.PopupFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFocusRequestFlags;

import java.util.Optional;

public class IkGuiInternal {

    static Context context;

    public static void clearActiveID() {
        context.activeIDActivatedThisFrame = false;
        context.activeIDAllowOverlap = false;
        context.activeIDClickOffset.set(0, 0);
        context.activeIDFromShortcut = false;
        context.activeIDHasBeenEditedBefore = false;
        context.activeIDHasBeenEditedThisFrame = false;
        context.activeIDHasBeenPressedBefore = false;
        context.activeIDMouseButton = MouseButton.NONE;
        context.activeIDPreviousFrame = 0;
        context.activeIDRetainOnFocusLoss = false;
        context.activeIDSeenThisFrame = false;
        context.activeIDSource = GuiInputSource.NONE;
        context.activeIDTimer = 0;
        context.activeIDWindow = null;
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

    public static void handleWindowDragging() {
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
                // TODO(ches) start dragging windows, check if we need to cancel moving
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
}
