/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
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

        /** The target updates per second. */
        public int ups = GraphicsManager.TARGET_FPS;

        /** The width of the window in pixels. */
        public int width;
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
    private MouseInput mouseInput;

    /** The resize function to call. */
    private Callable<Void> resizeFunc;

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
                    ImGuiIO io = ImGui.getIO();
                    if (action == GLFW_PRESS) {
                        io.setKeysDown(key, true);
                    }
                    if (action == GLFW_RELEASE) {
                        io.setKeysDown(key, false);
                    }
                    io.setKeyCtrl(
                            io.getKeysDown(GLFW_KEY_LEFT_CONTROL)
                                    || io.getKeysDown(GLFW_KEY_RIGHT_CONTROL));
                    io.setKeyShift(
                            io.getKeysDown(GLFW_KEY_LEFT_SHIFT)
                                    || io.getKeysDown(GLFW_KEY_RIGHT_SHIFT));
                    io.setKeyAlt(
                            io.getKeysDown(GLFW_KEY_LEFT_ALT)
                                    || io.getKeysDown(GLFW_KEY_RIGHT_ALT));
                    io.setKeySuper(
                            io.getKeysDown(GLFW_KEY_LEFT_SUPER)
                                    || io.getKeysDown(GLFW_KEY_RIGHT_SUPER));
                });
        glfwSetScrollCallback(
                windowHandle,
                (window, xOffset, yOffset) -> {
                    ImGuiIO io = ImGui.getIO();
                    io.setMouseWheelH((float) (io.getMouseWheelH() + xOffset));
                    io.setMouseWheel((float) (io.getMouseWheel() + yOffset));
                });
        glfwSetCharCallback(
                windowHandle,
                (window, codepoint) -> {
                    ImGuiIO io = ImGui.getIO();
                    io.addInputCharacter(codepoint);
                });
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
}
