package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;

/**
 * Provides convenience methods for a GLFW window. Input that goes to IkGui is hooked up by {@link
 * GlfwGuiBackend}.
 */
@Slf4j
@Getter
public class Window {

    /**
     * Set up the window hints.
     *
     * @param options How to create the window.
     */
    private static void setWindowHints(@NonNull WindowOptions options) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        // Windows that start hidden are shown when ready, which shouldn't take the focus
        glfwWindowHint(GLFW_FOCUSED, options.visible() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, options.resizable() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, options.decorated() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING, options.floating() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, options.focusOnShow() ? GLFW_TRUE : GLFW_FALSE);
        if (options.maximized()) {
            glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
        }
        // Vulkan draws to the window, so GLFW shouldn't make an OpenGL context
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
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

    /** Whether we are currently fullscreen (borderless). */
    private boolean fullscreen;

    /** The resize function to call. */
    private final Consumer<Window> resizeFunc;

    /** The title shown for the window. */
    private String title;

    /**
     * Whether the window's mouse, keyboard and focus go to IkGui.
     *
     * @return True if IkGui gets the window's input.
     */
    private final boolean guiInput;

    /** Where {@link #getContentScale} reads the horizontal scale into, kept between calls. */
    private final float[] contentScaleX = new float[1];

    /** Where it reads the vertical scale into. */
    private final float[] contentScaleY = new float[1];

    /**
     * Create a new window, and show it unless the options say not to. {@link GlfwPlatform} must be
     * initialized first.
     *
     * @param options How to create the window.
     * @param resizeFunc The function to call upon the window resizing.
     * @throws WindowCreationException If the window could not be created.
     */
    public Window(@NonNull WindowOptions options, @NonNull Consumer<Window> resizeFunc) {
        if (!GlfwPlatform.isInitialized()) {
            throw new WindowCreationException("GLFW must be initialized before creating windows");
        }
        this.resizeFunc = resizeFunc;
        this.fullscreen = false;
        this.guiInput = options.guiInput();

        setWindowHints(options);

        if (options.maximized()) {
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode == null) {
                final String error =
                        "Failed to create a GLFW window due to an error fetching video mode";
                log.warn(error);
                throw new WindowCreationException(error);
            }
            width = vidMode.width();
            height = vidMode.height();
        } else {
            width = options.width();
            height = options.height();
        }

        this.title = options.title();
        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            final String error = "Failed to create a GLFW window";
            log.warn(error);
            throw new WindowCreationException(error);
        }

        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> resized(w, h));

        if (options.icon() && glfwGetPlatform() != GLFW_PLATFORM_COCOA) {
            setWindowIcon();
        }

        if (options.visible()) {
            glfwShowWindow(windowHandle);
        }

        int[] arrWidth = new int[1];
        int[] arrHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, arrWidth, arrHeight);
        width = arrWidth[0];
        height = arrHeight[0];
    }

    /**
     * Show the window, if it was created hidden.
     *
     * @param focus Whether to give it the input focus too.
     */
    public void show(boolean focus) {
        glfwShowWindow(windowHandle);
        if (focus) {
            glfwFocusWindow(windowHandle);
        }
    }

    /**
     * Change the title shown for the window.
     *
     * @param newTitle The new title.
     */
    public void setTitle(@NonNull String newTitle) {
        title = newTitle;
        glfwSetWindowTitle(windowHandle, newTitle);
    }

    /**
     * Disable the cursor.
     *
     * @see #enableCursor()
     */
    public void disableCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    /** Destroy the window. GLFW stays initialized, for the other windows. */
    public void destroy() {
        if (NULL == windowHandle) {
            return;
        }

        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
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
        if (IkGui.getContext() != null) {
            final IkIO io = IkGui.getIO();
            if (io.wantCaptureKeyboard) {
                return false;
            }
        }
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    /**
     * The window's content scale, which the OS sets from the monitor's DPI and the user's display
     * scaling setting.
     *
     * @return The horizontal content scale, where 1 is 96 DPI on Windows.
     */
    public float getContentScale() {
        glfwGetWindowContentScale(windowHandle, contentScaleX, contentScaleY);
        return contentScaleX[0];
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

    /**
     * Ask the window to close, as if the user closed it. For the main window, that shuts graphics
     * down on the next tick.
     */
    public void requestClose() {
        glfwSetWindowShouldClose(windowHandle, true);
    }

    /**
     * Whether the window has been asked to close.
     *
     * @return True once it should close.
     */
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    @Override
    public String toString() {
        return "Window " + title;
    }
}
