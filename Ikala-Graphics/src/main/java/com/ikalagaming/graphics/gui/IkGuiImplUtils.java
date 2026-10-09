package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
class IkGuiImplUtils {

    /**
     * Used to wrap a large frame count back to 0, which I like more than accidentally wrapping into
     * the negatives. We just need the numbers to be different each frame anyway, so this shouldn't
     * matter much.
     */
    private static final int FRAME_COUNT_CAP = 2_000_000_000;

    /**
     * How long we lock scrolling to the window that is being scrolled with the mouse wheel, in
     * milliseconds.
     */
    private static final long WINDOWS_MOUSE_WHEEL_SCROLL_LOCK_TIMER = 700;

    /** Sort child windows so that popups and tooltips are always above regular children. */
    private static final Comparator<Window> CHILD_WINDOW_COMPARATOR =
            Comparator.<Window>comparingInt(w -> w.flags & WindowFlags.INTERNAL_POPUP)
                    .thenComparingInt(w -> w.flags & WindowFlags.INTERNAL_TOOLTIP)
                    .thenComparingInt(w -> w.beginOrderWithinParent);

    static Context context;

    public static int applyGlobalAlpha(int color, boolean disabled) {
        int result = color;
        result = Color.multiplyAlpha(result, context.style.variable.alpha);
        if (disabled) {
            result = Color.multiplyAlpha(result, context.style.variable.disabledAlpha);
        }
        return result;
    }

    public static void beginDisabled(boolean disabled) {
        final boolean wasDisabled = (context.currentItemFlags & ItemFlags.DISABLED) != 0;
        if (!wasDisabled && disabled) {
            context.disabledAlphaBackup = context.style.variable.alpha;
            // Alpha is multiplied so a disabled item fades out
            context.style.variable.alpha *= context.style.variable.disabledAlpha;
        }
        if (wasDisabled || disabled) {
            context.currentItemFlags |= ItemFlags.DISABLED;
        }
        context.itemFlagsStack.push(context.currentItemFlags);
        context.disabledStackSize++;
    }

    /**
     * Calculate the size of some text, which may contain multiple lines.
     *
     * @param result Where to store the result.
     * @param text The text.
     * @param hideTextAfterDoubleHash Whether to ignore anything after "##".
     * @param wrapWidth The width to wrap text at, 0 or negative for no wrapping.
     */
    public static void calcTextSize(
            @NonNull Vector2f result,
            String text,
            boolean hideTextAfterDoubleHash,
            float wrapWidth) {
        final float fontSize = IkGuiInternal.getFontSize();
        String displayed = hideTextAfterDoubleHash ? Hash.getDisplayedText(text) : text;
        if (displayed == null || displayed.isEmpty()) {
            result.set(0.0f, fontSize);
            return;
        }
        if (wrapWidth > 0.0f) {
            displayed = IkGuiInternal.wrapText(displayed, wrapWidth);
        }

        float maxWidth = 0.0f;
        int lineCount = 0;
        int lineStart = 0;
        while (lineStart <= displayed.length()) {
            // Like upstream, a trailing newline doesn't add an empty line at the end
            if (lineStart == displayed.length() && lineCount > 0) {
                break;
            }
            int lineEnd = displayed.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = displayed.length();
            }
            maxWidth =
                    Math.max(
                            maxWidth,
                            DrawList.calcTextWidth(
                                    context.fontSize, displayed.substring(lineStart, lineEnd)));
            lineCount++;
            lineStart = lineEnd + 1;
        }

