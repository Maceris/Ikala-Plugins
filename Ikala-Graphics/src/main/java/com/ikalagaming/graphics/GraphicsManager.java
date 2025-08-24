package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwTerminate;

import com.ikalagaming.graphics.backend.opengl.OpenGLInstance;
import com.ikalagaming.graphics.backend.vulkan.VulkanInstance;
import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.frontend.BackendType;
import com.ikalagaming.graphics.frontend.DeletionQueue;
import com.ikalagaming.graphics.frontend.Instance;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Provides utilities for handling graphics. */
@Slf4j
public class GraphicsManager {

    /** Whether we are currently initialized. */
    static final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Whether we want to refresh the scene information. */
    private static final AtomicBoolean refreshRequested = new AtomicBoolean(false);

    /**
     * The scene we are rendering.
     *
     * @return The current scene.
     */
    @Getter private static Scene scene;

    /**
     * The rendering instance.
     *
     * @return The rendering instance.
     */
    @Getter private static Instance renderInstance;

    /**
     * The camera manager.
     *
     * @return The camera manager.
     */
    @Getter private static CameraManager cameraManager;

    /** Used to track when we should render another frame. */
    private static Timer renderTimer;

    /** Used to track when we should make updates. */
    private static Timer updateTimer;

    /** The target frames per second that we want to hit. */
    public static final int TARGET_FPS = 144;

    /** The target updates per second that we want to hit. */
    public static final int TARGET_UPS = 60;

    /** The (fractional) number of seconds between frame renders at the target FPS. */
    private static final float FRAME_TIME = 1f / TARGET_FPS;

    /** The (fractional) number of seconds between updates at the target UPS. */
    private static final float UPDATE_TIME = 1f / TARGET_UPS;

    /** The time when the next render call should happen to maintain the target FPS. */
    private static double nextRenderTime;

    /** The time when the next render call should happen to maintain the target UPS. */
    private static double nextUpdateTime;

    /** The last time we rendered a frame, for calculating FPS. */
    private static double lastTime;

    /** How many frames we rendered since we calculated FPS. */
    private static int framesSinceLastCalculation;

    /**
     * The last recorded Frames Per Second.
     *
     * @return The last known FPS.
     */
    @Getter private static int lastFPS;

    /** Whether we should shut down. */
    @Getter(value = AccessLevel.PACKAGE)
    private static final AtomicBoolean shutdownFlag = new AtomicBoolean(false);

    /**
     * Used to track the tick method reference in the framework.
     *
     * @param tickStageID The stage ID to use.
     * @return The tick stage ID.
     */
    @Getter(value = AccessLevel.PACKAGE)
    @Setter(value = AccessLevel.PACKAGE)
    private static UUID tickStageID;

    /**
     * The window utility.
     *
     * @return The window object.
     */
    @Getter private static Window window;

    /**
     * The window manager for drawing GUIs.
     *
     * @return The window manager.
     */
    @Getter private static final WindowManager windowManager = new WindowManager();

    /** A queue used to delete resources. */
    @Getter private static final DeletionQueue deletionQueue = new DeletionQueue();

    @Getter private static final BackendType backendType = BackendType.OPENGL;

    /**
     * Creates a graphics window, fires off a {@link WindowCreated} event. Won't do anything if a
     * window already exists.
     */
    public static boolean createWindow() {
        if ((null != window) || !initialized.compareAndSet(false, true)) {
            return false;
        }
        shutdownFlag.set(false);

        Window.WindowOptions opts = new Window.WindowOptions();
        opts.antiAliasing = true;
        opts.fps = 144;
        window =
                new Window(
                        "Ikala Gaming",
                        opts,
                        () -> {
                            resize();
                            return null;
                        });

        log.debug(
                SafeResourceLoader.getString("WINDOW_CREATED", GraphicsPlugin.getResourceBundle()));
        new WindowCreated(window.getWindowHandle()).fire();

        renderInstance =
                switch (backendType) {
                    case OPENGL -> new OpenGLInstance();
                    case VULKAN -> new VulkanInstance();
                };

        if (!renderInstance.initialize(window)) {
            return false;
        }

        log.debug(
                SafeResourceLoader.getString(
                        "RENDERER_CREATED", GraphicsPlugin.getResourceBundle()));

        scene = new Scene(window.getWidth(), window.getHeight());

        cameraManager = new CameraManager(scene.getCamera(), window);

        renderInstance.setupData(scene);

        renderTimer = new Timer();
        renderTimer.init();
        nextRenderTime = renderTimer.getLastLoopTime() + FRAME_TIME;

        updateTimer = new Timer();
        updateTimer.init();
        nextUpdateTime = renderTimer.getLastLoopTime() + UPDATE_TIME;

        lastTime = glfwGetTime();
        return true;
    }

    /**
     * Whether we are currently initialized.
     *
     * @return If we have already set up the window.
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /** Set up the render data after adding/removing renderables. */
    public static void refreshRenderData() {
        refreshRequested.set(true);
    }

    /** Render to the screen. */
    private static void render() {
        renderInstance.render(scene);

        window.update();
        ++framesSinceLastCalculation;

        final double currentTime = glfwGetTime();
        if (currentTime - lastTime >= 1d) {
            lastFPS = framesSinceLastCalculation;
            framesSinceLastCalculation = 0;
            lastTime = currentTime;
        }
    }

    private static void resize() {
        int width = window.getWidth();
        int height = window.getHeight();
        scene.resize(width, height);
        renderInstance.resize(width, height);
    }

    /**
     * Change over to another rendering pipeline.
     *
     * @param config The configuration specifying the pipeline to switch to.
     * @see RenderConfig For details on obtaining the values to pass here.
     */
    public static void swapPipeline(final int config) {
        renderInstance.swapPipeline(config);
    }

    /**
     * Terminate GLFW and free the error callback. If any windows still remain, they are destroyed.
     */
    public static void terminate() {
        renderInstance.cleanup();

        if (null != window) {
            window.destroy();
            window = null;
        }

        glfwTerminate();
        Optional.ofNullable(glfwSetErrorCallback(null)).ifPresent(GLFWErrorCallback::free);
        initialized.set(false);
    }

    /**
     * Does a graphical update.
     *
     * @return A status indicating to the launcher the status of this main loop stage.
     */
    static int tick() {
        if (null == window) {
            return Launcher.STATUS_ERROR;
        }
        if (shutdownFlag.get()) {
            terminate();
            return Launcher.STATUS_OK;
        }

        if (window.windowShouldClose()) {
            window.destroy();
            window = null;
            new Shutdown().fire();
            return Launcher.STATUS_REQUEST_REMOVAL;
        }

        window.pollEvents();

        ModelLoader.loadModel();

        renderInstance.processResources();

        if (updateTimer.getTime() >= nextUpdateTime) {
            final float elapsedTime = updateTimer.getElapsedTime();

            window.getMouseInput().input();
            cameraManager.updateCamera(elapsedTime);

            windowManager.handleGuiInput(scene, window);

            if (refreshRequested.compareAndSet(true, false)) {
                renderInstance.setupData(scene);
            }

            // Update the next time we should update models
            nextUpdateTime = updateTimer.getLastLoopTime() + UPDATE_TIME;
        }

        if (renderTimer.getTime() >= nextRenderTime) {
            render();
            // Update the next time we should render a frame
            nextRenderTime = renderTimer.getLastLoopTime() + FRAME_TIME;
        }

        return Launcher.STATUS_OK;
    }

    /** Private constructor so this class is not initialized. */
    private GraphicsManager() {}
}
