package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * A platform window that we render into. The main viewport covers the application window. When
 * multiple viewports are enabled, windows that are moved outside the main viewport get their own
 * platform (OS) window, each represented by a viewport.
 *
 * <p>About the main area vs the work area:
 *
 * <ul>
 *   <li>Main area = the entire viewport.
 *   <li>Work area = the entire viewport minus sections used by main menu bars (for platform
 *       windows), or by the task bar (for platform monitors).
 *   <li>Windows are generally trying to stay within the work area of their host viewport.
 * </ul>
 */
public class Viewport {
    /** The ID of the main viewport. */
    public static final int DEFAULT_ID = 0x11111111;

    /** Unique identifier for the viewport. */
    public int id;

    /**
     * @see ViewportFlags
     */
    public int flags;

    /**
     * Main area: position of the viewport. Coordinates are the same as OS desktop coordinates when
     * multiple viewports are enabled.
     */
    public final Vector2f position;

    /** Main area: size of the viewport. */
    public final Vector2f size;

    /**
     * Density of the viewport for high DPI displays, which is always 1, 1 on Windows but may be 2,
     * 2 on macOS.
     */
    public final Vector2f framebufferScale;

    /**
     * Work area: position of the viewport minus task bars, menus bars, status bars. Should be
     * greater than or equal to the main area position.
     */
    public final Vector2f workPosition;

    /**
     * Work area: size of the viewport minus task bars, menu bars, status bars. Should be less than
     * or equal to the main area size.
     */
    public final Vector2f workSize;

    /** The DPI scale, where 1.0 = 96 DPI = no extra scale. */
    public float dpiScale;

    /**
     * (Advanced) 0 for no parent. Tells the platform backend to set up a parent/child relationship
     * between platform windows.
     */
    public int parentViewportID;

    /** (Advanced) Shortcut to the viewport with an ID of {@link #parentViewportID}, may be null. */
    public Viewport parentViewport;

    /**
     * The draw data for this viewport. Valid after render() and until the next call to newFrame(),
     * see {@link DrawData#valid}.
     */
    public final DrawData drawData;

    /**
     * Storage for a renderer backend, like a swap chain or framebuffers. Generally set by the
     * renderer create window callback. The library never uses this.
     */
    public Object rendererUserData;

    /**
     * Storage for a platform backend, like windowing info or a rendering context. Generally set by
     * the platform create window callback. The library never uses this.
     */
    public Object platformUserData;

    /**
     * A higher-level platform window handle, like the GLFW window handle, used to look up viewports
     * by handle.
     */
    public Object platformHandle;

    /**
     * The platform window has been created (the platform create window callback was called). This
     * is false during the first frame where a viewport is being created.
     */
    public boolean platformWindowCreated;

    /**
     * The platform window requested a move (it was moved by the OS/window manager, so the
     * authoritative position will be the OS window position).
     */
    public boolean platformRequestMove;

    /**
     * The platform window requested a resize (it was resized by the OS/window manager, so the
     * authoritative size will be the OS window size).
     */
    public boolean platformRequestResize;

    /** The platform window requested closing (like pressing Alt+F4). */
    public boolean platformRequestClose;

    /**
     * Set when the viewport is owned by a window (and {@link ViewportFlags#CAN_HOST_OTHER_WINDOWS}
     * is not set).
     */
    public Window window;

    /** The index of this viewport in the context viewport list. */
    public int index;

    /** The last frame number this viewport was activated by a window. */
    public int lastFrameActive;

    /**
     * Last stamp number from when a window hosted by this viewport was focused. By comparing this
     * value between viewports, we have an implicit viewport z-order we use as a fallback.
     */
    public int lastFocusedStampCount;

    /** The last title we sent to the platform, so we only update it when it changes. */
    public String lastTitle;

    public final Vector2f lastPosition;
    public final Vector2f lastSize;

    /** Window opacity (when dragging dockable windows/viewports we make them transparent). */
    public float alpha;

    public float lastAlpha;

    /**
     * Instead of maintaining the last focused window (which is harder to maintain correctly), we
     * just store whether there was a nav window the last time this viewport was focused.
     */
    public boolean lastFocusedHadNavWindow;

    /** The index of the monitor this viewport is on, or -1 if unknown. */
    public int platformMonitor;

    /**
     * Background draw list, created on demand. Drawn behind all windows in this viewport. May be
     * null.
     */
    public DrawList backgroundDrawList;

    /**
     * Foreground draw list, created on demand. Drawn in front of all windows in this viewport. May
     * be null.
     */
    public DrawList foregroundDrawList;

    /** The last frame the background draw list was used. */
    public int backgroundDrawListLastFrameActive;

    /** The last frame the foreground draw list was used. */
    public int foregroundDrawListLastFrameActive;

    /**
     * Temporary storage while building the draw data, for draw lists that go on top of other
     * windows (like tooltips).
     */
    public final List<DrawList> drawDataBuilderTopLayer;

    public final Vector2f lastPlatformPosition;
    public final Vector2f lastPlatformSize;
    public final Vector2f lastRendererSize;

    /**
     * Work area top-left inset, from the main area. This is locked at the start of the frame from
     * the build values of the previous frame.
     */
    public final Vector2f workInsetMin;

    /**
     * Work area bottom-right inset, from the main area. This is locked at the start of the frame
     * from the build values of the previous frame.
     */
    public final Vector2f workInsetMax;

