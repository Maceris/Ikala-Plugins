package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides utilities for handling graphics.
 */
@Slf4j
public class GraphicsManager {

	/**
	 * Whether we are currently initialized.
	 */
	static AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * Whether we want to refresh the scene information.
	 */
	private static AtomicBoolean refreshRequested = new AtomicBoolean(false);

	/**
	 * The scene we are rendering.
	 *
	 * @return The current scene.
	 */
	@Getter
	private static Scene scene;
	/**
	 * The rendering handler.
	 *
	 * @return The render stages.
	 */
	@Getter
	private static Render render;
	/**
	 * The camera manager.
	 *
	 * @return The camera manager.
	 */
	@Getter
	private static CameraManager cameraManager;
	/**
	 * Used to track when we should render another frame.
	 */
	private static Timer renderTimer;
	/**
	 * Used to track when we should make updates.
	 */
	private static Timer updateTimer;
	/**
	 * The target frames per second that we want to hit.
	 */
	public static final int TARGET_FPS = 144;
	/**
	 * The target updates per second that we want to hit.
	 */
	public static final int TARGET_UPS = 60;
	/**
	 * The (fractional) number of seconds between frame renders at the target
	 * FPS.
	 */
	private static final float FRAME_TIME = 1f / GraphicsManager.TARGET_FPS;
	/**
	 * The (fractional) number of seconds between updates at the target UPS.
	 */
	private static final float UPDATE_TIME = 1f / GraphicsManager.TARGET_UPS;
	/**
	 * The time when the next render call should happen to maintain the target
	 * FPS.
	 */
	private static double nextRenderTime;
	/**
	 * The time when the next render call should happen to maintain the target
	 * UPS.
	 */
	private static double nextUpdateTime;
	/**
	 * The last time we rendered a frame, for calculating FPS.
	 */
	private static double lastTime;
	/**
	 * How many frames we rendered since we calculated FPS.
	 */
	private static int framesSinceLastCalculation;
	/**
	 * The last recorded Frames Per Second.
	 *
	 * @return The last known FPS.
	 */
	@Getter
	private static int lastFPS;

	/**
	 * Whether we should shut down.
	 */
	@Getter(value = AccessLevel.PACKAGE)
	private static AtomicBoolean shutdownFlag = new AtomicBoolean(false);
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
	@Getter
	private static Window window;

