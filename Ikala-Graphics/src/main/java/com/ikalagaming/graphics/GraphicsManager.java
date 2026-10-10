package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwTerminate;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.vulkan.DeletionQueue;
import com.ikalagaming.graphics.vulkan.TextureRegistry;
import com.ikalagaming.graphics.vulkan.VulkanInstance;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Provides utilities for handling graphics. */
@Slf4j
public class GraphicsManager {

    /** Whether we are currently initialized. */
    static final AtomicBoolean initialized = new AtomicBoolean(false);

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
    @Getter private static VulkanInstance renderInstance;

    /**
     * The camera manager.
     *
     * @return The camera manager.
     */
    @Getter private static CameraManager cameraManager;

    private static double lastRenderTime;
    private static double lastUpdateTime;

    /** The maximum number of frames in flight we have. */
    public static final int MAX_FRAMES_IN_FLIGHT = 2;

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
    private static double lastFPSTime;

    /** How many frames we rendered since we calculated FPS. */
    private static int framesSinceLastCalculation;

    /**
     * The last recorded Frames Per Second.
     *
     * @return The last known FPS.
     */
    @Getter private static int lastFPS;

    /** Used to signal that someone has requested the rendering quality be changed. */
    private static final AtomicBoolean qualityChanged = new AtomicBoolean(false);

    /**
     * The new quality that was requested. Null if irrelevant, but see {@link #qualityChanged} for
     * determining if the quality was set.
     */
    @Nullable private static GraphicsSettings.Quality requestedQuality = null;

    /**
     * Whether another thread asked us to shut down, which the main thread picks up on its next
     * tick.
     *
     * @see #requestShutdown()
     */
    private static final AtomicBoolean shutdownFlag = new AtomicBoolean(false);

    /**
     * The thread that created the window. GLFW and the renderer must only be used from this thread,
     * including when cleaning up.
     */
    private static Thread mainThread;

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

    /**
     * The retained UI surfaces, drawn along with the GUI windows.
     *
     * @return The UI manager.
     */
    @Getter private static final UiManager uiManager = new UiManager();

    static {
        windowManager.setUiManager(uiManager);
    }

    /** A queue used to delete resources. */
    @Getter private static final DeletionQueue deletionQueue = new DeletionQueue();

    /** The graphics context for each plugin that asked for one, by plugin name. */
    private static final Map<String, GraphicsContext> contexts = new ConcurrentHashMap<>();

    /** The settings to use for rendering. Should be set up before creating a window. */
    @Getter private static final GraphicsSettings settings = new GraphicsSettings();

    /**
     * Creates a graphics window, fires off a {@link WindowCreated} event. Won't do anything if a
     * window already exists.
     */
    public static boolean createWindow() {
        if ((null != window) || !initialized.compareAndSet(false, true)) {
            return false;
        }
        shutdownFlag.set(false);
        mainThread = Thread.currentThread();

        /*
         * TODO(ches) we will probably want to refactor this so windows are more transient, rather than being treated
         * as equivalent to the application. Some amount of the rendering instance can survive windows being
         * created and destroyed, besides things like swapchains.
         */

        window = new Window("Ikala Gaming", settings, GraphicsManager::resize);

        log.debug("Window created");
        new WindowCreated(window.getWindowHandle()).fire();

        renderInstance = new VulkanInstance();

        if (!renderInstance.initialize(window)) {
            return false;
        }

        log.debug("Renderer created");

        scene = new Scene(renderInstance.getState(), window.getWidth(), window.getHeight());

        cameraManager = new CameraManager(scene.getCamera(), window);

        lastRenderTime = getTime();
        nextRenderTime = lastRenderTime + FRAME_TIME;

        lastUpdateTime = getTime();
        nextUpdateTime = lastUpdateTime + UPDATE_TIME;

        lastFPSTime = glfwGetTime();
        return true;
    }

