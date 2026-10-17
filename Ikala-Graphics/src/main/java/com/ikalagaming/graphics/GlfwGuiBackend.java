package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.PlatformIO;
import com.ikalagaming.graphics.gui.data.PlatformMonitor;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.flags.ViewportFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.util.List;

/**
 * Connects IkGui to GLFW: the clipboard, mouse cursors, gamepads, and every window's input. With
 * IkGui viewports on, it also creates a window for each viewport IkGui asks for, and draws it like
 * any other window. This is a port of Dear ImGui's GLFW backend, with the renderer side handed to
 * {@link GraphicsManager}.
 *
 * <p>Viewports put mouse positions in desktop coordinates, so positions from a window are offset by
 * where the window is. Without them, positions are relative to the main window as before.
 * Everything here runs on the main thread.
 */
@Slf4j
final class GlfwGuiBackend {

    /** What we know about the window of a viewport, kept in its platform user data. */
    private static final class ViewportData {
        /** The window. */
        final Window window;

        /** Whether we created the window for the viewport, rather than it being the main one. */
        final boolean owned;

        /** How IkGui changes the cursor of the window. */
        final GlfwMouseCursors.CursorTarget cursorTarget;

        /** The frame we moved the window in, so the move event that follows is ignored. */
        int ignorePositionEventFrame = -1;

        /** The frame we resized the window in, so the resize event that follows is ignored. */
        int ignoreSizeEventFrame = -1;

        /**
         * Track a window.
         *
         * @param window The window.
         * @param owned Whether we created it for the viewport.
         */
        ViewportData(@NonNull Window window, boolean owned) {
            this.window = window;
            this.owned = owned;
            final long handle = window.getWindowHandle();
            cursorTarget =
                    new GlfwMouseCursors.CursorTarget() {
                        @Override
                        public int getCursorMode() {
                            return glfwGetInputMode(handle, GLFW_CURSOR);
                        }

                        @Override
                        public void setCursorMode(int mode) {
                            glfwSetInputMode(handle, GLFW_CURSOR, mode);
                        }

                        @Override
                        public void setCursor(long cursor) {
                            glfwSetCursor(handle, cursor);
                        }
                    };
        }
    }

    /** The main window, which the main viewport is drawn in. */
    private final Window mainWindow;

    /** The mouse cursors shown for IkGui, null until installed. */
    private GlfwMouseCursors mouseCursors;

    /** The window the OS cursor is over, or null if it isn't over any of ours. */
    private Window mouseWindow;

