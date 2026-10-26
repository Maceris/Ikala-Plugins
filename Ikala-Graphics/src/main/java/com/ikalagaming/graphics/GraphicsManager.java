package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

import com.ikalagaming.graphics.benchmark.GateBenchmark;
import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.events.WindowDestroyed;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.vulkan.DeletionQueue;
import com.ikalagaming.graphics.vulkan.GeometryArena;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.LodRenderer;
import com.ikalagaming.graphics.vulkan.SkinnedRenderer;
import com.ikalagaming.graphics.vulkan.TextureRegistry;
import com.ikalagaming.graphics.vulkan.VulkanInstance;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Provides utilities for handling graphics. */
@Slf4j
public class GraphicsManager {

    /** The name of the application, used for the main window title and the Vulkan instance. */
    public static final String APPLICATION_NAME = "Ikala Gaming";

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

    /** How long before the next frame or update the main thread stops waiting, in seconds. */
    private static final double WAKE_EARLY = 0.0015;

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
     * The thread that initialized graphics. GLFW and the renderer must only be used from this
     * thread, including when cleaning up.
     */
    private static Thread mainThread;

    /**
     * The main window. Closing it shuts the application down.
     *
     * @return The main window.
     */
    @Getter private static Window mainWindow;

    /**
     * The window the scene is drawn in, the main window unless {@link #setSceneWindow(Window)}
     * moved it.
     *
     * @return The scene window.
     */
    @Getter private static Window sceneWindow;

    /** The windows besides the main one, in the order they were created. Main thread only. */
    private static final List<Window> extraWindows = new ArrayList<>();

    /**
     * The windows IkGui created for its viewports, which it also closes. Kept apart from the other
     * windows, so nothing else closes them or draws the scene in them. Main thread only.
     */
    private static final List<Window> guiWindows = new ArrayList<>();

    /** Whether graphics are shutting down, when window events are no longer fired. */
    private static boolean terminating = false;

    /** Connects IkGui to GLFW, null while graphics aren't running. */
    private static GlfwGuiBackend guiBackend;

    /** Work other threads asked to do on the main thread, run at the start of the next tick. */
    private static final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

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

    /** The settings to use for rendering. Should be set up before initializing. */
    @Getter private static final GraphicsSettings settings = new GraphicsSettings();

    /**
     * Start up graphics: GLFW, then the Vulkan instance and device, then the main window, which
     * fires off a {@link WindowCreated} event, then the GUI and scene. Won't do anything if we are
     * already initialized.
     *
     * @return True if graphics started, false if they were already running or failed.
     */
    public static boolean initialize() {
        if ((null != mainWindow) || !initialized.compareAndSet(false, true)) {
            return false;
        }
        shutdownFlag.set(false);
        mainThread = Thread.currentThread();

        GlfwPlatform.initialize();

        renderInstance = new VulkanInstance();
        if (!renderInstance.initialize(APPLICATION_NAME)) {
            return false;
        }
        log.debug("Renderer created");

        mainWindow =
                new Window(
                        WindowOptions.mainWindow(APPLICATION_NAME, settings),
                        GraphicsManager::windowResized);
        sceneWindow = mainWindow;
        log.debug("Window created");
        new WindowCreated(mainWindow.getWindowHandle()).fire();

        renderInstance.createPresentation(mainWindow);
        initializeGui(mainWindow);
        log.trace("Initialized GUI");
        renderInstance.initializePipelines(mainWindow.getWidth(), mainWindow.getHeight());

        scene = new Scene(mainWindow.getWidth(), mainWindow.getHeight());

        cameraManager = new CameraManager(scene.getCamera(), GraphicsManager::getSceneWindow);

        lastRenderTime = getTime();
        nextRenderTime = lastRenderTime + FRAME_TIME;

        lastUpdateTime = getTime();
        nextUpdateTime = lastUpdateTime + UPDATE_TIME;

        lastFPSTime = glfwGetTime();
        return true;
    }

