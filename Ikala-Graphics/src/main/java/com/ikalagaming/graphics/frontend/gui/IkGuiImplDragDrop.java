package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Drag and drop.
 *
 * <p>Payload types are user-defined strings, strings starting with '_' are reserved for IkGui
 * internal types. Variants that take a class, or set an object without a type, use the fully
 * qualified class name as the type. Unlike ImGui, payload data is not copied, IkGui holds the
 * object that was passed to setDragDropPayload().
 */
@Slf4j
class IkGuiImplDragDrop {

    /** Internal payload type for docking windows, the payload is a {@link Window}. */
    static final String PAYLOAD_TYPE_WINDOW = "_IMWINDOW";

    /**
     * Time in milliseconds for drag-hold to activate items accepting the
     * ButtonFlags.INTERNAL_PRESSED_ON_DRAG_DROP_HOLD button behavior.
     */
    static final long DRAG_DROP_HOLD_TO_OPEN_TIMER = 700;

    static Context context;

    /**
     * Accept contents of a given type. If {@link DragDropFlags#ACCEPT_BEFORE_DELIVERY} is set you
     * can peek into the payload before the mouse button is released.
     *
     * @param aClass The class of the payload, whose name is the type.
     * @param dragDropFlags Flags for accepting the payload.
     * @param <T> The type of payload data.
     * @return The payload data, or null if there is nothing to accept (yet).
     * @see DragDropFlags
     */
    public static <T> T acceptDragDropPayload(@NonNull Class<T> aClass, int dragDropFlags) {
        final Payload payload = acceptDragDropPayloadInfo(aClass.getName(), dragDropFlags);
        if (payload == null || !aClass.isInstance(payload.data)) {
            return null;
        }
        return aClass.cast(payload.data);
    }

    /**
     * Accept contents of a given type. If {@link DragDropFlags#ACCEPT_BEFORE_DELIVERY} is set you
     * can peek into the payload before the mouse button is released.
     *
     * @param dataType The payload type, or null to accept any type.
     * @param dragDropFlags Flags for accepting the payload.
     * @param <T> The type of payload data.
     * @return The payload data, or null if there is nothing to accept (yet).
     * @see DragDropFlags
     */
    public static <T> T acceptDragDropPayload(String dataType, int dragDropFlags) {
        final Payload payload = acceptDragDropPayloadInfo(dataType, dragDropFlags);
        return payload == null ? null : payload.getData();
    }