	/**
	 * Creates a graphics window, fires off a {@link WindowCreated} event. Won't
	 * do anything if a window already exists.
	 *
	 */
	public static void createWindow() {
		if ((null != GraphicsManager.window)
			|| !GraphicsManager.initialized.compareAndSet(false, true)) {
			return;
		}
		GraphicsManager.shutdownFlag.set(false);

		Window.WindowOptions opts = new Window.WindowOptions();
		opts.antiAliasing = true;
		opts.fps = 144;
		GraphicsManager.window = new Window("Ikala Gaming", opts, () -> {
			GraphicsManager.resize();
			return null;
		});

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsPlugin.getResourceBundle()));
		new WindowCreated(GraphicsManager.window.getWindowHandle()).fire();

		GraphicsManager.render = new Render(GraphicsManager.window);

		GraphicsManager.log.debug(SafeResourceLoader
			.getString("RENDERER_CREATED", GraphicsPlugin.getResourceBundle()));

		GraphicsManager.scene = new Scene(GraphicsManager.window.getWidth(),
			GraphicsManager.window.getHeight());

		GraphicsManager.cameraManager = new CameraManager(
			GraphicsManager.scene.getCamera(), GraphicsManager.window);

		GraphicsManager.init();

		GraphicsManager.renderTimer = new Timer();
		GraphicsManager.renderTimer.init();
		GraphicsManager.nextRenderTime =
			GraphicsManager.renderTimer.getLastLoopTime()
				+ GraphicsManager.FRAME_TIME;

		GraphicsManager.updateTimer = new Timer();
		GraphicsManager.updateTimer.init();
		GraphicsManager.nextUpdateTime =
			GraphicsManager.renderTimer.getLastLoopTime()
				+ GraphicsManager.UPDATE_TIME;

		GraphicsManager.lastTime = GLFW.glfwGetTime();
	}

	/**
	 * Initialize the application.
	 */
	private static void init() {
		GraphicsManager.render.setupData(GraphicsManager.scene);
	}

	/**
	 * Whether we are currently initialized.
	 *
	 * @return If we have already set up the window.
	 */
	public static boolean isInitialized() {
		return GraphicsManager.initialized.get();
	}

	/**
	 * Set up the render data after adding/removing renderables.
	 */
	public static void refreshRenderData() {
		GraphicsManager.refreshRequested.set(true);
	}

	/**
	 * Render to the screen.
	 *
	 * @param elapsedTime The time since the last render.
	 */
	private static void render(final float elapsedTime) {
		GraphicsManager.render.render(GraphicsManager.window,
			GraphicsManager.scene);

		GraphicsManager.window.update();
		++GraphicsManager.framesSinceLastCalculation;

		final double currentTime = GLFW.glfwGetTime();
		if (currentTime - GraphicsManager.lastTime >= 1d) {
			GraphicsManager.lastFPS =
				GraphicsManager.framesSinceLastCalculation;
			GraphicsManager.framesSinceLastCalculation = 0;
			GraphicsManager.lastTime = currentTime;
		}
	}

	private static void resize() {
		int width = GraphicsManager.window.getWidth();
		int height = GraphicsManager.window.getHeight();
		GraphicsManager.scene.resize(width, height);
		GraphicsManager.render.resize(width, height);
	}

	/**
	 * Set the GUI instance to use.
	 *
	 * @param gui The user interface.
	 */
	public static void setGUI(GuiInstance gui) {
		GraphicsManager.scene.setGuiInstance(gui);
	}

	/**
	 * Terminate GLFW and free the error callback. If any windows still remain,
	 * they are destroyed.
	 */
	public static void terminate() {
		GraphicsManager.render.cleanup();

		if (null != GraphicsManager.window) {
			GraphicsManager.window.destroy();
			GraphicsManager.window = null;
		}

		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
		GraphicsManager.initialized.set(false);
	}

	/**
	 * Does a graphical update. Returns the value of if it still exists. That
	 * is, returns false if the window was destroyed and true if it still
	 * exists.
	 *
	 * @return True if the window updated, false if has been destroyed.
	 */
	static int tick() {
		if (null == GraphicsManager.window) {
			return Launcher.STATUS_ERROR;
		}
		if (GraphicsManager.shutdownFlag.get()) {
			GraphicsManager.terminate();
			return Launcher.STATUS_OK;
		}

		if (GraphicsManager.window.windowShouldClose()) {
			GraphicsManager.window.destroy();
			GraphicsManager.window = null;
			return Launcher.STATUS_REQUEST_REMOVAL;
		}

		GraphicsManager.window.pollEvents();

		ModelLoader.loadModel();

		if (GraphicsManager.updateTimer
			.getTime() >= GraphicsManager.nextUpdateTime) {
			final float elapsedTime =
				GraphicsManager.updateTimer.getElapsedTime();

			GraphicsManager.window.getMouseInput().input();
			GraphicsManager.cameraManager.updateCamera(elapsedTime);

			GuiInstance guiInstance = GraphicsManager.scene.getGuiInstance();
			if (guiInstance != null) {
				guiInstance.handleGuiInput(GraphicsManager.scene,
					GraphicsManager.window);
			}

			if (GraphicsManager.refreshRequested.compareAndSet(true, false)) {
				GraphicsManager.render.setupData(GraphicsManager.scene);
			}

			// Update the next time we should update models
			GraphicsManager.nextUpdateTime =
				GraphicsManager.updateTimer.getLastLoopTime()
					+ GraphicsManager.UPDATE_TIME;
		}

		if (GraphicsManager.renderTimer
			.getTime() >= GraphicsManager.nextRenderTime) {
			final float elapsedTime =
				GraphicsManager.renderTimer.getElapsedTime();

			GraphicsManager.render(elapsedTime);
			// Update the next time we should render a frame
			GraphicsManager.nextRenderTime =
				GraphicsManager.renderTimer.getLastLoopTime()
					+ GraphicsManager.FRAME_TIME;
		}

		return Launcher.STATUS_OK;
	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
