package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides utilities for handling graphics.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsManager {

	private static AtomicBoolean initialized = new AtomicBoolean(false);

	private static List<SceneItem> items;

	/**
	 * A reference to the graphics plugin for resource bundle access.
	 */
	private static GraphicsPlugin plugin;

	private static Renderer renderer;

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

	private static final String WINDOW_TITLE = "Ikala Gaming";

	/**
	 * The window handle that have been created.
	 *
	 * @return The window handle.
	 */
	@Getter
	private static long windowID = MemoryUtil.NULL;

	/**
	 * Creates a graphics window, fires off a {@link WindowCreated} event. Won't
	 * do anything if a window already exists.
	 *
	 */
	public static void createWindow() {
		if (MemoryUtil.NULL != GraphicsManager.windowID) {
			return;
		}
		GraphicsManager.init();

		// Configure GLFW

		// optional, the current window hints are already the default
		GLFW.glfwDefaultWindowHints();
		// the window will stay hidden after creation
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		// the window will be resizable
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,
			GLFW.GLFW_OPENGL_CORE_PROFILE);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

		// Create the window
		GraphicsManager.windowID = GLFW.glfwCreateWindow(640, 480,
			GraphicsManager.WINDOW_TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
		if (GraphicsManager.windowID == MemoryUtil.NULL) {
			GraphicsManager.log.error("Failed to create the GLFW window");
			throw new WindowCreationException(
				"Failed to create the GLFW window");
		}
		GraphicsManager.window = new Window(GraphicsManager.windowID);

		/*
		 * Setup a key callback. It will be called every time a key is pressed,
		 * repeated or released.
		 */
		GLFW.glfwSetKeyCallback(GraphicsManager.windowID,
			(window1, key, scancode, action, mods) -> {
				if (key == GLFW.GLFW_KEY_ESCAPE
					&& action == GLFW.GLFW_RELEASE) {
					// We will detect this in the rendering loop
					GLFW.glfwSetWindowShouldClose(window1, true);
				}
			});

		GraphicsManager.window.centerWindow();

		// Make the OpenGL context current
		GLFW.glfwMakeContextCurrent(GraphicsManager.windowID);
		// Enable v-sync
		GLFW.glfwSwapInterval(1);

		// Make the window visible
		GLFW.glfwShowWindow(GraphicsManager.windowID);

		/*
		 * This line is critical for LWJGL's interoperation with GLFW's OpenGL
		 * context, or any context that is managed externally. LWJGL detects the
		 * context that is current in the current thread, creates the
		 * GLCapabilities instance and makes the OpenGL bindings available for
		 * use.
		 */
		GL.createCapabilities();

		// Set the clear color
		GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsManager.plugin.getResourceBundle()));
		new WindowCreated(GraphicsManager.windowID).fire();

		GraphicsManager.renderer = new Renderer();
		GraphicsManager.renderer.init();

		float[] positions = new float[] {-0.5f, 0.5f, -1.05f, -0.5f, -0.5f,
			-1.05f, 0.5f, -0.5f, -1.05f, 0.5f, 0.5f, -1.05f,};
		int[] indices = new int[] {0, 1, 3, 3, 1, 2};
		float[] colors = new float[] {0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f,
			0.0f, 0.5f, 0.0f, 0.5f, 0.5f};

		Mesh mesh = new Mesh(positions, colors, indices);
		SceneItem item = new SceneItem(mesh);

		GraphicsManager.items = new ArrayList<>();
		GraphicsManager.items.add(item);
	}

	/**
	 * Destroy the window with the given window handle.
	 *
	 */
	public static void destroyWindow() {
		if (MemoryUtil.NULL == GraphicsManager.windowID) {
			return;
		}
		Callbacks.glfwFreeCallbacks(GraphicsManager.windowID);
		GLFW.glfwDestroyWindow(GraphicsManager.windowID);

		GraphicsManager.windowID = MemoryUtil.NULL;

		GraphicsManager.log.debug(SafeResourceLoader.getString(
			"WINDOW_DESTROYED", GraphicsManager.plugin.getResourceBundle()));
	}

	private static void init() {
		if (!GraphicsManager.initialized.compareAndSet(false, true)) {
			return;
		}
		/*
		 * Setup an error callback. The default implementation will print the
		 * error message in System.err.
		 */
		GLFWErrorCallback.createPrint(System.err).set();
		if (!GLFW.glfwInit()) {
			GraphicsManager.log.error(SafeResourceLoader.getString(
				"GLFW_INIT_FAIL", GraphicsManager.plugin.getResourceBundle()));
			throw new IllegalStateException("Unable to initialize GLFW");
		}
		GraphicsManager.shutdownFlag.set(false);
	}

	/**
	 * Set the plugin to the given reference.
	 *
	 * @param plugin The plugin to use.
	 */
	static synchronized void setPlugin(GraphicsPlugin plugin) {
		GraphicsManager.plugin = plugin;
	}

	/**
	 * Terminate GLFW and free the error callback. If any windows still remain,
	 * they are destroyed.
	 */
	public static void terminate() {
		GraphicsManager.renderer.cleanup();
		GraphicsManager.items.forEach(SceneItem::cleanup);
		GraphicsManager.destroyWindow();
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
		if (MemoryUtil.NULL == GraphicsManager.windowID) {
			return Launcher.STATUS_ERROR;
		}
		if (GraphicsManager.shutdownFlag.get()) {
			GraphicsManager.terminate();
			return Launcher.STATUS_OK;
		}

		// Poll for window events
		GLFW.glfwPollEvents();

		if (GLFW.glfwWindowShouldClose(GraphicsManager.windowID)) {
			GraphicsManager.destroyWindow();
			return Launcher.STATUS_REQUEST_REMOVAL;
		}

		// clear the framebuffer
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GraphicsManager.renderer.render(GraphicsManager.items);

		GLFW.glfwSwapBuffers(GraphicsManager.windowID); // swap the color
														// buffers

		return Launcher.STATUS_OK;

	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