    /**
     * Accept contents of a given type, returning the full payload. If {@link
     * DragDropFlags#ACCEPT_BEFORE_DELIVERY} is set you can peek into the payload before the mouse
     * button is released and use {@link Payload#isDelivery()} to check for delivery.
     *
     * @param dataType The payload type, or null to accept any type.
     * @param dragDropFlags Flags for accepting the payload.
     * @return The payload, or null if there is nothing to accept (yet).
     * @see DragDropFlags
     */
    static Payload acceptDragDropPayloadInfo(String dataType, int dragDropFlags) {
        final Payload payload = context.dragDropPayload;
        if (!context.dragDropActive) {
            IkGuiImplDebugTools.reportError(
                    log, "acceptDragDropPayload() called outside beginDragDropTarget()");
            return null;
        }
        if (payload.dataFrameCount == -1) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "acceptDragDropPayload() called with no payload, missing endDragDropTarget()?");
            return null;
        }
        if (dataType != null && !payload.isDataType(dataType)) {
            return null;
        }

        // Accept the smallest drag target bounding box, which allows us to nest drag targets
        // without ordering constraints. We currently accept 0 IDs as targets, but overlapping
        // targets require a unique ID to function.
        final boolean wasAcceptedPreviously =
                context.dragDropAcceptIDPrev == context.dragDropTargetID;
        final RectFloat rect = context.dragDropTargetRect;
        final float rectSurface = rect.getWidth() * rect.getHeight();
        if (rectSurface > context.dragDropAcceptIDCurrentRectSurface) {
            return null;
        }

        context.dragDropAcceptFlagsCurrent = dragDropFlags;
        context.dragDropAcceptIDCurrent = context.dragDropTargetID;
        context.dragDropAcceptIDCurrentRectSurface = rectSurface;

        // Render default drop visuals
        payload.preview = wasAcceptedPreviously;
        // The source can also inhibit the preview, useful for external sources that live 1 frame
        dragDropFlags |= context.dragDropSourceFlags & DragDropFlags.ACCEPT_NO_DRAW_DEFAULT_RECT;
        final boolean drawTargetRect =
                payload.preview && (dragDropFlags & DragDropFlags.ACCEPT_NO_DRAW_DEFAULT_RECT) == 0;
        if (drawTargetRect && context.dragDropTargetFullViewport != 0) {
            renderDragDropTargetRectForViewport(context.dragDropTargetFullViewport, rect);
        } else if (drawTargetRect) {
            renderDragDropTargetRectForItem(rect);
        }

        context.dragDropAcceptFrameCount = context.frameCount;
        if ((context.dragDropSourceFlags & DragDropFlags.SOURCE_EXTERN) != 0
                && context.dragDropMouseButton == MouseButton.NONE) {
            payload.delivery =
                    wasAcceptedPreviously && context.dragDropSourceFrameCount < context.frameCount;
        } else {
            // For external drag sources affecting OS window focus, it's easier to just test
            // !isMouseDown() instead of isMouseReleased()
            payload.delivery =
                    wasAcceptedPreviously && !context.io.getMouseDown(context.dragDropMouseButton);
        }
        if (!payload.delivery && (dragDropFlags & DragDropFlags.ACCEPT_BEFORE_DELIVERY) == 0) {
            return null;
        }
        return payload;
    }

    /**
     * Call after submitting an item which may be dragged. When this returns true, call
     * setDragDropPayload() exactly once, optionally render the payload description, then call
     * endDragDropSource().
     *
     * <p>If the item has an ID, this requires the item to be active (typically via
     * buttonBehavior()), and the mouse button that activated the item carries the drag. If the item
     * has no ID, {@link DragDropFlags#SOURCE_ALLOW_NULL_ID} is required and the left mouse button
     * is assumed.
     *
     * @param dragDropFlags Flags for the source.
     * @return True if dragging, and the payload should be set.
     * @see DragDropFlags
     */
    public static boolean beginDragDropSource(int dragDropFlags) {
        final Window window = context.windowCurrent;

        // While in the common case of dragging from an active ID we can tell the mouse button,
        // for external sources and items without IDs we assume the left mouse button.
        MouseButton mouseButton = MouseButton.LEFT;

        boolean sourceDragActive;
        int sourceID;
        int sourceParentID = 0;
        if ((dragDropFlags & DragDropFlags.SOURCE_EXTERN) == 0) {
            sourceID = context.lastItemData.id;
            if (sourceID != 0) {
                // Common path: items with an ID
                if (context.activeID != sourceID) {
                    return false;
                }
                if (context.activeIDMouseButton != MouseButton.NONE) {
                    mouseButton = context.activeIDMouseButton;
                }
                if (!context.io.getMouseDown(mouseButton) || window.skipItems) {
                    return false;
                }
                context.activeIDAllowOverlap = false;
            } else {
                // Uncommon path: items without an ID
                if (!context.io.getMouseDown(mouseButton) || window.skipItems) {
                    return false;
                }
                if ((context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_RECT) == 0
                        && (context.activeID == 0 || context.activeIDWindow != window)) {
                    return false;
                }

                // To use beginDragDropSource() on an item with no ID like text() or image(), you
                // need to use DragDropFlags.SOURCE_ALLOW_NULL_ID
                if ((dragDropFlags & DragDropFlags.SOURCE_ALLOW_NULL_ID) == 0) {
                    IkGuiImplDebugTools.reportError(
                            log,
                            "beginDragDropSource() on an item with no ID requires"
                                    + " DragDropFlags.SOURCE_ALLOW_NULL_ID");
                    return false;
                }

                // Build a throwaway ID based on the current ID stack and the window-relative
                // rectangle of the item. This won't survive repositioning/resizing the item, so
                // if it moves the drag is canceled. We don't need to clear the active ID since
                // releasing the button will early out and the ID will no longer be alive.
                sourceID = window.getIDFromRectangle(context.lastItemData.rect);
                context.lastItemData.id = sourceID;
                IkGuiInternal.keepAliveID(sourceID);
                final boolean isHovered =
                        IkGuiInternal.itemHoverable(
                                context.lastItemData.rect,
                                sourceID,
                                context.lastItemData.itemFlags);
                if (isHovered && context.io.getMouseClicked(mouseButton)) {
                    IkGuiInternal.setActiveID(sourceID, window);
                    IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
                }
                if (context.activeID == sourceID) {
                    // Allow the underlying item to display/return hovered during the mouse
                    // release frame, otherwise it flickers
                    context.activeIDAllowOverlap = isHovered;
                }
            }
            if (context.activeID != sourceID) {
                return false;
            }
            sourceParentID = window.idStack.peek();
            sourceDragActive = IkGuiImplUtils.isMouseDragging(mouseButton, -1.0f);

            // Disable navigation and key inputs while dragging, and cancel any existing requests
            IkGuiImplKeys.setActiveIDUsingAllKeyboardKeys();
        } else {
            sourceID = Hash.getID("#SourceExtern");
            sourceDragActive = true;
            mouseButton =
                    context.io.getMouseDown(MouseButton.LEFT) ? MouseButton.LEFT : MouseButton.NONE;
            IkGuiInternal.keepAliveID(sourceID);
            IkGuiInternal.setActiveID(sourceID, null);
        }

        if (context.dragDropWithinTarget) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't nest beginDragDropSource() and beginDragDropTarget()");
            return false;
        }
        if (!sourceDragActive) {
            return false;
        }

        // Activate drag and drop
        if (!context.dragDropActive) {
            clearDragDrop();
            final Payload payload = context.dragDropPayload;
            payload.sourceID = sourceID;
            payload.sourceParentID = sourceParentID;
            context.dragDropActive = true;
            context.dragDropSourceFlags = dragDropFlags;
            context.dragDropMouseButton = mouseButton;
            if (payload.sourceID == context.activeID) {
                context.activeIDNoClearOnFocusLost = true;
            }
        }
        context.dragDropSourceFrameCount = context.frameCount;
        context.dragDropWithinSource = true;

        // Unlike ImGui, we clear the hovered state before beginning the tooltip. Otherwise we would
        // be clearing the tooltip's last item data, and the source item's data would be restored
        // unchanged when the tooltip ends.
        if ((dragDropFlags & DragDropFlags.SOURCE_NO_DISABLE_HOVER) == 0
                && (dragDropFlags & DragDropFlags.SOURCE_EXTERN) == 0) {
            context.lastItemData.statusFlags &= ~ItemStatusFlags.HOVERED_RECT;
        }

        if ((dragDropFlags & DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP) == 0) {
            // The target can request that the source not display its tooltip. We can't just skip
            // the tooltip since the caller may be emitting contents.
            if (context.dragDropAcceptIDPrev != 0
                    && (context.dragDropAcceptFlagsPrev & DragDropFlags.ACCEPT_NO_PREVIEW_TOOLTIP)
                            != 0) {
                beginTooltipHidden();
            } else {
                IkGuiImplPopups.beginTooltip();
            }
        }

        return true;
    }

    /**
     * Call after submitting an item that may receive a payload. If this returns true, call
     * acceptDragDropPayload() and then endDragDropTarget().
     *
     * @return True if a payload is being dragged over the item.
     */
    public static boolean beginDragDropTarget() {
        // We don't use beginDragDropTargetCustom() because the last item data handles items that
        // push a temporary clip rectangle, and this is faster since it's frequently called.
        if (!context.dragDropActive) {
            return false;
        }

        final Window window = context.windowCurrent;
        if ((context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_RECT) == 0) {
            return false;
        }
        final Window hoveredWindow = context.windowHoveredUnderMovingWindow;
        if (hoveredWindow == null
                || window.rootWindowDockTree != hoveredWindow.rootWindowDockTree
                || window.skipItems) {
            return false;
        }

        final RectFloat displayRect =
                (context.lastItemData.statusFlags & ItemStatusFlags.HAS_DISPLAY_RECT) != 0
                        ? context.lastItemData.displayRect
                        : context.lastItemData.rect;
        int id = context.lastItemData.id;
        if (id == 0) {
            id = window.getIDFromRectangle(displayRect);
            IkGuiInternal.keepAliveID(id);
        }
        if (context.dragDropPayload.sourceID == id) {
            return false;
        }

        if (context.dragDropWithinTarget || context.dragDropWithinSource) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't nest beginDragDropSource() and beginDragDropTarget()");
            return false;
        }
        context.dragDropTargetRect.set(displayRect);
        context.dragDropTargetClipRect.set(
                (context.lastItemData.statusFlags & ItemStatusFlags.HAS_CLIP_RECT) != 0
                        ? context.lastItemData.clipRect
                        : window.rectCurrentClip);
        context.dragDropTargetID = id;
        context.dragDropTargetFullViewport = 0;
        context.dragDropWithinTarget = true;
        return true;
    }

    /**
     * Begin a drag and drop target over an arbitrary rectangle, rather than the last item.
     *
     * @param bb The bounding box of the target, in screen space.
     * @param id The ID of the target, must not be 0.
     * @return True if a payload is being dragged over the target.
     */
    static boolean beginDragDropTargetCustom(@NonNull RectFloat bb, int id) {
        if (!context.dragDropActive) {
            return false;
        }

        final Window window = context.windowCurrent;
        final Window hoveredWindow = context.windowHoveredUnderMovingWindow;
        if (hoveredWindow == null
                || window.rootWindowDockTree != hoveredWindow.rootWindowDockTree) {
            return false;
        }
        if (id == 0) {
            IkGuiImplDebugTools.reportError(
                    log, "beginDragDropTargetCustom() requires a non-zero ID");
            return false;
        }
        if (!IkGuiInternal.isMouseHoveringRect(
                        bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), true)
                || id == context.dragDropPayload.sourceID) {
            return false;
        }
        if (window.skipItems) {
            return false;
        }

        if (context.dragDropWithinTarget || context.dragDropWithinSource) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't nest beginDragDropSource() and beginDragDropTarget()");
            return false;
        }
        context.dragDropTargetRect.set(bb);
        context.dragDropTargetClipRect.set(window.rectCurrentClip);
        context.dragDropTargetID = id;
        context.dragDropTargetFullViewport = 0;
        context.dragDropWithinTarget = true;
        return true;
    }

    /**
     * Begin a drag and drop target covering a viewport. The hover test is left to the caller,
     * typically:
     *
     * <pre>{@code
     * if (!IkGui.isWindowHovered(HoveredFlags.ANY_WINDOW | HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM)
     *         && IkGuiInternal.beginDragDropTargetViewport(IkGui.getMainViewport(), null)) {
     * }</pre>
     *
     * @param viewport The viewport.
     * @param bb The bounding box of the target in screen space, or null to use the viewport work
     *     area.
     * @return True if a payload is being dragged over the viewport.
     */
    static boolean beginDragDropTargetViewport(@NonNull Viewport viewport, RectFloat bb) {
        if (!context.dragDropActive) {
            return false;
        }

        final RectFloat rect =
                bb != null ? new RectFloat(bb) : viewport.getWorkRect(new RectFloat(0, 0, 0, 0));
        final int id = viewport.id;
        if (context.mouseViewport != viewport
                || !IkGuiInternal.isMouseHoveringRect(
                        rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), false)
                || id == context.dragDropPayload.sourceID) {
            return false;
        }

        if (context.dragDropWithinTarget || context.dragDropWithinSource) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't nest beginDragDropSource() and beginDragDropTarget()");
            return false;
        }
        context.dragDropTargetRect.set(rect);
        context.dragDropTargetClipRect.set(rect);
        context.dragDropTargetID = id;
        context.dragDropTargetFullViewport = id;
        context.dragDropWithinTarget = true;
        return true;
    }

    /**
     * Begin a hidden tooltip window, which still accepts contents but is not displayed.
     *
     * @return True, currently always.
     */
    static boolean beginTooltipHidden() {
        final boolean result =
                IkGuiImplWindows.begin(
                        "##Tooltip_Hidden",
                        null,
                        WindowFlags.INTERNAL_TOOLTIP
                                | WindowFlags.NO_INPUTS
                                | WindowFlags.NO_TITLE_BAR
                                | WindowFlags.NO_MOVE
                                | WindowFlags.NO_RESIZE
                                | WindowFlags.NO_SAVED_SETTINGS
                                | WindowFlags.ALWAYS_AUTO_RESIZE);
        IkGuiImplPopups.setWindowHiddenAndSkipItemsForCurrentFrame(context.windowCurrent);
        return result;
    }

    /** Cancel any drag and drop operation, and clear the payload. */
    static void clearDragDrop() {
        IkGuiImplDebugTools.debugLog(DebugLogFlags.EVENT_ACTIVE_ID, "[dragdrop] ClearDragDrop()");
        context.dragDropActive = false;
        context.dragDropPayload.clear();
        context.dragDropAcceptFlagsCurrent = DragDropFlags.NONE;
        context.dragDropAcceptIDCurrent = 0;
        context.dragDropAcceptIDPrev = 0;
        context.dragDropAcceptIDCurrentRectSurface = Float.MAX_VALUE;
        context.dragDropAcceptFrameCount = -1;
    }

    /** Only call endDragDropSource() if beginDragDropSource() returns true. */
    public static void endDragDropSource() {
        if (!context.dragDropActive || !context.dragDropWithinSource) {
            IkGuiImplDebugTools.reportError(
                    log, "endDragDropSource() called without a matching beginDragDropSource()");
            return;
        }

        if ((context.dragDropSourceFlags & DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP) == 0) {
            IkGuiImplPopups.endTooltip();
        }

        // Discard the drag if setDragDropPayload() was not called
        if (context.dragDropPayload.dataFrameCount == -1) {
            clearDragDrop();
        }
        context.dragDropWithinSource = false;
    }

    /** Only call endDragDropTarget() if beginDragDropTarget() returns true. */
    public static void endDragDropTarget() {
        if (!context.dragDropActive || !context.dragDropWithinTarget) {
            IkGuiImplDebugTools.reportError(
                    log, "endDragDropTarget() called without a matching beginDragDropTarget()");
            return;
        }
        context.dragDropWithinTarget = false;

        // Clear drag and drop state right after delivery
        if (context.dragDropPayload.delivery) {
            clearDragDrop();
        }
    }

    /**
     * Peek directly into the current payload data from anywhere.
     *
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive.
     */
    public static <T> T getDragDropPayload() {
        final Payload payload = getDragDropPayloadInfo();
        return payload == null ? null : payload.getData();
    }

    /**
     * Peek directly into the current payload data from anywhere, if it has the given type.
     *
     * @param aClass The class of the payload, whose name is the type.
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive, or the payload
     *     is a different type.
     */
    public static <T> T getDragDropPayload(@NonNull Class<T> aClass) {
        final Payload payload = getDragDropPayloadInfo();
        if (payload == null
                || !payload.isDataType(aClass.getName())
                || !aClass.isInstance(payload.data)) {
            return null;
        }
        return aClass.cast(payload.data);
    }

    /**
     * Peek directly into the current payload data from anywhere, if it has the given type.
     *
     * @param dataType The payload type.
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive, or the payload
     *     is a different type.
     */
    public static <T> T getDragDropPayload(@NonNull String dataType) {
        final Payload payload = getDragDropPayloadInfo();
        if (payload == null || !payload.isDataType(dataType)) {
            return null;
        }
        return payload.getData();
    }

    /**
     * Peek directly into the current payload from anywhere. Use {@link Payload#isDataType(String)}
     * to check the type of payload.
     *
     * @return The payload, or null when drag and drop is finished or inactive.
     */
    public static Payload getDragDropPayloadInfo() {
        return context.dragDropActive && context.dragDropPayload.dataFrameCount != -1
                ? context.dragDropPayload
                : null;
    }

    /**
     * Whether a drag and drop operation is in progress.
     *
     * @return True if drag and drop is active.
     */
    static boolean isDragDropActive() {
        return context.dragDropActive;
    }

    /**
     * Whether a target accepted the payload last frame.
     *
     * @return True if drag and drop is active and the payload is being accepted by a target.
     */
    static boolean isDragDropPayloadBeingAccepted() {
        return context.dragDropActive && context.dragDropAcceptIDPrev != 0;
    }

    /**
     * Render the drop target highlight rectangle. Clipped then expanded, so there is a way to
     * visualize that a target is not entirely visible.
     *
     * @param bb The bounding box of the target.
     */
    static void renderDragDropTargetRectForItem(@NonNull RectFloat bb) {
        final Window window = context.windowCurrent;
        final RectFloat display = new RectFloat(bb);
        display.clipWith(context.dragDropTargetClipRect);
        final float padding = context.style.variable.dragDropTargetPadding;
        display.expand(padding, padding);
        final boolean pushClipRect = !window.rectCurrentClip.contains(display);
        if (pushClipRect) {
            window.drawList.pushClipRectFullScreen();
        }
        renderDragDropTargetRectEx(
                window.drawList, display, context.style.variable.dragDropTargetRounding);
        if (pushClipRect) {
            window.drawList.popClipRect();
        }
    }

    /**
     * Render the drop target highlight rectangle for a full viewport target.
     *
     * @param viewportID The viewport ID.
     * @param bb The bounding box of the target.
     */
    static void renderDragDropTargetRectForViewport(int viewportID, @NonNull RectFloat bb) {
        final Viewport viewport = IkGuiImplUtils.findViewportByID(viewportID);
        if (viewport == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Could not find drag and drop target viewport {}", viewportID);
            return;
        }
        final RectFloat padded = new RectFloat(bb);
        final float padding = context.style.variable.dragDropTargetPadding;
        padded.expand(-padding, -padding);
        renderDragDropTargetRectEx(
                IkGuiImplUtils.getForegroundDrawList(viewport),
                padded,
                context.style.variable.dragDropTargetRounding);
    }

    /**
     * Render a drop target highlight rectangle.
     *
     * @param drawList The draw list to render to.
     * @param bb The rectangle.
     * @param rounding The corner rounding.
     */
    static void renderDragDropTargetRectEx(
            @NonNull DrawList drawList, @NonNull RectFloat bb, float rounding) {
        drawList.addRectFilled(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                IkGuiImplUtils.getColor(ColorType.DRAG_DROP_TARGET_BACKGROUND),
                rounding);
        drawList.addRect(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                IkGuiImplUtils.getColor(ColorType.DRAG_DROP_TARGET),
                rounding,
                DrawFlags.ROUND_CORNERS_ALL,
                context.style.variable.dragDropTargetBorderSize);
    }

    /**
     * Set the payload using the fully qualified class name of the payload as the type. Call between
     * beginDragDropSource() and endDragDropSource().
     *
     * @param payload The payload, which must not be null.
     * @param condition {@link Condition#ALWAYS} (or NONE) to set the payload every frame, {@link
     *     Condition#ONCE} to only set it on the first frame of the drag.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(Object payload, @NonNull Condition condition) {
        if (payload == null) {
            IkGuiImplDebugTools.reportError(
                    log, "setDragDropPayload() requires a type when the payload is null");
            return false;
        }
        return setDragDropPayload(payload.getClass().getName(), payload, condition);
    }

    /**
     * Set the payload. Call between beginDragDropSource() and endDragDropSource().
     *
     * @param dataType The user-defined type. Strings starting with '_' are reserved for IkGui
     *     internal types.
     * @param payload The payload, which is held (not copied) by IkGui. May be null.
     * @param condition {@link Condition#ALWAYS} (or NONE) to set the payload every frame, {@link
     *     Condition#ONCE} to only set it on the first frame of the drag.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(
            @NonNull String dataType, Object payload, @NonNull Condition condition) {
        final Payload current = context.dragDropPayload;
        if (condition == Condition.NONE) {
            condition = Condition.ALWAYS;
        }
        if (condition != Condition.ALWAYS && condition != Condition.ONCE) {
            IkGuiImplDebugTools.reportError(
                    log, "setDragDropPayload() only supports Condition.ALWAYS or Condition.ONCE");
            return false;
        }
        if (current.sourceID == 0) {
            IkGuiImplDebugTools.reportError(
                    log, "setDragDropPayload() called outside beginDragDropSource()");
            return false;
        }

        if (condition == Condition.ALWAYS || current.dataFrameCount == -1) {
            current.dataType = dataType;
            current.data = payload;
        }
        current.dataFrameCount = context.frameCount;

        // Return whether the payload has been accepted
        return context.dragDropAcceptFrameCount == context.frameCount
                || context.dragDropAcceptFrameCount == context.frameCount - 1;
    }

    /**
     * Drag and drop bookkeeping at the start of a frame, before navigation is updated. Escape
     * cancels any drag in progress.
     */
    static void dragDropNewFrame() {
        context.dragDropAcceptIDPrev = context.dragDropAcceptIDCurrent;
        context.dragDropAcceptIDCurrent = 0;
        context.dragDropAcceptFlagsPrev = context.dragDropAcceptFlagsCurrent;
        context.dragDropAcceptFlagsCurrent = DragDropFlags.NONE;
        context.dragDropAcceptIDCurrentRectSurface = Float.MAX_VALUE;
        context.dragDropWithinSource = false;
        context.dragDropWithinTarget = false;
        context.dragDropHoldJustPressedID = 0;
        if (context.dragDropActive) {
            // Also works when there is no active ID (a leftover payload in progress)
            final int ownerID =
                    context.activeID != 0
                            ? context.activeID
                            : Hash.getID("##DragDropCancelHandler");
            if (IkGuiImplKeys.shortcut(KeyChord.of(Key.ESCAPE), InputFlags.ROUTE_GLOBAL, ownerID)) {
                IkGuiInternal.clearActiveID();
                clearDragDrop();
            }
        }
    }

    /**
     * Drag and drop bookkeeping at the end of a frame. Elapses the payload if it was delivered, or
     * the source stopped being submitted, and displays a fallback tooltip if the source tooltip is
     * missing.
     */
    static void dragDropEndFrame() {
        if (context.dragDropActive) {
            final boolean isDelivered = context.dragDropPayload.delivery;
            final boolean isElapsed =
                    context.dragDropSourceFrameCount + 1 < context.frameCount
                            && ((context.dragDropSourceFlags & DragDropFlags.PAYLOAD_AUTO_EXPIRE)
                                            != 0
                                    || context.dragDropMouseButton == MouseButton.NONE
                                    || !context.io.getMouseDown(context.dragDropMouseButton));
            if (isDelivered || isElapsed) {
                clearDragDrop();
            }
        }

        // Fallback for a missing source tooltip. To handle the source item disappearing, submit
        // the description tooltip from a single spot by reading the payload data, instead of in
        // the beginDragDropSource() block of the item.
        if (context.dragDropActive
                && context.dragDropSourceFrameCount + 1 < context.frameCount
                && (context.dragDropSourceFlags & DragDropFlags.SOURCE_NO_PREVIEW_TOOLTIP) == 0) {
            context.dragDropWithinSource = true;
            IkGuiImplPopups.setTooltip("...");
            context.dragDropWithinSource = false;
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDragDrop() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
