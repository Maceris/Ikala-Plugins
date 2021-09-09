package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides utilities for handling graphics.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsManager {

	private static boolean initialized = false;

	/**
	 * A reference to the graphics plugin for resource bundle access.
	 */
	private static GraphicsPlugin plugin;

	/**
	 * Set the plugin to the given reference.
	 * 
	 * @param plugin The plugin to use.
	 */
	static synchronized void setPlugin(GraphicsPlugin plugin) {
		GraphicsManager.plugin = plugin;
	}

	/**
	 * The list of window handles that have been created.
	 *
	 * @return All known window handles.
	 */
	@Getter
	private static List<Long> windows = new ArrayList<>();

	/**
	 * Creates a graphics window, fires off a {@link WindowCreated} event.
	 *
	 */
	public static void createWindow() {
		GraphicsManager.init();

		long window;
		// Configure GLFW

		// optional, the current window hints are already the default
		GLFW.glfwDefaultWindowHints();
		// the window will stay hidden after creation
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		// the window will be resizable
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

		// Create the window
		window = GLFW.glfwCreateWindow(300, 300, "Hello World!",
			MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {
			GraphicsManager.log.error("Failed to create the GLFW window");
			throw new RuntimeException("Failed to create the GLFW window");
		}

		GraphicsManager.windows.add(window);

		// Setup a key callback. It will be called every time a key is pressed,
		// repeated or released.
		GLFW.glfwSetKeyCallback(window,
			(window1, key, scancode, action, mods) -> {
				if (key == GLFW.GLFW_KEY_ESCAPE
					&& action == GLFW.GLFW_RELEASE) {
					// We will detect this in the rendering loop
					GLFW.glfwSetWindowShouldClose(window1, true);
				}
			});

		// Get the thread stack and push a new frame
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*

			// Get the window size passed to glfwCreateWindow
			GLFW.glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode =
				GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());

			// Center the window
			GLFW.glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2,
				(vidmode.height() - pHeight.get(0)) / 2);
		} // the stack frame is popped automatically

		// Make the OpenGL context current
		GLFW.glfwMakeContextCurrent(window);
		// Enable v-sync
		GLFW.glfwSwapInterval(1);

		// Make the window visible
		GLFW.glfwShowWindow(window);

		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		// LWJGL detects the context that is current in the current thread,
		// creates the GLCapabilities instance and makes the OpenGL
		// bindings available for use.
		GL.createCapabilities();

		// Set the clear color
		GL11.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsManager.plugin.getResourceBundle()));
		new WindowCreated(window).fire();
	}

	/**
	 * Destroy all the window handles that we know about.
	 *
	 * @see #getWindows()
	 * @see #destroyWindow(long)
	 */
	public static void destroyAllWindows() {
		// copy of the list so we don't have concurrent modification
		List<Long> temp = new ArrayList<>(GraphicsManager.windows);
		for (long handle : temp) {
			GraphicsManager.destroyWindow(handle);
		}
	}

	/**
	 * Destroy the window with the given window handle.
	 *
	 * @param window The window handle we want to destroy.
	 */
	public static void destroyWindow(long window) {
		Callbacks.glfwFreeCallbacks(window);
		GLFW.glfwDestroyWindow(window);
		GraphicsManager.windows.remove(window);

		GraphicsManager.log.debug(SafeResourceLoader.getString(
			"WINDOW_DESTROYED", GraphicsManager.plugin.getResourceBundle()));
	}

	private static void init() {
		if (GraphicsManager.initialized) {
			return;
		}
		GraphicsManager.initialized = true;
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
	}

	/**
	 * Terminate GLFW and free the error callback. If any windows still remain,
	 * they are destroyed.
	 */
	public static void terminate() {
		if (!GraphicsManager.windows.isEmpty()) {
			GraphicsManager.destroyAllWindows();
		}
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
	}

	/**
	 * Does a graphical update. Returns the value of if it still exists. That
	 * is, returns false if the window was destroyed and true if it still
	 * exists.
	 *
	 * @param window the window to update.
	 * @return True if the window updated, false if has been destroyed.
	 */
	static boolean tick(long window) {
		// Run the rendering loop until the user has attempted to close
		// the window or has pressed the ESCAPE key.
		if (!GLFW.glfwWindowShouldClose(window)) {
			// clear the framebuffer
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

			GLFW.glfwSwapBuffers(window); // swap the color buffers

			// Poll for window events. The key callback above will only be
			// invoked during this call.
			GLFW.glfwPollEvents();
			return true;
		}
		GraphicsManager.destroyWindow(window);
		return false;
	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