    /**
     * Work area top-left inset being accumulated during the current frame (e.g. by the main menu
     * bar), applied next frame.
     */
    public final Vector2f buildWorkInsetMin;

    /**
     * Work area bottom-right inset being accumulated during the current frame, applied next frame.
     */
    public final Vector2f buildWorkInsetMax;

    /**
     * Create a viewport.
     *
     * @param sharedTextures The texture list that is shared by the draw data of all viewports.
     */
    public Viewport(@NonNull DrawTextures sharedTextures) {
        id = 0;
        flags = ViewportFlags.NONE;
        position = new Vector2f(0.0f, 0.0f);
        size = new Vector2f(0.0f, 0.0f);
        framebufferScale = new Vector2f(0.0f, 0.0f);
        workPosition = new Vector2f(0.0f, 0.0f);
        workSize = new Vector2f(0.0f, 0.0f);
        dpiScale = 0.0f;
        parentViewportID = 0;
        parentViewport = null;
        drawData = new DrawData(sharedTextures);
        rendererUserData = null;
        platformUserData = null;
        platformHandle = null;
        platformWindowCreated = false;
        platformRequestMove = false;
        platformRequestResize = false;
        platformRequestClose = false;
        window = null;
        index = -1;
        lastFrameActive = -1;
        lastFocusedStampCount = -1;
        lastTitle = null;
        lastPosition = new Vector2f(0.0f, 0.0f);
        lastSize = new Vector2f(0.0f, 0.0f);
        alpha = 1.0f;
        lastAlpha = 1.0f;
        lastFocusedHadNavWindow = false;
        platformMonitor = -1;
        backgroundDrawList = null;
        foregroundDrawList = null;
        backgroundDrawListLastFrameActive = -1;
        foregroundDrawListLastFrameActive = -1;
        drawDataBuilderTopLayer = new ArrayList<>();
        lastPlatformPosition = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        lastPlatformSize = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        lastRendererSize = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        workInsetMin = new Vector2f(0.0f, 0.0f);
        workInsetMax = new Vector2f(0.0f, 0.0f);
        buildWorkInsetMin = new Vector2f(0.0f, 0.0f);
        buildWorkInsetMax = new Vector2f(0.0f, 0.0f);
    }

    /** Clear the requests from the platform. */
    public void clearRequestFlags() {
        platformRequestClose = false;
        platformRequestMove = false;
        platformRequestResize = false;
    }

    /**
     * A name for the viewport, for debugging.
     *
     * @return The name of the owning window, or "n/a" if there is none.
     */
    public String getDebugName() {
        return window != null ? window.name : "n/a";
    }

    /**
     * Fetch the work rectangle that is being built during the current frame, which takes into
     * account anything that has already claimed space this frame (like the main menu bar).
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getBuildWorkRect(@NonNull RectFloat output) {
        final float minX = position.x + Math.max(0.0f, buildWorkInsetMin.x);
        final float minY = position.y + Math.max(0.0f, buildWorkInsetMin.y);
        final float maxX = position.x + size.x - Math.max(0.0f, buildWorkInsetMax.x);
        final float maxY = position.y + size.y - Math.max(0.0f, buildWorkInsetMax.y);
        output.set(minX, minY, Math.max(minX, maxX), Math.max(minY, maxY));
        return output;
    }

    /**
     * Lock in the work insets that were built over the last frame, and reset the build insets for
     * the next frame. Does not update the work area, see {@link #updateWorkRect()}.
     */
    public void lockWorkInsets() {
        workInsetMin.set(buildWorkInsetMin);
        workInsetMax.set(buildWorkInsetMax);
        buildWorkInsetMin.set(0.0f, 0.0f);
        buildWorkInsetMax.set(0.0f, 0.0f);
    }

    /** Update the work area from the main area and the locked insets. */
    public void updateWorkRect() {
        workPosition.set(
                position.x + Math.max(0.0f, workInsetMin.x),
                position.y + Math.max(0.0f, workInsetMin.y));
        workSize.set(
                Math.max(
                        0.0f,
                        size.x - Math.max(0.0f, workInsetMin.x) - Math.max(0.0f, workInsetMax.x)),
                Math.max(
                        0.0f,
                        size.y - Math.max(0.0f, workInsetMin.y) - Math.max(0.0f, workInsetMax.y)));
    }

    /**
     * Fetch the main rectangle of the viewport.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getMainRect(@NonNull RectFloat output) {
        output.set(position.x, position.y, position.x + size.x, position.y + size.y);
        return output;
    }

    /**
     * Fetch the work rectangle of the viewport.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getWorkRect(@NonNull RectFloat output) {
        output.set(
                workPosition.x,
                workPosition.y,
                workPosition.x + workSize.x,
                workPosition.y + workSize.y);
        return output;
    }

    /**
     * Fetch the center of the main rectangle.
     *
     * @param output Where to store the result.
     * @return The output vector, for convenience.
     */
    public Vector2f getCenter(@NonNull Vector2f output) {
        return output.set(position.x + size.x * 0.5f, position.y + size.y * 0.5f);
    }

    /**
     * Fetch the center of the work rectangle.
     *
     * @param output Where to store the result.
     * @return The output vector, for convenience.
     */
    public Vector2f getWorkCenter(@NonNull Vector2f output) {
        return output.set(workPosition.x + workSize.x * 0.5f, workPosition.y + workSize.y * 0.5f);
    }
}