    /** Where the mouse last was inside one of our windows, to restore when it comes back. */
    private final Vector2f lastValidMousePosition =
            new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);

    /** Whether the monitors changed since we last listed them. */
    private boolean monitorsChanged = true;

    /** Reused for reading window positions and sizes. */
    private final int[] intX = new int[1];

    /** Reused for reading window positions and sizes. */
    private final int[] intY = new int[1];

    /** Reused for reading cursor positions. */
    private final double[] doubleX = new double[1];

    /** Reused for reading cursor positions. */
    private final double[] doubleY = new double[1];

    /**
     * Create the backend for the main window. Call {@link #install()} once the IkGui context
     * exists.
     *
     * @param mainWindow The main window.
     */
    GlfwGuiBackend(@NonNull Window mainWindow) {
        this.mainWindow = mainWindow;
    }

    /**
     * Hook IkGui up to GLFW: the platform functions, the main window's input, and the backend flags
     * for what we support.
     */
    void install() {
        final IkIO io = IkGui.getIO();
        final PlatformIO platformIO = IkGui.getPlatformIO();
        final long mainHandle = mainWindow.getWindowHandle();
        platformIO.getClipboardTextFunction =
                () -> {
                    final String text = glfwGetClipboardString(mainHandle);
                    return text == null ? "" : text;
                };
        platformIO.setClipboardTextFunction = text -> glfwSetClipboardString(mainHandle, text);

        // We can honor io.wantSetMousePosition requests and getMouseCursor() values
        io.backendFlags |= BackendFlags.HAS_SET_MOUSE_POS | BackendFlags.HAS_MOUSE_CURSORS;
        // We create windows for viewports and draw them, and can tell which one the mouse is
        // over, letting it pass through viewports that don't take input
        io.backendFlags |=
                BackendFlags.PLATFORM_HAS_VIEWPORTS
                        | BackendFlags.RENDERER_HAS_VIEWPORTS
                        | BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT;
        if (mouseCursors == null) {
            mouseCursors = GlfwMouseCursors.createStandardCursors();
        }

        installViewportFunctions(platformIO);
        final Viewport mainViewport = IkGui.getMainViewport();
        mainViewport.platformHandle = mainWindow;
        mainViewport.platformUserData = new ViewportData(mainWindow, false);

        glfwSetMonitorCallback((monitor, event) -> monitorsChanged = true);
        updateMonitors();

        installInputCallbacks(mainWindow);
    }

    /**
     * Unhook IkGui from GLFW, destroying the windows made for viewports. Call before the renderer
     * cleans up, since their swapchains go first.
     */
    void shutdown() {
        IkGui.destroyPlatformWindows();
        GLFWMonitorCallback monitorCallback = glfwSetMonitorCallback(null);
        if (monitorCallback != null) {
            monitorCallback.free();
        }
        if (mouseCursors != null) {
            mouseCursors.destroy();
            mouseCursors = null;
        }
        final PlatformIO platformIO = IkGui.getPlatformIO();
        platformIO.clearPlatformHandlers();
        platformIO.clearRendererHandlers();
        IkGui.getIO().backendFlags &=
                ~(BackendFlags.HAS_SET_MOUSE_POS
                        | BackendFlags.HAS_MOUSE_CURSORS
                        | BackendFlags.PLATFORM_HAS_VIEWPORTS
                        | BackendFlags.RENDERER_HAS_VIEWPORTS
                        | BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT);
    }

    /**
     * Whether IkGui viewports are on, which puts mouse positions in desktop coordinates.
     *
     * @return True if viewports are on.
     */
    private static boolean viewportsEnabled() {
        return (IkGui.getIO().configFlags & ConfigFlags.VIEWPORTS_ENABLE) != 0;
    }

    /**
     * Handle input for every window, then update what IkGui needs from GLFW for the next frame: the
     * monitors, the mouse, the cursor shapes and the gamepads.
     */
    void pollEvents() {
        glfwPollEvents();
        if (IkGui.getContext() == null) {
            return;
        }
        final IkIO io = IkGui.getIO();
        if (monitorsChanged) {
            updateMonitors();
        }
        // Automation moves its own mouse, which counts as inside even when the real one isn't
        final boolean driving = isAutomationDriving();
        io.mouseInsideWindow = mouseWindow != null || driving;
        updateMouseData(io, driving);
        updateMouseCursors(io);
        if (!driving) {
            updateGamepad(io);
        }
    }

    /**
     * The windows IkGui draws in this frame, with what we know about them: the main window, and
     * with viewports on, every window made for a viewport.
     *
     * @return The viewports that have windows, the main one first.
     */
    private static List<Viewport> viewportsWithWindows() {
        if (!viewportsEnabled()) {
            return List.of(IkGui.getMainViewport());
        }
        return IkGui.getPlatformIO().viewports.stream()
                .filter(viewport -> viewport.platformUserData instanceof ViewportData)
                .toList();
    }

    /**
     * Move the OS cursor where IkGui asked, and keep the mouse position up to date while the cursor
     * is outside every window, like while dragging a window out of another. Also tell IkGui which
     * viewport the mouse is over.
     *
     * @param io The IkGui IO.
     * @param driving Whether automation drives the mouse, in which case the OS cursor is left
     *     alone.
     */
    private void updateMouseData(@NonNull IkIO io, boolean driving) {
        final boolean viewports = viewportsEnabled();
        int mouseViewportID = 0;
        for (Viewport viewport : viewportsWithWindows()) {
            final ViewportData data = (ViewportData) viewport.platformUserData;
            final long handle = data.window.getWindowHandle();
            final boolean focused = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
            if (focused && !driving) {
                // Move the OS cursor if navigation requested it (io.configNavMoveSetMousePosition)
                if (io.wantSetMousePosition) {
                    final float offsetX = viewports ? viewport.position.x : 0;
                    final float offsetY = viewports ? viewport.position.y : 0;
                    glfwSetCursorPos(
                            handle, io.mousePosition.x - offsetX, io.mousePosition.y - offsetY);
                }
                // GLFW stops sending positions once the cursor leaves our windows, so read it
                if (viewports && mouseWindow == null) {
                    glfwGetCursorPos(handle, doubleX, doubleY);
                    glfwGetWindowPos(handle, intX, intY);
                    final float x = (float) doubleX[0] + intX[0];
                    final float y = (float) doubleY[0] + intY[0];
                    io.addMousePosEvent(x, y);
                    lastValidMousePosition.set(x, y);
                }
            }
            if (viewports) {
                // Let the mouse fall through viewports like drag previews to whatever is below
                final boolean noInputs = (viewport.flags & ViewportFlags.NO_INPUTS) != 0;
                if (data.owned) {
                    glfwSetWindowAttrib(
                            handle, GLFW_MOUSE_PASSTHROUGH, noInputs ? GLFW_TRUE : GLFW_FALSE);
                }
                if (!noInputs && glfwGetWindowAttrib(handle, GLFW_HOVERED) == GLFW_TRUE) {
                    mouseViewportID = viewport.id;
                }
            }
        }
        if (viewports && !driving) {
            io.addMouseViewportEvent(mouseViewportID);
        }
    }

    /**
     * Show the cursor shape IkGui wants in every window it draws in.
     *
     * @param io The IkGui IO.
     */
    private void updateMouseCursors(@NonNull IkIO io) {
        if (mouseCursors == null) {
            return;
        }
        final boolean noChange = (io.configFlags & ConfigFlags.NO_MOUSE_CURSOR_CHANGE) != 0;
        for (Viewport viewport : viewportsWithWindows()) {
            final ViewportData data = (ViewportData) viewport.platformUserData;
            mouseCursors.update(
                    data.cursorTarget, IkGui.getMouseCursor(), noChange, io.configMouseDrawCursor);
        }
    }

    /** List the monitors for IkGui, which keeps popups and tooltips on one monitor. */
    private void updateMonitors() {
        monitorsChanged = false;
        final List<PlatformMonitor> monitors = IkGui.getPlatformIO().monitors;
        monitors.clear();
        final PointerBuffer handles = glfwGetMonitors();
        if (handles == null) {
            return;
        }
        final float[] scaleX = new float[1];
        final float[] scaleY = new float[1];
        final int[] workWidth = new int[1];
        final int[] workHeight = new int[1];
        // The primary monitor comes first
        for (int i = 0; i < handles.limit(); ++i) {
            final long handle = handles.get(i);
            final GLFWVidMode mode = glfwGetVideoMode(handle);
            if (mode == null) {
                continue;
            }
            PlatformMonitor monitor = new PlatformMonitor();
            glfwGetMonitorPos(handle, intX, intY);
            monitor.mainPosition.set(intX[0], intY[0]);
            monitor.mainSize.set(mode.width(), mode.height());
            glfwGetMonitorWorkarea(handle, intX, intY, workWidth, workHeight);
            if (workWidth[0] > 0 && workHeight[0] > 0) {
                monitor.workPosition.set(intX[0], intY[0]);
                monitor.workSize.set(workWidth[0], workHeight[0]);
            } else {
                monitor.workPosition.set(monitor.mainPosition);
                monitor.workSize.set(monitor.mainSize);
            }
            glfwGetMonitorContentScale(handle, scaleX, scaleY);
            if (scaleX[0] <= 0) {
                // Some platforms report 0 for monitors that are turning off
                continue;
            }
            monitor.dpiScale = scaleX[0];
            monitor.platformHandle = handle;
            monitors.add(monitor);
        }
    }

    /**
     * Send a window's input and focus to IkGui.
     *
     * @param window The window.
     */
    void installInputCallbacks(@NonNull Window window) {
        final long handle = window.getWindowHandle();
        glfwSetKeyCallback(
                handle,
                (unused, key, scancode, action, mods) -> {
                    // Key repeats are generated by IkGui itself
                    if (action != GLFW_PRESS && action != GLFW_RELEASE) {
                        return;
                    }
                    final IkIO io = getPlayerInputIO();
                    if (io == null) {
                        return;
                    }
                    updateKeyModifiers(io, keyToModifier(key, action, mods));
                    final int translatedKey = translateUntranslatedKey(key, scancode);
                    io.addKeyEvent(mapGLFWToIkGuiKey(translatedKey), action == GLFW_PRESS);
                });
        glfwSetWindowFocusCallback(
                handle,
                (unused, focused) -> {
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.addFocusEvent(focused);
                    }
                });
        // GLFW only reports the cursor entering later, so start from where it is now
        if (glfwGetWindowAttrib(handle, GLFW_HOVERED) == GLFW_TRUE) {
            mouseWindow = window;
        }
        glfwSetCursorEnterCallback(
                handle,
                (unused, entered) -> {
                    final IkIO io = getIkGuiIO();
                    if (entered) {
                        mouseWindow = window;
                        if (io != null && !isAutomationDriving()) {
                            io.addMousePosEvent(lastValidMousePosition.x, lastValidMousePosition.y);
                        }
                    } else if (mouseWindow == window) {
                        // Leaving one of our windows for another enters the other first
                        mouseWindow = null;
                        if (io != null) {
                            lastValidMousePosition.set(io.mousePosition);
                        }
                    }
                    if (io != null) {
                        io.mouseInsideWindow = mouseWindow != null || isAutomationDriving();
                    }
                });
        glfwSetCursorPosCallback(
                handle,
                (unused, posX, posY) -> {
                    // Keep sending positions while the cursor is disabled, since the camera
                    // controls use the mouse delta from IkIO
                    final IkIO io = getPlayerInputIO();
                    if (io == null) {
                        return;
                    }
                    float x = (float) posX;
                    float y = (float) posY;
                    if (viewportsEnabled()) {
                        glfwGetWindowPos(handle, intX, intY);
                        x += intX[0];
                        y += intY[0];
                    }
                    io.addMousePosEvent(x, y);
                    lastValidMousePosition.set(x, y);
                });
        glfwSetMouseButtonCallback(
                handle,
                (unused, button, action, mods) -> {
                    final MouseButton buttonType = mapGLFWToIkGuiMouseButton(button);
                    if (buttonType == MouseButton.NONE
                            || (action != GLFW_PRESS && action != GLFW_RELEASE)) {
                        return;
                    }
                    final IkIO io = getPlayerInputIO();
                    if (io == null) {
                        return;
                    }
                    updateKeyModifiers(io, mods);
                    io.addMouseButtonEvent(buttonType, action == GLFW_PRESS);
                });
        glfwSetScrollCallback(
                handle,
                (unused, xOffset, yOffset) -> {
                    final IkIO io = getPlayerInputIO();
                    if (io != null) {
                        io.addMouseWheelEvent((float) xOffset, (float) yOffset);
                    }
                });
        glfwSetCharCallback(
                handle,
                (unused, codepoint) -> {
                    final IkIO io = getPlayerInputIO();
                    if (io != null) {
                        io.addInputCharacter(codepoint);
                    }
                });
    }

    /**
     * Fetch what we know about a viewport's window.
     *
     * @param viewport The viewport.
     * @return The data, or null if the viewport has no window of ours.
     */
    private static ViewportData dataOf(@NonNull Viewport viewport) {
        return viewport.platformUserData instanceof ViewportData data ? data : null;
    }

    /**
     * Find the viewport drawn in a window.
     *
     * @param window The window.
     * @return The viewport, or null if it isn't one IkGui draws in.
     */
    private static Viewport viewportOf(@NonNull Window window) {
        return IkGui.getContext() == null ? null : IkGui.findViewportByPlatformHandle(window);
    }

    /**
     * Install the platform and renderer functions IkGui uses to create, move, resize, show and
     * destroy the windows of viewports, which are drawn like any other window.
     *
     * @param platformIO The platform IO.
     */
    private void installViewportFunctions(@NonNull PlatformIO platformIO) {
        platformIO.platformCreateWindow = this::createWindow;
        platformIO.platformDestroyWindow = this::destroyWindow;
        platformIO.platformShowWindow =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        data.window.show(
                                (viewport.flags & ViewportFlags.NO_FOCUS_ON_APPEARING) == 0);
                    }
                };
        platformIO.platformSetWindowPos =
                (viewport, position) -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        data.ignorePositionEventFrame = IkGui.getFrameCount();
                        glfwSetWindowPos(
                                data.window.getWindowHandle(), (int) position.x, (int) position.y);
                    }
                };
        platformIO.platformGetWindowPos =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data == null) {
                        return new Vector2f();
                    }
                    glfwGetWindowPos(data.window.getWindowHandle(), intX, intY);
                    return new Vector2f(intX[0], intY[0]);
                };
        platformIO.platformSetWindowSize =
                (viewport, size) -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        data.ignoreSizeEventFrame = IkGui.getFrameCount();
                        glfwSetWindowSize(
                                data.window.getWindowHandle(), (int) size.x, (int) size.y);
                    }
                };
        platformIO.platformGetWindowSize =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data == null) {
                        return new Vector2f();
                    }
                    glfwGetWindowSize(data.window.getWindowHandle(), intX, intY);
                    return new Vector2f(intX[0], intY[0]);
                };
        platformIO.platformGetWindowFramebufferScale =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data == null) {
                        return new Vector2f(1, 1);
                    }
                    final long handle = data.window.getWindowHandle();
                    glfwGetWindowSize(handle, intX, intY);
                    final int windowWidth = intX[0];
                    final int windowHeight = intY[0];
                    glfwGetFramebufferSize(handle, intX, intY);
                    if (windowWidth <= 0 || windowHeight <= 0) {
                        return new Vector2f(1, 1);
                    }
                    return new Vector2f(
                            (float) intX[0] / windowWidth, (float) intY[0] / windowHeight);
                };
        platformIO.platformSetWindowFocus =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        glfwFocusWindow(data.window.getWindowHandle());
                    }
                };
        platformIO.platformGetWindowFocus =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    return data != null
                            && glfwGetWindowAttrib(data.window.getWindowHandle(), GLFW_FOCUSED)
                                    == GLFW_TRUE;
                };
        platformIO.platformGetWindowMinimized =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    return data != null
                            && glfwGetWindowAttrib(data.window.getWindowHandle(), GLFW_ICONIFIED)
                                    == GLFW_TRUE;
                };
        platformIO.platformSetWindowTitle =
                (viewport, title) -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null && data.owned) {
                        data.window.setTitle(title);
                    }
                };
        platformIO.platformSetWindowAlpha =
                (viewport, alpha) -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        glfwSetWindowOpacity(data.window.getWindowHandle(), alpha);
                    }
                };
        platformIO.platformGetWindowDpiScale =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    return data == null ? 1.0f : data.window.getContentScale();
                };

        // The windows are drawn along with every other window, so only their swapchains are ours
        platformIO.rendererCreateWindow =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null && data.owned) {
                        GraphicsManager.createGuiPresentation(data.window);
                    }
                };
        platformIO.rendererDestroyWindow =
                viewport -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null && data.owned) {
                        GraphicsManager.destroyGuiPresentation(data.window);
                    }
                    viewport.rendererUserData = null;
                };
        platformIO.rendererSetWindowSize =
                (viewport, size) -> {
                    final ViewportData data = dataOf(viewport);
                    if (data != null) {
                        GraphicsManager.guiWindowResized(data.window);
                    }
                };
    }

    /**
     * Create a hidden window for a viewport, which IkGui places and shows afterward.
     *
     * @param viewport The viewport.
     */
    private void createWindow(@NonNull Viewport viewport) {
        final WindowOptions options =
                WindowOptions.guiViewport(
                        Math.max(1, (int) viewport.size.x),
                        Math.max(1, (int) viewport.size.y),
                        (viewport.flags & ViewportFlags.NO_DECORATION) == 0,
                        (viewport.flags & ViewportFlags.TOP_MOST) != 0);
        final Window window = GraphicsManager.createGuiWindow(options);
        final long handle = window.getWindowHandle();
        glfwSetWindowPos(handle, (int) viewport.position.x, (int) viewport.position.y);

        final ViewportData data = new ViewportData(window, true);
        viewport.platformHandle = window;
        viewport.platformUserData = data;
        installInputCallbacks(window);
        glfwSetWindowCloseCallback(
                handle,
                unused -> {
                    final Viewport closing = viewportOf(window);
                    if (closing != null) {
                        closing.platformRequestClose = true;
                    }
                });
        glfwSetWindowPosCallback(
                handle,
                (unused, x, y) -> {
                    final Viewport moved = viewportOf(window);
                    // Ignore the move we just made ourselves
                    if (moved != null
                            && IkGui.getFrameCount() > data.ignorePositionEventFrame + 1) {
                        moved.platformRequestMove = true;
                    }
                });
        glfwSetWindowSizeCallback(
                handle,
                (unused, width, height) -> {
                    final Viewport resized = viewportOf(window);
                    if (resized != null && IkGui.getFrameCount() > data.ignoreSizeEventFrame + 1) {
                        resized.platformRequestResize = true;
                    }
                });
    }

    /**
     * Destroy the window of a viewport, if we made it for the viewport.
     *
     * @param viewport The viewport.
     */
    private void destroyWindow(@NonNull Viewport viewport) {
        final ViewportData data = dataOf(viewport);
        if (data != null && data.owned) {
            if (mouseWindow == data.window) {
                mouseWindow = null;
            }
            GraphicsManager.destroyGuiWindow(data.window);
        }
        viewport.platformUserData = null;
        viewport.platformHandle = null;
    }

    /**
     * Fetch the IkGui IO to forward input to, if IkGui is set up yet.
     *
     * @return The IkGui IO, or null if there is no IkGui context yet.
     */
    private static IkIO getIkGuiIO() {
        return IkGui.getContext() == null ? null : IkGui.getIO();
    }

    /**
     * The IkGui IO to send the player's mouse and keyboard input to, unless UI automation is
     * driving input, so the player's input doesn't fight it.
     *
     * @return The IO, or null if there is no context or automation is driving.
     */
    private static IkIO getPlayerInputIO() {
        if (isAutomationDriving()) {
            return null;
        }
        return getIkGuiIO();
    }

    /**
     * Whether UI automation is driving input instead of the player.
     *
     * @return True while an automation run drives input.
     */
    private static boolean isAutomationDriving() {
        return GraphicsManager.getUiManager().getAutomation().isDriving();
    }

    /**
     * Convert a GLFW mouse button into an IkGui mouse button.
     *
     * @param button The GLFW mouse button.
     * @return The IkGui mouse button, or MouseButton.NONE if there is no equivalent.
     */
    private static MouseButton mapGLFWToIkGuiMouseButton(int button) {
        return switch (button) {
            case GLFW_MOUSE_BUTTON_1 -> MouseButton.LEFT;
            case GLFW_MOUSE_BUTTON_2 -> MouseButton.RIGHT;
            case GLFW_MOUSE_BUTTON_3 -> MouseButton.MIDDLE;
            case GLFW_MOUSE_BUTTON_4 -> MouseButton.BACK;
            case GLFW_MOUSE_BUTTON_5 -> MouseButton.FORWARD;
            default -> MouseButton.NONE;
        };
    }

    /**
     * Send the modifier key state to IkGui. GLFW provides the modifiers with key and mouse button
     * events, which is more reliable than tracking the left/right keys ourselves, like when a
     * modifier is released while the window is unfocused.
     *
     * @param io The IkGui IO.
     * @param mods The GLFW modifier bit flags.
     */
    private static void updateKeyModifiers(@NonNull IkIO io, int mods) {
        io.addKeyEvent(Key.MOD_CTRL, (mods & GLFW_MOD_CONTROL) != 0);
        io.addKeyEvent(Key.MOD_SHIFT, (mods & GLFW_MOD_SHIFT) != 0);
        io.addKeyEvent(Key.MOD_ALT, (mods & GLFW_MOD_ALT) != 0);
        io.addKeyEvent(Key.MOD_SUPER, (mods & GLFW_MOD_SUPER) != 0);
    }

    /**
     * GLFW reports the modifier state from before a modifier key event, so fix up the modifiers
     * when the key itself is a modifier.
     *
     * @param key The GLFW key code.
     * @param action The GLFW action.
     * @param mods The GLFW modifier bit flags provided with the key event.
     * @return The modifier bit flags including the effect of this key event.
     */
    private static int keyToModifier(int key, int action, int mods) {
        final int modifier =
                switch (key) {
                    case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> GLFW_MOD_CONTROL;
                    case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> GLFW_MOD_SHIFT;
                    case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> GLFW_MOD_ALT;
                    case GLFW_KEY_LEFT_SUPER, GLFW_KEY_RIGHT_SUPER -> GLFW_MOD_SUPER;
                    default -> 0;
                };
        if (modifier == 0) {
            return mods;
        }
        return action == GLFW_RELEASE ? mods & ~modifier : mods | modifier;
    }

    /**
     * Send the first connected gamepad's state to IkGui, and tell IkGui whether there is one.
     *
     * @param io The IkGui IO to send key events to.
     */
    private static void updateGamepad(@NonNull IkIO io) {
        io.backendFlags &= ~BackendFlags.HAS_GAMEPAD;
        for (int joystick = GLFW_JOYSTICK_1; joystick <= GLFW_JOYSTICK_LAST; ++joystick) {
            if (!glfwJoystickIsGamepad(joystick)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWGamepadState state = GLFWGamepadState.malloc(stack);
                if (!glfwGetGamepadState(joystick, state)) {
                    continue;
                }
                byte[] buttons = new byte[GLFW_GAMEPAD_BUTTON_LAST + 1];
                float[] axes = new float[GLFW_GAMEPAD_AXIS_LAST + 1];
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i] = state.buttons(i);
                }
                for (int i = 0; i < axes.length; ++i) {
                    axes[i] = state.axes(i);
                }
                io.backendFlags |= BackendFlags.HAS_GAMEPAD;
                GamepadInput.apply(buttons, axes, io::addKeyAnalogEvent);
            }
            return;
        }
    }

    /**
     * GLFW key codes are for the physical position of the key on a US keyboard, but shortcuts
     * should use what is printed on the key in the current keyboard layout, like Ctrl+Z being
     * labelled Ctrl+W on a French keyboard. Use the printable character of the key to figure out
     * which key it is in the current layout.
     *
     * @param key The GLFW key code.
     * @param scancode The platform specific scancode of the key.
     * @return The GLFW key code for the key in the current keyboard layout.
     */
    private static int translateUntranslatedKey(int key, int scancode) {
        // The keypad keys are the same in all layouts, and GLFW reports printable names for them
        if (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_EQUAL) {
            return key;
        }
        final String keyName = glfwGetKeyName(key, scancode);
        if (keyName == null || keyName.length() != 1) {
            return key;
        }
        final char c = keyName.charAt(0);
        if (c >= '0' && c <= '9') {
            return GLFW_KEY_0 + (c - '0');
        }
        if (c >= 'A' && c <= 'Z') {
            return GLFW_KEY_A + (c - 'A');
        }
        if (c >= 'a' && c <= 'z') {
            return GLFW_KEY_A + (c - 'a');
        }
        return switch (c) {
            case '`' -> GLFW_KEY_GRAVE_ACCENT;
            case '-' -> GLFW_KEY_MINUS;
            case '=' -> GLFW_KEY_EQUAL;
            case '[' -> GLFW_KEY_LEFT_BRACKET;
            case ']' -> GLFW_KEY_RIGHT_BRACKET;
            case '\\' -> GLFW_KEY_BACKSLASH;
            case ',' -> GLFW_KEY_COMMA;
            case ';' -> GLFW_KEY_SEMICOLON;
            case '\'' -> GLFW_KEY_APOSTROPHE;
            case '.' -> GLFW_KEY_PERIOD;
            case '/' -> GLFW_KEY_SLASH;
            default -> key;
        };
    }

    /**
     * Convert a GLFW key code into an IkGui key.
     *
     * @param key The GLFW key code.
     * @return The IkGui key, or Key.NONE if there is no equivalent.
     */
    static Key mapGLFWToIkGuiKey(int key) {
        return switch (key) {
            case GLFW_KEY_TAB -> Key.TAB;
            case GLFW_KEY_LEFT -> Key.ARROW_LEFT;
            case GLFW_KEY_RIGHT -> Key.ARROW_RIGHT;
            case GLFW_KEY_UP -> Key.ARROW_UP;
            case GLFW_KEY_DOWN -> Key.ARROW_DOWN;
            case GLFW_KEY_PAGE_UP -> Key.PAGE_UP;
            case GLFW_KEY_PAGE_DOWN -> Key.PAGE_DOWN;
            case GLFW_KEY_HOME -> Key.HOME;
            case GLFW_KEY_END -> Key.END;
            case GLFW_KEY_INSERT -> Key.INSERT;
            case GLFW_KEY_DELETE -> Key.DELETE;
            case GLFW_KEY_BACKSPACE -> Key.BACKSPACE;
            case GLFW_KEY_SPACE -> Key.SPACE;
            case GLFW_KEY_ENTER -> Key.ENTER;
            case GLFW_KEY_ESCAPE -> Key.ESCAPE;
            case GLFW_KEY_KP_ENTER -> Key.NUMPAD_ENTER;
            case GLFW_KEY_LEFT_CONTROL -> Key.LEFT_CTRL;
            case GLFW_KEY_RIGHT_CONTROL -> Key.RIGHT_CTRL;
            case GLFW_KEY_LEFT_SHIFT -> Key.LEFT_SHIFT;
            case GLFW_KEY_RIGHT_SHIFT -> Key.RIGHT_SHIFT;
            case GLFW_KEY_LEFT_ALT -> Key.LEFT_ALT;
            case GLFW_KEY_RIGHT_ALT -> Key.RIGHT_ALT;
            case GLFW_KEY_LEFT_SUPER -> Key.LEFT_SUPER;
            case GLFW_KEY_RIGHT_SUPER -> Key.RIGHT_SUPER;
            case GLFW_KEY_MENU -> Key.MENU;
            case GLFW_KEY_PAUSE -> Key.PAUSE;
            case GLFW_KEY_CAPS_LOCK -> Key.CAPS_LOCK;
            case GLFW_KEY_SCROLL_LOCK -> Key.SCROLL_LOCK;
            case GLFW_KEY_PRINT_SCREEN -> Key.PRINT_SCREEN;

            case GLFW_KEY_A -> Key.A;
            case GLFW_KEY_B -> Key.B;
            case GLFW_KEY_C -> Key.C;
            case GLFW_KEY_D -> Key.D;
            case GLFW_KEY_E -> Key.E;
            case GLFW_KEY_F -> Key.F;
            case GLFW_KEY_G -> Key.G;
            case GLFW_KEY_H -> Key.H;
            case GLFW_KEY_I -> Key.I;
            case GLFW_KEY_J -> Key.J;
            case GLFW_KEY_K -> Key.K;
            case GLFW_KEY_L -> Key.L;
            case GLFW_KEY_M -> Key.M;
            case GLFW_KEY_N -> Key.N;
            case GLFW_KEY_O -> Key.O;
            case GLFW_KEY_P -> Key.P;
            case GLFW_KEY_Q -> Key.Q;
            case GLFW_KEY_R -> Key.R;
            case GLFW_KEY_S -> Key.S;
            case GLFW_KEY_T -> Key.T;
            case GLFW_KEY_U -> Key.U;
            case GLFW_KEY_V -> Key.V;
            case GLFW_KEY_W -> Key.W;
            case GLFW_KEY_X -> Key.X;
            case GLFW_KEY_Y -> Key.Y;
            case GLFW_KEY_Z -> Key.Z;

            case GLFW_KEY_0 -> Key.ZERO;
            case GLFW_KEY_1 -> Key.ONE;
            case GLFW_KEY_2 -> Key.TWO;
            case GLFW_KEY_3 -> Key.THREE;
            case GLFW_KEY_4 -> Key.FOUR;
            case GLFW_KEY_5 -> Key.FIVE;
            case GLFW_KEY_6 -> Key.SIX;
            case GLFW_KEY_7 -> Key.SEVEN;
            case GLFW_KEY_8 -> Key.EIGHT;
            case GLFW_KEY_9 -> Key.NINE;

            case GLFW_KEY_KP_0 -> Key.NUMPAD_ZERO;
            case GLFW_KEY_KP_1 -> Key.NUMPAD_ONE;
            case GLFW_KEY_KP_2 -> Key.NUMPAD_TWO;
            case GLFW_KEY_KP_3 -> Key.NUMPAD_THREE;
            case GLFW_KEY_KP_4 -> Key.NUMPAD_FOUR;
            case GLFW_KEY_KP_5 -> Key.NUMPAD_FIVE;
            case GLFW_KEY_KP_6 -> Key.NUMPAD_SIX;
            case GLFW_KEY_KP_7 -> Key.NUMPAD_SEVEN;
            case GLFW_KEY_KP_8 -> Key.NUMPAD_EIGHT;
            case GLFW_KEY_KP_9 -> Key.NUMPAD_NINE;
            case GLFW_KEY_KP_DECIMAL -> Key.NUMPAD_PERIOD;
            case GLFW_KEY_KP_DIVIDE -> Key.NUMPAD_DIVIDE;
            case GLFW_KEY_KP_MULTIPLY -> Key.NUMPAD_MULTIPLY;
            case GLFW_KEY_KP_SUBTRACT -> Key.NUMPAD_SUBTRACT;
            case GLFW_KEY_KP_ADD -> Key.NUMPAD_ADD;
            case GLFW_KEY_KP_EQUAL -> Key.NUMPAD_EQUAL;
            case GLFW_KEY_NUM_LOCK -> Key.NUM_LOCK;

            case GLFW_KEY_F1 -> Key.F1;
            case GLFW_KEY_F2 -> Key.F2;
            case GLFW_KEY_F3 -> Key.F3;
            case GLFW_KEY_F4 -> Key.F4;
            case GLFW_KEY_F5 -> Key.F5;
            case GLFW_KEY_F6 -> Key.F6;
            case GLFW_KEY_F7 -> Key.F7;
            case GLFW_KEY_F8 -> Key.F8;
            case GLFW_KEY_F9 -> Key.F9;
            case GLFW_KEY_F10 -> Key.F10;
            case GLFW_KEY_F11 -> Key.F11;
            case GLFW_KEY_F12 -> Key.F12;
            case GLFW_KEY_F13 -> Key.F13;
            case GLFW_KEY_F14 -> Key.F14;
            case GLFW_KEY_F15 -> Key.F15;
            case GLFW_KEY_F16 -> Key.F16;
            case GLFW_KEY_F17 -> Key.F17;
            case GLFW_KEY_F18 -> Key.F18;
            case GLFW_KEY_F19 -> Key.F19;
            case GLFW_KEY_F20 -> Key.F20;
            case GLFW_KEY_F21 -> Key.F21;
            case GLFW_KEY_F22 -> Key.F22;
            case GLFW_KEY_F23 -> Key.F23;
            case GLFW_KEY_F24 -> Key.F24;

            case GLFW_KEY_APOSTROPHE -> Key.APOSTROPHE;
            case GLFW_KEY_COMMA -> Key.COMMA;
            case GLFW_KEY_MINUS -> Key.MINUS;
            case GLFW_KEY_PERIOD -> Key.PERIOD;
            case GLFW_KEY_SLASH -> Key.FORWARD_SLASH;
            case GLFW_KEY_SEMICOLON -> Key.SEMICOLON;
            case GLFW_KEY_EQUAL -> Key.EQUALS;
            case GLFW_KEY_LEFT_BRACKET -> Key.LEFT_BRACKET;
            case GLFW_KEY_BACKSLASH -> Key.BACK_SLASH;
            case GLFW_KEY_RIGHT_BRACKET -> Key.RIGHT_BRACKET;
            case GLFW_KEY_GRAVE_ACCENT -> Key.GRAVE_ACCENT;
            case GLFW_KEY_WORLD_1, GLFW_KEY_WORLD_2 -> Key.OEM_102;
            default -> Key.NONE;
        };
    }
}