        result.set(IkGuiInternal.truncate(maxWidth + 0.999_99f), lineCount * fontSize);
    }

    /**
     * Calculate the default item width, based on the width set by pushItemWidth() or
     * setNextItemWidth().
     *
     * @return The item width.
     */
    public static float calculateItemWidth() {
        final Window window = context.windowCurrent;
        float width;
        if ((context.nextItemData.fieldFlags & NextItemFlags.HAS_WIDTH) != 0) {
            width = context.nextItemData.width;
        } else {
            width = window.currentItemWidth;
        }
        if (width < 0.0f) {
            final float regionAvailableX = getContentRegionAvailableX();
            width = Math.max(1.0f, regionAvailableX + width);
        }
        return IkGuiInternal.truncate(width);
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        if (in == null
                || out == null
                || in.length < 3
                || out.length < 3
                || !Color.hsvToColor(in, out)) {
            IkGuiImplDebugTools.reportError(
                    log, "colorConvertHSVtoRGB expects arrays with at least 3 elements");
        }
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        if (in == null
                || out == null
                || in.length < 3
                || out.length < 3
                || !Color.rgbTohsv(in, out)) {
            IkGuiImplDebugTools.reportError(
                    log, "colorConvertRGBtoHSV expects arrays with at least 3 elements");
        }
    }

    public static void colorConvertU32ToFloat4(int in, @NonNull Vector4f out) {
        final float scale = 1.0f / 255.0f;
        out.set(
                ((in >>> 24) & 0xFF) * scale,
                ((in >>> 16) & 0xFF) * scale,
                ((in >>> 8) & 0xFF) * scale,
                (in & 0xFF) * scale);
    }

    public static void endDisabled() {
        if (context.disabledStackSize <= 0) {
            IkGuiImplDebugTools.reportError(log, "Calling endDisabled() too many times!");
            return;
        }
        context.disabledStackSize--;
        final boolean wasDisabled = (context.currentItemFlags & ItemFlags.DISABLED) != 0;
        context.itemFlagsStack.pop();
        context.currentItemFlags = context.itemFlagsStack.peek();
        if (wasDisabled && (context.currentItemFlags & ItemFlags.DISABLED) == 0) {
            context.style.variable.alpha = context.disabledAlphaBackup;
        }
    }

    /**
     * Ends the frame, which is called automatically by render(). Finalizes the windows and input
     * state for the frame.
     */
    public static void endFrame() {
        // Don't process endFrame() multiple times
        if (context.frameCountEnded == context.frameCount) {
            return;
        }
        if (!context.withinFrameScope) {
            IkGuiImplDebugTools.reportError(log, "newFrame() must be called before endFrame()");
            return;
        }

        errorCheckEndFrameSanityChecks();
        IkGuiImplDebugTools.errorCheckEndFrameFinalizeErrorTooltip();

        // Notify the platform when our input method editor cursor has moved
        final PlatformImeData imeData = context.platformImeData;
        if (context.platformIO != null
                && context.platformIO.setImeDataFunction != null
                && !imeData.equals(context.platformImeDataPrevious)) {
            Viewport viewport = IkGuiImplViewports.findViewportByID(imeData.viewportID);
            if (viewport == null) {
                viewport = IkGuiImplViewports.getMainViewport();
            }
            context.platformIO.setImeDataFunction.accept(viewport, imeData);
        }
        context.wantTextInputNextFrame = imeData.wantTextInput ? 1 : 0;

        // Hide and unfocus implicit/fallback "Debug" window if it hasn't been used
        context.withinFrameScopeWithImplicitWindow = false;
        final Window current = context.windowCurrent;
        if (current != null && current.isFallbackWindow && !current.writeAccessed) {
            current.active = false;
            if (context.navFocusedWindow != null
                    && context.navFocusedWindow.rootWindow == current) {
                IkGuiInternal.focusWindow(null, WindowFocusRequestFlags.NONE);
            }
        }
        if (!context.windowStack.isEmpty()) {
            IkGuiImplWindows.end();
        }

        // Update navigation: Ctrl+Tab, wrap-around requests
        IkGuiImplNav.navEndFrame();

        // Update docking
        IkGuiImplDocking.dockContextEndFrame();

        IkGuiImplViewports.setCurrentViewport(null, null);

        // Drag and drop: elapse the payload, and add a fallback tooltip for missing sources
        IkGuiImplDragDrop.dragDropEndFrame();

        // End frame
        context.withinFrameScope = false;
        context.frameCountEnded = context.frameCount;

        // Initiate moving window + handle left-click and right-click focus
        IkGuiInternal.updateMouseMovingWindowEndFrame();

        // Update the user-facing viewport list (all viewports -> platform IO viewports, after
        // filtering out some)
        IkGuiImplViewports.updateViewportsEndFrame();

        // Sort the window list so that all child windows are after their parent. We cannot do that
        // in focusWindow() because children may not exist yet.
        List<Window> sorted = new ArrayList<>(context.windowDisplayOrder.size());
        for (Window window : context.windowDisplayOrder) {
            // If a child is active, its parent will add it
            if (window.active && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
                continue;
            }
            addWindowToSortBuffer(sorted, window);
        }
        if (sorted.size() != context.windowDisplayOrder.size()) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Window list mismatch when sorting ({} vs {}), parent/child links are probably wrong",
                    sorted.size(),
                    context.windowDisplayOrder.size());
        } else {
            context.windowDisplayOrder.clear();
            context.windowDisplayOrder.addAll(sorted);
        }
        context.io.metricsActiveWindows = context.windowActiveCount;

        // Clear input data for next frame
        context.io.mousePositionPrevious.set(context.io.mousePosition);
        context.io.appFocusLost = false;
        context.io.mouseWheel = 0.0f;
        context.io.mouseWheelH = 0.0f;
        context.io.inputQueueCharacters.setLength(0);
    }

    /**
     * Add a window and its active children (recursively) to the sorted list.
     *
     * @param sorted The list we are building.
     * @param window The window to add.
     */
    private static void addWindowToSortBuffer(
            @NonNull List<Window> sorted, @NonNull Window window) {
        sorted.add(window);
        if (window.active) {
            window.childWindows.sort(CHILD_WINDOW_COMPARATOR);
            for (Window child : window.childWindows) {
                if (child.active) {
                    addWindowToSortBuffer(sorted, child);
                }
            }
        }
    }

    /** Recover from invalid configuration at the start of the frame, logging errors. */
    private static void errorCheckNewFrameSanityChecks() {
        IkGuiImplViewports.errorCheckNewFrameSanityChecks();

        // We don't accept 100% silent recovery
        final IkIO io = context.io;
        if (io.configErrorRecovery
                && !io.configErrorRecoveryEnableAssert
                && !io.configErrorRecoveryEnableDebugLog
                && !io.configErrorRecoveryEnableTooltip
                && context.errorCallback == null) {
            io.configErrorRecoveryEnableDebugLog = true;
            IkGuiImplDebugTools.reportError(
                    log,
                    "Error recovery needs at least one of io.configErrorRecoveryEnableAssert,"
                            + " EnableDebugLog or EnableTooltip, or an error callback. Turning on"
                            + " the debug log.");
        }

        // Exactly one option of each of these groups must be selected
        final int[] colorEditMasks = {
            ColorEditFlags.DISPLAY_MASK,
            ColorEditFlags.DATA_TYPE_MASK,
            ColorEditFlags.PICKER_MASK,
            ColorEditFlags.INPUT_MASK
        };
        for (int mask : colorEditMasks) {
            final int selected = context.io.configColorEditFlags & mask;
            if (Integer.bitCount(selected) != 1) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "io.configColorEditFlags must have exactly one option of each group set");
                context.io.configColorEditFlags =
                        (context.io.configColorEditFlags & ~mask)
                                | (ColorEditFlags.DEFAULT_OPTIONS & mask);
            }
        }

        final int treeLinesFlags = context.style.variable.treeLinesFlags;
        if (treeLinesFlags != TreeNodeFlags.DRAW_LINES_NONE
                && treeLinesFlags != TreeNodeFlags.DRAW_LINES_FULL
                && treeLinesFlags != TreeNodeFlags.DRAW_LINES_TO_NODES) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "style.treeLinesFlags must be exactly one of TreeNodeFlags.DRAW_LINES_NONE,"
                            + " DRAW_LINES_FULL or DRAW_LINES_TO_NODES");
            context.style.variable.treeLinesFlags = TreeNodeFlags.DRAW_LINES_NONE;
        }

        if (context.io.mouseSingleClickDelay <= context.io.mouseDoubleClickTime) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "io.mouseSingleClickDelay ({}) must be larger than io.mouseDoubleClickTime"
                            + " ({})",
                    context.io.mouseSingleClickDelay,
                    context.io.mouseDoubleClickTime);
            context.io.mouseSingleClickDelay = context.io.mouseDoubleClickTime + 10;
        }
    }

    /** Recover from mismatched calls at the end of the frame, reporting errors. */
    private static void errorCheckEndFrameSanityChecks() {
        // Recover from errors
        if (context.io.configErrorRecovery) {
            IkGuiInternal.errorRecoveryTryToRecoverState(context.stackSizesInNewFrame);
        }

        // Only the implicit window should be left on the stack, which endFrame() will end
        final int expectedStackSize = context.withinFrameScopeWithImplicitWindow ? 1 : 0;
        if (context.windowStack.size() != expectedStackSize) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Expected {} windows on the stack at the end of the frame, but there were {}",
                    expectedStackSize,
                    context.windowStack.size());
        }
    }

    /** Starts a new frame. All IkGui calls for the frame need to happen after this. */
    public static void newFrame() {
        context.guiThread = Thread.currentThread();
        if (context.withinFrameScope && context.frameCountEnded != context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "Calling newFrame() without ending the previous frame, ending it now");
            endFrame();
        }

        // Check for common IO and configuration mistakes
        context.configFlagsLastFrame = context.configFlagsCurrentFrame;
        errorCheckNewFrameSanityChecks();
        context.configFlagsCurrentFrame = context.io.configFlags;

        // Updating frame time. Unlike ImGui we calculate the delta time ourselves, so this happens
        // before updating settings, which needs the delta time.
        final long lastFrameStart = context.frameStartTime;
        context.frameStartTime = context.timeSource.getAsLong();
        if (lastFrameStart > 0) {
            context.io.deltaTime = context.frameStartTime - lastFrameStart;
        }

        // Load settings on the first frame, save settings when modified (after a delay)
        IkGuiImplConfig.updateSettings();

        final long deltaTime = context.io.deltaTime;
        context.time += deltaTime;

        // Calculate the framerate as a rolling average over the last frames
        final float deltaSeconds = deltaTime / 1000.0f;
        final float[] secondsPerFrame = context.framerateSecondPerFrame;
        context.framerateSecondPerFrameAccumulator +=
                deltaSeconds - secondsPerFrame[context.framerateSecondPerFrameIndex];
        secondsPerFrame[context.framerateSecondPerFrameIndex] = deltaSeconds;
        context.framerateSecondPerFrameIndex =
                (context.framerateSecondPerFrameIndex + 1) % secondsPerFrame.length;
        context.framerateSecondPerFrameCount =
                Math.min(context.framerateSecondPerFrameCount + 1, secondsPerFrame.length);
        context.io.framerate =
                context.framerateSecondPerFrameAccumulator > 0.0f
                        ? 1.0f
                                / (context.framerateSecondPerFrameAccumulator
                                        / context.framerateSecondPerFrameCount)
                        : Float.MAX_VALUE;
        context.frameCount = (context.frameCount + 1) % FRAME_COUNT_CAP;
        context.sessionDate = context.platformIO.platformSessionDate;
        context.tooltipOverrideCount = 0;
        context.windowActiveCount = 0;
        context.menuIDsSubmittedThisFrame.clear();

        // Process the input queue, turn events into writes to the IO structure
        context.io.processInputEvents();

        // Update viewports (after processing the input queue, so the mouse hovered viewport is
        // set)
        IkGuiImplViewports.updateViewportsNewFrame();

        context.withinFrameScope = true;

        // Mark rendering data as invalid to prevent users who may have a handle on it from using
        // it, and reset the textures for the frame
        for (Viewport viewport : context.viewports) {
            viewport.drawData.valid = false;
            viewport.drawData.drawLists.clear();
        }
        context.drawTextures.clear();

        // Drag and drop keeps the source ID alive, so even if the source disappears our state is
        // consistent
        if (context.dragDropActive && context.dragDropPayload.sourceID == context.activeID) {
            IkGuiInternal.keepAliveID(context.dragDropPayload.sourceID);
        }

        // Update hovered ID data
        if (context.hoveredIDPreviousFrame == 0) {
            context.hoveredIDTimer = 0;
        }
        if (context.hoveredIDPreviousFrame == 0
                || (context.hoveredID != 0 && context.activeID == context.hoveredID)) {
            context.hoveredIDInactiveTimer = 0;
        }
        if (context.hoveredID != 0) {
            context.hoveredIDTimer += deltaTime;
        }
        if (context.hoveredID != 0 && context.activeID != context.hoveredID) {
            context.hoveredIDInactiveTimer += deltaTime;
        }
        // Count is locked while holding Ctrl
        if (!context.io.configDebugHighlightIdConflicts || !context.io.keyCtrl) {
            context.debugDrawIdConflictsID = 0;
        }
        if (context.io.configDebugHighlightIdConflicts
                && context.hoveredIDPreviousFrameItemCount > 1) {
            context.debugDrawIdConflictsID = context.hoveredIDPreviousFrame;
        }

        context.hoveredIDPreviousFrame = context.hoveredID;
        context.hoveredIDPreviousFrameItemCount = 0;
        context.hoveredID = 0;
        context.hoveredIDAllowOverlap = false;
        context.hoveredIDDisabled = false;

        // Clear the active ID if the item is not alive anymore
        if (context.activeID != 0
                && context.activeIDIsAlive != context.activeID
                && context.activeIDPreviousFrame == context.activeID) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_ACTIVE_ID,
                    "NewFrame(): ClearActiveID() 0x%08X because it isn't marked alive anymore!",
                    context.activeID);
            IkGuiInternal.clearActiveID();
        }

        // Update active ID data
        if (context.activeID != 0) {
            context.activeIDTimer += deltaTime;
        }
        if (context.activeID != 0 && context.activeID == context.lastActiveID) {
            context.lastActiveIDWasSelected = context.activeIDWasSelected;
            context.lastActiveIDWasSoleSelected = context.activeIDWasSoleSelected;
        }
        context.lastActiveIDTimer += deltaTime;
        context.activeIDPreviousFrame = context.activeID;
        context.activeIDIsAlive = 0;
        context.anyIDHasBeenEditedThisFrame = false;
        context.activeIDHasBeenEditedThisFrame = false;
        context.activeIDIsJustActivated = false;
        if (context.activeID == 0) {
            context.activeIDUsingNavDirMask = 0;
            context.activeIDUsingAllKeyboardKeys = false;
        }
        if (context.tempInputID != 0 && context.activeID != context.tempInputID) {
            context.tempInputID = 0;
        }
        if (context.inputTextReactivateID != 0
                && context.inputTextReactivateID != context.deactivatedItemData.id) {
            context.inputTextReactivateID = 0;
        }
        if (context.deactivatedItemData.elapsedFrame < context.frameCount) {
            context.deactivatedItemData.id = 0;
        }
        context.deactivatedItemData.isAlive = false;
        if (context.inputTextDeactivatedState.elapseFrame < context.frameCount) {
            context.inputTextDeactivatedState.id = 0;
        }

        // Record when we have been stationary, as this state is preserved while over the same item
        final long hoverStationaryDelay = context.style.variable.hoverStationaryDelay;
        if (context.hoverItemDelayID != 0
                && context.io.mouseStationaryTimer >= hoverStationaryDelay) {
            context.hoverItemUnlockedStationaryID = context.hoverItemDelayID;
        } else if (context.hoverItemDelayID == 0) {
            context.hoverItemUnlockedStationaryID = 0;
        }
        if (context.windowHovered != null
                && context.io.mouseStationaryTimer >= hoverStationaryDelay) {
            context.hoverWindowUnlockedStationaryID = context.windowHovered.id;
        } else if (context.windowHovered == null) {
            context.hoverWindowUnlockedStationaryID = 0;
        }

        // Update hover delay for isItemHovered() with delays and tooltips
        context.hoverItemDelayIDPreviousFrame = context.hoverItemDelayID;
        if (context.hoverItemDelayID != 0) {
            context.hoverItemDelayTimer += deltaTime;
            context.hoverItemDelayClearTimer = 0;
            context.hoverItemDelayID = 0;
        } else if (context.hoverItemDelayTimer > 0) {
            // This gives a little bit of leeway before clearing the hover timer, allowing the
            // mouse to cross gaps
            context.hoverItemDelayClearTimer += deltaTime;
            if (context.hoverItemDelayClearTimer >= Math.max(250, deltaTime * 2)) {
                context.hoverItemDelayTimer = 0;
                context.hoverItemDelayClearTimer = 0;
            }
        }

        // Update keyboard input state
        context.io.updateKeyboardInputs(deltaTime);
        IkGuiImplKeys.updateKeyOwnersAndRouting();

        // Drag and drop
        IkGuiImplDragDrop.dragDropNewFrame();

        // Clear transient table data
        IkGuiImplTables.tablesNewFrame();
        context.tooltipPreviousWindow = null;

        // Update keyboard/gamepad navigation
        IkGuiImplNav.navUpdate();

        // Update mouse input state
        context.io.updateMouseInputs(context.time, deltaTime);
        if (IkGuiImplUtils.isMousePosValid(
                context.io.mousePosition.x, context.io.mousePosition.y)) {
            context.mouseLastValidPosition.set(context.io.mousePosition);
        }
        // If the mouse moved, we re-enable mouse hovering in case it was disabled by navigation
        if (context.io.mouseDelta.x != 0.0f || context.io.mouseDelta.y != 0.0f) {
            context.navHighlightItemUnderNav = false;
        }
        // Clicking any mouse button also re-enables mouse hovering
        for (boolean clicked : context.io.mouseClicked) {
            if (clicked) {
                context.navHighlightItemUnderNav = false;
                break;
            }
        }

        // Undocking (needs to be before updateMouseMovingWindowNewFrame() so the window is
        // already offset and following the mouse on the detaching frame)
        IkGuiImplDocking.dockContextNewFrameUpdateUndocking();

        // Mark all windows as not visible
        for (Window window : context.windowDisplayOrder) {
            window.wasActive = window.active;
            window.active = false;
            window.writeAccessed = false;
            window.beginCountPreviousFrame = window.beginCount;
            window.beginCount = 0;
        }

        // Find the hovered window (needs to be before updateMouseMovingWindowNewFrame() so we fill
        // the window hovered under the moving window on the mouse release frame)
        IkGuiInternal.updateHoveredWindowAndCaptureFlags(context.io.mousePosition);

        // Handle user moving window with mouse (at the beginning of the frame to avoid input lag)
        IkGuiInternal.updateMouseMovingWindowNewFrame();

        // Background darkening/whitening
        if (IkGuiInternal.getTopmostPopupModal() != null
                || (context.navWindowingTarget != null
                        && context.navWindowingHighlightAlpha > 0.0f)) {
            context.dimBackgroundRatio =
                    Math.min(context.dimBackgroundRatio + deltaTime * 6.0f / 1000.0f, 1.0f);
        } else {
            context.dimBackgroundRatio =
                    Math.max(context.dimBackgroundRatio - deltaTime * 10.0f / 1000.0f, 0.0f);
        }

        context.mouseCursor = MouseCursor.ARROW;
        context.wantCaptureMouseNextFrame = -1;
        context.wantCaptureKeyboardNextFrame = -1;
        context.wantTextInputNextFrame = -1;

        // Platform IME data: reset for the frame
        context.platformImeDataPrevious.set(context.platformImeData);
        context.platformImeData.wantVisible = false;
        context.platformImeData.wantTextInput = false;

        // Mouse wheel scrolling
        updateMouseWheel();

        // Closing the focused window restores focus to the first active root window in
        // descending z-order
        if (context.navFocusedWindow != null && !context.navFocusedWindow.wasActive) {
            IkGuiInternal.focusTopMostWindowUnderOne(
                    null, null, WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD);
        }

        // No window should be open at the beginning of the frame. But in order to allow the user
        // to call newFrame() multiple times without calling render(), we are doing an explicit
        // clear.
        context.windowStack.clear();
        context.windowCurrent = null;
        context.currentTable = null;
        context.beginPopupStack.clear();
        context.itemFlagsStack.clear();
        context.itemFlagsStack.push(ItemFlags.DEFAULT);
        context.currentItemFlags = ItemFlags.DEFAULT;
        context.groupStack.clear();
        context.focusScopeStack.clear();
        context.currentFocusScopeID = 0;

        // Docking
        IkGuiImplDocking.dockContextNewFrameUpdateDocking();

        // Update debug features
        IkGuiImplDebugTools.debugToolsNewFrame();

        // Create the implicit/fallback window, which we only render if the user has added
        // something to it. This prevents IkGui calls from crashing when no window is active.
        context.withinFrameScopeWithImplicitWindow = true;
        IkGuiImplWindows.setNextWindowSize(400, 400, Condition.FIRST_USE_EVER);
        IkGuiImplWindows.begin("Debug##Default", null, WindowFlags.NONE);
        if (context.windowCurrent == null || !context.windowCurrent.isFallbackWindow) {
            IkGuiImplDebugTools.reportError(log, "Failed to set up fallback debug window");
        }

        // Store stack sizes
        context.errorCountCurrentFrame = 0;
        IkGuiInternal.errorRecoveryStoreState(context.stackSizesInNewFrame);
    }

    /**
     * Finalize the draw data for the frame. This ends the frame if that hasn't been done yet, then
     * builds the list of draw lists in the order they should be rendered.
     */
    public static void render() {
        if (context.frameCountEnded != context.frameCount) {
            endFrame();
        }
        if (context.frameCountRendered == context.frameCount) {
            return;
        }
        context.frameCountRendered = context.frameCount;
        context.io.metricsRenderWindows = 0;

        // Add the background draw list (for each active viewport)
        for (Viewport viewport : context.viewports) {
            IkGuiImplViewports.initViewportDrawData(viewport);
            if (viewport.backgroundDrawList != null
                    && viewport.backgroundDrawListLastFrameActive == context.frameCount) {
                viewport.drawData.drawLists.add(viewport.backgroundDrawList);
            }
        }

        // Dim the background behind the top-most visible modal, or the Ctrl+Tab target window
        final Window modalWindow = IkGuiImplPopups.getTopMostAndVisiblePopupModal();
        final Window windowingTarget = context.navWindowingTargetAnim;
        final boolean dimForWindowList =
                modalWindow == null && windowingTarget != null && windowingTarget.active;
        final Window dimBehindWindow =
                modalWindow != null
                        ? modalWindow.rootWindowDockTree
                        : dimForWindowList ? windowingTarget.rootWindowDockTree : null;
        final DrawList dimDrawList = context.dimBackgroundDrawList;
        dimDrawList.clear();
        if (dimBehindWindow != null && context.dimBackgroundRatio > 0.0f) {
            final RectFloat viewportRect = dimBehindWindow.viewport.getMainRect(new RectFloat());
            dimDrawList.addRectFilled(
                    viewportRect.getLeft(),
                    viewportRect.getTop(),
                    viewportRect.getRight(),
                    viewportRect.getBottom(),
                    getColor(
                            modalWindow != null
                                    ? ColorType.MODAL_WINDOWING_DIM_BACKGROUND
                                    : ColorType.NAV_WINDOWING_DIM_BACKGROUND,
                            context.dimBackgroundRatio));

            // Draw over sibling docking nodes in the same docking tree
            final Window dimmedWindow = modalWindow != null ? modalWindow : windowingTarget;
            if (dimmedWindow.rootWindow.dockIsActive) {
                final DrawList drawList =
                        findFrontMostVisibleChildWindow(dimmedWindow.rootWindowDockTree).drawList;
                drawList.channelsMerge();
                drawList.pushClipRect(
                        viewportRect.getLeft(),
                        viewportRect.getTop(),
                        viewportRect.getRight(),
                        viewportRect.getBottom());
                IkGuiInternal.renderRectFilledWithHole(
                        drawList,
                        dimmedWindow.rootWindowDockTree.getRect(),
                        dimmedWindow.rootWindow.getRect(),
                        getColor(
                                modalWindow != null
                                        ? ColorType.MODAL_WINDOWING_DIM_BACKGROUND
                                        : ColorType.NAV_WINDOWING_DIM_BACKGROUND,
                                context.dimBackgroundRatio),
                        0.0f);
                drawList.popClipRect();
            }
        }
        if (dimForWindowList && context.navWindowingHighlightAlpha > 0.0f) {
            // Draw a border around the Ctrl+Tab target window
            final Viewport viewport = windowingTarget.viewport;
            final float distance = IkGuiInternal.getFontSize();
            final RectFloat bb =
                    new RectFloat(
                            windowingTarget.position.x,
                            windowingTarget.position.y,
                            windowingTarget.position.x + windowingTarget.size.x,
                            windowingTarget.position.y + windowingTarget.size.y);
            bb.expand(distance, distance);
            if (bb.getWidth() >= viewport.size.x && bb.getHeight() >= viewport.size.y) {
                // If a window fits the entire viewport, adjust its highlight inward
                bb.expand(-distance - 1.0f, -distance - 1.0f);
            }
            final DrawList drawList = windowingTarget.drawList;
            drawList.pushClipRect(
                    viewport.position.x,
                    viewport.position.y,
                    viewport.position.x + viewport.size.x,
                    viewport.position.y + viewport.size.y);
            drawList.addRect(
                    bb.getLeft(),
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    getColor(ColorType.NAV_WINDOWING_HIGHLIGHT, context.navWindowingHighlightAlpha),
                    windowingTarget.rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    3.0f);
            drawList.popClipRect();
        }

        // Draw the dimming background on the other viewports than the one our window is in
        if (dimBehindWindow != null && context.dimBackgroundRatio > 0.0f) {
            final int dimColor =
                    getColor(
                            modalWindow != null
                                    ? ColorType.MODAL_WINDOWING_DIM_BACKGROUND
                                    : ColorType.NAV_WINDOWING_DIM_BACKGROUND,
                            context.dimBackgroundRatio);
            for (Viewport viewport : context.viewports) {
                if (viewport == dimBehindWindow.viewport) {
                    continue;
                }
                if (modalWindow != null
                        && viewport.window != null
                        && IkGuiInternal.isWindowAbove(viewport.window, modalWindow)) {
                    continue;
                }
                IkGuiImplViewports.getViewportForegroundDrawList(viewport)
                        .addRectFilled(
                                viewport.position.x,
                                viewport.position.y,
                                viewport.position.x + viewport.size.x,
                                viewport.position.y + viewport.size.y,
                                dimColor);
            }
        }

        // Add draw lists to render. Tooltips are drawn on a layer above other windows.
        for (Window window : context.windowDisplayOrder) {
            if (IkGuiInternal.isWindowActiveAndVisible(window)
                    && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
                if (window == dimBehindWindow && context.dimBackgroundRatio > 0.0f) {
                    window.viewport.drawData.drawLists.add(dimDrawList);
                }
                addWindowToDrawData(window, (window.flags & WindowFlags.INTERNAL_TOOLTIP) != 0);
            }
        }

        // Draw the software mouse cursor if requested by io.configMouseDrawCursor
        if (context.io.configMouseDrawCursor && context.mouseCursor != MouseCursor.NONE) {
            IkGuiImplMouseCursor.renderMouseCursor(
                    context.io.mousePosition.x,
                    context.io.mousePosition.y,
                    context.style.variable.mouseCursorScale,
                    context.mouseCursor,
                    IkGuiImplMouseCursor.FILL_COLOR,
                    IkGuiImplMouseCursor.BORDER_COLOR,
                    IkGuiImplMouseCursor.SHADOW_COLOR);
        }

        for (Viewport viewport : context.viewports) {
            // Flatten the layers
            final DrawData drawData = viewport.drawData;
            drawData.drawLists.addAll(viewport.drawDataBuilderTopLayer);
            viewport.drawDataBuilderTopLayer.clear();

            // Add the foreground draw list (for each active viewport)
            if (viewport.foregroundDrawList != null
                    && viewport.foregroundDrawListLastFrameActive == context.frameCount) {
                drawData.drawLists.add(viewport.foregroundDrawList);
            }

            // Finalize all the buffers for rendering
            for (DrawList drawList : drawData.drawLists) {
                drawList.prepareForRender();
            }
        }
    }

    /**
     * Find the front-most visible child window of a window, recursively.
     *
     * @param window The window.
     * @return The front-most visible descendant, or the window itself if there are none.
     */
    static Window findFrontMostVisibleChildWindow(@NonNull Window window) {
        for (int n = window.childWindows.size() - 1; n >= 0; --n) {
            final Window child = window.childWindows.get(n);
            if (IkGuiInternal.isWindowActiveAndVisible(child)) {
                return findFrontMostVisibleChildWindow(child);
            }
        }
        return window;
    }

    /**
     * Add a window and its visible children (recursively) to the draw data of their viewports.
     * Child windows may use a different viewport than their parent (e.g. an extruding menu).
     *
     * @param window The window to add.
     * @param topLayer Whether to add to the layer on top of other windows (for tooltips). This is
     *     locked by the root window.
     */
    private static void addWindowToDrawData(@NonNull Window window, boolean topLayer) {
        final Viewport viewport = window.viewport;
        if (viewport == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Window {} has no viewport when rendering", window.name);
            return;
        }
        context.io.metricsRenderWindows++;
        // Merge if the user forgot to merge back, also required for dock node host windows
        window.drawList.channelsMerge();
        if (topLayer) {
            viewport.drawDataBuilderTopLayer.add(window.drawList);
        } else {
            viewport.drawData.drawLists.add(window.drawList);
        }
        for (Window child : window.childWindows) {
            if (IkGuiInternal.isWindowActiveAndVisible(child)) {
                addWindowToDrawData(child, topLayer);
            }
        }
    }

    /**
     * Lock scrolling to a window for a short time, so that scrolling doesn't jump between windows
     * when the contents move under the mouse.
     *
     * @param window The window to lock to, or null to unlock.
     * @param wheelAmount How much the wheel moved, which affects the lock time.
     */
    private static void lockWheelingWindow(Window window, float wheelAmount) {
        if (context.windowWheeling != window) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_IO,
                    "[io] LockWheelingWindow() \"%s\"",
                    IkGuiImplDebugTools.nameOf(window));
        }
        if (window != null) {
            context.windowWheelingReleaseTimer =
                    Math.min(
                            context.windowWheelingReleaseTimer
                                    + (long)
                                            (Math.abs(wheelAmount)
                                                    * WINDOWS_MOUSE_WHEEL_SCROLL_LOCK_TIMER),
                            WINDOWS_MOUSE_WHEEL_SCROLL_LOCK_TIMER);
        } else {
            context.windowWheelingReleaseTimer = 0;
        }
        if (context.windowWheeling == window) {
            return;
        }
        context.windowWheeling = window;
        context.windowWheelingRefMousePosition.set(context.io.mousePosition);
        if (window == null) {
            context.windowWheelingStartFrame = -1;
            context.windowWheelingAxisAverage.set(0.0f, 0.0f);
        }
    }

    /**
     * For each axis, find the window in the hovered hierarchy that may want to use scrolling.
     *
     * @param wheelX The horizontal wheel amount.
     * @param wheelY The vertical wheel amount.
     * @return The best window to scroll, or null if there isn't a clear winner.
     */
    private static Window findBestWheelingWindow(float wheelX, float wheelY) {
        Window[] windows = {null, null};
        final float[] wheel = {wheelX, wheelY};
        for (int axis = 0; axis < 2; ++axis) {
            if (wheel[axis] == 0.0f) {
                continue;
            }
            Window window = context.windowHovered;
            windows[axis] = window;
            while ((window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
                // Bubble up into the parent window if a child window doesn't allow any scrolling
                // or has the no scroll with mouse flag
                final float scrollMax = axis == 0 ? window.scrollMax.x : window.scrollMax.y;
                final boolean hasScrolling = scrollMax != 0.0f;
                final boolean inputsDisabled =
                        (window.flags & WindowFlags.NO_SCROLL_WITH_MOUSE) != 0
                                && (window.flags & WindowFlags.NO_MOUSE_INPUTS) == 0;
                if (hasScrolling && !inputsDisabled) {
                    // Select this window
                    break;
                }
                window = window.parentWindow;
                windows[axis] = window;
            }
        }
        if (windows[0] == null && windows[1] == null) {
            return null;
        }

        // If there's only one window or only one axis then there's no ambiguity
        if (windows[0] == windows[1] || windows[0] == null || windows[1] == null) {
            return windows[1] != null ? windows[1] : windows[0];
        }

        // If the candidates are different windows we need to decide which one to prioritize
        // - First frame: only find a winner if one axis is zero.
        // - Subsequent frames: only find a winner when one is more than the other.
        if (context.windowWheelingStartFrame == -1) {
            context.windowWheelingStartFrame = context.frameCount;
        }
        if ((context.windowWheelingStartFrame == context.frameCount
                        && wheelX != 0.0f
                        && wheelY != 0.0f)
                || context.windowWheelingAxisAverage.x == context.windowWheelingAxisAverage.y) {
            context.windowWheelingWheelRemainder.set(wheelX, wheelY);
            return null;
        }
        return context.windowWheelingAxisAverage.x > context.windowWheelingAxisAverage.y
                ? windows[0]
                : windows[1];
    }

    /** Apply mouse wheel scrolling to the hovered window. Called by newFrame(). */
    private static void updateMouseWheel() {
        // Reset the locked window if we move the mouse or after the timer elapses
        if (context.windowWheeling != null) {
            context.windowWheelingReleaseTimer -= context.io.deltaTime;
            final float threshold = context.io.mouseDragThreshold;
            if (IkGui.isMousePosValid(context.io.mousePosition)
                    && context.io.mousePosition.distanceSquared(
                                    context.windowWheelingRefMousePosition)
                            > threshold * threshold) {
                context.windowWheelingReleaseTimer = 0;
            }
            if (context.windowWheelingReleaseTimer <= 0) {
                lockWheelingWindow(null, 0.0f);
            }
        }

        final Window mouseWindow =
                context.windowWheeling != null ? context.windowWheeling : context.windowHovered;
        if (mouseWindow == null || mouseWindow.collapsed) {
            return;
        }

        // Items can take the wheel away from window scrolling, e.g. with
        // setItemKeyOwner(Key.MOUSE_WHEEL_Y)
        final int ownerID = mouseWindow.id;
        float wheelX =
                IkGuiImplKeys.testKeyOwner(Key.MOUSE_WHEEL_X, ownerID)
                        ? context.io.mouseWheelH
                        : 0.0f;
        float wheelY =
                IkGuiImplKeys.testKeyOwner(Key.MOUSE_WHEEL_Y, ownerID)
                        ? context.io.mouseWheel
                        : 0.0f;
        if (context.windowWheeling != null) {
            IkGuiImplKeys.setKeyOwner(Key.MOUSE_WHEEL_X, ownerID, InputFlags.NONE);
            IkGuiImplKeys.setKeyOwner(Key.MOUSE_WHEEL_Y, ownerID, InputFlags.NONE);
        }

        // TODO(ches) ctrl+wheel to zoom windows
        if (context.io.keyCtrl) {
            return;
        }

        // Mouse wheel scrolling
        if (context.io.mouseWheelRequestAxisSwap) {
            wheelX = wheelY;
            wheelY = 0.0f;
        }

        // Maintain a rough average of moving magnitude on both axes
        final Vector2f average = context.windowWheelingAxisAverage;
        average.x = exponentialMovingAverage(average.x, Math.abs(wheelX), 30);
        average.y = exponentialMovingAverage(average.y, Math.abs(wheelY), 30);

        // In the rare situation where findBestWheelingWindow() had to defer the first frame of
        // wheeling due to an ambiguous main axis, reinject it now
        wheelX += context.windowWheelingWheelRemainder.x;
        wheelY += context.windowWheelingWheelRemainder.y;
        context.windowWheelingWheelRemainder.set(0.0f, 0.0f);
        if (wheelX == 0.0f && wheelY == 0.0f) {
            return;
        }

        // Mouse wheel scrolling: find target and apply
        final Window window =
                context.windowWheeling != null
                        ? context.windowWheeling
                        : findBestWheelingWindow(wheelX, wheelY);
        if (window == null
                || (window.flags & WindowFlags.NO_SCROLL_WITH_MOUSE) != 0
                || (window.flags & WindowFlags.NO_MOUSE_INPUTS) != 0) {
            return;
        }

        boolean doScrollX = wheelX != 0.0f && window.scrollMax.x != 0.0f;
        boolean doScrollY = wheelY != 0.0f && window.scrollMax.y != 0.0f;
        if (doScrollX && doScrollY) {
            if (average.x > average.y) {
                doScrollY = false;
            } else {
                doScrollX = false;
            }
        }
        final float fontSize = IkGuiInternal.getFontSize();
        if (doScrollX) {
            lockWheelingWindow(window, wheelX);
            final float maxStep = window.rectInner.getWidth() * 0.67f;
            final float scrollStep = IkGuiInternal.truncate(Math.min(2 * fontSize, maxStep));
            IkGuiInternal.setScrollX(window, window.scrollPosition.x - wheelX * scrollStep);
            context.windowWheelingScrolledFrame = context.frameCount;
        }
        if (doScrollY) {
            lockWheelingWindow(window, wheelY);
            final float maxStep = window.rectInner.getHeight() * 0.67f;
            final float scrollStep = IkGuiInternal.truncate(Math.min(5 * fontSize, maxStep));
            IkGuiInternal.setScrollY(window, window.scrollPosition.y - wheelY * scrollStep);
            context.windowWheelingScrolledFrame = context.frameCount;
        }
    }

    private static float exponentialMovingAverage(float average, float sample, int n) {
        average -= average / n;
        average += sample / n;
        return average;
    }

    public static Viewport findViewportByID(int id) {
        return IkGuiImplViewports.findViewportByID(id);
    }

    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return IkGuiImplViewports.getBackgroundDrawList(viewport);
    }

    public static String getClipboardText() {
        PlatformIO platformIO = context.platformIO;
        if (platformIO != null && platformIO.getClipboardTextFunction != null) {
            return platformIO.getClipboardTextFunction.get();
        }
        return context.clipboardHandlerData.toString();
    }

    public static int getColor(@NonNull ColorType type) {
        int result = context.style.color.get(type);

        for (var iterator = context.colorStack.iterator(); iterator.hasNext(); ) {
            ColorMod mod = iterator.next();
            if (mod.type() == type) {
                result = mod.color();
                break;
            }
        }

        // A flashing style color from the debug tools ignores pushed colors
        if (type == context.debugFlashStyleColor) {
            result = context.debugFlashStyleColorValue;
        }

        return result;
    }

    public static int getColor(@NonNull ColorType type, float alphaMultiplier) {
        int color = getColor(type);
        return Color.multiplyAlpha(color, alphaMultiplier);
    }

    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color, false);
    }

    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor, boolean disabled) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color, disabled);
    }

    public static Vector2f getContentRegionAvailable() {
        Vector2f region = new Vector2f();
        getContentRegionAvailable(region);
        return region;
    }

    /**
     * Fetch the space available from the current cursor position to the edge of the content region.
     *
     * @param region Where to store the result.
     */
    public static void getContentRegionAvailable(@NonNull Vector2f region) {
        final Window window = context.windowCurrent;
        final RectFloat bounds =
                context.currentTable != null ? window.rectWork : window.rectContent;
        region.set(
                bounds.getRight() - window.cursorPosition.x,
                bounds.getBottom() - window.cursorPosition.y);
    }

    public static float getContentRegionAvailableX() {
        final Window window = context.windowCurrent;
        final RectFloat bounds =
                context.currentTable != null ? window.rectWork : window.rectContent;
        return bounds.getRight() - window.cursorPosition.x;
    }

    public static float getContentRegionAvailableY() {
        final Window window = context.windowCurrent;
        final RectFloat bounds =
                context.currentTable != null ? window.rectWork : window.rectContent;
        return bounds.getBottom() - window.cursorPosition.y;
    }

    public static Vector2f getCursorPos() {
        Vector2f pos = new Vector2f();
        getCursorPos(pos);
        return pos;
    }

    /**
     * The cursor position in window-local coordinates, which is relative to the window position and
     * includes scrolling.
     *
     * @param pos Where to store the result.
     */
    public static void getCursorPos(@NonNull Vector2f pos) {
        final Window window = context.windowCurrent;
        pos.set(window.cursorPosition).sub(window.position).add(window.scrollPosition);
    }

    public static float getCursorPosX() {
        final Window window = context.windowCurrent;
        return window.cursorPosition.x - window.position.x + window.scrollPosition.x;
    }

    public static float getCursorPosY() {
        final Window window = context.windowCurrent;
        return window.cursorPosition.y - window.position.y + window.scrollPosition.y;
    }

    public static Vector2f getCursorScreenPos() {
        Vector2f pos = new Vector2f();
        getCursorScreenPos(pos);
        return pos;
    }

    public static void getCursorScreenPos(@NonNull Vector2f pos) {
        pos.set(context.windowCurrent.cursorPosition);
    }

    public static float getCursorScreenPosX() {
        return context.windowCurrent.cursorPosition.x;
    }

    public static float getCursorScreenPosY() {
        return context.windowCurrent.cursorPosition.y;
    }

    public static Vector2f getCursorStartPos() {
        Vector2f pos = new Vector2f();
        getCursorStartPos(pos);
        return pos;
    }

    public static void getCursorStartPos(@NonNull Vector2f pos) {
        final Window window = context.windowCurrent;
        pos.set(window.cursorStartPosition).sub(window.position);
    }

    public static float getCursorStartPosX() {
        final Window window = context.windowCurrent;
        return window.cursorStartPosition.x - window.position.x;
    }

    public static float getCursorStartPosY() {
        final Window window = context.windowCurrent;
        return window.cursorStartPosition.y - window.position.y;
    }

    public static DrawList getForegroundDrawList(Viewport viewport) {
        return IkGuiImplViewports.getForegroundDrawList(viewport);
    }

    public static int getFrameCount() {
        return context.frameCount;
    }

    /**
     * Calculate how many times a key press has repeated this frame, given a repeat delay and rate.
     *
     * @param key The key.
     * @param repeatDelay The delay before repeating starts, in milliseconds.
     * @param repeatRate The rate of repeating, in milliseconds.
     * @return The number of repeats that happened this frame, usually 0 or 1.
     */
    public static int getKeyPressedAmount(@NonNull Key key, long repeatDelay, long repeatRate) {
        final long t = context.io.getKeyDownDuration(key);
        if (t < 0) {
            return 0;
        }
        return IkGuiInternal.calcTypematicRepeatAmount(
                t - context.io.deltaTime, t, repeatDelay, repeatRate);
    }

    public static MouseCursor getMouseCursor() {
        return context.mouseCursor;
    }

    /**
     * Fetch the distance the mouse has been dragged since clicking, if it has moved further than
     * the threshold.
     *
     * @param output Where to store the result.
     * @param button The mouse button.
     * @param lockThreshold The threshold, negative to use the IO default.
     */
    public static void getMouseDragDelta(
            @NonNull Vector2f output, @NonNull MouseButton button, float lockThreshold) {
        if (lockThreshold < 0.0f) {
            lockThreshold = context.io.mouseDragThreshold;
        }
        final int index = button.index;
        if ((context.io.mouseDown[index] || context.io.mouseReleased[index])
                && context.io.mouseDragMaxDistanceSquare[index] >= lockThreshold * lockThreshold
                && IkGui.isMousePosValid(context.io.mousePosition)
                && IkGui.isMousePosValid(context.io.mouseClickedPosition[index])) {
            output.set(context.io.mousePosition).sub(context.io.mouseClickedPosition[index]);
            return;
        }
        output.set(0.0f, 0.0f);
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button, float lockThreshold) {
        Vector2f result = new Vector2f();
        getMouseDragDelta(result, button, lockThreshold);
        return result.x;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button, float lockThreshold) {
        Vector2f result = new Vector2f();
        getMouseDragDelta(result, button, lockThreshold);
        return result.y;
    }

    public static void getMousePos(@NonNull Vector2f output) {
        output.set(context.io.mousePosition);
    }

    public static void getMousePosOnOpeningCurrentPopup(@NonNull Vector2f output) {
        if (!context.beginPopupStack.isEmpty()) {
            output.set(context.beginPopupStack.getLast().mousePosition);
        } else {
            output.set(context.io.mousePosition);
        }
    }

    public static float getMousePosOnOpeningCurrentPopupX() {
        Vector2f result = new Vector2f();
        getMousePosOnOpeningCurrentPopup(result);
        return result.x;
    }

    public static float getMousePosOnOpeningCurrentPopupY() {
        Vector2f result = new Vector2f();
        getMousePosOnOpeningCurrentPopup(result);
        return result.y;
    }

    public static float getScrollMaxX() {
        return context.windowCurrent.scrollMax.x;
    }

    public static float getScrollMaxY() {
        return context.windowCurrent.scrollMax.y;
    }

    public static float getScrollX() {
        return context.windowCurrent.scrollPosition.x;
    }

    public static float getScrollY() {
        return context.windowCurrent.scrollPosition.y;
    }

    public static float getStyleVarFloat(@NonNull StyleVariable variable) {
        if (variable.getDimensions() != 1) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, trying to fetch 1 float",
                    variable,
                    variable.getDimensions());
            return 0;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} is a {} value, trying to fetch as float",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return 0;
        }
        return getStyleVarRaw(variable, 0);
    }

    public static void getStyleVarFloat2(
            @NonNull StyleVariable variable, @NonNull Vector2f target) {
        if (variable.getDimensions() != 2) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, trying to fetch 2 floats",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} is a {} value, trying to fetch as 2 floats",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        target.set(getStyleVarRaw(variable, 0), getStyleVarRaw(variable, 1));
    }

    public static int getStyleVarInt(@NonNull StyleVariable variable) {
        if (variable.getDimensions() != 1) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, trying to fetch 1 int",
                    variable,
                    variable.getDimensions());
            return 0;
        }
        if (variable.getExpectedType() != Integer.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} is a {} value, trying to fetch as int",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return 0;
        }
        return (int) getStyleVarRaw(variable, 0);
    }

    /**
     * Fetch the current value of a style variable component.
     *
     * @param variable The style variable.
     * @param component 0 for x (or single values), 1 for y.
     * @return The value.
     */
    private static float getStyleVarRaw(@NonNull StyleVariable variable, int component) {
        final StyleVariables style = context.style.variable;
        return switch (variable) {
            case ALPHA -> style.alpha;
            case BUTTON_TEXT_ALIGN -> style.buttonTextAlign.get(component);
            case CELL_PADDING -> style.cellPadding.get(component);
            case CHILD_BORDER_SIZE -> style.childBorderSize;
            case CHILD_ROUNDING -> style.childRounding;
            case COLOR_BUTTON_POSITION -> style.colorButtonPosition.getIntValue();
            case DISABLED_ALPHA -> style.disabledAlpha;
            case DOCKING_SEPARATOR_SIZE -> style.dockingSeparatorSize;
            case DRAG_DROP_TARGET_ROUNDING -> style.dragDropTargetRounding;
            case FRAME_BORDER_SIZE -> style.frameBorderSize;
            case FRAME_PADDING -> style.framePadding.get(component);
            case FRAME_ROUNDING -> style.frameRounding;
            case GRAB_MIN_SIZE -> style.grabMinSize;
            case GRAB_ROUNDING -> style.grabRounding;
            case IMAGE_BORDER_SIZE -> style.imageBorderSize;
            case IMAGE_ROUNDING -> style.imageRounding;
            case INDENT_SPACING -> style.indentSpacing;
            case ITEM_INNER_SPACING -> style.itemInnerSpacing.get(component);
            case ITEM_SPACING -> style.itemSpacing.get(component);
            case LOG_SLIDER_DEADZONE -> style.logSliderDeadzone;
            case MENU_ITEM_ROUNDING -> style.menuItemRounding;
            case POPUP_BORDER_SIZE -> style.popupBorderSize;
            case POPUP_ROUNDING -> style.popupRounding;
            case SCROLLBAR_PADDING -> style.scrollbarPadding;
            case SCROLLBAR_ROUNDING -> style.scrollbarRounding;
            case SCROLLBAR_SIZE -> style.scrollbarSize;
            case SELECTABLE_ROUNDING -> style.selectableRounding;
            case SELECTABLE_TEXT_ALIGN -> style.selectableTextAlign.get(component);
            case SEPARATOR_SIZE -> style.separatorSize;
            case SEPARATOR_TEXT_ALIGN -> style.separatorTextAlign.get(component);
            case SEPARATOR_TEXT_BORDER_SIZE -> style.separatorTextBorderSize;
            case SEPARATOR_TEXT_PADDING -> style.separatorTextPadding.get(component);
            case TAB_BAR_BORDER_SIZE -> style.tabBarBorderSize;
            case TAB_BAR_OVERLINE_SIZE -> style.tabBarOverlineSize;
            case TREE_LINES_ROUNDING -> style.treeLinesRounding;
            case TREE_LINES_SIZE -> style.treeLinesSize;
            case TAB_BORDER_SIZE -> style.tabBorderSize;
            case TAB_MIN_WIDTH_BASE -> style.tabMinWidthBase;
            case TAB_MIN_WIDTH_SHRINK -> style.tabMinWidthShrink;
            case TABLE_ANGLED_HEADERS_ANGLE -> style.tableAngledHeadersAngle;
            case TABLE_ANGLED_HEADERS_TEXT_ALIGN ->
                    style.tableAngledHeadersTextAlign.get(component);
            case TAB_ROUNDING -> style.tabRounding;
            case TOUCH_EXTRA_PADDING -> style.touchExtraPadding.get(component);
            case WINDOW_ALPHA_RADIUS -> style.windowAlphaRadius;
            case WINDOW_BORDER_HOVER_PADDING -> style.windowBorderHoverPadding;
            case WINDOW_BORDER_SIZE -> style.windowBorderSize;
            case WINDOW_MENU_BUTTON_POSITION -> style.windowMenuButtonPosition.getIntValue();
            case WINDOW_MIN_SIZE -> style.windowMinSize.get(component);
            case WINDOW_PADDING -> style.windowPadding.get(component);
            case WINDOW_ROUNDING -> style.windowRounding;
            case WINDOW_TITLE_ALIGN -> style.windowTitleAlign.get(component);
        };
    }

    /**
     * Set the current value of a style variable.
     *
     * @param variable The style variable.
     * @param x The value for single values, or the x component.
     * @param y The y component, ignored for single values.
     */
    private static void setStyleVarRaw(@NonNull StyleVariable variable, float x, float y) {
        final StyleVariables style = context.style.variable;
        switch (variable) {
            case ALPHA -> style.alpha = x;
            case BUTTON_TEXT_ALIGN -> style.buttonTextAlign.set(x, y);
            case CELL_PADDING -> style.cellPadding.set(x, y);
            case CHILD_BORDER_SIZE -> style.childBorderSize = x;
            case CHILD_ROUNDING -> style.childRounding = x;
            case COLOR_BUTTON_POSITION ->
                    style.colorButtonPosition = ColorButtonPosition.fromInteger((int) x);
            case DISABLED_ALPHA -> style.disabledAlpha = x;
            case DOCKING_SEPARATOR_SIZE -> style.dockingSeparatorSize = x;
            case DRAG_DROP_TARGET_ROUNDING -> style.dragDropTargetRounding = x;
            case FRAME_BORDER_SIZE -> style.frameBorderSize = x;
            case FRAME_PADDING -> style.framePadding.set(x, y);
            case FRAME_ROUNDING -> style.frameRounding = x;
            case GRAB_MIN_SIZE -> style.grabMinSize = x;
            case GRAB_ROUNDING -> style.grabRounding = x;
            case IMAGE_BORDER_SIZE -> style.imageBorderSize = x;
            case IMAGE_ROUNDING -> style.imageRounding = x;
            case INDENT_SPACING -> style.indentSpacing = x;
            case ITEM_INNER_SPACING -> style.itemInnerSpacing.set(x, y);
            case ITEM_SPACING -> style.itemSpacing.set(x, y);
            case LOG_SLIDER_DEADZONE -> style.logSliderDeadzone = x;
            case MENU_ITEM_ROUNDING -> style.menuItemRounding = x;
            case POPUP_BORDER_SIZE -> style.popupBorderSize = x;
            case POPUP_ROUNDING -> style.popupRounding = x;
            case SCROLLBAR_PADDING -> style.scrollbarPadding = x;
            case SCROLLBAR_ROUNDING -> style.scrollbarRounding = x;
            case SCROLLBAR_SIZE -> style.scrollbarSize = x;
            case SELECTABLE_ROUNDING -> style.selectableRounding = x;
            case SELECTABLE_TEXT_ALIGN -> style.selectableTextAlign.set(x, y);
            case SEPARATOR_SIZE -> style.separatorSize = x;
            case SEPARATOR_TEXT_ALIGN -> style.separatorTextAlign.set(x, y);
            case SEPARATOR_TEXT_BORDER_SIZE -> style.separatorTextBorderSize = x;
            case SEPARATOR_TEXT_PADDING -> style.separatorTextPadding.set(x, y);
            case TAB_BAR_BORDER_SIZE -> style.tabBarBorderSize = x;
            case TAB_BAR_OVERLINE_SIZE -> style.tabBarOverlineSize = x;
            case TREE_LINES_ROUNDING -> style.treeLinesRounding = x;
            case TREE_LINES_SIZE -> style.treeLinesSize = x;
            case TAB_BORDER_SIZE -> style.tabBorderSize = x;
            case TAB_MIN_WIDTH_BASE -> style.tabMinWidthBase = x;
            case TAB_MIN_WIDTH_SHRINK -> style.tabMinWidthShrink = x;
            case TABLE_ANGLED_HEADERS_ANGLE -> style.tableAngledHeadersAngle = x;
            case TABLE_ANGLED_HEADERS_TEXT_ALIGN -> style.tableAngledHeadersTextAlign.set(x, y);
            case TAB_ROUNDING -> style.tabRounding = x;
            case TOUCH_EXTRA_PADDING -> style.touchExtraPadding.set(x, y);
            case WINDOW_ALPHA_RADIUS -> style.windowAlphaRadius = x;
            case WINDOW_BORDER_HOVER_PADDING -> style.windowBorderHoverPadding = x;
            case WINDOW_BORDER_SIZE -> style.windowBorderSize = x;
            case WINDOW_MENU_BUTTON_POSITION ->
                    style.windowMenuButtonPosition = WindowMenuButtonPosition.fromInteger((int) x);
            case WINDOW_MIN_SIZE -> style.windowMinSize.set(x, y);
            case WINDOW_PADDING -> style.windowPadding.set(x, y);
            case WINDOW_ROUNDING -> style.windowRounding = x;
            case WINDOW_TITLE_ALIGN -> style.windowTitleAlign.set(x, y);
        }
    }

    public static boolean isAnyItemActive() {
        return context.activeID != 0;
    }

    public static boolean isAnyItemFocused() {
        return context.navID != 0 && context.navCursorVisible;
    }

    public static boolean isAnyItemHovered() {
        return context.hoveredID != 0 || context.hoveredIDPreviousFrame != 0;
    }

    public static boolean isAnyMouseDown() {
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            if (context.io.mouseDown[i]) {
                return true;
            }
        }
        return false;
    }

    public static boolean isItemActivated() {
        return context.activeID != 0
                && context.activeID == context.lastItemData.id
                && context.activeIDPreviousFrame != context.lastItemData.id;
    }

    public static boolean isItemActive() {
        return context.activeID != 0 && context.activeID == context.lastItemData.id;
    }

    public static boolean isItemClicked(@NonNull MouseButton button) {
        return isMouseClicked(button, false) && isItemHovered(HoveredFlags.NONE);
    }

    public static boolean isItemDeactivated() {
        if ((context.lastItemData.statusFlags & ItemStatusFlags.HAS_DEACTIVATED) != 0) {
            return (context.lastItemData.statusFlags & ItemStatusFlags.DEACTIVATED) != 0;
        }
        return context.lastItemData.id != 0
                && context.deactivatedItemData.id == context.lastItemData.id
                && context.deactivatedItemData.elapsedFrame >= context.frameCount;
    }

    public static boolean isItemDeactivatedAfterEdit() {
        return isItemDeactivated() && context.deactivatedItemData.hasBeenEditedBefore;
    }

    public static boolean isItemEdited() {
        return (context.lastItemData.statusFlags & ItemStatusFlags.EDITED) != 0;
    }

    public static boolean isItemFocused() {
        if (context.navID != context.lastItemData.id || context.navID == 0) {
            return false;
        }
        // Special handling for the dummy item after begin() which represents the title bar or
        // tab. When the window is collapsed that last item will never be overwritten.
        final Window window = context.windowCurrent;
        return !(context.lastItemData.id == window.id && window.writeAccessed);
    }

    /**
     * Check if the last item is hovered, and usable (not blocked by a popup, etc.).
     *
     * @param hoveredFlags Flags to modify the behavior.
     * @return True if the last item is hovered.
     * @see HoveredFlags
     */
    public static boolean isItemHovered(int hoveredFlags) {
        final Window window = context.windowCurrent;
        final LastItemData lastItem = context.lastItemData;

        final int id = lastItem.id;
        if (context.navHighlightItemUnderNav
                && context.navCursorVisible
                && (hoveredFlags & HoveredFlags.NO_NAV_OVERRIDE) == 0) {
            // Navigation highlight overrides mouse hovering
            if (!isItemFocused()) {
                return false;
            }
            if ((lastItem.itemFlags & ItemFlags.DISABLED) != 0
                    && (hoveredFlags & HoveredFlags.ALLOW_WHEN_DISABLED) == 0) {
                return false;
            }
            if ((hoveredFlags & HoveredFlags.FOR_TOOLTIP) != 0) {
                hoveredFlags =
                        applyHoverFlagsForTooltip(
                                hoveredFlags, context.style.variable.hoverFlagsForTooltipNav);
            }
        } else {
            // Test for bounding box overlap, as updated by itemAdd()
            final int statusFlags = lastItem.statusFlags;
            if ((statusFlags & ItemStatusFlags.HOVERED_RECT) == 0) {
                return false;
            }

            if ((hoveredFlags & HoveredFlags.FOR_TOOLTIP) != 0) {
                hoveredFlags =
                        applyHoverFlagsForTooltip(
                                hoveredFlags, context.style.variable.hoverFlagsForTooltipMouse);
            }

            // Done with rectangle culling so we can perform heavier checks now. Test if we are
            // hovering the right window (our window could be behind another window).
            if (context.windowHovered != window
                    && (statusFlags & ItemStatusFlags.HOVERED_WINDOW) == 0
                    && (hoveredFlags & HoveredFlags.ALLOW_WHEN_OVERLAPPED_BY_WINDOW) == 0) {
                return false;
            }

            // Test if another item is active (e.g. being dragged)
            if ((hoveredFlags & HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM) == 0
                    && context.activeID != 0
                    && context.activeID != id
                    && !context.activeIDAllowOverlap
                    && !context.activeIDFromShortcut) {
                // When the active ID is the move ID, it means that either:
                // - the user clicked on void or an item with no ID, which triggers moving the
                // window
                // - the user clicked a disabled item
                boolean cancelIsHovered =
                        !(context.activeID == window.idMove
                                && (id == 0 || context.activeIDDisabledId == id));
                // When the active ID is the tab ID, it means the user clicked the docking tab
                if (context.activeID == window.idTab) {
                    cancelIsHovered = false;
                }
                if (cancelIsHovered) {
                    return false;
                }
            }

            // Test if interactions on this window are blocked by an active popup or modal
            if (!IkGuiInternal.isWindowContentHoverable(window, hoveredFlags)
                    && (lastItem.itemFlags & ItemFlags.INTERNAL_NO_WINDOW_HOVERABLE_CHECK) == 0) {
                return false;
            }

            // Test if the item is disabled
            if ((lastItem.itemFlags & ItemFlags.DISABLED) != 0
                    && (hoveredFlags & HoveredFlags.ALLOW_WHEN_DISABLED) == 0) {
                return false;
            }

            // Special handling for calling after begin(), which represents the title bar. When the
            // window is skipped/collapsed, the last item will never be overwritten so we need to
            // detect the case.
            if (id == window.idMove && window.writeAccessed) {
                return false;
            }

            // Test if using allow overlap and overlapped
            if ((lastItem.itemFlags & ItemFlags.ALLOW_OVERLAP) != 0
                    && id != 0
                    && (hoveredFlags & HoveredFlags.ALLOW_WHEN_OVERLAPPED_BY_ITEM) == 0
                    && context.hoveredIDPreviousFrame != lastItem.id) {
                return false;
            }
        }

        // Handle hover delay
        final long delay = calcDelayFromHoveredFlags(hoveredFlags);
        if (delay > 0 || (hoveredFlags & HoveredFlags.STATIONARY) != 0) {
            final int hoverDelayID =
                    lastItem.id != 0
                            ? lastItem.id
                            : Hash.getID(
                                    Float.hashCode(lastItem.rect.getLeft()) * 31
                                            + Float.hashCode(lastItem.rect.getTop()),
                                    window.idStack.peek());
            if ((hoveredFlags & HoveredFlags.NO_SHARED_DELAY) != 0
                    && context.hoverItemDelayIDPreviousFrame != hoverDelayID) {
                context.hoverItemDelayTimer = 0;
            }
            context.hoverItemDelayID = hoverDelayID;

            // When changing hovered item we require a bit of stationary delay before activating
            // the hover timer, but once unlocked on a given item we also allow moving
            if ((hoveredFlags & HoveredFlags.STATIONARY) != 0
                    && context.hoverItemUnlockedStationaryID != hoverDelayID) {
                return false;
            }

            if (context.hoverItemDelayTimer < delay) {
                return false;
            }
        }

        return true;
    }

    private static long calcDelayFromHoveredFlags(int hoveredFlags) {
        if ((hoveredFlags & HoveredFlags.DELAY_NORMAL) != 0) {
            return context.style.variable.hoverDelayNormal;
        }
        if ((hoveredFlags & HoveredFlags.DELAY_SHORT) != 0) {
            return context.style.variable.hoverDelayShort;
        }
        return 0;
    }

    private static int applyHoverFlagsForTooltip(int userFlags, int sharedFlags) {
        // Allow instance flags to override shared flags
        final int delayFlags =
                HoveredFlags.DELAY_NONE | HoveredFlags.DELAY_SHORT | HoveredFlags.DELAY_NORMAL;
        if ((userFlags & delayFlags) != 0) {
            sharedFlags &= ~delayFlags;
        }
        return userFlags | sharedFlags;
    }

    public static boolean isItemToggledOpen() {
        return (context.lastItemData.statusFlags & ItemStatusFlags.TOGGLED_OPEN) != 0;
    }

    public static boolean isItemVisible() {
        return (context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) != 0;
    }

    public static boolean isKeyDown(@NonNull Key key) {
        return IkGuiImplKeys.isKeyDown(key, KeyRoutingData.KEY_OWNER_ANY);
    }

    public static boolean isKeyPressed(@NonNull Key key, boolean repeat) {
        return IkGuiImplKeys.isKeyPressed(
                key, repeat ? InputFlags.REPEAT : InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY);
    }

    public static boolean isKeyReleased(@NonNull Key key) {
        return IkGuiImplKeys.isKeyReleased(key, KeyRoutingData.KEY_OWNER_ANY);
    }

    public static boolean isMouseClicked(@NonNull MouseButton button, boolean repeat) {
        return isMouseClicked(
                button, repeat ? InputFlags.REPEAT : InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * Check if a mouse button was clicked this frame, if the button is available to the owner.
     *
     * @param button The mouse button.
     * @param inputFlags InputFlags.REPEAT to also return true while the button repeats.
     * @param ownerID The owner ID, KEY_OWNER_ANY to accept any owner, or KEY_OWNER_NO_OWNER to
     *     require that nothing owns the button.
     * @return True if the button was clicked.
     */
    public static boolean isMouseClicked(@NonNull MouseButton button, int inputFlags, int ownerID) {
        // In theory this is already covered by the duration, but testing it allows eating clicks
        if (!context.io.getMouseDown(button)) {
            return false;
        }
        final long t = context.io.getMouseDownDuration(button);
        if (t < 0) {
            return false;
        }
        if ((inputFlags & ~InputFlags.REPEAT) != 0) {
            log.warn("Unsupported flags passed to isMouseClicked()");
        }
        final boolean repeat = (inputFlags & InputFlags.REPEAT) != 0;
        // Durations are in whole milliseconds, so they can stay at 0 for several fast frames.
        // Check whether the button went down this frame instead.
        boolean pressed = context.io.mouseDownDurationPrevious[button.index] < 0;
        if (!pressed && repeat) {
            pressed =
                    IkGuiInternal.calcTypematicRepeatAmount(
                                    t - context.io.deltaTime,
                                    t,
                                    context.io.keyRepeatDelay,
                                    context.io.keyRepeatRate)
                            > 0;
        }
        if (!pressed) {
            return false;
        }
        return IkGuiImplKeys.testKeyOwner(Key.fromMouseButton(button), ownerID);
    }

    public static boolean isMouseDoubleClicked(@NonNull MouseButton button) {
        return isMouseDoubleClicked(button, KeyRoutingData.KEY_OWNER_ANY);
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
        return context.io.getMouseClickedCount(button) == 2
                && IkGuiImplKeys.testKeyOwner(Key.fromMouseButton(button), ownerID);
    }

    public static boolean isMouseDown(@NonNull MouseButton button) {
        return isMouseDown(button, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * Check if a mouse button is held down, if the button is available to the owner.
     *
     * @param button The mouse button.
     * @param ownerID The owner ID, or KEY_OWNER_ANY to accept any owner.
     * @return True if the button is down.
     */
    public static boolean isMouseDown(@NonNull MouseButton button, int ownerID) {
        return context.io.getMouseDown(button)
                && IkGuiImplKeys.testKeyOwner(Key.fromMouseButton(button), ownerID);
    }

    public static boolean isMouseDragging(@NonNull MouseButton button, float lockThreshold) {
        if (!context.io.getMouseDown(button)) {
            return false;
        }
        return IkGuiInternal.isMouseDragPastThreshold(button, lockThreshold);
    }

    public static boolean isMouseHoveringRect(
            float minX, float minY, float maxX, float maxY, boolean clip) {
        return IkGuiInternal.isMouseHoveringRect(minX, minY, maxX, maxY, clip);
    }

    public static boolean isMousePosValid(float x, float y) {
        final float MOUSE_INVALID = -256_000.0f;
        return x >= MOUSE_INVALID && y >= MOUSE_INVALID;
    }

    public static boolean isMouseReleased(@NonNull MouseButton button) {
        return isMouseReleased(button, KeyRoutingData.KEY_OWNER_ANY);
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
        return context.io.getMouseReleased(button)
                && IkGuiImplKeys.testKeyOwner(Key.fromMouseButton(button), ownerID);
    }

    /**
     * Check if the mouse button was released a certain time ago, which is true for a single frame.
     * Prefer getItemClickedCountWithSingleClickDelay().
     *
     * @param button The mouse button.
     * @param delay The delay after the release, in milliseconds, or negative to use
     *     io.mouseSingleClickDelay.
     * @return True on the frame where the delay passes, if the button wasn't pressed again.
     */
    public static boolean isMouseReleasedWithDelay(@NonNull MouseButton button, long delay) {
        if (isMouseDown(button)) {
            return false;
        }
        if (delay < 0) {
            delay = context.io.mouseSingleClickDelay;
        }
        final long timeSinceRelease = context.time - context.io.mouseReleasedTime[button.index];
        return timeSinceRelease - context.io.deltaTime < delay && timeSinceRelease >= delay;
    }

    /**
     * Tell single clicks and double clicks on the last item apart. Returns 1 for a single click,
     * but only after a delay of io.mouseSingleClickDelay after the release, and 2 or more right
     * away for double clicks and further repeated clicks.
     *
     * <p>To check whether the item was selected at the time of the click, before the delay, use
     * context.lastActiveIDWasSelected or context.lastActiveIDWasSoleSelected. For example, to
     * rename on a delayed click like a file browser:
     *
     * <pre>{@code
     * int clickCount = IkGui.getItemClickedCountWithSingleClickDelay(MouseButton.LEFT);
     * if (clickCount == 1 && IkGui.getContext().lastActiveIDWasSoleSelected) {
     *     startRename();
     * }
     * if (clickCount == 2) {
     *     open();
     * }
     * }</pre>
     *
     * @param button The mouse button.
     * @param delay The delay after the release, in milliseconds, or negative to use
     *     io.mouseSingleClickDelay. It's at least slightly longer than io.mouseDoubleClickTime.
     * @return 1 for a delayed single click, 2 or more for repeated clicks, otherwise 0.
     */
    public static int getItemClickedCountWithSingleClickDelay(
            @NonNull MouseButton button, long delay) {
        // Double clicks and subsequent clicks
        final int clickedCount = context.io.mouseClickedCount[button.index];
        if (clickedCount >= 2 && isItemClicked(button)) {
            return clickedCount;
        }

        // A single click, delayed
        int id = context.lastItemData.id;
        if (id == 0) {
            id = lastItemOverlayButtonForNullID(button);
        }
        if (context.lastActiveID == id) {
            if (delay >= 0) {
                delay = Math.max(delay, context.io.mouseDoubleClickTime + 10);
            }
            if (isMouseReleasedWithDelay(button, delay)
                    && context.io.mouseClickedLastCount[button.index] == 1) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Make an item without an ID clickable, by giving it a throwaway ID based on its rectangle and
     * acting like a button that covers it.
     *
     * @param button The mouse button.
     * @return The ID used for the item.
     */
    private static int lastItemOverlayButtonForNullID(@NonNull MouseButton button) {
        final Window window = context.windowCurrent;
        final int id = window.getIDFromRectangle(context.lastItemData.rect);
        if (context.io.getMouseClicked(button)
                && IkGuiInternal.itemHoverable(
                        context.lastItemData.rect, id, context.lastItemData.itemFlags)) {
            IkGuiInternal.setActiveID(id, window);
            IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
        } else if (context.activeID == id) {
            IkGuiInternal.keepAliveID(id);
            if (!context.io.getMouseDown(button)) {
                IkGuiInternal.clearActiveID();
            }
        }
        return id;
    }

    public static boolean isRectVisible(float width, float height) {
        final Window window = context.windowCurrent;
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        return window.rectCurrentClip.overlaps(new RectFloat(x, y, x + width, y + height));
    }

    public static boolean isRectVisible(float minX, float minY, float maxX, float maxY) {
        return context.windowCurrent.rectCurrentClip.overlaps(
                new RectFloat(minX, minY, maxX, maxY));
    }

    public static boolean isWindowAppearing() {
        return context.windowCurrent.appearing;
    }

    public static boolean isWindowCollapsed() {
        return context.windowCurrent.collapsed;
    }

    public static boolean isWindowDocked() {
        return context.windowCurrent.dockIsActive;
    }

    /**
     * Check if the current window is focused, or its root/child depending on flags.
     *
     * @param focusedFlags Flags to modify which windows we consider.
     * @return True if focused.
     * @see FocusedFlags
     */
    public static boolean isWindowFocused(int focusedFlags) {
        final Window referenceWindow = context.navFocusedWindow;
        Window currentWindow = context.windowCurrent;

        if (referenceWindow == null) {
            return false;
        }
        if ((focusedFlags & FocusedFlags.ANY_WINDOW) != 0) {
            return true;
        }

        final boolean popupHierarchy = (focusedFlags & FocusedFlags.NO_POPUP_HIERARCHY) == 0;
        final boolean dockHierarchy = (focusedFlags & FocusedFlags.DOCK_HIERARCHY) != 0;
        if ((focusedFlags & FocusedFlags.ROOT_WINDOW) != 0) {
            currentWindow =
                    IkGuiInternal.getCombinedRootWindow(
                            currentWindow, popupHierarchy, dockHierarchy);
        }

        if ((focusedFlags & FocusedFlags.CHILD_WINDOWS) != 0) {
            return IkGuiInternal.isWindowChildOf(
                    referenceWindow, currentWindow, popupHierarchy, dockHierarchy);
        }
        return referenceWindow == currentWindow;
    }

    /**
     * Check if the current window is hovered, and hoverable (not blocked by a popup/modal).
     *
     * @param hoveredFlags Flags to modify which windows we consider.
     * @return True if hovered.
     * @see HoveredFlags
     */
    public static boolean isWindowHovered(int hoveredFlags) {
        final Window referenceWindow = context.windowHovered;
        Window currentWindow = context.windowCurrent;
        if (referenceWindow == null) {
            return false;
        }

        if ((hoveredFlags & HoveredFlags.ANY_WINDOW) == 0) {
            final boolean popupHierarchy = (hoveredFlags & HoveredFlags.NO_POPUP_HIERARCHY) == 0;
            final boolean dockHierarchy = (hoveredFlags & HoveredFlags.DOCK_HIERARCHY) != 0;
            if ((hoveredFlags & HoveredFlags.ROOT_WINDOW) != 0) {
                currentWindow =
                        IkGuiInternal.getCombinedRootWindow(
                                currentWindow, popupHierarchy, dockHierarchy);
            }

            final boolean result;
            if ((hoveredFlags & HoveredFlags.CHILD_WINDOWS) != 0) {
                result =
                        IkGuiInternal.isWindowChildOf(
                                referenceWindow, currentWindow, popupHierarchy, dockHierarchy);
            } else {
                result = referenceWindow == currentWindow;
            }
            if (!result) {
                return false;
            }
        }

        if (!IkGuiInternal.isWindowContentHoverable(referenceWindow, hoveredFlags)) {
            return false;
        }
        if ((hoveredFlags & HoveredFlags.ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM) == 0
                && context.activeID != 0
                && !context.activeIDAllowOverlap
                && context.activeID != referenceWindow.idMove) {
            return false;
        }

        // When changing hovered window we require a bit of stationary delay before activating
        // hover timer
        return (hoveredFlags & HoveredFlags.STATIONARY) == 0
                || context.hoverWindowUnlockedStationaryID == referenceWindow.id;
    }

    public static void popID() {
        final Window window = context.windowCurrent;
        if (window.idStack.size() <= 1) {
            IkGuiImplDebugTools.reportError(log, "Calling popID() too many times!");
            return;
        }
        window.idStack.pop();
    }

    public static void popItemFlag() {
        if (context.itemFlagsStack.size() <= 1) {
            IkGuiImplDebugTools.reportError(log, "Calling popItemFlag() too many times!");
            return;
        }
        context.itemFlagsStack.pop();
        context.currentItemFlags = context.itemFlagsStack.peek();
    }

    public static void popStyleColor() {
        try {
            context.colorStack.pop();
        } catch (NoSuchElementException ignored) {
            IkGuiImplDebugTools.reportError(
                    log, "Trying to pop more style colors than we have pushed");
        }
    }

    public static void popStyleColor(int count) {
        try {
            for (int i = 0; i < count; ++i) {
                context.colorStack.pop();
            }
        } catch (NoSuchElementException ignored) {
            IkGuiImplDebugTools.reportError(
                    log, "Trying to pop more style colors (pop {}) than we have pushed", count);
        }
    }

    public static void popStyleVar() {
        popStyleVar(1);
    }

    public static void popStyleVar(int count) {
        for (int i = 0; i < count; ++i) {
            if (context.styleVariableStack.isEmpty()) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Trying to pop more style variables (pop {}) than we have pushed",
                        count);
                return;
            }
            // We stored the old values, so restore them
            StyleMod backup = context.styleVariableStack.pop();
            setStyleVarRaw(backup.type(), backup.x(), backup.y());
        }
    }

    public static int getID(String name) {
        return context.windowCurrent.getID(name);
    }

    public static int getID(int value) {
        return context.windowCurrent.getID(value);
    }

    public static int pushID(int id) {
        final Window window = context.windowCurrent;
        final int result = window.getID(id);
        window.idStack.push(result);
        return result;
    }

    public static int pushID(String name) {
        final Window window = context.windowCurrent;
        final int result = window.getID(name);
        window.idStack.push(result);
        return result;
    }

    public static void pushItemFlag(int option, boolean enabled) {
        int itemFlags = context.currentItemFlags;
        if (enabled) {
            itemFlags |= option;
        } else {
            itemFlags &= ~option;
        }
        context.currentItemFlags = itemFlags;
        context.itemFlagsStack.push(itemFlags);
    }

    public static void pushStyleColor(@NonNull ColorType type, int rgba) {
        context.colorStack.push(new ColorMod(type, rgba));
    }

    public static void pushStyleVarFloat(@NonNull StyleVariable variable, float value) {
        if (variable.getDimensions() != 1) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, 1 float provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} expects a {} value, Float provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected float range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        pushStyleVarRaw(variable, value, 0);
    }

    public static void pushStyleVarFloat2(@NonNull StyleVariable variable, float x, float y) {
        if (variable.getDimensions() != 2) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, 2 floats provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} expects {} values, Floats provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (x < variable.getMinValue() || x > variable.getMaxValue()) {
            log.warn(
                    "Variable {} x value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        if (y < variable.getMinValue() || y > variable.getMaxValue()) {
            log.warn(
                    "Variable {} y value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        pushStyleVarRaw(variable, x, y);
    }

    /**
     * Temporarily change one component of a style variable that has 2 floats, keeping the other.
     *
     * @param variable The style variable, which must have 2 float components.
     * @param component 0 for x, 1 for y.
     * @param value The new value of the component.
     */
    private static void pushStyleVarComponent(
            @NonNull StyleVariable variable, int component, float value) {
        if (variable.getDimensions() != 2 || variable.getExpectedType() != Float.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} is not 2 floats, so one component can't be pushed",
                    variable);
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} {} value outside the expected float range({}, {})",
                    variable,
                    component == 0 ? "x" : "y",
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        final float x = component == 0 ? value : getStyleVarRaw(variable, 0);
        final float y = component == 1 ? value : getStyleVarRaw(variable, 1);
        pushStyleVarRaw(variable, x, y);
    }

    /**
     * Temporarily change the x component of a style variable that has 2 floats, keeping y.
     *
     * @param variable The style variable.
     * @param x The new x value.
     */
    public static void pushStyleVarX(@NonNull StyleVariable variable, float x) {
        pushStyleVarComponent(variable, 0, x);
    }

    /**
     * Temporarily change the y component of a style variable that has 2 floats, keeping x.
     *
     * @param variable The style variable.
     * @param y The new y value.
     */
    public static void pushStyleVarY(@NonNull StyleVariable variable, float y) {
        pushStyleVarComponent(variable, 1, y);
    }

    public static void pushStyleVarInt(@NonNull StyleVariable variable, int value) {
        if (variable.getDimensions() != 1) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, 1 int provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} expects a {} value, Integer provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        pushStyleVarRaw(variable, value, 0);
    }

    public static void pushStyleVarInt2(@NonNull StyleVariable variable, int x, int y) {
        if (variable.getDimensions() != 2) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} has {} dimensions, 2 integers provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Style variable {} expects {} values, Integers provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (x < variable.getMinValue() || x > variable.getMaxValue()) {
            log.warn(
                    "Variable {} x value outside of the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        if (y < variable.getMinValue() || y > variable.getMaxValue()) {
            log.warn(
                    "Variable {} y value outside of the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        pushStyleVarRaw(variable, x, y);
    }

    /**
     * Back up the current value of a style variable onto the stack, then set the new value.
     *
     * @param variable The variable.
     * @param x The new value, or x component.
     * @param y The new y component, ignored for single values.
     */
    private static void pushStyleVarRaw(@NonNull StyleVariable variable, float x, float y) {
        final float oldX = getStyleVarRaw(variable, 0);
        final float oldY = variable.getDimensions() == 2 ? getStyleVarRaw(variable, 1) : 0;
        context.styleVariableStack.push(new StyleMod(variable, oldX, oldY));
        setStyleVarRaw(variable, x, y);
    }

    public static void resetMouseDragDelta(@NonNull MouseButton button) {
        // NB: We don't need to reset the max drag distance, it's only used for thresholds
        context.io.mouseClickedPosition[button.index].set(context.io.mousePosition);
    }

    public static void setClipboardText(String text) {
        PlatformIO platformIO = context.platformIO;
        if (platformIO != null && platformIO.setClipboardTextFunction != null) {
            platformIO.setClipboardTextFunction.accept(text == null ? "" : text);
            return;
        }
        context.clipboardHandlerData.setLength(0);
        if (text != null) {
            context.clipboardHandlerData.append(text);
        }
    }

    public static void setCursorPos(float x, float y) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.cursorPosition.set(
                window.position.x - window.scrollPosition.x + x,
                window.position.y - window.scrollPosition.y + y);
        window.setPos = true;
    }

    public static void setCursorPosX(float x) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.cursorPosition.x = window.position.x - window.scrollPosition.x + x;
        window.setPos = true;
    }

    public static void setCursorPosY(float y) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.cursorPosition.y = window.position.y - window.scrollPosition.y + y;
        window.setPos = true;
    }

    public static void setCursorScreenPos(float x, float y) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.cursorPosition.set(x, y);
        window.setPos = true;
    }

    public static void setItemDefaultFocus() {
        IkGuiImplNav.setItemDefaultFocus();
    }

    public static void setKeyboardFocusHere(int offset) {
        IkGuiImplNav.setKeyboardFocusHere(offset);
    }

    public static void setMouseCursor(@NonNull MouseCursor cursor) {
        context.mouseCursor = cursor;
    }

    public static void setNextItemOpen(boolean isOpen, @NonNull Condition condition) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_OPEN;
        context.nextItemData.openValue = isOpen;
        context.nextItemData.openCondition = condition;
    }

    public static void setNextItemWidth(float width) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_WIDTH;
        context.nextItemData.width = width;
    }

    public static void setScrollFromPosX(float localX, float centerXRatio) {
        IkGuiInternal.setScrollFromPosX(IkGuiInternal.getCurrentWindow(), localX, centerXRatio);
    }

    public static void setScrollFromPosY(float localY, float centerYRatio) {
        IkGuiInternal.setScrollFromPosY(IkGuiInternal.getCurrentWindow(), localY, centerYRatio);
    }

    public static void setScrollHereX(float centerXRatio) {
        final Window window = IkGuiInternal.getCurrentWindow();
        final float spacingX = Math.max(window.padding.x, context.style.variable.itemSpacing.x);
        final RectFloat rect = context.lastItemData.rect;
        final float targetPositionX =
                rect.getLeft()
                        - spacingX
                        + (rect.getRight() + spacingX - (rect.getLeft() - spacingX)) * centerXRatio;
        IkGuiInternal.setScrollFromPosX(window, targetPositionX - window.position.x, centerXRatio);

        // Tweak: snap on edges when aiming at an item very close to the edge
        window.scrollTargetEdgeSnapDist.x = Math.max(0.0f, window.padding.x - spacingX);
    }

    public static void setScrollHereY(float centerYRatio) {
        final Window window = IkGuiInternal.getCurrentWindow();
        final float spacingY = Math.max(window.padding.y, context.style.variable.itemSpacing.y);
        final RectFloat rect = context.lastItemData.rect;
        final float targetPositionY =
                rect.getTop()
                        - spacingY
                        + (rect.getBottom() + spacingY - (rect.getTop() - spacingY)) * centerYRatio;
        IkGuiInternal.setScrollFromPosY(window, targetPositionY - window.position.y, centerYRatio);

        // Tweak: snap on edges when aiming at an item very close to the edge
        window.scrollTargetEdgeSnapDist.y = Math.max(0.0f, window.padding.y - spacingY);
    }

    public static void setScrollX(float x) {
        IkGuiInternal.setScrollX(IkGuiInternal.getCurrentWindow(), x);
    }

    public static void setScrollY(float y) {
        IkGuiInternal.setScrollY(IkGuiInternal.getCurrentWindow(), y);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplUtils() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
