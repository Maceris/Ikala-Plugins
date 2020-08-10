package com.ikalagaming.graphics;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.EventManager;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.events.CreateWindow;
import com.ikalagaming.graphics.events.GraphicsTick;
import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.events.WindowDestroyed;
import com.ikalagaming.logging.Logging;
import com.ikalagaming.util.SafeResourceLoader;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/**
 * Handles the API requests to control graphics.
 *
 * @author Ches Burks
 *
 */
public class ControlListener implements Listener {

	private GraphicsState state;
	private GraphicsPlugin par;
	
	/**
	 * Creates a listener given a parent.
	 * 
	 * @param parent the parent this belongs to.
	 */
	public ControlListener(GraphicsPlugin parent) {
		par = parent;
	}

	private void init() {
		if (this.state.isInitialized()) {
			return;
		}
		/*
		 * Setup an error callback. The default implementation will print the
		 * error message in System.err.
		 */
		GLFWErrorCallback.createPrint(System.err).set();
		if (!GLFW.glfwInit()) {
			Logging.severe(GraphicsPlugin.NAME, SafeResourceLoader
				.getString("GLFW_INIT_FAIL", par.getResourceBundle()));
			throw new IllegalStateException("Unable to initialize GLFW");
		}
	}

	/**
	 * Creates a window when requested, fires off a {@link WindowCreated} event.
	 *
	 * @param event The event to respond to.
	 */
	@EventHandler
	public void onCreateWindow(CreateWindow event) {
		this.init();

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
			Logging.severe(GraphicsPlugin.NAME,
				"Failed to create the GLFW window");
			throw new RuntimeException("Failed to create the GLFW window");
		}

		EventManager.getInstance().fireEvent(new WindowCreated(window));
		this.state.addWindow(window);

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

		Logging.finer(GraphicsPlugin.NAME, SafeResourceLoader
			.getString("WINDOW_CREATED", par.getResourceBundle()));
	}

	/**
	 * Does a graphical update. Returns the value of if it still exists. That
	 * is, returns false if the window was destroyed and true if it still
	 * exists.
	 * 
	 * @param window the window to update.
	 * @return True if the window updated, false if has been destroyed.
	 */
	private boolean tick(long window) {
		// Run the rendering loop until the user has attempted to close
		// the window or has pressed the ESCAPE key.
		if (!GLFW.glfwWindowShouldClose(window)) {
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); // clear
																				// the
			// framebuffer

			GLFW.glfwSwapBuffers(window); // swap the color buffers

			// Poll for window events. The key callback above will only be
			// invoked during this call.
			GLFW.glfwPollEvents();
			return true;
		}
		destroyWindow(window);
		return false;
	}

	private void destroyWindow(long window) {
		Callbacks.glfwFreeCallbacks(window);
		GLFW.glfwDestroyWindow(window);

		Logging.finer(GraphicsPlugin.NAME, SafeResourceLoader
			.getString("WINDOW_DESTROYED", par.getResourceBundle()));
	}

	/**
	 * Updated the windows, destroying if necessary.
	 * 
	 * @param event The tick.
	 */
	@EventHandler
	public void onGraphicsTick(GraphicsTick event) {
		for (long window : this.state.getWindowHandles()) {
			if (!tick(window)) {
				EventManager.getInstance()
					.fireEvent(new WindowDestroyed(window));
			}
		}
	}

	/**
	 * Sets the graphics state to use.
	 *
	 * @param s The state object.
	 */
	void setState(GraphicsState s) {
		this.state = s;
	}

	/**
	 * Clears out all windows.
	 */
	void shutdown() {
		this.state.clearWindows();
		// Terminate GLFW and free the error callback
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
		this.state = null;
		this.par = null;
	}

}
