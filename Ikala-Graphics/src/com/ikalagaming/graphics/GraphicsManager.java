package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

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
	 * The window handle that have been created.
	 *
	 * @return The window handle.
	 */
	@Getter
	private static long window = MemoryUtil.NULL;

	private static double lastTimestamp;

	private static int frameCount;

	private static ShaderProgram shaderProgram;
	private static Mesh mesh;

	/**
	 * Creates a graphics window, fires off a {@link WindowCreated} event. Won't
	 * do anything if a window already exists.
	 *
	 */
	public static void createWindow() {
		if (MemoryUtil.NULL != GraphicsManager.window) {
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
		GraphicsManager.window = GLFW.glfwCreateWindow(640, 480, "Hello World!",
			MemoryUtil.NULL, MemoryUtil.NULL);
		if (GraphicsManager.window == MemoryUtil.NULL) {
			GraphicsManager.log.error("Failed to create the GLFW window");
			throw new RuntimeException("Failed to create the GLFW window");
		}

		// Setup a key callback. It will be called every time a key is pressed,
		// repeated or released.
		GLFW.glfwSetKeyCallback(GraphicsManager.window,
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
			GLFW.glfwGetWindowSize(GraphicsManager.window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode =
				GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());

			// Center the window
			GLFW.glfwSetWindowPos(GraphicsManager.window,
				(vidmode.width() - pWidth.get(0)) / 2,
				(vidmode.height() - pHeight.get(0)) / 2);
		} // the stack frame is popped automatically

		// Make the OpenGL context current
		GLFW.glfwMakeContextCurrent(GraphicsManager.window);
		// Enable v-sync
		GLFW.glfwSwapInterval(1);

		// Make the window visible
		GLFW.glfwShowWindow(GraphicsManager.window);

		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		// LWJGL detects the context that is current in the current thread,
		// creates the GLCapabilities instance and makes the OpenGL
		// bindings available for use.
		GL.createCapabilities();

		// Set the clear color
		GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsManager.plugin.getResourceBundle()));
		new WindowCreated(GraphicsManager.window).fire();
		GraphicsManager.frameCount = 0;
		GraphicsManager.lastTimestamp = GLFW.glfwGetTime();

		// Create a shader program
		GraphicsManager.shaderProgram = new ShaderProgram();
		GraphicsManager.shaderProgram.createVertexShader(
			FileUtils.readAsString(PluginFolder.getResource(GraphicsPlugin.NAME,
				ResourceType.DATA, "vertex.vs")));
		GraphicsManager.shaderProgram.createFragmentShader(
			FileUtils.readAsString(PluginFolder.getResource(GraphicsPlugin.NAME,
				ResourceType.DATA, "fragment.fs")));
		GraphicsManager.shaderProgram.link();

		// Set up vertices
		float[] vertices =
			new float[] {-0.5f, 0.5f, 0.0f, -0.5f, -0.5f, 0.0f, 0.5f, 0.5f,
				0.0f, 0.5f, 0.5f, 0.0f, -0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f,};

		GraphicsManager.mesh = new Mesh(vertices);
	}

	/**
	 * Destroy the window with the given window handle.
	 *
	 */
	public static void destroyWindow() {
		if (MemoryUtil.NULL == GraphicsManager.window) {
			return;
		}
		Callbacks.glfwFreeCallbacks(GraphicsManager.window);
		GLFW.glfwDestroyWindow(GraphicsManager.window);

		GraphicsManager.window = MemoryUtil.NULL;

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
		if (GraphicsManager.shaderProgram != null) {
			GraphicsManager.shaderProgram.cleanup();
		}
		if (GraphicsManager.mesh != null) {
			GraphicsManager.mesh.cleanUp();
		}

		GraphicsManager.destroyWindow();
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
	}

	/**
	 * Does a graphical update. Returns the value of if it still exists. That
	 * is, returns false if the window was destroyed and true if it still
	 * exists.
	 *
	 * @return True if the window updated, false if has been destroyed.
	 */
	static int tick() {
		if (MemoryUtil.NULL == GraphicsManager.window) {
			return -1;
		}

		// Measure speed
		double currentTime = GLFW.glfwGetTime();
		GraphicsManager.frameCount++;
		// If a second has passed.
		if (currentTime - GraphicsManager.lastTimestamp >= 1.0) {
			// Display the frame count here any way you want.
			// displayFPS(frameCount);

			GraphicsManager.frameCount = 0;
			GraphicsManager.lastTimestamp = currentTime;
		}

		// Poll for window events
		GLFW.glfwPollEvents();

		if (GLFW.glfwWindowShouldClose(GraphicsManager.window)) {
			GraphicsManager.destroyWindow();
			return -1;
		}
		// clear the framebuffer
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GraphicsManager.shaderProgram.bind();

		// Bind to the VAO
		GL30.glBindVertexArray(GraphicsManager.mesh.getVaoId());
		GL20.glEnableVertexAttribArray(0);

		// Draw the vertices
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0,
			GraphicsManager.mesh.getVertexCount());

		// Restore state
		GL20.glDisableVertexAttribArray(0);
		GL30.glBindVertexArray(0);

		GraphicsManager.shaderProgram.unbind();

		GLFW.glfwSwapBuffers(GraphicsManager.window); // swap the color buffers

		return Launcher.STATUS_OK;

	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