    /**
     * Fetch the graphics context for the currently loaded instance of a plugin. Everything created
     * through it is owned by that plugin instance, and is released when it unloads.
     *
     * @param pluginName The name of the plugin, as it appears in its plugin.yml.
     * @return The plugin's graphics context.
     */
    public static GraphicsContext forPlugin(@NonNull String pluginName) {
        // Look this up before touching the map, so we never wait on the plugin lock inside it
        final Plugin current = loadedInstance(pluginName);
        GraphicsContext[] replaced = {null};
        GraphicsContext context =
                contexts.compute(
                        pluginName,
                        (name, existing) -> {
                            if (existing != null && existing.belongsTo(current)) {
                                return existing;
                            }
                            replaced[0] = existing;
                            return new GraphicsContext(name, current);
                        });
        if (replaced[0] != null) {
            // An older instance of the plugin, whose unload we haven't processed yet
            release(replaced[0]);
        }
        return context;
    }

    /**
     * Release everything an unloaded plugin owned and close its context. If the plugin was already
     * loaded again and has a new context, that context is left alone. Safe to call from any thread,
     * since the resources are deleted on the render thread later.
     *
     * @param pluginName The name of the plugin that unloaded.
     */
    static void pluginUnloaded(@NonNull String pluginName) {
        final Plugin current = loadedInstance(pluginName);
        GraphicsContext[] stale = {null};
        contexts.computeIfPresent(
                pluginName,
                (name, existing) -> {
                    if (current != null && existing.belongsTo(current)) {
                        return existing;
                    }
                    stale[0] = existing;
                    return null;
                });
        if (stale[0] != null) {
            release(stale[0]);
        }
    }

    /**
     * Close a context and queue up deletion of everything it owns.
     *
     * @param context The context to release.
     */
    private static void release(@NonNull GraphicsContext context) {
        context.close();
        int windows = windowManager.removeAllOwnedBy(context);
        uiManager.removeAllOwnedBy(context);
        if (windows > 0) {
            log.debug("Removed {} windows owned by {}", windows, context.getOwnerKey());
        }
        if (renderInstance == null) {
            return;
        }
        TextureRegistry registry = renderInstance.getState().textureRegistry;
        if (registry == null) {
            return;
        }
        var textures = registry.removeAllOwnedBy(context.getOwnerKey());
        textures.forEach(deletionQueue::add);
        if (!textures.isEmpty()) {
            log.debug("Released {} textures owned by {}", textures.size(), context.getOwnerKey());
        }
    }

    /**
     * Hand every debug shape that plugins want drawn this frame to a consumer, emptying their
     * queues of immediate shapes. Render thread only.
     *
     * @param consumer Receives each shape.
     */
    public static void collectDebugShapes(@NonNull Consumer<DebugShape> consumer) {
        contexts.values().forEach(context -> context.collectDebugShapes(consumer));
    }

    /**
     * Find the currently loaded instance of a plugin.
     *
     * @param pluginName The plugin name.
     * @return The plugin, or null if it isn't loaded.
     */
    private static Plugin loadedInstance(@NonNull String pluginName) {
        // Never create a plugin manager here: this runs in unload events, which can arrive while
        // the program shuts down and the plugin manager is already gone
        return PluginManager.getExistingInstance()
                .flatMap(manager -> manager.getPlugin(pluginName))
                .orElse(null);
    }

    /**
     * Whether we are currently initialized.
     *
     * @return If we have already set up the window.
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /** Render to the screen. */
    private static void render() {
        renderInstance.render(scene, window);

        ++framesSinceLastCalculation;

        final double currentTime = glfwGetTime();
        if (currentTime - lastFPSTime >= 1d) {
            lastFPS = framesSinceLastCalculation;
            framesSinceLastCalculation = 0;
            lastFPSTime = currentTime;
        }
    }

    private static void resize(@NonNull Window window) {
        int width = window.getWidth();
        int height = window.getHeight();
        scene.resize(width, height);
        renderInstance.resize(window, width, height);
    }

