package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hooks for interacting with the platform (operating system, windowing library) and renderer. Any
 * of these may be null, in which case we fall back to a reasonable default behavior.
 *
 * <h2>Multiple viewports</h2>
 *
 * This allows windows to be dragged outside the main application window, by creating new platform
 * (OS) windows on the fly and rendering into them. We manage the viewports, and the backend creates
 * and maintains one platform window for each of them.
 *
 * <ul>
 *   <li>When multiple viewports are enabled, all coordinates become absolute desktop coordinates
 *       (the same as OS coordinates). So setNextWindowPos(0, 0) will position a window relative to
 *       the primary monitor. To position windows relative to the main application window, use the
 *       main viewport position as a base.
 *   <li>Backends: set the platform and renderer callbacks below, set {@link
 *       com.ikalagaming.graphics.frontend.gui.flags.BackendFlags#PLATFORM_HAS_VIEWPORTS} and {@link
 *       com.ikalagaming.graphics.frontend.gui.flags.BackendFlags#RENDERER_HAS_VIEWPORTS}, update
 *       the {@link #monitors} list every frame, set up the main viewport platform handle, and
 *       update the mouse position in absolute coordinates.
 *   <li>Applications: enable the feature with {@link
 *       com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags#VIEWPORTS_ENABLE}, and call
 *       updatePlatformWindows() and renderPlatformWindowsDefault() after render() in the main loop.
 * </ul>
 *
 * <p>The general idea is that newFrame() reads the current platform/OS state, and
 * updatePlatformWindows() writes to it. Platform functions are typically called before their
 * renderer counterpart, apart from destroy which is called the other way. Each callback lists which
 * functions call it:
 *
 * <ul>
 *   <li>N = newFrame(), at the beginning of the frame to read info from platform windows.
 *   <li>F = begin() or endFrame(), during the frame.
 *   <li>U = updatePlatformWindows(), after the frame to create and update platform windows.
 *   <li>R = renderPlatformWindowsDefault(), to render.
 *   <li>D = destroyPlatformWindows(), at shutdown.
 * </ul>
 */
public class PlatformIO {
    /**
     * Fetch the clipboard text from the platform. If null, we use an internal clipboard that is
     * only shared within the application.
     */
    public Supplier<String> getClipboardTextFunction;

    /**
     * Set the clipboard text for the platform. If null, we use an internal clipboard that is only
     * shared within the application.
     */
    public Consumer<String> setClipboardTextFunction;

    /**
     * Open a link, folder or file with the operating system shell, used by textLinkOpenURL().
     * Should return false on failure, but some platforms may always return true. Defaults to {@link
     * #openInShellDefault(String)}.
     */
    public Predicate<String> openInShellFunction = PlatformIO::openInShellDefault;

    /**
     * The decimal point for the locale, used by numeric text inputs. Typed '.' and ',' characters
     * are replaced with this.
     */
    public char localeDecimalPoint = '.';

    /**
     * Notify the platform of the input method editor (IME) position, for typing languages like
     * Chinese/Japanese/Korean. May be null.
     */
    public BiConsumer<Viewport, PlatformImeData> setImeDataFunction;

    /**
     * The date at the beginning of the application session, as an integer in the form YYYYMMDD,
     * e.g. 20261231. Used to record when .ini entries were last used. Set to 0 if unavailable.
     * Defaults to the current local date when the platform IO is created.
     */
    public int platformSessionDate = toPackedDate(LocalDate.now());

    /**
     * Convert a date to the YYYYMMDD integer form used for session dates.
     *
     * @param date The date.
     * @return The date as YYYYMMDD.
     */
    public static int toPackedDate(LocalDate date) {
        return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    // ---------------------------------------------------------------------------------------------
    // Platform backend functions for multiple viewports (e.g. GLFW)
    // ---------------------------------------------------------------------------------------------

    /** (U) Create a new platform window for the given viewport. It should start out hidden. */
    public Consumer<Viewport> platformCreateWindow;

    /** (N, U, D) Destroy the platform window for the viewport. */
    public Consumer<Viewport> platformDestroyWindow;

    /**
     * (U) Show a newly created window. Newly created windows are initially hidden so the position,
     * size and title can be set on them before showing the window.
     */
    public Consumer<Viewport> platformShowWindow;

    /** (U) Set the platform window position, given the upper-left corner of the client area. */
    public BiConsumer<Viewport, Vector2f> platformSetWindowPos;

    /** (N) Fetch the platform window position, of the upper-left corner of the client area. */
    public Function<Viewport, Vector2f> platformGetWindowPos;

    /** (U) Set the platform window client area size, ignoring OS decorations like a title bar. */
    public BiConsumer<Viewport, Vector2f> platformSetWindowSize;

    /** (N) Fetch the platform window client area size. */
    public Function<Viewport, Vector2f> platformGetWindowSize;

    /**
     * (N) Fetch the viewport density. Always 1, 1 on Windows, often 2, 2 on high DPI displays on
     * macOS. Must be integer values.
     */
    public Function<Viewport, Vector2f> platformGetWindowFramebufferScale;

    /** (N) Move the window to the front and set input focus. */
    public Consumer<Viewport> platformSetWindowFocus;

    /** (U) Check if the platform window has focus. */
    public Predicate<Viewport> platformGetWindowFocus;

    /**
     * (N) Check if the platform window is minimized. When minimized, we generally won't attempt to
     * get or set the size, and contents will be culled more easily.
     */
    public Predicate<Viewport> platformGetWindowMinimized;

    /** (U) Set the platform window title. */
    public BiConsumer<Viewport, String> platformSetWindowTitle;

    /** (U) Optional, set up global transparency for the window (not per-pixel transparency). */
    public BiConsumer<Viewport, Float> platformSetWindowAlpha;

    /**
     * (U) Optional, called by updatePlatformWindows() so the platform backend can perform general
     * bookkeeping every frame.
     */
    public Consumer<Viewport> platformUpdateWindow;

    /**
     * (R) Optional, platform side of rendering. Often unused, or just setting a "current" context
     * for OpenGL bindings. The second argument is the value passed to
     * renderPlatformWindowsDefault().
     */
    public BiConsumer<Viewport, Object> platformRenderWindow;

    /**
     * (R) Optional, call present/swap buffers on the platform side, which is often unused. The
     * second argument is the value passed to renderPlatformWindowsDefault().
     */
    public BiConsumer<Viewport, Object> platformSwapBuffers;

    /** (N) Optional, fetch the DPI scale for the viewport, where 1.0 = 96 DPI. */
    public Function<Viewport, Float> platformGetWindowDpiScale;

    /**
     * (F) Optional, called during begin() every time the viewport we are outputting into changes,
     * so the backend has a chance to swap fonts to adjust style.
     */
    public Consumer<Viewport> platformOnChangedViewport;

    /**
     * (N) Optional, fetch the initial work area insets for the viewport, which won't be covered by
     * the main menu bar, dockspace over viewport, etc. Defaults to 0. The result is (left, top,
     * right, bottom) and must not be negative.
     */
    public Function<Viewport, Vector4f> platformGetWindowWorkAreaInsets;

    // ---------------------------------------------------------------------------------------------
    // Renderer backend functions for multiple viewports (e.g. OpenGL, Vulkan)
    // ---------------------------------------------------------------------------------------------

    /** (U) Create a swap chain, framebuffers, etc. Called after the platform window is created. */
    public Consumer<Viewport> rendererCreateWindow;

    /**
     * (N, U, D) Destroy the swap chain, framebuffers, etc. Called before the platform window is
     * destroyed.
     */
    public Consumer<Viewport> rendererDestroyWindow;

    /**
     * (U) Resize the swap chain, framebuffers, etc. Called after the platform window size is set.
     */
    public BiConsumer<Viewport, Vector2f> rendererSetWindowSize;

    /**
     * (R) Optional, clear the framebuffer, set up the render target, then render the viewport draw
     * data. The second argument is the value passed to renderPlatformWindowsDefault().
     */
    public BiConsumer<Viewport, Object> rendererRenderWindow;

    /**
     * (R) Optional, call present/swap buffers. The second argument is the value passed to
     * renderPlatformWindowsDefault().
     */
    public BiConsumer<Viewport, Object> rendererSwapBuffers;

    /**
     * Optional, the list of monitors. Updated by the application/backend every frame, to
     * dynamically support changing monitor or DPI configurations. Used to query DPI info, and to
     * keep popups and tooltips within the same monitor instead of straddling monitors.
     */
    public final List<PlatformMonitor> monitors = new ArrayList<>();

    /**
     * Output: the main viewport, followed by all secondary viewports that need to be rendered.
     * Updated by endFrame() or render().
     */
    public final List<Viewport> viewports = new ArrayList<>();

    /** Clear all the platform functions, typically called on platform backend shutdown. */
    public void clearPlatformHandlers() {
        getClipboardTextFunction = null;
        setClipboardTextFunction = null;
        setImeDataFunction = null;
        // Keep the default rather than losing the functionality when a backend is recreated
        openInShellFunction = PlatformIO::openInShellDefault;
        platformCreateWindow = null;
        platformDestroyWindow = null;
        platformShowWindow = null;
        platformSetWindowPos = null;
        platformGetWindowPos = null;
        platformSetWindowSize = null;
        platformGetWindowSize = null;
        platformGetWindowFramebufferScale = null;
        platformSetWindowFocus = null;
        platformGetWindowFocus = null;
        platformGetWindowMinimized = null;
        platformSetWindowTitle = null;
        platformSetWindowAlpha = null;
        platformUpdateWindow = null;
        platformRenderWindow = null;
        platformSwapBuffers = null;
        platformGetWindowDpiScale = null;
        platformOnChangedViewport = null;
        platformGetWindowWorkAreaInsets = null;
    }

    /** Clear all the renderer functions, typically called on renderer backend shutdown. */
    public void clearRendererHandlers() {
        rendererCreateWindow = null;
        rendererDestroyWindow = null;
        rendererSetWindowSize = null;
        rendererRenderWindow = null;
        rendererSwapBuffers = null;
    }

    /**
     * Open a link, folder or file with the default application, using the operating system shell.
     * The process is started without waiting for it to finish.
     *
     * @param path The URL or path to open.
     * @return True if the process was started, false if it failed or the path was empty.
     */
    public static boolean openInShellDefault(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final List<String> command;
        if (os.startsWith("windows")) {
            command = List.of("rundll32", "url.dll,FileProtocolHandler", path);
        } else if (os.startsWith("mac")) {
            command = List.of("open", "--", path);
        } else {
            command = List.of("xdg-open", path);
        }
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
