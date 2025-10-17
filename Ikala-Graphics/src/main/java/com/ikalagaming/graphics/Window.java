package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.graphics.frontend.BackendType;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.Callable;

/** Provides convenience methods for an OpenGL window. */
@Slf4j
@Getter
public class Window {

    /** Options for the window. */
    public static class WindowOptions {
        /** Whether anti-aliasing is enabled. */
        public boolean antiAliasing;

        /** Whether we want to use a compatible profile instead of the core one. */
        public boolean compatibleProfile;

        /** The target frames per second, which relates to vsync. */
        public int fps;

        /** The height of the window in pixels. */
        public int height;

        /** The width of the window in pixels. */
        public int width;
    }

    /**
     * Set up the window hints.
     *
     * @param options The configuration to use.
     */
    private static void setWindowHints(@NonNull WindowOptions options) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        if (options.antiAliasing) {
            glfwWindowHint(GLFW_SAMPLES, 4);
        }

        if (BackendType.OPENGL == GraphicsManager.getBackendType()) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
            if (options.compatibleProfile) {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
            } else {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            }
        } else if (BackendType.VULKAN == GraphicsManager.getBackendType()) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        }
    }

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

    /** Mouse input handler. */
    private final MouseInput mouseInput;

    /** The resize function to call. */
    private final Callable<Void> resizeFunc;

    /** The title that was provided for the window. */
    @Getter private final String title;

    /**
     * Create a new window.
     *
     * @param title The title of the window to display.
     * @param opts Options for the window setup.
     * @param resizeFunc The function to call upon the window resizing.
     */
    public Window(
            @NonNull String title,
            @NonNull WindowOptions opts,
            @NonNull Callable<Void> resizeFunc) {
        this.resizeFunc = resizeFunc;

        glfwInitHint(GLFW_COCOA_CHDIR_RESOURCES, GLFW_TRUE);
        if (!glfwInit()) {
            String error =
                    SafeResourceLoader.getString(
                            "GLFW_INIT_FAIL", GraphicsPlugin.getResourceBundle());
            log.warn(error);
            throw new WindowCreationException(error);
        }

        if (BackendType.VULKAN == GraphicsManager.getBackendType()
                && !GLFWVulkan.glfwVulkanSupported()) {
            String error =
                    SafeResourceLoader.getString(
                            "GLFW_VULKAN_UNSUPPORTED", GraphicsPlugin.getResourceBundle());
            log.warn(error);
            throw new WindowCreationException(error);
        }

        setWindowHints(opts);

        if (opts.width > 0 && opts.height > 0) {
            width = opts.width;
            height = opts.height;
        } else {
            glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode == null) {
                String error =
                        SafeResourceLoader.getString(
                                "WINDOW_ERROR_CREATION", GraphicsPlugin.getResourceBundle());
                log.warn(error);
                throw new WindowCreationException(error);
            }
            width = vidMode.width();
            height = vidMode.height();
        }

        this.title = title;
        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            String error =
                    SafeResourceLoader.getString(
                            "WINDOW_ERROR_CREATION", GraphicsPlugin.getResourceBundle());
            log.warn(error);
            throw new WindowCreationException(error);
        }

        setCallbacks();

        if (BackendType.OPENGL == GraphicsManager.getBackendType()) {
            glfwMakeContextCurrent(windowHandle);

            if (opts.fps > 0) {
                glfwSwapInterval(0);
            } else {
                glfwSwapInterval(1);
            }
        }

        setWindowIcon();

        glfwShowWindow(windowHandle);

        int[] arrWidth = new int[1];
        int[] arrHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, arrWidth, arrHeight);
        width = arrWidth[0];
        height = arrHeight[0];

        mouseInput = new MouseInput(windowHandle);
    }

    /** Destroy the window. */
    public void destroy() {
        if (NULL == windowHandle) {
            return;
        }

        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
        windowHandle = NULL;
        log.debug(
                SafeResourceLoader.getString(
                        "WINDOW_DESTROYED", GraphicsPlugin.getResourceBundle()));
    }

    /**
     * Checks if a key is pressed.
     *
     * @param keyCode The keycode to look for.
     * @return True if the key is pressed, false if not.
     */
    public boolean isKeyPressed(int keyCode) {
        // TODO(ches) use our new input system
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            return false;
        }
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    /** Poll for events and process input. */
    public void pollEvents() {
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
            resizeFunc.call();
        } catch (Exception e) {
            log.warn(
                    SafeResourceLoader.getString(
                            "RESIZE_ERROR", GraphicsPlugin.getResourceBundle()),
                    e);
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
                    // TODO(ches) use our new input system
                    ImGuiIO io = ImGui.getIO();

                    final int mappedKey = mapGLFWToImGuiKey(key);
                    if (mappedKey == ImGuiKey.None) {
                        return;
                    }

                    boolean state;
                    switch (action) {
                        case GLFW_PRESS -> state = true;
                        case GLFW_RELEASE -> state = false;
                        default -> {
                            // Repeat, etc. We don't care about it right now.
                            return;
                        }
                    }

                    io.addKeyEvent(mappedKey, state);

                    if (mappedKey == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
                        io.setKeyCtrl(state);
                    } else if (mappedKey == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                        io.setKeyShift(state);
                    } else if (mappedKey == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT) {
                        io.setKeyAlt(state);
                    } else if (mappedKey == GLFW_KEY_LEFT_SUPER || key == GLFW_KEY_RIGHT_SUPER) {
                        io.setKeySuper(state);
                    }
                });
        glfwSetScrollCallback(
                windowHandle,
                (window, xOffset, yOffset) -> {
                    // TODO(ches) use our new input system
                    ImGuiIO io = ImGui.getIO();
                    io.setMouseWheelH((float) (io.getMouseWheelH() + xOffset));
                    io.setMouseWheel((float) (io.getMouseWheel() + yOffset));
                });
        glfwSetCharCallback(
                windowHandle,
                (window, codepoint) -> {
                    // TODO(ches) use our new input system
                    ImGuiIO io = ImGui.getIO();
                    io.addInputCharacter(codepoint);
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
                    SafeResourceLoader.getString(
                            "ICON_MISSING", GraphicsPlugin.getResourceBundle()),
                    icon.getAbsolutePath());
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer buffer = STBImage.stbi_load(iconPath, w, h, channels, 4);
            if (buffer == null) {
                String error =
                        SafeResourceLoader.getString(
                                "TEXTURE_ERROR_LOADING", GraphicsPlugin.getResourceBundle());
                log.info(error, iconPath, STBImage.stbi_failure_reason());
                throw new TextureException(
                        SafeResourceLoader.format(error, iconPath, STBImage.stbi_failure_reason()));
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
        glfwSwapBuffers(windowHandle);
    }

    /**
     * Checks if the window should close.
     *
     * @return True if the window should close, false if not.
     */
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    public static int mapGLFWToImGuiKey(int key) {
        return switch (key) {
            case GLFW_KEY_TAB -> ImGuiKey.Tab;
            case GLFW_KEY_LEFT -> ImGuiKey.LeftArrow;
            case GLFW_KEY_RIGHT -> ImGuiKey.RightArrow;
            case GLFW_KEY_UP -> ImGuiKey.UpArrow;
            case GLFW_KEY_DOWN -> ImGuiKey.DownArrow;
            case GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp;
            case GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown;
            case GLFW_KEY_HOME -> ImGuiKey.Home;
            case GLFW_KEY_END -> ImGuiKey.End;
            case GLFW_KEY_INSERT -> ImGuiKey.Insert;
            case GLFW_KEY_DELETE -> ImGuiKey.Delete;
            case GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace;
            case GLFW_KEY_SPACE -> ImGuiKey.Space;
            case GLFW_KEY_ENTER -> ImGuiKey.Enter;
            case GLFW_KEY_ESCAPE -> ImGuiKey.Escape;
            case GLFW_KEY_KP_ENTER -> ImGuiKey.KeypadEnter;

            case GLFW_KEY_A -> ImGuiKey.A;
            case GLFW_KEY_B -> ImGuiKey.B;
            case GLFW_KEY_C -> ImGuiKey.C;
            case GLFW_KEY_D -> ImGuiKey.D;
            case GLFW_KEY_E -> ImGuiKey.E;
            case GLFW_KEY_F -> ImGuiKey.F;
            case GLFW_KEY_G -> ImGuiKey.G;
            case GLFW_KEY_H -> ImGuiKey.H;
            case GLFW_KEY_I -> ImGuiKey.I;
            case GLFW_KEY_J -> ImGuiKey.J;
            case GLFW_KEY_K -> ImGuiKey.K;
            case GLFW_KEY_L -> ImGuiKey.L;
            case GLFW_KEY_M -> ImGuiKey.M;
            case GLFW_KEY_N -> ImGuiKey.N;
            case GLFW_KEY_O -> ImGuiKey.O;
            case GLFW_KEY_P -> ImGuiKey.P;
            case GLFW_KEY_Q -> ImGuiKey.Q;
            case GLFW_KEY_R -> ImGuiKey.R;
            case GLFW_KEY_S -> ImGuiKey.S;
            case GLFW_KEY_T -> ImGuiKey.T;
            case GLFW_KEY_U -> ImGuiKey.U;
            case GLFW_KEY_V -> ImGuiKey.V;
            case GLFW_KEY_W -> ImGuiKey.W;
            case GLFW_KEY_X -> ImGuiKey.X;
            case GLFW_KEY_Y -> ImGuiKey.Y;
            case GLFW_KEY_Z -> ImGuiKey.Z;

            case GLFW_KEY_1 -> ImGuiKey._1;
            case GLFW_KEY_2 -> ImGuiKey._2;
            case GLFW_KEY_3 -> ImGuiKey._3;
            case GLFW_KEY_4 -> ImGuiKey._4;
            case GLFW_KEY_5 -> ImGuiKey._5;
            case GLFW_KEY_6 -> ImGuiKey._6;
            case GLFW_KEY_7 -> ImGuiKey._7;
            case GLFW_KEY_8 -> ImGuiKey._8;
            case GLFW_KEY_9 -> ImGuiKey._9;
            case GLFW_KEY_0 -> ImGuiKey._0;

            case GLFW_KEY_KP_1 -> ImGuiKey.Keypad1;
            case GLFW_KEY_KP_2 -> ImGuiKey.Keypad2;
            case GLFW_KEY_KP_3 -> ImGuiKey.Keypad3;
            case GLFW_KEY_KP_4 -> ImGuiKey.Keypad4;
            case GLFW_KEY_KP_5 -> ImGuiKey.Keypad5;
            case GLFW_KEY_KP_6 -> ImGuiKey.Keypad6;
            case GLFW_KEY_KP_7 -> ImGuiKey.Keypad7;
            case GLFW_KEY_KP_8 -> ImGuiKey.Keypad8;
            case GLFW_KEY_KP_9 -> ImGuiKey.Keypad9;
            case GLFW_KEY_KP_0 -> ImGuiKey.Keypad0;

            case GLFW_KEY_F1 -> ImGuiKey.F1;
            case GLFW_KEY_F2 -> ImGuiKey.F2;
            case GLFW_KEY_F3 -> ImGuiKey.F3;
            case GLFW_KEY_F4 -> ImGuiKey.F4;
            case GLFW_KEY_F5 -> ImGuiKey.F5;
            case GLFW_KEY_F6 -> ImGuiKey.F6;
            case GLFW_KEY_F7 -> ImGuiKey.F7;
            case GLFW_KEY_F8 -> ImGuiKey.F8;
            case GLFW_KEY_F9 -> ImGuiKey.F9;
            case GLFW_KEY_F10 -> ImGuiKey.F10;
            case GLFW_KEY_F11 -> ImGuiKey.F11;
            case GLFW_KEY_F12 -> ImGuiKey.F12;

            case GLFW_KEY_APOSTROPHE -> ImGuiKey.Apostrophe;
            case GLFW_KEY_COMMA -> ImGuiKey.Comma;
            case GLFW_KEY_MINUS -> ImGuiKey.Minus;
            case GLFW_KEY_PERIOD -> ImGuiKey.Period;
            case GLFW_KEY_SLASH -> ImGuiKey.Slash;
            case GLFW_KEY_SEMICOLON -> ImGuiKey.Semicolon;
            case GLFW_KEY_EQUAL -> ImGuiKey.Equal;
            case GLFW_KEY_LEFT_BRACKET -> ImGuiKey.LeftBracket;
            case GLFW_KEY_BACKSLASH -> ImGuiKey.Backslash;
            case GLFW_KEY_RIGHT_BRACKET -> ImGuiKey.RightBracket;
            case GLFW_KEY_GRAVE_ACCENT -> ImGuiKey.GraveAccent;
            case GLFW_KEY_CAPS_LOCK -> ImGuiKey.CapsLock;
            case GLFW_KEY_SCROLL_LOCK -> ImGuiKey.ScrollLock;
            case GLFW_KEY_NUM_LOCK -> ImGuiKey.NumLock;
            case GLFW_KEY_PRINT_SCREEN -> ImGuiKey.PrintScreen;
            case GLFW_KEY_PAUSE -> ImGuiKey.Pause;
            case GLFW_KEY_F13 -> ImGuiKey.F13;
            case GLFW_KEY_F14 -> ImGuiKey.F14;
            case GLFW_KEY_F15 -> ImGuiKey.F15;
            case GLFW_KEY_F16 -> ImGuiKey.F16;
            case GLFW_KEY_F17 -> ImGuiKey.F17;
            case GLFW_KEY_F18 -> ImGuiKey.F18;
            case GLFW_KEY_F19 -> ImGuiKey.F19;
            case GLFW_KEY_F20 -> ImGuiKey.F20;
            case GLFW_KEY_F21 -> ImGuiKey.F21;
            case GLFW_KEY_F22 -> ImGuiKey.F22;
            case GLFW_KEY_F23 -> ImGuiKey.F23;
            case GLFW_KEY_F24 -> ImGuiKey.F24;
            case GLFW_KEY_KP_DECIMAL -> ImGuiKey.KeypadDecimal;
            case GLFW_KEY_KP_DIVIDE -> ImGuiKey.KeypadDivide;
            case GLFW_KEY_KP_MULTIPLY -> ImGuiKey.KeypadMultiply;
            case GLFW_KEY_KP_SUBTRACT -> ImGuiKey.KeypadSubtract;
            case GLFW_KEY_KP_ADD -> ImGuiKey.KeypadAdd;
            case GLFW_KEY_KP_EQUAL -> ImGuiKey.KeypadEqual;
            case GLFW_KEY_LEFT_SHIFT -> ImGuiKey.LeftShift;
            case GLFW_KEY_LEFT_CONTROL -> ImGuiKey.LeftCtrl;
            case GLFW_KEY_LEFT_ALT -> ImGuiKey.LeftAlt;
            case GLFW_KEY_LEFT_SUPER -> ImGuiKey.LeftSuper;
            case GLFW_KEY_RIGHT_SHIFT -> ImGuiKey.RightShift;
            case GLFW_KEY_RIGHT_CONTROL -> ImGuiKey.RightCtrl;
            case GLFW_KEY_RIGHT_ALT -> ImGuiKey.RightAlt;
            case GLFW_KEY_RIGHT_SUPER -> ImGuiKey.RightSuper;
            case GLFW_KEY_MENU -> ImGuiKey.Menu;

            default -> ImGuiKey.None;
        };
    }
}
