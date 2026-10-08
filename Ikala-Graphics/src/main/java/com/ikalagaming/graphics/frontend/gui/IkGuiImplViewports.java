package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.PlatformIO;
import com.ikalagaming.graphics.frontend.gui.data.PlatformMonitor;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFocusRequestFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Viewports and platform windows. A viewport is a platform (OS) window that we render into. The
 * main viewport is owned by the application, and when multiple viewports are enabled we create
 * secondary viewports for windows that are moved outside the main viewport, and the backend creates
 * a platform window for each of them.
 *
 * <p>Note that most of this runs even if {@link ConfigFlags#VIEWPORTS_ENABLE} is not set, in order
 * to keep the main viewport up to date and clear out unused viewports.
 */
@Slf4j
class IkGuiImplViewports {
    /** The shared context. */
    static Context context;

    /** Private constructor so this is not instantiated. */
    private IkGuiImplViewports() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Check if multiple viewports are enabled for this frame.
     *
     * @return True if multiple viewports are enabled.
     */
    static boolean viewportsEnabled() {
        return (context.configFlagsCurrentFrame & ConfigFlags.VIEWPORTS_ENABLE) != 0;
    }

    /**
     * Fetch the main viewport, which is always the first viewport.
     *
     * @return The main viewport.
     */
    static Viewport getMainViewport() {
        return context.viewports.getFirst();
    }

    /**
     * Find a viewport by ID.
     *
     * @param id The viewport ID.
     * @return The viewport, or null if not found.
     */
    static Viewport findViewportByID(int id) {
        for (Viewport viewport : context.viewports) {
            if (viewport.id == id) {
                return viewport;
            }
        }
        return null;
    }

    /**
     * Find a viewport by the platform handle that the backend stored in it.
     *
     * @param platformHandle The platform handle, compared by equality.
     * @return The viewport, or null if not found.
     */
    static Viewport findViewportByPlatformHandle(Object platformHandle) {
        for (Viewport viewport : context.viewports) {
            if (viewport.platformHandle != null && viewport.platformHandle.equals(platformHandle)) {
                return viewport;
            }
        }
        return null;
    }

    /**
     * Set the viewport we are currently outputting into.
     *
     * @param currentWindow The current window, may be null.
     * @param viewport The viewport, may be null.
     */
    static void setCurrentViewport(Window currentWindow, Viewport viewport) {
        if (viewport != null) {
            viewport.lastFrameActive = context.frameCount;
        }
        if (context.currentViewport == viewport) {
            return;
        }
        context.currentDpiScale = viewport != null ? viewport.dpiScale : 1.0f;
        context.currentViewport = viewport;

        // Notify the platform layer of viewport changes
        final PlatformIO platformIO = context.platformIO;
        if (context.currentViewport != null && platformIO.platformOnChangedViewport != null) {
            platformIO.platformOnChangedViewport.accept(context.currentViewport);
        }
    }

    /**
     * Move a window into a viewport.
     *
     * @param window The window.
     * @param viewport The viewport.
     */
    static void setWindowViewport(@NonNull Window window, @NonNull Viewport viewport) {
        // Abandon our old viewport
        if (window.viewportOwned && window.viewport != null && window.viewport.window == window) {
            window.viewport.size.set(0.0f, 0.0f);
        }

        window.viewport = viewport;
        window.viewportID = viewport.id;
        window.viewportOwned = viewport.window == window;
    }

    /**
     * Check if a window always wants its own viewport, rather than merging into a host viewport.
     * Tooltips and menus are not automatically forced into their own viewport when the no merge
     * flag is set, however the multiplication of viewports makes them more likely to protrude and
     * create their own.
     *
     * @param window The window.
     * @return True if the window always wants its own viewport.
     */
    static boolean getWindowAlwaysWantOwnViewport(@NonNull Window window) {
        if (!context.io.configViewportsNoAutoMerge
                && (window.windowClass.viewportFlagsOverrideSet & ViewportFlags.NO_AUTO_MERGE)
                        == 0) {
            return false;
        }
        if (!viewportsEnabled() || window.dockIsActive) {
            return false;
        }
        final int flags = window.flags;
        if ((flags
                        & (WindowFlags.INTERNAL_CHILD_WINDOW
                                | WindowFlags.INTERNAL_CHILD_MENU
                                | WindowFlags.INTERNAL_TOOLTIP))
                != 0) {
            return false;
        }
        return (flags & WindowFlags.INTERNAL_POPUP) == 0
                || (flags & WindowFlags.INTERNAL_MODAL) != 0;
    }

    /**
     * A heuristic for whether one viewport is above another, depending on how backends handle OS
     * level parenting. Due to how the parent viewport stack is laid out, isViewportAbove(a, b) is
     * not always the same as !isViewportAbove(b, a).
     *
     * @param potentialAbove The viewport that might be above.
     * @param potentialBelow The viewport that might be below.
     * @return True if the first viewport is probably above the second one.
     */
    private static boolean isViewportAbove(
            @NonNull Viewport potentialAbove, @NonNull Viewport potentialBelow) {
        // If the backend handles parent viewports, the parent chain should be accurate
        if ((context.io.backendFlags & BackendFlags.HAS_PARENT_VIEWPORT) != 0) {
            for (Viewport v = potentialAbove;
                    v != null && v.parentViewport != null;
                    v = v.parentViewport) {
                if (v.parentViewport == potentialBelow) {
                    return true;
                }
            }
        } else if (potentialAbove.parentViewport == potentialBelow) {
            return true;
        }

        return potentialAbove.lastFocusedStampCount > potentialBelow.lastFocusedStampCount;
    }

    /**
     * Try to merge a window (and the windows in its viewport) into a host viewport.
     *
     * @param window The window, which must be the root of its dock tree.
     * @param destination The viewport to merge into.
     * @return True if the window was merged.
     */
    static boolean updateTryMergeWindowIntoHostViewport(
            @NonNull Window window, @NonNull Viewport destination) {
        if (window != window.rootWindowDockTree) {
            IkGuiImplDebugTools.reportError(
                    log, "Only root windows can be merged into a host viewport");
            return false;
        }
        final Viewport source = window.viewport;
        if (source == destination) {
            return false;
        }
        if ((destination.flags & ViewportFlags.CAN_HOST_OTHER_WINDOWS) == 0) {
            return false;
        }
        if ((destination.flags & ViewportFlags.IS_MINIMIZED) != 0) {
            return false;
        }
        final RectFloat windowRect = window.getRect();
        final RectFloat viewportRect = new RectFloat();
        if (!destination.getMainRect(viewportRect).contains(windowRect)) {
            return false;
        }
        if (getWindowAlwaysWantOwnViewport(window)) {
            return false;
        }

        for (Viewport obstructing : context.viewports) {
            if (obstructing == source
                    || obstructing == destination
                    || !obstructing.platformWindowCreated) {
                continue;
            }
            if (obstructing.getMainRect(viewportRect).overlaps(windowRect)
                    && isViewportAbove(obstructing, destination)
                    && (source == null || isViewportAbove(source, obstructing))) {
                // The obstructing viewport is between the source and destination, so we can't
                // merge
                return false;
            }
        }

        // Move to the existing viewport, and move child/hosted windows as well
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_VIEWPORT,
                "[viewport] Window '%s' merge into Viewport 0X%08X",
                window.name,
                destination.id);
        if (window.viewportOwned) {
            for (Window other : context.windowDisplayOrder) {
                if (other.viewport == source) {
                    setWindowViewport(other, destination);
                }
            }
        }
        setWindowViewport(window, destination);
        if ((window.flags & WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS) == 0) {
            IkGuiInternal.bringWindowToDisplayFront(window);
        }

        return true;
    }

    /**
     * Try to merge a window into one of the host viewports, which currently is just the main
     * viewport.
     *
     * @param window The window, which must be the root of its dock tree.
     * @return True if the window was merged.
     */
    static boolean updateTryMergeWindowIntoHostViewports(@NonNull Window window) {
        return updateTryMergeWindowIntoHostViewport(window, getMainViewport());
    }

    /**
     * Move a window by an offset, including its cached rectangles and cursor positions.
     *
     * @param window The window.
     * @param deltaX The amount to move on the x-axis.
     * @param deltaY The amount to move on the y-axis.
     */
    static void translateWindow(@NonNull Window window, float deltaX, float deltaY) {
        window.position.add(deltaX, deltaY);
        window.rectCurrentClip.translate(deltaX, deltaY);
        window.rectOuterClipped.translate(deltaX, deltaY);
        window.rectInner.translate(deltaX, deltaY);
        window.cursorPosition.add(deltaX, deltaY);
        window.cursorStartPosition.add(deltaX, deltaY);
        window.cursorMaxPosition.add(deltaX, deltaY);
        window.cursorIdealMaxPosition.add(deltaX, deltaY);
    }

    /**
     * Translate windows when a host viewport has been moved. This additionally keeps windows at the
     * same place when {@link ConfigFlags#VIEWPORTS_ENABLE} is toggled.
     *
     * @param viewport The viewport that moved.
     * @param oldPosition The previous position.
     * @param newPosition The new position.
     * @param oldSize The previous size.
     * @param newSize The new size.
     */
    static void translateWindowsInViewport(
            @NonNull Viewport viewport,
            @NonNull Vector2f oldPosition,
            @NonNull Vector2f newPosition,
            @NonNull Vector2f oldSize,
            @NonNull Vector2f newSize) {
        if (viewport.window != null
                || (viewport.flags & ViewportFlags.CAN_HOST_OTHER_WINDOWS) == 0) {
            IkGuiImplDebugTools.reportError(log, "Only host viewports can translate their windows");
            return;
        }

        // 1) We test if VIEWPORTS_ENABLE was just toggled, which allows us to conveniently
        // translate windows from OS-window-local to absolute coordinates or vice-versa.
        // 2) If it's not going to fit into the new size, keep it at the same absolute position.
        final boolean translateAllWindows =
                (context.configFlagsCurrentFrame & ConfigFlags.VIEWPORTS_ENABLE)
                        != (context.configFlagsLastFrame & ConfigFlags.VIEWPORTS_ENABLE);
        final RectFloat testStillFitRect = new RectFloat(oldPosition, oldSize);
        final float deltaX = newPosition.x - oldPosition.x;
        final float deltaY = newPosition.y - oldPosition.y;
        for (Window window : context.windowDisplayOrder) {
            if (translateAllWindows
                    || (window.viewport == viewport
                            && (oldSize.equals(newSize)
                                    || testStillFitRect.contains(window.getRect())))) {
                translateWindow(window, deltaX, deltaY);
            }
        }
    }

    /**
     * Scale a window position and size around the origin of its viewport. This is lossy.
     *
     * @param window The window.
     * @param scale The amount to scale by.
     */
    static void scaleWindow(@NonNull Window window, float scale) {
        final Vector2f origin = window.viewport.position;
        window.position.set(
                (float) Math.floor((window.position.x - origin.x) * scale + origin.x),
                (float) Math.floor((window.position.y - origin.y) * scale + origin.y));
        window.size.set(
                IkGuiInternal.truncate(window.size.x * scale),
                IkGuiInternal.truncate(window.size.y * scale));
        window.sizeFull.set(
                IkGuiInternal.truncate(window.sizeFull.x * scale),
                IkGuiInternal.truncate(window.sizeFull.y * scale));
        window.contentSize.set(
                IkGuiInternal.truncate(window.contentSize.x * scale),
                IkGuiInternal.truncate(window.contentSize.y * scale));
    }

    /**
     * Scale all windows in a viewport (position, size), like when changing DPI. This is lossy.
     *
     * @param viewport The viewport.
     * @param scale The amount to scale by.
     */
    static void scaleWindowsInViewport(@NonNull Viewport viewport, float scale) {
        if (viewport.window != null) {
            scaleWindow(viewport.window, scale);
            return;
        }
        for (Window window : context.windowDisplayOrder) {
            if (window.viewport == viewport) {
                scaleWindow(window, scale);
            }
        }
    }

    /**
     * Find the hovered viewport ourselves, if the backend doesn't support {@link
     * BackendFlags#HAS_MOUSE_HOVERED_VIEWPORT} or doesn't honor {@link ViewportFlags#NO_INPUTS} for
     * it. This won't take into account the possibility that non-GUI windows may be in-between our
     * dragged window and our target window, and requires the platform get window focus callback to
     * be implemented.
     *
     * @param mouseX The mouse x position, in platform coordinates.
     * @param mouseY The mouse y position, in platform coordinates.
     * @return The best candidate, or null if there are none.
     */
    static Viewport findHoveredViewportFromPlatformWindowStack(float mouseX, float mouseY) {
        Viewport bestCandidate = null;
        final RectFloat rect = new RectFloat();
        for (Viewport viewport : context.viewports) {
            if ((viewport.flags & (ViewportFlags.NO_INPUTS | ViewportFlags.IS_MINIMIZED)) == 0
                    && viewport.getMainRect(rect).contains(mouseX, mouseY)
                    && (bestCandidate == null
                            || bestCandidate.lastFocusedStampCount < viewport.lastFocusedStampCount)
                    && viewport.platformWindowCreated) {
                bestCandidate = viewport;
            }
        }
        return bestCandidate;
    }

    /**
     * Update viewports and monitor info at the start of the frame. This runs even if multiple
     * viewports are not enabled, in order to clear unused viewports (if any) and update monitor
     * info.
     */
    static void updateViewportsNewFrame() {
        final PlatformIO platformIO = context.platformIO;
        if (platformIO.viewports.size() > context.viewports.size()) {
            IkGuiImplDebugTools.reportError(
                    log, "Platform viewport list is larger than the viewport list");
        }

        // Update minimized status (we need it first in order to decide if we'll apply the main
        // viewport position/size), and focus status
        final boolean viewportsEnabled = viewportsEnabled();
        if (viewportsEnabled) {
            updateViewportsPlatformFocus();
        }

        // Create/update the main viewport with the current platform position. The size is driven
        // by the backend/user code through the display size.
        final Viewport mainViewport = getMainViewport();
        if (mainViewport.id != Viewport.DEFAULT_ID || mainViewport.window != null) {
            IkGuiImplDebugTools.reportError(log, "Main viewport is not set up correctly");
        }
        final Vector2f mainViewportPosition = new Vector2f(0.0f, 0.0f);
        final Vector2f mainViewportSize = new Vector2f(context.io.displaySize);
        if (viewportsEnabled) {
            if ((mainViewport.flags & ViewportFlags.IS_MINIMIZED) != 0) {
                // Preserve the last position/size when minimized
                mainViewportPosition.set(mainViewport.position);
                mainViewportSize.set(mainViewport.size);
            } else if (platformIO.platformGetWindowPos != null) {
                mainViewportPosition.set(platformIO.platformGetWindowPos.apply(mainViewport));
            }
        }
        addUpdateViewport(
                null,
                Viewport.DEFAULT_ID,
                mainViewportPosition,
                mainViewportSize,
                ViewportFlags.OWNED_BY_APP | ViewportFlags.CAN_HOST_OTHER_WINDOWS);

        context.currentDpiScale = 0.0f;
        context.currentViewport = null;
        context.mouseViewport = null;
        for (int n = 0; n < context.viewports.size(); ++n) {
            final Viewport viewport = context.viewports.get(n);
            viewport.index = n;

            // Erase unused viewports
            if (n > 0 && viewport.lastFrameActive < context.frameCount - 2) {
                destroyViewport(viewport);
                --n;
                continue;
            }

            final boolean platformFunctionsAvailable = viewport.platformWindowCreated;
            if (viewportsEnabled
                    && (viewport.flags & ViewportFlags.IS_MINIMIZED) == 0
                    && platformFunctionsAvailable) {
                // Update position and size (from the platform window to us) if requested. We do
                // it early in the frame instead of waiting for updatePlatformWindows() to avoid a
                // frame of lag when moving/resizing using OS facilities.
                if (viewport.platformRequestMove && platformIO.platformGetWindowPos != null) {
                    viewport.position.set(platformIO.platformGetWindowPos.apply(viewport));
                    viewport.lastPlatformPosition.set(viewport.position);
                }
                if (viewport.platformRequestResize && platformIO.platformGetWindowSize != null) {
                    viewport.size.set(platformIO.platformGetWindowSize.apply(viewport));
                    viewport.lastPlatformSize.set(viewport.size);
                }
                if (platformIO.platformGetWindowFramebufferScale != null) {
                    viewport.framebufferScale.set(
                            platformIO.platformGetWindowFramebufferScale.apply(viewport));
                }
            }

            // Update/copy monitor info
            updateViewportPlatformMonitor(viewport);

            // Lock down space taken by menu bars and status bars, and query the initial insets
            // from the backend. This sets up the initial value for functions like
            // beginMainMenuBar(), dockSpaceOverViewport() etc.
            viewport.lockWorkInsets();
            if (platformIO.platformGetWindowWorkAreaInsets != null && platformFunctionsAvailable) {
                final Vector4f insets = platformIO.platformGetWindowWorkAreaInsets.apply(viewport);
                if (insets.x < 0.0f || insets.y < 0.0f || insets.z < 0.0f || insets.w < 0.0f) {
                    IkGuiImplDebugTools.reportError(
                            log, "Platform work area insets must not be negative");
                }
                viewport.buildWorkInsetMin.set(insets.x, insets.y);
                viewport.buildWorkInsetMax.set(insets.z, insets.w);
            }
            viewport.updateWorkRect();

            // Reset alpha every frame. Users of transparency (docking) need to request a lower
            // alpha again.
            viewport.alpha = 1.0f;

            // Translate windows when a host viewport has been moved. This additionally keeps
            // windows at the same place when VIEWPORTS_ENABLE is toggled.
            final float deltaX = viewport.position.x - viewport.lastPosition.x;
            final float deltaY = viewport.position.y - viewport.lastPosition.y;
            if ((viewport.flags & ViewportFlags.CAN_HOST_OTHER_WINDOWS) != 0
                    && (deltaX != 0.0f || deltaY != 0.0f)) {
                translateWindowsInViewport(
                        viewport,
                        viewport.lastPosition,
                        viewport.position,
                        viewport.lastSize,
                        viewport.size);
            }

            // Update DPI scale
            final float newDpiScale;
            if (platformIO.platformGetWindowDpiScale != null && platformFunctionsAvailable) {
                newDpiScale = platformIO.platformGetWindowDpiScale.apply(viewport);
            } else if (viewport.platformMonitor != -1) {
                newDpiScale = platformIO.monitors.get(viewport.platformMonitor).dpiScale;
            } else {
                newDpiScale = viewport.dpiScale != 0.0f ? viewport.dpiScale : 1.0f;
            }
            if (viewport.dpiScale != 0.0f
                    && newDpiScale != viewport.dpiScale
                    && context.io.configDpiScaleViewports) {
                scaleWindowsInViewport(viewport, newDpiScale / viewport.dpiScale);
            }
            viewport.dpiScale = newDpiScale;
        }

        // Update the fallback monitor
        final RectFloat fullWorkRect = context.platformMonitorsFullWorkRect;
        fullWorkRect.set(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        final PlatformMonitor fallbackMonitor = context.fallbackMonitor;
        if (platformIO.monitors.isEmpty()) {
            fallbackMonitor.mainPosition.set(mainViewport.position);
            fallbackMonitor.mainSize.set(mainViewport.size);
            fallbackMonitor.workPosition.set(mainViewport.workPosition);
            fallbackMonitor.workSize.set(mainViewport.workSize);
            fallbackMonitor.dpiScale = mainViewport.dpiScale;
            fallbackMonitor.platformHandle = null;
            fullWorkRect.add(fallbackMonitor.getWorkRect(new RectFloat()));
        } else {
            fallbackMonitor.set(platformIO.monitors.getFirst());
        }
        final RectFloat monitorRect = new RectFloat();
        for (PlatformMonitor monitor : platformIO.monitors) {
            fullWorkRect.add(monitor.getWorkRect(monitorRect));
        }

        if (!viewportsEnabled) {
            context.mouseViewport = mainViewport;
            return;
        }

        updateMouseViewport();
    }

    /**
     * Update the minimized and focused status of viewports from the platform, and apply our focus
     * when the focused platform window changes.
     */
    private static void updateViewportsPlatformFocus() {
        final PlatformIO platformIO = context.platformIO;
        Viewport focusedViewport = null;
        for (Viewport viewport : context.viewports) {
            final boolean platformFunctionsAvailable = viewport.platformWindowCreated;
            if (platformIO.platformGetWindowMinimized != null && platformFunctionsAvailable) {
                if (platformIO.platformGetWindowMinimized.test(viewport)) {
                    viewport.flags |= ViewportFlags.IS_MINIMIZED;
                } else {
                    viewport.flags &= ~ViewportFlags.IS_MINIMIZED;
                }
            }

            // Update our implicit z-order knowledge of platform windows, which is used when the
            // backend cannot provide the mouse hovered viewport
            if (platformIO.platformGetWindowFocus != null && platformFunctionsAvailable) {
                if (platformIO.platformGetWindowFocus.test(viewport)) {
                    viewport.flags |= ViewportFlags.IS_FOCUSED;
                    focusedViewport = viewport;
                } else {
                    viewport.flags &= ~ViewportFlags.IS_FOCUSED;
                }
            }
        }

        if (focusedViewport == null) {
            return;
        }

        // Has the focused viewport changed?
        if (context.platformLastFocusedViewportID != focusedViewport.id) {
            final Viewport previousFocusedViewport =
                    findViewportByID(context.platformLastFocusedViewportID);
            final boolean previousFocusedHasBeenDestroyed =
                    previousFocusedViewport == null
                            || !previousFocusedViewport.platformWindowCreated;

            // Store a tag so we can infer the z-order easily from all our windows. We compare the
            // last focused ID so newly created viewports with NO_FOCUS_ON_APPEARING will keep the
            // front most stamp instead of losing it back to their parent viewport.
            if (focusedViewport.lastFocusedStampCount != context.viewportFocusedStampCount) {
                focusedViewport.lastFocusedStampCount = ++context.viewportFocusedStampCount;
            }
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_VIEWPORT,
                    "[viewport] Focused viewport changed %08X -> %08X '%s', attempting to apply our"
                            + " focus.",
                    context.platformLastFocusedViewportID,
                    focusedViewport.id,
                    focusedViewport.getDebugName());
            context.platformLastFocusedViewportID = focusedViewport.id;

            // Focus the associated window, if focus didn't happen with a click within our
            // boundaries (like clicking the platform title bar), or because we destroyed another
            // window
            final boolean applyFocusOnFocusedViewport =
                    !IkGuiImplUtils.isAnyMouseDown() && !previousFocusedHasBeenDestroyed;
            if (applyFocusOnFocusedViewport
                    && context.io.configViewportsPlatformFocusSetsWindowFocus) {
                // Update so a window changing viewport won't lose focus
                focusedViewport.lastFocusedHadNavWindow |=
                        context.navFocusedWindow != null
                                && context.navFocusedWindow.viewport == focusedViewport;
                final int focusRequestFlags =
                        WindowFocusRequestFlags.UNLESS_BELOW_MODAL
                                | WindowFocusRequestFlags.RESTORE_FOCUSED_CHILD;
                if (focusedViewport.window != null) {
                    IkGuiInternal.focusWindow(focusedViewport.window, focusRequestFlags);
                } else if (focusedViewport.lastFocusedHadNavWindow) {
                    // Focus the top most window in the viewport
                    IkGuiInternal.focusTopMostWindowUnderOne(
                            null, null, focusedViewport, focusRequestFlags);
                } else {
                    // No window had focus last time the viewport was focused
                    IkGuiInternal.focusWindow(null, focusRequestFlags);
                }
            }
        }
        focusedViewport.lastFocusedHadNavWindow =
                context.navFocusedWindow != null
                        && context.navFocusedWindow.viewport == focusedViewport;
    }

    /**
     * Decide on the actual mouse viewport for this frame, between the active/focused viewport and
     * the hovered viewport.
     */
    private static void updateMouseViewport() {
        final Vector2f mousePosition = context.io.mousePosition;

        // The hovered viewport should skip over any viewport that has NO_INPUTS set
        Viewport viewportHovered;
        if ((context.io.backendFlags & BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT) != 0) {
            viewportHovered =
                    context.io.mouseHoveredViewport != 0
                            ? findViewportByID(context.io.mouseHoveredViewport)
                            : null;
            if (viewportHovered != null && (viewportHovered.flags & ViewportFlags.NO_INPUTS) != 0) {
                // The backend failed to handle NO_INPUTS viewports, revert to our fallback
                viewportHovered =
                        findHoveredViewportFromPlatformWindowStack(
                                mousePosition.x, mousePosition.y);
            }
        } else {
            // If the backend doesn't know how to honor NO_INPUTS, we do a search ourselves
            viewportHovered =
                    findHoveredViewportFromPlatformWindowStack(mousePosition.x, mousePosition.y);
        }
        if (viewportHovered != null) {
            context.mouseLastHoveredViewport = viewportHovered;
        } else if (context.mouseLastHoveredViewport == null) {
            context.mouseLastHoveredViewport = getMainViewport();
        }

        // Update the mouse reference viewport. When moving a window we aim at its viewport, but
        // this will be overwritten below if we go in drag and drop mode. The moving window's
        // viewport will be null in the rare situation where the window disappeared while moving.
        if (context.windowMoving != null && context.windowMoving.viewport != null) {
            context.mouseViewport = context.windowMoving.viewport;
        } else {
            context.mouseViewport = context.mouseLastHoveredViewport;
        }

        // When dragging something, always refer to the last hovered viewport.
        // - When releasing a moving window we will revert to aiming behind (at the hovered
        //   viewport).
        // - When we are between viewports, our dragged preview will tend to show in the last
        //   viewport even if we don't have tooltips in their viewports (when lacking monitor
        //   info).
        // - Consider the case of holding on a menu item to browse child menus: even though a
        //   mouse button is held, there's no active ID because menu items only react on mouse
        //   release.
        final boolean isMouseDraggingWithAnExpectedDestination = context.dragDropActive;
        if (isMouseDraggingWithAnExpectedDestination && viewportHovered == null) {
            viewportHovered = context.mouseLastHoveredViewport;
        }
        if ((isMouseDraggingWithAnExpectedDestination
                        || context.activeID == 0
                        || !IkGuiImplUtils.isAnyMouseDown())
                && viewportHovered != null
                && viewportHovered != context.mouseViewport
                && (viewportHovered.flags & ViewportFlags.NO_INPUTS) == 0) {
            context.mouseViewport = viewportHovered;
        }

        if (context.mouseViewport == null) {
            IkGuiImplDebugTools.reportError(log, "Failed to find a mouse viewport");
            context.mouseViewport = getMainViewport();
        }
    }

    /** Update the user-facing viewport list in the platform IO, at the end of the frame. */
    static void updateViewportsEndFrame() {
        final PlatformIO platformIO = context.platformIO;
        platformIO.viewports.clear();
        for (int i = 0; i < context.viewports.size(); ++i) {
            final Viewport viewport = context.viewports.get(i);
            viewport.lastPosition.set(viewport.position);
            viewport.lastSize.set(viewport.size);
            // Always include the main viewport in the list
            if (i > 0
                    && (viewport.lastFrameActive < context.frameCount
                            || viewport.size.x <= 0.0f
                            || viewport.size.y <= 0.0f)) {
                continue;
            }
            if (viewport.window != null
                    && !IkGuiInternal.isWindowActiveAndVisible(viewport.window)) {
                continue;
            }
            if (i > 0 && viewport.window == null) {
                IkGuiImplDebugTools.reportError(
                        log, "Secondary viewport {} has no window", viewport.id);
            }
            platformIO.viewports.add(viewport);
        }

        // Clear the main viewport flags because updatePlatformWindows() won't do it, and may not
        // even be called
        getMainViewport().clearRequestFlags();
    }

    /**
     * Create a viewport, or update an existing one.
     *
     * @param window The window that owns the viewport, or null for host viewports.
     * @param id The viewport ID, which matches the window ID if there is a window.
     * @param position The position of the viewport.
     * @param size The size of the viewport.
     * @param flags The viewport flags.
     * @return The viewport.
     * @see ViewportFlags
     */
    static Viewport addUpdateViewport(
            Window window, int id, @NonNull Vector2f position, @NonNull Vector2f size, int flags) {
        if (id == 0) {
            IkGuiImplDebugTools.reportError(log, "Viewport IDs must not be 0");
        }

        flags |= ViewportFlags.IS_PLATFORM_WINDOW;
        if (window != null) {
            final boolean windowCanUseInputs =
                    (window.flags & WindowFlags.NO_MOUSE_INPUTS) == 0
                            || (window.flags & WindowFlags.NO_NAV_INPUTS) == 0;
            if (context.windowMoving != null && context.windowMoving.rootWindowDockTree == window) {
                flags |= ViewportFlags.NO_INPUTS | ViewportFlags.NO_FOCUS_ON_APPEARING;
            }
            if (!windowCanUseInputs) {
                flags |= ViewportFlags.NO_INPUTS;
            }
            if ((window.flags & WindowFlags.NO_FOCUS_ON_APPEARING) != 0) {
                flags |= ViewportFlags.NO_FOCUS_ON_APPEARING;
            }
        }

        Viewport viewport = findViewportByID(id);
        if (viewport != null) {
            // Always update the main viewport, as we are already pulling the correct platform
            // position and size
            final float previousX = viewport.position.x;
            final float previousY = viewport.position.y;
            final float previousWidth = viewport.size.x;
            final float previousHeight = viewport.size.y;
            if (!viewport.platformRequestMove || viewport.id == Viewport.DEFAULT_ID) {
                viewport.position.set(position);
            }
            if (!viewport.platformRequestResize || viewport.id == Viewport.DEFAULT_ID) {
                viewport.size.set(size);
            }
            // Preserve the existing status flags
            viewport.flags =
                    flags
                            | (viewport.flags
                                    & (ViewportFlags.IS_MINIMIZED | ViewportFlags.IS_FOCUSED));
            if (previousX != viewport.position.x
                    || previousY != viewport.position.y
                    || previousWidth != viewport.size.x
                    || previousHeight != viewport.size.y) {
                updateViewportPlatformMonitor(viewport);
            }
        } else {
            // New viewport
            viewport = new Viewport(context.drawTextures);
            viewport.id = id;
            viewport.index = context.viewports.size();
            viewport.position.set(position);
            viewport.lastPosition.set(position);
            viewport.size.set(size);
            viewport.lastSize.set(size);
            viewport.flags = flags;
            updateViewportPlatformMonitor(viewport);
            context.viewports.add(viewport);
            context.viewportCreatedCount++;
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_VIEWPORT,
                    "[viewport] Add Viewport %08X '%s'",
                    id,
                    window != null ? window.name : "<NULL>");

            // We assume the window becomes front-most (even when NO_FOCUS_ON_APPEARING is used).
            // This is useful for our platform z-order heuristic when the mouse hovered viewport
            // is not available.
            viewport.lastFocusedStampCount = ++context.viewportFocusedStampCount;

            // Store the initial DPI scale before the OS platform window is created, based on the
            // expected monitor data. This is so we can select an appropriate font size on the
            // first frame of our window lifetime.
            viewport.dpiScale = getViewportPlatformMonitor(viewport).dpiScale;
        }

        viewport.window = window;
        viewport.lastFrameActive = context.frameCount;
        viewport.updateWorkRect();
        if (window != null && viewport.id != window.id) {
            IkGuiImplDebugTools.reportError(log, "Window viewport IDs must match the window ID");
        }

        // Initialize the framebuffer scale for the main viewport and new viewports, before
        // updatePlatformWindows() has a chance to fetch it from the platform
        if (viewport.framebufferScale.x <= 0.0f
                || viewport.framebufferScale.y <= 0.0f
                || viewport.id == Viewport.DEFAULT_ID) {
            viewport.framebufferScale.set(context.io.displayFramebufferScale);
        }

        if (window != null) {
            window.viewportOwned = true;
        }

        return viewport;
    }

    /**
     * Destroy a viewport, clearing references to it in windows (where the viewport ID becomes the
     * master data).
     *
     * @param viewport The viewport to destroy.
     */
    static void destroyViewport(@NonNull Viewport viewport) {
        for (Window window : context.windowDisplayOrder) {
            if (window.viewport != viewport) {
                continue;
            }
            window.viewport = null;
            window.viewportOwned = false;
        }
        if (viewport == context.mouseLastHoveredViewport) {
            context.mouseLastHoveredViewport = null;
        }

        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_VIEWPORT,
                "[viewport] Delete Viewport %08X '%s'",
                viewport.id,
                viewport.getDebugName());
        // In most circumstances the platform window will already be destroyed here
        destroyPlatformWindow(viewport);
        if (context.platformIO.viewports.contains(viewport)) {
            IkGuiImplDebugTools.reportError(
                    log, "Destroying a viewport that is still in the platform viewport list");
            context.platformIO.viewports.remove(viewport);
        }
        if (context.viewports.get(viewport.index) != viewport) {
            IkGuiImplDebugTools.reportError(log, "Viewport index is out of date");
            context.viewports.remove(viewport);
            return;
        }
        context.viewports.remove(viewport.index);
    }

    /**
     * Select the viewport for a window during begin().
     *
     * @param window The window.
     */
    static void windowSelectViewport(@NonNull Window window) {
        final int flags = window.flags;
        window.viewportAllowPlatformMonitorExtend = -1;

        // Restore the main viewport if multiple viewports are not supported by the backend
        final Viewport mainViewport = getMainViewport();
        if (!viewportsEnabled()) {
            setWindowViewport(window, mainViewport);
            return;
        }
        window.viewportOwned = false;

        // Appearing popups reset their viewport so they can inherit again
        if ((flags & (WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_TOOLTIP)) != 0
                && window.appearing) {
            window.viewport = null;
            window.viewportID = 0;
        }

        final boolean hasViewport =
                (context.nextWindowData.fieldFlags & NextWindowFlags.HAS_VIEWPORT) != 0;
        if (!hasViewport) {
            // By default, inherit from the parent window
            if (window.viewport == null
                    && window.parentWindow != null
                    && (!window.parentWindow.isFallbackWindow || window.parentWindow.wasActive)) {
                window.viewport = window.parentWindow.viewport;
            }

            // Attempt to restore the saved viewport ID (for a window that hasn't been activated
            // yet), try to restore the viewport based on the saved viewport position from the
            // settings
            if (window.viewport == null && window.viewportID != 0) {
                window.viewport = findViewportByID(window.viewportID);
                if (window.viewport == null
                        && window.viewportPosition.x != Float.MAX_VALUE
                        && window.viewportPosition.y != Float.MAX_VALUE) {
                    window.viewport =
                            addUpdateViewport(
                                    window,
                                    window.id,
                                    window.viewportPosition,
                                    window.size,
                                    ViewportFlags.NONE);
                }
            }
        }

        boolean lockViewport = false;
        if (hasViewport) {
            // Code explicitly requested a viewport
            window.viewport = findViewportByID(context.nextWindowData.viewportID);
            // Store the ID even if the viewport isn't resolved yet
            window.viewportID = context.nextWindowData.viewportID;
            if (window.viewport != null
                    && (window.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) != 0
                    && window.viewport.window != null) {
                window.viewport.window = window;
                // Overwrite the ID (always owned by the node)
                window.viewport.id = window.id;
                window.viewportID = window.id;
            }
            lockViewport = true;
        } else if ((flags & (WindowFlags.INTERNAL_CHILD_WINDOW | WindowFlags.INTERNAL_CHILD_MENU))
                != 0) {
            // Always inherit the viewport from the parent window
            if (window.dockNode != null
                    && window.dockNode.hostWindow != null
                    && window.dockNode.hostWindow.viewport != window.parentWindow.viewport) {
                IkGuiImplDebugTools.reportError(
                        log, "Docked child window is in a different viewport than its host");
            }
            window.viewport = window.parentWindow.viewport;
        } else if (window.dockNode != null && window.dockNode.hostWindow != null) {
            // This covers the "always inherit viewport from parent window" case for when a window
            // reattaches to a node that was just created mid-frame
            window.viewport = window.dockNode.hostWindow.viewport;
        } else if ((flags & WindowFlags.INTERNAL_TOOLTIP) != 0) {
            window.viewport = context.mouseViewport;
        } else if (getWindowAlwaysWantOwnViewport(window)) {
            window.viewport =
                    addUpdateViewport(
                            window, window.id, window.position, window.size, ViewportFlags.NONE);
        } else if (context.windowMoving != null
                && context.windowMoving.rootWindowDockTree == window
                && IkGuiImplUtils.isMousePosValid(
                        context.io.mousePosition.x, context.io.mousePosition.y)) {
            if (window.viewport != null && window.viewport.window == window) {
                window.viewport =
                        addUpdateViewport(
                                window,
                                window.id,
                                window.position,
                                window.size,
                                ViewportFlags.NONE);
            }
        } else {
            // Merge into the host viewport? We cannot test viewportOwned as it is set lower in the
            // function. We check the active ID to avoid merging during a short-term widget
            // interaction, mainly to avoid merging during a resize.
            final boolean tryToMergeIntoHostViewport =
                    window.viewport != null
                            && window == window.viewport.window
                            && (context.activeID == 0 || context.activeIDAllowOverlap);
            if (tryToMergeIntoHostViewport) {
                updateTryMergeWindowIntoHostViewports(window);
            }
        }

        // Fallback: merge into the default viewport if the z-order matches, otherwise create a new
        // viewport
        if (window.viewport == null
                && !updateTryMergeWindowIntoHostViewport(window, mainViewport)) {
            window.viewport =
                    addUpdateViewport(
                            window, window.id, window.position, window.size, ViewportFlags.NONE);
        }

        // Mark the window as allowed to protrude outside its viewport and into the current monitor
        if (!lockViewport) {
            if ((flags & (WindowFlags.INTERNAL_TOOLTIP | WindowFlags.INTERNAL_POPUP)) != 0) {
                // We need to take into account the possibility that the mouse may become invalid.
                // Popups/tooltips always set the extend monitor so the allowed extent rect
                // will return the full monitor bounds.
                final Vector2f mouseReference =
                        (flags & WindowFlags.INTERNAL_TOOLTIP) != 0
                                ? context.io.mousePosition
                                : context.beginPopupStack.getLast().mousePosition;
                final boolean useMouseReference =
                        !context.navCursorVisible
                                || !context.navHighlightItemUnderNav
                                || context.navFocusedWindow == null;
                final boolean mouseValid =
                        IkGuiImplUtils.isMousePosValid(mouseReference.x, mouseReference.y);
                if ((window.appearing
                                || (flags
                                                & (WindowFlags.INTERNAL_TOOLTIP
                                                        | WindowFlags.INTERNAL_CHILD_MENU))
                                        != 0)
                        && (!useMouseReference || mouseValid)) {
                    final Vector2f referencePosition = new Vector2f();
                    if (useMouseReference && mouseValid) {
                        referencePosition.set(mouseReference);
                    } else {
                        IkGuiImplNav.navCalcPreferredRefPos(
                                (flags & WindowFlags.INTERNAL_POPUP) != 0, referencePosition);
                    }
                    window.viewportAllowPlatformMonitorExtend =
                            findPlatformMonitorForPos(referencePosition.x, referencePosition.y);
                } else {
                    window.viewportAllowPlatformMonitorExtend = window.viewport.platformMonitor;
                }
            } else if (window.viewport != null
                    && window != window.viewport.window
                    && window.viewport.window != null
                    && (flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0
                    && window.dockNode == null) {
                // When called from begin() we don't have access to a proper version of the hidden
                // flag yet, so we replicate this code
                final boolean willBeVisible = !window.dockIsActive || window.dockTabIsVisible;
                if ((window.flags & WindowFlags.INTERNAL_DOCK_NODE_HOST) != 0
                        && window.viewport.lastFrameActive < context.frameCount
                        && willBeVisible) {
                    // Steal/transfer ownership
                    IkGuiImplDebugTools.debugLog(
                            DebugLogFlags.EVENT_VIEWPORT,
                            "[viewport] Window '%s' steal Viewport %08X from Window '%s'",
                            window.name,
                            window.viewport.id,
                            window.viewport.window.name);
                    window.viewport.window = window;
                    window.viewport.id = window.id;
                    window.viewport.lastTitle = null;
                } else if (!updateTryMergeWindowIntoHostViewports(window)) {
                    // New viewport
                    window.viewport =
                            addUpdateViewport(
                                    window,
                                    window.id,
                                    window.position,
                                    window.size,
                                    ViewportFlags.NO_FOCUS_ON_APPEARING);
                }
            } else if (window.viewportAllowPlatformMonitorExtend < 0
                    && (flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
                // Regular (non-child, non-popup) windows by default are also allowed to protrude.
                // Child windows are kept contained within their parent.
                window.viewportAllowPlatformMonitorExtend = window.viewport.platformMonitor;
            }
        }

        // Update flags
        window.viewportOwned = window == window.viewport.window;
        window.viewportID = window.viewport.id;
    }

    /**
     * Synchronize a window and the viewport it owns, during begin(). Typically we copy the window
     * position/size into the viewport, but if the platform window was moved or resized by the
     * OS/window manager we copy those back into the window.
     *
     * @param window The window that owns its viewport.
     * @param parentWindowInStack The parent window in the begin stack, may be null.
     */
    static void windowSyncOwnedViewport(@NonNull Window window, Window parentWindowInStack) {
        final Viewport viewport = window.viewport;
        boolean viewportRectChanged = false;

        // Synchronize window -> viewport in most situations. Synchronize viewport -> window in
        // case the platform window has been moved or resized from the OS/window manager.
        if (viewport.platformRequestMove) {
            window.position.set(viewport.position);
            IkGuiInternal.markIniSettingsDirty(window);
        } else if (!viewport.position.equals(window.position)) {
            viewportRectChanged = true;
            viewport.position.set(window.position);
        }

        if (viewport.platformRequestResize) {
            window.size.set(viewport.size);
            window.sizeFull.set(viewport.size);
            IkGuiInternal.markIniSettingsDirty(window);
        } else if (!viewport.size.equals(window.size)) {
            viewportRectChanged = true;
            viewport.size.set(window.size);
        }
        viewport.updateWorkRect();

        // The viewport may have changed monitor since the global update in newFrame(). Either a
        // setNextWindowPos() call in the current frame or a setWindowPos() call in the previous
        // frame may have this effect.
        if (viewportRectChanged) {
            updateViewportPlatformMonitor(viewport);
        }

        // Update common viewport flags
        final int viewportFlagsToClear =
                ViewportFlags.TOP_MOST
                        | ViewportFlags.NO_TASK_BAR_ICON
                        | ViewportFlags.NO_DECORATION
                        | ViewportFlags.NO_RENDERER_CLEAR;
        int viewportFlags = viewport.flags & ~viewportFlagsToClear;
        final int windowFlags = window.flags;
        final boolean isModal = (windowFlags & WindowFlags.INTERNAL_MODAL) != 0;
        final boolean isShortLivedFloatingWindow =
                (windowFlags
                                & (WindowFlags.INTERNAL_CHILD_MENU
                                        | WindowFlags.INTERNAL_TOOLTIP
                                        | WindowFlags.INTERNAL_POPUP))
                        != 0;
        if ((windowFlags & WindowFlags.INTERNAL_TOOLTIP) != 0) {
            viewportFlags |= ViewportFlags.TOP_MOST;
        }
        if ((context.io.configViewportsNoTaskBarIcon || isShortLivedFloatingWindow) && !isModal) {
            viewportFlags |= ViewportFlags.NO_TASK_BAR_ICON;
        }
        if (context.io.configViewportsNoDecoration || isShortLivedFloatingWindow) {
            viewportFlags |= ViewportFlags.NO_DECORATION;
        }

        // For popups and menus that may be protruding out of their parent viewport, we enable
        // NO_FOCUS_ON_CLICK so that clicking on them won't steal the OS focus away from their
        // parent window (which may be reflected in the OS title bar decoration).
        if (isShortLivedFloatingWindow && !isModal) {
            viewportFlags |= ViewportFlags.NO_FOCUS_ON_APPEARING | ViewportFlags.NO_FOCUS_ON_CLICK;
        }

        // We can overwrite viewport flags using the window class (advanced users)
        viewportFlags |= window.windowClass.viewportFlagsOverrideSet;
        viewportFlags &= ~window.windowClass.viewportFlagsOverrideClear;

        // We can also tell the backend that clearing the platform window won't be necessary, as
        // our window background is filling the viewport
        if ((windowFlags & WindowFlags.NO_BACKGROUND) == 0) {
            viewportFlags |= ViewportFlags.NO_RENDERER_CLEAR;
        }

        viewport.flags = viewportFlags;

        // Update the parent viewport ID (the fallback window test mimics the one in
        // windowSelectViewport())
        if (window.windowClass.parentViewportID != -1) {
            final int oldParentViewportID = viewport.parentViewportID;
            viewport.parentViewportID = window.windowClass.parentViewportID;
            if (viewport.parentViewportID != oldParentViewportID) {
                viewport.parentViewport = findViewportByID(viewport.parentViewportID);
            }
        } else if ((windowFlags & (WindowFlags.INTERNAL_POPUP | WindowFlags.INTERNAL_TOOLTIP)) != 0
                && parentWindowInStack != null
                && (!parentWindowInStack.isFallbackWindow || parentWindowInStack.wasActive)) {
            viewport.parentViewport = parentWindowInStack.viewport;
            viewport.parentViewportID = parentWindowInStack.viewport.id;
        } else if (context.io.configViewportsNoDefaultParent) {
            viewport.parentViewport = null;
            viewport.parentViewportID = 0;
        } else {
            viewport.parentViewport = getMainViewport();
            viewport.parentViewportID = Viewport.DEFAULT_ID;
        }
    }

    /**
     * Fetch the window whose title should be displayed for a window, which for dock node host
     * windows is the visible docked window.
     *
     * @param window The window, may be null.
     * @return The window to use for the title, may be null.
     */
    private static Window getWindowForTitleDisplay(Window window) {
        if (window == null) {
            return null;
        }
        return window.dockNodeAsHost != null ? window.dockNodeAsHost.visibleWindow : window;
    }

    /**
     * Create, update, and destroy platform windows to match each active viewport. Called by the
     * application at the end of the main loop, after render(). This handles the creation/update of
     * all OS windows via the functions defined in the platform IO.
     */
    static void updatePlatformWindows() {
        if (context.frameCountEnded != context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "Call render() or endFrame() before updatePlatformWindows()");
            return;
        }
        if (context.frameCountPlatformEnded >= context.frameCount) {
            IkGuiImplDebugTools.reportError(
                    log, "updatePlatformWindows() was already called this frame");
            return;
        }
        context.frameCountPlatformEnded = context.frameCount;
        if (!viewportsEnabled()) {
            return;
        }

        final PlatformIO platformIO = context.platformIO;

        // Create/resize/destroy platform windows to match each active viewport. Skip the main
        // viewport (index 0), which is always fully handled by the application.
        for (int i = 1; i < context.viewports.size(); ++i) {
            final Viewport viewport = context.viewports.get(i);

            // Destroy the platform window if the viewport hasn't been submitted or if it is
            // hosting a hidden window. The implicit/fallback debug window will be registering its
            // viewport then be disabled, causing a dummy destroy to be made each frame.
            final boolean destroyPlatformWindow =
                    viewport.lastFrameActive < context.frameCount - 1
                            || (viewport.window != null
                                    && !IkGuiInternal.isWindowActiveAndVisible(viewport.window));
            if (destroyPlatformWindow) {
                destroyPlatformWindow(viewport);
                continue;
            }

            // New windows that appear directly in a new viewport won't always have a size on
            // their first frame
            if (viewport.lastFrameActive < context.frameCount
                    || viewport.size.x <= 0.0f
                    || viewport.size.y <= 0.0f) {
                continue;
            }

            // Create the window
            final boolean isNewPlatformWindow = !viewport.platformWindowCreated;
            if (isNewPlatformWindow) {
                IkGuiImplDebugTools.debugLog(
                        DebugLogFlags.EVENT_VIEWPORT,
                        "[viewport] Create Platform Window %08X '%s'",
                        viewport.id,
                        viewport.getDebugName());
                if (platformIO.platformCreateWindow != null) {
                    platformIO.platformCreateWindow.accept(viewport);
                }
                if (platformIO.rendererCreateWindow != null) {
                    platformIO.rendererCreateWindow.accept(viewport);
                }
                context.platformWindowsCreatedCount++;
                viewport.lastTitle = null;
                // By clearing these we enforce a call to set the position/size below, before
                // showing the window
                viewport.lastPlatformPosition.set(Float.MAX_VALUE, Float.MAX_VALUE);
                viewport.lastPlatformSize.set(Float.MAX_VALUE, Float.MAX_VALUE);
                // We don't need to set the renderer size, as the renderer create window callback
                // is expected to have done it already
                viewport.lastRendererSize.set(viewport.size);
                viewport.platformWindowCreated = true;
            }

            // Apply the position and size (from us to the platform/renderer backends)
            if (!viewport.lastPlatformPosition.equals(viewport.position)
                    && !viewport.platformRequestMove
                    && platformIO.platformSetWindowPos != null) {
                platformIO.platformSetWindowPos.accept(viewport, viewport.position);
            }
            if (!viewport.lastPlatformSize.equals(viewport.size)
                    && !viewport.platformRequestResize
                    && platformIO.platformSetWindowSize != null) {
                platformIO.platformSetWindowSize.accept(viewport, viewport.size);
            }
            if (!viewport.lastRendererSize.equals(viewport.size)
                    && platformIO.rendererSetWindowSize != null) {
                platformIO.rendererSetWindowSize.accept(viewport, viewport.size);
            }
            viewport.lastPlatformPosition.set(viewport.position);
            viewport.lastPlatformSize.set(viewport.size);
            viewport.lastRendererSize.set(viewport.size);

            // Update the title bar (if it changed)
            final Window windowForTitle = getWindowForTitleDisplay(viewport.window);
            if (windowForTitle != null) {
                final String title = Hash.getDisplayedText(windowForTitle.name);
                if (!title.equals(viewport.lastTitle)) {
                    if (platformIO.platformSetWindowTitle != null) {
                        platformIO.platformSetWindowTitle.accept(viewport, title);
                    }
                    viewport.lastTitle = title;
                }
            }

            // Update alpha (if it changed)
            if (viewport.lastAlpha != viewport.alpha && platformIO.platformSetWindowAlpha != null) {
                platformIO.platformSetWindowAlpha.accept(viewport, viewport.alpha);
            }
            viewport.lastAlpha = viewport.alpha;

            // Optional, general purpose call to allow the backend to perform general bookkeeping
            // even if things haven't changed
            if (platformIO.platformUpdateWindow != null) {
                platformIO.platformUpdateWindow.accept(viewport);
            }

            if (isNewPlatformWindow) {
                // On startup ensure new platform windows don't steal focus (give it a few frames,
                // as nested contents may lead to the viewport being created a few frames late)
                if (context.frameCount < 3) {
                    viewport.flags |= ViewportFlags.NO_FOCUS_ON_APPEARING;
                }

                // Show the window
                if (platformIO.platformShowWindow != null) {
                    platformIO.platformShowWindow.accept(viewport);
                }
            }

            // Clear request flags
            viewport.clearRequestFlags();
        }
    }

    /**
     * A default/basic function for rendering and swapping multiple platform windows. Custom
     * renderers may prefer not to call this function at all, and instead iterate the platform IO
     * viewport list and handle rendering/sync themselves. The render and swap callbacks in the
     * platform IO only exist to allow this helper to exist.
     *
     * @param platformRenderArgument Passed to the platform render and swap callbacks, may be null.
     * @param rendererRenderArgument Passed to the renderer render and swap callbacks, may be null.
     */
    static void renderPlatformWindowsDefault(
            Object platformRenderArgument, Object rendererRenderArgument) {
        // Skip the main viewport (index 0), which is always fully handled by the application
        final PlatformIO platformIO = context.platformIO;
        for (int i = 1; i < platformIO.viewports.size(); ++i) {
            final Viewport viewport = platformIO.viewports.get(i);
            if ((viewport.flags & ViewportFlags.IS_MINIMIZED) != 0) {
                continue;
            }
            if (platformIO.platformRenderWindow != null) {
                platformIO.platformRenderWindow.accept(viewport, platformRenderArgument);
            }
            if (platformIO.rendererRenderWindow != null) {
                platformIO.rendererRenderWindow.accept(viewport, rendererRenderArgument);
            }
        }
        for (int i = 1; i < platformIO.viewports.size(); ++i) {
            final Viewport viewport = platformIO.viewports.get(i);
            if ((viewport.flags & ViewportFlags.IS_MINIMIZED) != 0) {
                continue;
            }
            if (platformIO.platformSwapBuffers != null) {
                platformIO.platformSwapBuffers.accept(viewport, platformRenderArgument);
            }
            if (platformIO.rendererSwapBuffers != null) {
                platformIO.rendererSwapBuffers.accept(viewport, rendererRenderArgument);
            }
        }
    }

    /**
     * Find the monitor that contains a position.
     *
     * @param x The x position.
     * @param y The y position.
     * @return The monitor index, or -1 if none contain the position.
     */
    static int findPlatformMonitorForPos(float x, float y) {
        final RectFloat rect = new RectFloat();
        for (int n = 0; n < context.platformIO.monitors.size(); ++n) {
            if (context.platformIO.monitors.get(n).getMainRect(rect).contains(x, y)) {
                return n;
            }
        }
        return -1;
    }

    /**
     * Search for the monitor with the largest intersection area with the given rectangle.
     *
     * @param rect The rectangle.
     * @return The monitor index, or -1 if there are no monitors.
     */
    static int findPlatformMonitorForRect(@NonNull RectFloat rect) {
        final int monitorCount = context.platformIO.monitors.size();
        if (monitorCount <= 1) {
            return monitorCount - 1;
        }

        // Use a minimum threshold of 1.0 so a zero-sized rect won't false positive, and will still
        // find the correct monitor given its position. This is necessary for tooltips which
        // always resize down to zero at first.
        final float surfaceThreshold = Math.max(rect.getWidth() * rect.getHeight() * 0.5f, 1.0f);
        // Default to the first monitor as a fallback
        int bestMonitor = 0;
        float bestMonitorSurface = 0.001f;

        final RectFloat monitorRect = new RectFloat();
        final RectFloat overlapping = new RectFloat();
        for (int n = 0; n < monitorCount && bestMonitorSurface < surfaceThreshold; ++n) {
            context.platformIO.monitors.get(n).getMainRect(monitorRect);
            if (monitorRect.contains(rect)) {
                return n;
            }
            overlapping.set(rect);
            overlapping.clipWithFull(monitorRect);
            final float overlappingSurface = overlapping.getWidth() * overlapping.getHeight();
            if (overlappingSurface < bestMonitorSurface) {
                continue;
            }
            bestMonitorSurface = overlappingSurface;
            bestMonitor = n;
        }
        return bestMonitor;
    }

    /**
     * Update the monitor of a viewport from its rectangle. We use this info to clamp windows and
     * save windows lost on a removed monitor.
     *
     * @param viewport The viewport.
     */
    static void updateViewportPlatformMonitor(@NonNull Viewport viewport) {
        viewport.platformMonitor =
                findPlatformMonitorForRect(viewport.getMainRect(new RectFloat()));
    }

    /**
     * Fetch the monitor that a viewport is on. Don't hold on to it across frames.
     *
     * @param viewport The viewport.
     * @return The monitor, or the fallback monitor if unknown. Never null.
     */
    static PlatformMonitor getViewportPlatformMonitor(@NonNull Viewport viewport) {
        final int monitorIndex = viewport.platformMonitor;
        if (monitorIndex >= 0 && monitorIndex < context.platformIO.monitors.size()) {
            return context.platformIO.monitors.get(monitorIndex);
        }
        return context.fallbackMonitor;
    }

    /**
     * Destroy the platform window for a viewport, if it was created.
     *
     * @param viewport The viewport.
     */
    static void destroyPlatformWindow(@NonNull Viewport viewport) {
        final PlatformIO platformIO = context.platformIO;
        if (viewport.platformWindowCreated) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_VIEWPORT,
                    "[viewport] Destroy Platform Window %08X '%s'",
                    viewport.id,
                    viewport.getDebugName());
            if (platformIO.rendererDestroyWindow != null) {
                platformIO.rendererDestroyWindow.accept(viewport);
            }
            if (platformIO.platformDestroyWindow != null) {
                platformIO.platformDestroyWindow.accept(viewport);
            }
            if (viewport.rendererUserData != null || viewport.platformUserData != null) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Backend did not clear the user data when destroying viewport {}",
                        viewport.id);
            }

            // Don't clear the created flag for the main viewport, as we initially set that up to
            // true when creating the context
            if (viewport.id != Viewport.DEFAULT_ID) {
                viewport.platformWindowCreated = false;
            }
        } else if (viewport.rendererUserData != null
                || viewport.platformUserData != null
                || viewport.platformHandle != null) {
            IkGuiImplDebugTools.reportError(
                    log, "Viewport {} has backend data but no platform window", viewport.id);
        }
        viewport.rendererUserData = null;
        viewport.platformUserData = null;
        viewport.platformHandle = null;
        viewport.clearRequestFlags();
    }

    /**
     * Call the destroy window callbacks for every viewport (including the main viewport), to give
     * the backend a chance to clear any data they may have stored. Call this when shutting down the
     * backend, before destroying the context.
     */
    static void destroyPlatformWindows() {
        for (Viewport viewport : context.viewports) {
            destroyPlatformWindow(viewport);
        }
    }

    /**
     * Fetch the background draw list for a viewport, creating it on demand because they are not
     * frequently used for all viewports.
     *
     * @param viewport The viewport.
     * @return The background draw list.
     */
    static DrawList getViewportBackgroundDrawList(@NonNull Viewport viewport) {
        if (viewport.backgroundDrawList == null) {
            viewport.backgroundDrawList = new DrawList("##Background");
        }
        if (viewport.backgroundDrawListLastFrameActive != context.frameCount) {
            viewport.backgroundDrawList.clear();
            viewport.backgroundDrawListLastFrameActive = context.frameCount;
        }
        return viewport.backgroundDrawList;
    }

    /**
     * Fetch the foreground draw list for a viewport, creating it on demand because they are not
     * frequently used for all viewports.
     *
     * @param viewport The viewport.
     * @return The foreground draw list.
     */
    static DrawList getViewportForegroundDrawList(@NonNull Viewport viewport) {
        if (viewport.foregroundDrawList == null) {
            viewport.foregroundDrawList = new DrawList("##Foreground");
        }
        if (viewport.foregroundDrawListLastFrameActive != context.frameCount) {
            viewport.foregroundDrawList.clear();
            viewport.foregroundDrawListLastFrameActive = context.frameCount;
        }
        return viewport.foregroundDrawList;
    }

    /**
     * Fetch the background draw list for a viewport.
     *
     * @param viewport The viewport, or null for the viewport of the current window.
     * @return The background draw list.
     */
    static DrawList getBackgroundDrawList(Viewport viewport) {
        return getViewportBackgroundDrawList(viewportOrCurrent(viewport));
    }

    /**
     * Fetch the foreground draw list for a viewport.
     *
     * @param viewport The viewport, or null for the viewport of the current window.
     * @return The foreground draw list.
     */
    static DrawList getForegroundDrawList(Viewport viewport) {
        return getViewportForegroundDrawList(viewportOrCurrent(viewport));
    }

    /**
     * Resolve a viewport that may be null to the viewport of the current window.
     *
     * @param viewport The viewport, may be null.
     * @return The viewport, the current window's viewport, or the main viewport.
     */
    private static Viewport viewportOrCurrent(Viewport viewport) {
        if (viewport != null) {
            return viewport;
        }
        if (context.windowCurrent != null && context.windowCurrent.viewport != null) {
            return context.windowCurrent.viewport;
        }
        return getMainViewport();
    }

    /**
     * Set up the draw data of a viewport for rendering.
     *
     * @param viewport The viewport.
     */
    static void initViewportDrawData(@NonNull Viewport viewport) {
        final DrawData drawData = viewport.drawData;
        drawData.drawLists.clear();
        viewport.drawDataBuilderTopLayer.clear();

        // When minimized, we report the display size as zero to be consistent with non-viewport
        // mode, and to allow applications/backends to easily skip rendering
        final boolean isMinimized = (viewport.flags & ViewportFlags.IS_MINIMIZED) != 0;

        drawData.valid = true;
        drawData.displayPosition.set(viewport.position);
        if (isMinimized) {
            drawData.displaySize.set(0.0f, 0.0f);
        } else {
            drawData.displaySize.set(viewport.size);
        }
        drawData.framebufferScale.set(viewport.framebufferScale);
        drawData.ownerViewport = viewport;
    }

    /**
     * Fetch the draw data for the main viewport.
     *
     * @return The draw data, or null if it is not valid (between newFrame() and render()).
     */
    static DrawData getDrawData() {
        final DrawData drawData = getMainViewport().drawData;
        return drawData.valid ? drawData : null;
    }

    /**
     * Set the viewport of the next window.
     *
     * @param viewportID The viewport ID.
     */
    static void setNextWindowViewport(int viewportID) {
        context.nextWindowData.fieldFlags |= NextWindowFlags.HAS_VIEWPORT;
        context.nextWindowData.viewportID = viewportID;
    }

    /**
     * Fetch the viewport of the current window.
     *
     * @return The viewport of the current window.
     */
    static Viewport getWindowViewport() {
        final Window window = context.windowCurrent;
        if (window == null) {
            IkGuiImplDebugTools.reportError(log, "getWindowViewport() requires a current window");
            return getMainViewport();
        }
        if (context.currentViewport != null && context.currentViewport != window.viewport) {
            IkGuiImplDebugTools.reportError(
                    log, "The current viewport does not match the current window viewport");
        }
        return window.viewport;
    }

    /**
     * Check the viewport configuration at the start of the frame, disabling multiple viewports if
     * the backends don't support them.
     */
    static void errorCheckNewFrameSanityChecks() {
        final PlatformIO platformIO = context.platformIO;
        if ((context.io.configFlags & ConfigFlags.VIEWPORTS_ENABLE) == 0) {
            return;
        }

        if (context.frameCount == 1
                && (context.configFlagsLastFrame & ConfigFlags.VIEWPORTS_ENABLE) == 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Set VIEWPORTS_ENABLE before the first call to newFrame(), otherwise you will"
                            + " lose your .ini settings");
        }

        final int bothBackends =
                BackendFlags.PLATFORM_HAS_VIEWPORTS | BackendFlags.RENDERER_HAS_VIEWPORTS;
        if ((context.io.backendFlags & bothBackends) != bothBackends) {
            // Disable the feature, our backends do not support it
            context.io.configFlags &= ~ConfigFlags.VIEWPORTS_ENABLE;
            return;
        }

        if (context.frameCount != 0 && context.frameCount != context.frameCountPlatformEnded) {
            IkGuiImplDebugTools.reportError(
                    log, "Call updatePlatformWindows() in the main loop after render()");
        }
        if (platformIO.platformCreateWindow == null
                || platformIO.platformDestroyWindow == null
                || platformIO.platformGetWindowPos == null
                || platformIO.platformSetWindowPos == null
                || platformIO.platformGetWindowSize == null
                || platformIO.platformSetWindowSize == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Platform backend didn't install the viewport handlers");
        }
        if (platformIO.monitors.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Platform backend didn't set up the monitor list");
        }
        final Viewport mainViewport = getMainViewport();
        if (mainViewport.platformUserData == null && mainViewport.platformHandle == null) {
            IkGuiImplDebugTools.reportError(
                    log, "Platform backend didn't set up the main viewport");
        }
        if (context.io.configDockingTransparentPayload
                && (context.io.configFlags & ConfigFlags.DOCKING_ENABLE) != 0
                && platformIO.platformSetWindowAlpha == null) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "The platform set window alpha handler is required to use"
                            + " io.configDockingTransparentPayload");
        }

        // Perform simple checks on platform monitor data
        final RectFloat mainRect = new RectFloat();
        final RectFloat workRect = new RectFloat();
        for (PlatformMonitor monitor : platformIO.monitors) {
            if (monitor.mainSize.x <= 0.0f || monitor.mainSize.y <= 0.0f) {
                IkGuiImplDebugTools.reportError(log, "Monitor main bounds are not set up properly");
            }
            if (!monitor.getMainRect(mainRect).contains(monitor.getWorkRect(workRect))) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "Monitor work bounds are not set up properly. If you don't have work area"
                                + " information, copy the main position and size into them");
            }
            if (monitor.dpiScale <= 0.0f || monitor.dpiScale >= 99.0f) {
                IkGuiImplDebugTools.reportError(log, "Monitor DPI scale is invalid");
            }
        }
    }

    /**
     * Check if a dock node flag is set on a window's dock node, used for closing windows by
     * platform request.
     *
     * @param window The window.
     * @return True if the window is in a dockspace.
     */
    static boolean isWindowInDockSpace(@NonNull Window window) {
        return window.dockNode != null
                && (window.dockNode.mergedFlags & DockNodeFlags.INTERNAL_DOCK_SPACE) != 0;
    }
}
