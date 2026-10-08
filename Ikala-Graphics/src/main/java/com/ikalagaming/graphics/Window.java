package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.graphics.frontend.BackendType;
import com.ikalagaming.graphics.frontend.GraphicsSettings;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.data.PlatformIO;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.function.Consumer;

/** Provides convenience methods for an OpenGL window. */
@Slf4j
@Getter
public class Window {
    // TODO(ches) move this somewhere more specific, like frontend. Possibly tighter integration
    // with the gui system

    /**
     * Set up the window hints.
     *
     * @param settings The configuration to use.
     */
    private static void setWindowHints(@NonNull GraphicsSettings settings) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        if (settings.antiAliasing) {
            glfwWindowHint(GLFW_SAMPLES, 4);
        }

        if (BackendType.OPENGL == GraphicsManager.getBackendType()) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
            if (settings.compatibleProfile) {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
            } else {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            }
        } else if (BackendType.VULKAN == GraphicsManager.getBackendType()) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        }
    }

    /** The width of the largest monitor we could find, in pixels. */
    @Getter private static int largestMonitorWidth = 0;

    /** The height of the largest monitor we could find, in pixels. */
    @Getter private static int largestMonitorHeight = 0;

    /**
     * The GLFW window handle.
     *
     * @return The window handle.
     */
    private long windowHandle;

    /**
     * The width of the window in pixels.
     *
     * @return The current width of the window.
     */
    private int width;

    /**
     * The height of the window in pixels.
     *
     * @return The current height of the window.
     */
    private int height;

    /** Whether we are currently fullscreen (borderless). */
    private boolean fullscreen;

    /** The resize function to call. */
    private final Consumer<Window> resizeFunc;

    /** The title that was provided for the window. */
    private final String title;

    /** The mouse cursors shown for IkGui, null until the IkGui platform IO is set up. */
    private GlfwMouseCursors mouseCursors;

    /** How IkGui changes the cursor of this window. */
    private final GlfwMouseCursors.CursorTarget cursorTarget =
            new GlfwMouseCursors.CursorTarget() {
                @Override
                public int getCursorMode() {
                    return glfwGetInputMode(windowHandle, GLFW_CURSOR);
                }

                @Override
                public void setCursorMode(int mode) {
                    glfwSetInputMode(windowHandle, GLFW_CURSOR, mode);
                }

                @Override
                public void setCursor(long cursor) {
                    glfwSetCursor(windowHandle, cursor);
                }
            };

    /**
     * Create a new window.
     *
     * @param title The title of the window to display.
     * @param settings Graphics settings.
     * @param resizeFunc The function to call upon the window resizing.
     */
    public Window(
            @NonNull String title,
            @NonNull GraphicsSettings settings,
            @NonNull Consumer<Window> resizeFunc) {
        this.resizeFunc = resizeFunc;
        this.fullscreen = false;

        glfwInitHint(GLFW_COCOA_CHDIR_RESOURCES, GLFW_TRUE);
        if (!glfwInit()) {
            final String error = "Unable to initialize GLFW";
            log.warn(error);
            throw new WindowCreationException(error);
        }

        if (glfwSetErrorCallback(createGLFWErrorLogger()) != null) {
            log.debug("We are replacing a GLFWErrorCallback, hope that's fine");
        }

        if (BackendType.VULKAN == GraphicsManager.getBackendType()
                && !GLFWVulkan.glfwVulkanSupported()) {
            final String error = "GLFW cannot find the Vulkan loader";
            log.warn(error);
            throw new WindowCreationException(error);
        }

        setWindowHints(settings);

        if (settings.requestedWindowWidth > 0 && settings.requestedWindowHeight > 0) {
            width = settings.requestedWindowWidth;
            height = settings.requestedWindowHeight;
        } else {
            glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode == null) {
                final String error =
                        "Failed to create a GLFW window due to an error fetching video mode";
                log.warn(error);
                throw new WindowCreationException(error);
            }
            width = vidMode.width();
            height = vidMode.height();
        }

        if (largestMonitorWidth == 0 || largestMonitorHeight == 0) {
            PointerBuffer monitors = glfwGetMonitors();
            if (monitors == null) {
                final String error = "Can't find monitor info";
                log.warn(error);
                throw new WindowCreationException(error);
            }
            int maxWidth = 0;
            int maxHeight = 0;
            int maxArea = 0;
            for (int i = 0; i < monitors.limit(); i++) {
                final GLFWVidMode.Buffer modes = glfwGetVideoModes(monitors.get(i));
                for (int j = 0; j < modes.limit(); j++) {
                    final GLFWVidMode mode = modes.get(j);
                    final int area = mode.width() * mode.height();
                    if (area > maxArea) {
                        maxWidth = mode.width();
                        maxHeight = mode.height();
                        maxArea = area;
                    }
                }
            }
            largestMonitorWidth = maxWidth;
            largestMonitorHeight = maxHeight;
            log.debug(
                    "We found a monitor that is {}x{}, using that as the max size for rendering targets",
                    maxWidth,
                    maxHeight);
        }

        this.title = title;
        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            final String error = "Failed to create a GLFW window";
            log.warn(error);
            throw new WindowCreationException(error);
        }

        setCallbacks();

        if (BackendType.OPENGL == GraphicsManager.getBackendType()) {
            glfwMakeContextCurrent(windowHandle);

            if (settings.targetFPS > 0) {
                glfwSwapInterval(0);
            } else {
                glfwSwapInterval(1);
            }
        }

        if (glfwGetPlatform() != GLFW_PLATFORM_COCOA) {
            setWindowIcon();
        }

        glfwShowWindow(windowHandle);

        int[] arrWidth = new int[1];
        int[] arrHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, arrWidth, arrHeight);
        width = arrWidth[0];
        height = arrHeight[0];
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

    private GLFWErrorCallback createGLFWErrorLogger() {
        return new GLFWErrorCallback() {
            private final Map<Integer, String> ERROR_CODES =
                    APIUtil.apiClassTokens(
                            (field, value) -> 0x10000 < value && value < 0x20000, null, GLFW.class);

            @Override
            public void invoke(int error, long description) {
                String msg = getDescription(description);

                StringBuilder sb = new StringBuilder(512);
                sb.append("[GLFW] ")
                        .append(ERROR_CODES.get(error))
                        .append(" error\n")
                        .append("\tDescription : ")
                        .append(msg)
                        .append("\n")
                        .append("\tStacktrace  :\n");

                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                for (int i = 4; i < stack.length; i++) {
                    sb.append("\t\t");
                    sb.append(stack[i]);
                    sb.append("\n");
                }

                log.error(sb.toString());
            }
        };
    }

    /**
     * Disable the cursor.
     *
     * @see #enableCursor()
     */
    public void disableCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    /** Destroy the window. */
    public void destroy() {
        if (NULL == windowHandle) {
            return;
        }

        if (mouseCursors != null) {
            mouseCursors.destroy();
            mouseCursors = null;
        }
        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
        windowHandle = NULL;
        log.debug("Window destroyed");
    }

    /**
     * Enable the cursor.
     *
     * @see #disableCursor()
     */
    public void enableCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    /**
     * Checks if a key is pressed.
     *
     * @param keyCode The keycode to look for.
     * @return True if the key is pressed, false if not.
     */
    public boolean isKeyPressed(int keyCode) {
        final IkIO io = getIkGuiIO();
        if (io != null && io.wantCaptureKeyboard) {
            return false;
        }
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    /** Poll for events and process input. */
    public void pollEvents() {
        if (IkGui.getContext() != null) {
            final IkIO io = IkGui.getIO();
            // Move the OS cursor if navigation requested it (io.configNavMoveSetMousePosition)
            if (io.wantSetMousePosition) {
                glfwSetCursorPos(windowHandle, io.mousePosition.x, io.mousePosition.y);
            }
            // Show the cursor shape IkGui wants
            if (mouseCursors != null) {
                mouseCursors.update(
                        cursorTarget,
                        IkGui.getMouseCursor(),
                        (io.configFlags & ConfigFlags.NO_MOUSE_CURSOR_CHANGE) != 0,
                        io.configMouseDrawCursor);
            }
        }
        glfwPollEvents();
    }

    /**
     * A callback for resizing the window.
     *
     * @param width The new width of the window.
     * @param height The new height of the window.
     */
    protected void resized(int width, int height) {
        this.width = width;
        this.height = height;
        try {
            resizeFunc.accept(this);
        } catch (Exception e) {
            log.warn("Error calling resize callback", e);
        }
    }

    /** Set up the window callbacks. */
    private void setCallbacks() {

        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> resized(w, h));

        glfwSetErrorCallback(
                (int errorCode, long msgPtr) ->
                        log.error("Error code {} - {}", errorCode, MemoryUtil.memUTF8(msgPtr)));

        glfwSetKeyCallback(
                windowHandle,
                (window, key, scancode, action, mods) -> {
                    // Key repeats are generated by IkGui itself
                    if (action != GLFW_PRESS && action != GLFW_RELEASE) {
                        return;
                    }
                    final IkIO io = getIkGuiIO();
                    if (io == null) {
                        return;
                    }
                    updateKeyModifiers(io, keyToModifier(key, action, mods));
                    final int translatedKey = translateUntranslatedKey(key, scancode);
                    io.addKeyEvent(mapGLFWToIkGuiKey(translatedKey), action == GLFW_PRESS);
                });
        glfwSetWindowFocusCallback(
                windowHandle,
                (window, focused) -> {
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.addFocusEvent(focused);
                    }
                });
        glfwSetCursorEnterCallback(
                windowHandle,
                (window, entered) -> {
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.mouseInsideWindow = entered;
                    }
                });
        glfwSetCursorPosCallback(
                windowHandle,
                (window, posX, posY) -> {
                    // Keep sending positions while the cursor is disabled, since the camera
                    // controls use the mouse delta from IkIO
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.addMousePosEvent((float) posX, (float) posY);
                    }
                });
        glfwSetMouseButtonCallback(
                windowHandle,
                (window, button, action, mods) -> {
                    final MouseButton buttonType = mapGLFWToIkGuiMouseButton(button);
                    if (buttonType == MouseButton.NONE
                            || (action != GLFW_PRESS && action != GLFW_RELEASE)) {
                        return;
                    }
                    final IkIO io = getIkGuiIO();
                    if (io == null) {
                        return;
                    }
                    updateKeyModifiers(io, mods);
                    io.addMouseButtonEvent(buttonType, action == GLFW_PRESS);
                });
        glfwSetScrollCallback(
                windowHandle,
                (window, xOffset, yOffset) -> {
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.addMouseWheelEvent((float) xOffset, (float) yOffset);
                    }
                });
        glfwSetCharCallback(
                windowHandle,
                (window, codepoint) -> {
                    final IkIO io = getIkGuiIO();
                    if (io != null) {
                        io.addInputCharacter(codepoint);
                    }
                });
    }

    /** Set up the window icon. */
    private void setWindowIcon() {
        PluginConfig config = ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME);

        File icon =
                PluginFolder.getResource(
                        GraphicsPlugin.PLUGIN_NAME,
                        ResourceType.DATA,
                        config.getString("icon-path"));

        String iconPath = icon.getAbsolutePath();
        if (!icon.exists()) {
            log.warn(
                    "Icon {} does not exist! Not setting an icon for the program",
                    icon.getAbsolutePath());
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer buffer = STBImage.stbi_load(iconPath, w, h, channels, 4);
            if (buffer == null) {
                final String error =
                        SafeResourceLoader.format(
                                "Image file {} not loaded: {}",
                                iconPath,
                                STBImage.stbi_failure_reason());
                log.info(error);
                throw new TextureException(error);
            }
            GLFWImage.Buffer iconBuffer = GLFWImage.create(1);
            GLFWImage iconImage = GLFWImage.create().set(w.get(), h.get(), buffer);
            iconBuffer.put(0, iconImage);

            glfwSetWindowIcon(windowHandle, iconBuffer);

            STBImage.stbi_image_free(buffer);
        }
    }

    /** Render the window. */
    public void update() {
        if (GraphicsManager.getBackendType() == BackendType.OPENGL) {
            glfwSwapBuffers(windowHandle);
        }
    }

    /**
     * Checks if the window should close.
     *
     * @return True if the window should close, false if not.
     */
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    /**
     * Hook up the IkGui platform functions (like the clipboard) to this window. Call this after the
     * IkGui context is created.
     */
    public void setupIkGuiPlatformIO() {
        final PlatformIO platformIO = IkGui.getPlatformIO();
        platformIO.getClipboardTextFunction =
                () -> {
                    final String text = glfwGetClipboardString(windowHandle);
                    return text == null ? "" : text;
                };
        platformIO.setClipboardTextFunction = text -> glfwSetClipboardString(windowHandle, text);
        // We can honor io.wantSetMousePosition requests
        IkGui.getIO().backendFlags |= BackendFlags.HAS_SET_MOUSE_POS;
        // We can honor getMouseCursor() values
        if (mouseCursors == null) {
            mouseCursors = GlfwMouseCursors.createStandardCursors();
        }
        IkGui.getIO().backendFlags |= BackendFlags.HAS_MOUSE_CURSORS;
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
    public static Key mapGLFWToIkGuiKey(int key) {
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