    /**
     * Request that a new rendering quality be used. This is a very heavy operation, so it will
     * happen at a time the rendering engine finds most convenient rather than immediately, and this
     * call is ignored if the requested quality is the quality that we currently are using.
     *
     * @param quality The new quality.
     */
    public static void setQuality(@NonNull GraphicsSettings.Quality quality) {
        if (settings.quality == quality) {
            return;
        }
        requestedQuality = quality;
        qualityChanged.set(true);
    }

    /**
     * Fetch the current rendering configuration.
     *
     * @return The current pipeline config.
     * @see RenderConfig
     */
    public static int getPipelineConfig() {
        return renderInstance.getPipelineConfig();
    }

    /**
     * Get the current system time in seconds.
     *
     * @return The current time.
     */
    private static double getTime() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    /**
     * Change over to another rendering pipeline.
     *
     * @param config The configuration specifying the pipeline to switch to.
     * @see RenderConfig
     */
    public static void swapPipeline(final int config) {
        renderInstance.swapPipeline(config);
    }

    /**
     * Shut down graphics from any thread. On the main thread, this cleans up immediately.
     * Otherwise, it flags the main thread to clean up on its next tick, after which the tick stage
     * removes itself.
     */
    static void requestShutdown() {
        if (Thread.currentThread() == mainThread) {
            terminate();
        } else {
            shutdownFlag.set(true);
        }
    }

    /**
     * Clean up the renderer, terminate GLFW and free the error callback. If any windows still
     * remain, they are destroyed. Does nothing if we already terminated. Must be called from the
     * main thread.
     */
    public static void terminate() {
        if (!initialized.get()) {
            return;
        }
        // The renderer releases every texture as it cleans up
        contexts.values().forEach(GraphicsContext::close);
        contexts.clear();
        if (scene != null) {
            // Queues everything up for deletion, which the renderer processes as it cleans up
            scene.cleanup();
        }
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
            // Already terminated, so there is nothing left to tick
            return Launcher.STATUS_REQUEST_REMOVAL;
        }
        if (shutdownFlag.get()) {
            terminate();
            return Launcher.STATUS_REQUEST_REMOVAL;
        }

        if (window.windowShouldClose()) {
            /*
             * Clean up the renderer now, while the window it renders to still exists. Once this stage is removed
             * nothing would call terminate() later.
             */
            terminate();
            new Shutdown().fire();
            return Launcher.STATUS_REQUEST_REMOVAL;
        }

        window.pollEvents();
        uiManager.setContentScale(window.getContentScale());
        // Button clicks and the like from the last frame, run here rather than mid-render
        uiManager.dispatchEvents();

        ModelLoader.loadModel();

        renderInstance.processResources();

        final double currentTime = getTime();

        if (currentTime >= nextUpdateTime) {
            final float elapsedTime = (float) (currentTime - lastUpdateTime);

            IkIO ikIO = IkGui.getIO();
            if (!ikIO.wantCaptureMouse) {
                if (ikIO.getMouseDown(MouseButton.RIGHT)) {
                    window.disableCursor();
                } else {
                    window.enableCursor();
                }
            }
            cameraManager.updateCamera(elapsedTime);

            windowManager.handleGuiInput(scene, window);
            windowManager.updateValues(scene, window);

            // Update the next time we should update models
            lastUpdateTime = currentTime;
            nextUpdateTime = currentTime + UPDATE_TIME;
        }

        if (qualityChanged.get() && requestedQuality != null) {
            final GraphicsSettings.Quality oldQuality = settings.quality;
            settings.quality = requestedQuality;
            renderInstance.setQuality(oldQuality, requestedQuality);
            qualityChanged.set(false);
        }

        if (currentTime >= nextRenderTime) {
            render();
            // Update the next time we should render a frame
            lastRenderTime = currentTime;
            nextRenderTime = currentTime + FRAME_TIME;
        }

        return Launcher.STATUS_OK;
    }

    /** Private constructor so this class is not initialized. */
    private GraphicsManager() {}
}