    /**
     * Create the IkGui context, which every window shares, and configure it.
     *
     * @param window The main window, which IkGui gets its display size and platform functions from.
     */
    private static void initializeGui(@NonNull Window window) {
        IkGui.createContext();

        IkIO ikIO = IkGui.getIO();
        // Window positions, sizes and docking are kept between runs
        ikIO.iniFilename = settings.guiLayoutFile;
        // Tab, arrow keys and gamepads move between items, so menus work without a mouse
        ikIO.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.NAV_ENABLE_GAMEPAD;
        // GUI windows can be docked into each other, as tabs or side by side
        ikIO.configFlags |= ConfigFlags.DOCKING_ENABLE;
        if (settings.guiViewports) {
            // GUI windows can be dragged out of the main window into windows of their own
            ikIO.configFlags |= ConfigFlags.VIEWPORTS_ENABLE;
        }
        ikIO.displaySize.set(window.getWidth(), window.getHeight());
        guiBackend = new GlfwGuiBackend(window);
        guiBackend.install();
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
        if (scene != null) {
            int lights = scene.getLightRegistry().removeAllOwnedBy(context.getOwnerKey());
            int media = scene.getMediumRegistry().removeAllOwnedBy(context.getOwnerKey());
            if (media > 0) {
                log.debug("Removed {} medium volumes owned by {}", media, context.getOwnerKey());
            }
            int emitters = scene.getEmitterRegistry().removeAllOwnedBy(context.getOwnerKey());
            if (emitters > 0) {
                log.debug(
                        "Removed {} particle emitters owned by {}",
                        emitters,
                        context.getOwnerKey());
            }
            if (lights > 0) {
                log.debug("Removed {} lights owned by {}", lights, context.getOwnerKey());
            }
        }
        if (renderInstance == null) {
            return;
        }
        // Sections first, so none is placed again after its instances are removed below
        if (renderInstance.getState().sections != null) {
            int sections =
                    renderInstance.getState().sections.removeAllOwnedBy(context.getOwnerKey());
            if (sections > 0) {
                log.debug("Removed {} sections owned by {}", sections, context.getOwnerKey());
            }
        }
        InstanceTable instances = renderInstance.getState().instances;
        if (instances != null) {
            var removed = instances.getRegistry().removeAllOwnedBy(context.getOwnerKey());
            removed.forEach(instances::retire);
            if (!removed.isEmpty()) {
                log.debug(
                        "Removed {} instances owned by {}", removed.size(), context.getOwnerKey());
            }
        }
        GeometryArena geometry = renderInstance.getState().geometry;
        if (geometry != null) {
            int meshes = geometry.releaseAllOwnedBy(context.getOwnerKey());
            if (meshes > 0) {
                log.debug("Released {} meshes owned by {}", meshes, context.getOwnerKey());
            }
        }
        if (renderInstance.getState().bakeSources != null) {
            renderInstance.getState().bakeSources.removeAllOwnedBy(context.getOwnerKey());
        }
        LodRenderer lods = renderInstance.getState().lods;
        if (lods != null) {
            lods.removeAllOwnedBy(context.getOwnerKey());
        }
        SkinnedRenderer skins = renderInstance.getState().skins;
        if (skins != null) {
            int removed = skins.removeAllOwnedBy(context.getOwnerKey());
            if (removed > 0) {
                log.debug(
                        "Removed {} skinned instances and meshes owned by {}",
                        removed,
                        context.getOwnerKey());
            }
        }
        GeometryArena bakedGeometry = renderInstance.getState().bakedGeometry;
        if (bakedGeometry != null) {
            int meshes = bakedGeometry.releaseAllOwnedBy(context.getOwnerKey());
            if (meshes > 0) {
                log.debug("Released {} baked meshes owned by {}", meshes, context.getOwnerKey());
            }
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
     * @return If graphics are running.
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /** Render to the screen. */
    private static void render() {
        // The GUI is built before rendering starts, since it can create and close windows
        Map<Window, DrawData> gui = Map.of();
        if (renderInstance.getPipelineConfig().gui()) {
            final IkIO io = IkGui.getIO();
            windowManager.drawGui((int) io.displaySize.x, (int) io.displaySize.y);
            // Creates, moves and closes the windows of IkGui viewports
            IkGui.updatePlatformWindows();
            gui = guiDrawData();
        }
        renderInstance.render(scene, sceneWindow, gui);

        ++framesSinceLastCalculation;

        final double currentTime = glfwGetTime();
        if (currentTime - lastFPSTime >= 1d) {
            lastFPS = framesSinceLastCalculation;
            framesSinceLastCalculation = 0;
            lastFPSTime = currentTime;
        }
    }

    /**
     * The GUI draw data for each window, from IkGui's viewports: the main viewport is drawn in the
     * main window, and any other viewport in the window whose platform handle it holds.
     *
     * @return The draw data by window, only for windows that are still open.
     */
    private static Map<Window, DrawData> guiDrawData() {
        Map<Window, DrawData> gui = new HashMap<>();
        final DrawData main = IkGui.getDrawData();
        if (main != null) {
            gui.put(mainWindow, main);
        }
        for (Viewport viewport : IkGui.getPlatformIO().viewports) {
            if (viewport.platformHandle instanceof Window window
                    && window != mainWindow
                    && guiWindows.contains(window)
                    && viewport.drawData.valid) {
                gui.put(window, viewport.drawData);
            }
        }
        return gui;
    }

    /**
     * A window was resized. Its swapchain follows, the scene follows the scene window, and the GUI
     * follows the main window.
     *
     * @param window The window.
     */
    private static void windowResized(@NonNull Window window) {
        renderInstance.resize(window);
        if (window == sceneWindow) {
            scene.resize(window.getWidth(), window.getHeight());
        }
        if (window == mainWindow) {
            IkGui.getIO().displaySize.set(window.getWidth(), window.getHeight());
        }
    }

    /**
     * Draw the scene in another open window from the next frame on. Only one window draws the scene
     * at a time, and every window can draw GUI. If the scene window closes, the scene goes back to
     * the main window. Main thread only, see {@link #runOnMainThread(Runnable)}.
     *
     * @param window The window to draw the scene in.
     * @throws IllegalArgumentException If the window isn't open.
     * @throws IllegalStateException If this isn't the main thread.
     */
    public static void setSceneWindow(@NonNull Window window) {
        checkMainThread();
        if (window != mainWindow && !extraWindows.contains(window)) {
            throw new IllegalArgumentException("The scene can only be drawn in an open window");
        }
        if (window == sceneWindow) {
            return;
        }
        // Don't leave the cursor hidden in the old window if it was turning the camera
        sceneWindow.enableCursor();
        sceneWindow = window;
        scene.resize(window.getWidth(), window.getHeight());
        renderInstance.sceneWindowChanged(window);
        log.debug("Drawing the scene in {}", window.getTitle());
    }

    /**
     * Create another window, which the application owns until it is closed. Closing it, from the OS
     * or {@link #closeWindow(Window)}, only destroys that window. Main thread only, see {@link
     * #runOnMainThread(Runnable)}.
     *
     * @param options How to create the window.
     * @return The new window.
     * @throws IllegalStateException If graphics aren't running, or this isn't the main thread.
     */
    public static Window createWindow(@NonNull WindowOptions options) {
        checkMainThread();
        Window window = new Window(options, GraphicsManager::windowResized);
        try {
            renderInstance.createPresentation(window);
        } catch (RuntimeException e) {
            window.destroy();
            throw e;
        }
        extraWindows.add(window);
        if (options.guiInput()) {
            guiBackend.installInputCallbacks(window);
        }
        log.debug("Created window {}", window.getTitle());
        new WindowCreated(window.getWindowHandle()).fire();
        return window;
    }

    /**
     * Create a hidden window for an IkGui viewport. Its swapchain is made separately, once IkGui
     * asks the renderer for it.
     *
     * @param options How to create the window.
     * @return The window.
     */
    static Window createGuiWindow(@NonNull WindowOptions options) {
        Window window = new Window(options, GraphicsManager::windowResized);
        guiWindows.add(window);
        new WindowCreated(window.getWindowHandle()).fire();
        return window;
    }

    /**
     * Give an IkGui viewport's window its swapchain.
     *
     * @param window The window.
     */
    static void createGuiPresentation(@NonNull Window window) {
        renderInstance.createPresentation(window);
    }

    /**
     * Destroy an IkGui viewport's swapchain, before its window.
     *
     * @param window The window.
     */
    static void destroyGuiPresentation(@NonNull Window window) {
        renderInstance.destroyPresentation(window);
    }

    /**
     * Destroy an IkGui viewport's window, once its swapchain is gone.
     *
     * @param window The window.
     */
    static void destroyGuiWindow(@NonNull Window window) {
        if (guiWindows.remove(window)) {
            if (!terminating) {
                new WindowDestroyed(window.getWindowHandle()).fire();
            }
            window.destroy();
        }
    }

    /**
     * IkGui resized a viewport's window, so its swapchain follows.
     *
     * @param window The window.
     */
    static void guiWindowResized(@NonNull Window window) {
        renderInstance.resize(window);
    }

    /**
     * Close a window. Closing the main window asks the application to shut down, like the user
     * closing it would. Main thread only, see {@link #runOnMainThread(Runnable)}.
     *
     * @param window The window to close.
     * @throws IllegalStateException If this isn't the main thread.
     */
    public static void closeWindow(@NonNull Window window) {
        checkMainThread();
        if (window == mainWindow) {
            mainWindow.requestClose();
            return;
        }
        if (!extraWindows.contains(window)) {
            return;
        }
        if (window == sceneWindow) {
            setSceneWindow(mainWindow);
        }
        extraWindows.remove(window);
        // The surface has to go before the window it was made for
        renderInstance.destroyPresentation(window);
        new WindowDestroyed(window.getWindowHandle()).fire();
        window.destroy();
        log.debug("Closed window {}", window.getTitle());
    }

    /**
     * Every open window, the main window first. Main thread only.
     *
     * @return The windows, which can't be changed.
     */
    public static List<Window> getWindows() {
        if (mainWindow == null) {
            return List.of();
        }
        List<Window> windows = new ArrayList<>(extraWindows.size() + 1);
        windows.add(mainWindow);
        windows.addAll(extraWindows);
        return Collections.unmodifiableList(windows);
    }

    /**
     * Run something on the main thread at the start of the next tick, such as creating or closing
     * windows. Safe from any thread. Dropped if graphics shut down first.
     *
     * @param task The work to do.
     */
    public static void runOnMainThread(@NonNull Runnable task) {
        mainThreadTasks.add(task);
    }

    /** Run the work other threads queued up for the main thread. */
    private static void runMainThreadTasks() {
        Runnable task = mainThreadTasks.poll();
        while (task != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("A main thread task failed", e);
            }
            task = mainThreadTasks.poll();
        }
    }

    /**
     * Make sure graphics are running and we are on the main thread, where GLFW and the renderer
     * must be used.
     *
     * @throws IllegalStateException If not.
     */
    private static void checkMainThread() {
        if (mainWindow == null || Thread.currentThread() != mainThread) {
            throw new IllegalStateException(
                    "Windows can only be changed on the main thread while graphics are running");
        }
    }

    /** Close any windows the user closed, other than the main window. */
    private static void closeClosedWindows() {
        // Backward, since closing one takes it out of the list (and may take others with it)
        for (int i = extraWindows.size() - 1; i >= 0; --i) {
            if (i >= extraWindows.size()) {
                continue;
            }
            final Window window = extraWindows.get(i);
            if (window.windowShouldClose()) {
                closeWindow(window);
            }
        }
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
    public static RenderConfig getPipelineConfig() {
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
    public static void swapPipeline(@NonNull RenderConfig config) {
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
     * Clean up the renderer and the GUI, destroy the windows, then terminate GLFW. Does nothing if
     * we already terminated. Must be called from the main thread.
     */
    public static void terminate() {
        if (!initialized.get()) {
            return;
        }
        terminating = true;
        // The renderer releases every texture as it cleans up
        contexts.values().forEach(GraphicsContext::close);
        contexts.clear();
        if (scene != null) {
            // Queues everything up for deletion, which the renderer processes as it cleans up
            scene.cleanup();
        }
        if (guiBackend != null && IkGui.getContext() != null) {
            // Destroys the windows of viewports, while the renderer can still destroy their
            // swapchains
            guiBackend.shutdown();
        }
        guiBackend = null;
        // Destroys the window surfaces too, which must go before the windows
        renderInstance.cleanup();
        if (IkGui.getContext() != null) {
            IkGui.destroyContext();
        }

        extraWindows.forEach(Window::destroy);
        extraWindows.clear();
        guiWindows.forEach(Window::destroy);
        guiWindows.clear();
        mainThreadTasks.clear();
        if (null != mainWindow) {
            mainWindow.destroy();
            mainWindow = null;
            sceneWindow = null;
        }

        GlfwPlatform.terminate();
        terminating = false;
        initialized.set(false);
    }

    /**
     * Does a graphical update.
     *
     * @return A status indicating to the launcher the status of this main loop stage.
     */
    static int tick() {
        if (null == mainWindow) {
            // Already terminated, so there is nothing left to tick
            return Launcher.STATUS_REQUEST_REMOVAL;
        }
        if (shutdownFlag.get()) {
            terminate();
            return Launcher.STATUS_REQUEST_REMOVAL;
        }

        if (mainWindow.windowShouldClose()) {
            /*
             * Clean up the renderer now, while the window it renders to still exists. Once this stage is removed
             * nothing would call terminate() later.
             */
            terminate();
            new Shutdown().fire();
            return Launcher.STATUS_REQUEST_REMOVAL;
        }

        // Handles the input for every window
        guiBackend.pollEvents();
        closeClosedWindows();
        runMainThreadTasks();
        uiManager.setContentScale(mainWindow.getContentScale());
        // Button clicks and the like from the last frame, run here rather than mid-render
        uiManager.dispatchEvents();

        ModelLoader.loadModel();

        renderInstance.processResources();

        final double currentTime = getTime();

        if (currentTime >= nextUpdateTime) {
            final float elapsedTime = (float) (currentTime - lastUpdateTime);

            IkIO ikIO = IkGui.getIO();
            if (!ikIO.wantCaptureMouse) {
                // The camera turns in the window that shows the scene
                if (ikIO.getMouseDown(MouseButton.RIGHT)) {
                    sceneWindow.disableCursor();
                } else {
                    sceneWindow.enableCursor();
                }
            }
            cameraManager.updateCamera(elapsedTime);

            windowManager.handleGuiInput(scene);
            windowManager.updateValues(scene);

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
            // Drives the gate benchmark's camera and timings, if one is running
            GateBenchmark.update(scene);
            render();
            // Update the next time we should render a frame
            lastRenderTime = currentTime;
            nextRenderTime = currentTime + FRAME_TIME;
        }

        // Nothing is due until the next update or frame: wait for input rather than spin the main
        // loop, which would otherwise call this hundreds of thousands of times a second. The wait
        // can overshoot by a timer tick, so it ends a little early and the last stretch polls, to
        // keep frames evenly spaced. That stretch spins here rather than through the main loop,
        // since each pass of the loop polls input and allocates; the next pass polls once before
        // the frame.
        final double due = Math.min(nextUpdateTime, nextRenderTime);
        final double idle = due - getTime() - WAKE_EARLY;
        if (idle > 0) {
            GlfwPlatform.waitEvents(Math.min(idle, FRAME_TIME));
        } else {
            while (getTime() < due) {
                Thread.onSpinWait();
            }
        }

        return Launcher.STATUS_OK;
    }

    /** Private constructor so this class is not initialized. */
    private GraphicsManager() {}
}
