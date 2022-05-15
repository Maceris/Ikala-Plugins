package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Provides convenience methods for an OpenGL window.
 *
 * @author Ches Burks
 *
 */
@Slf4j
@Getter
public class Window {

	/**
	 * The OpenGL window handle.
	 *
	 * @return The window handle.
	 */
	private long windowHandle;
	/**
	 * The title of the window.
	 *
	 * @return The window title.
	 */
	private final String title;
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
	/**
	 * Whether or not the window has been resized.
	 *
	 * @return True if the window has been resized, false otherwise.
	 * @param resized resized If the window has been resized.
	 */
	@Getter
	@Setter
	private boolean resized;
	/**
	 * If vSync is enabled.
	 *
	 * @return True if vSync is enabled.
	 * @param vSync If vSync should be enabled.
	 */
	@Setter
	private boolean vSync;

	/**
	 * Create a new window.
	 *
	 * @param title The title of the window to create.
	 * @param width The width of the window in pixels.
	 * @param height The height of the window in pixels.
	 * @param vSync If we want vSync enabled or not.
	 */
	public Window(String title, int width, int height, boolean vSync) {
		this.title = title;
		this.width = width;
		this.height = height;
		this.vSync = vSync;
		this.resized = false;
	}

	/**
	 * Destroy the window.
	 */
	public void destroy() {
		if (MemoryUtil.NULL == this.windowHandle) {
			return;
		}

		Callbacks.glfwFreeCallbacks(this.windowHandle);
		GLFW.glfwDestroyWindow(this.windowHandle);
		this.windowHandle = MemoryUtil.NULL;
		Window.log.debug(SafeResourceLoader.getString("WINDOW_DESTROYED",
			GraphicsPlugin.getResourceBundle()));
	}

	/**
	 * Initialize the window.
	 */
	public void init() {
		/*
		 * Setup an error callback. The default implementation will print the
		 * error message in System.err.
		 */
		GLFWErrorCallback.createPrint(System.err).set();// NOSONAR
		if (!GLFW.glfwInit()) {
			Window.log.error(SafeResourceLoader.getString("GLFW_INIT_FAIL",
				GraphicsPlugin.getResourceBundle()));
			throw new IllegalStateException("Unable to initialize GLFW");
		}

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
		this.windowHandle = GLFW.glfwCreateWindow(this.width, this.height,
			this.title, MemoryUtil.NULL, MemoryUtil.NULL);
		if (MemoryUtil.NULL == this.windowHandle) {
			Window.log.error(SafeResourceLoader.getString(
				"WINDOW_ERROR_CREATION", GraphicsPlugin.getResourceBundle()));
			throw new WindowCreationException();
		}

		// Setup resize callback
		GLFW.glfwSetFramebufferSizeCallback(this.windowHandle,
			(window, w, h) -> {
				this.width = w;
				this.height = h;
				this.setResized(true);
			});

		/*
		 * Setup a key callback. It will be called every time a key is pressed,
		 * repeated or released.
		 */
		GLFW.glfwSetKeyCallback(this.windowHandle,
			(window1, key, scancode, action, mods) -> {
				if (key == GLFW.GLFW_KEY_ESCAPE
					&& action == GLFW.GLFW_RELEASE) {
					// We will detect this in the rendering loop
					GLFW.glfwSetWindowShouldClose(window1, true);
				}
			});

		// Get the resolution of the primary monitor
		GLFWVidMode vidmode =
			GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
		// Center our window
		GLFW.glfwSetWindowPos(this.windowHandle,
			(vidmode.width() - this.width) / 2,
			(vidmode.height() - this.height) / 2);

		// Make the OpenGL context current
		GLFW.glfwMakeContextCurrent(this.windowHandle);
		// Enable v-sync
		if (this.isVSync()) {
			GLFW.glfwSwapInterval(1);
		}

		// Make the window visible
		GLFW.glfwShowWindow(this.windowHandle);

		/*
		 * This line is critical for LWJGL's interoperation with GLFW's OpenGL
		 * context, or any context that is managed externally. LWJGL detects the
		 * context that is current in the current thread, creates the
		 * GLCapabilities instance and makes the OpenGL bindings available for
		 * use.
		 */
		GL.createCapabilities();

		// Set the clear color
		GL11.glClearColor(0.5f, 0.73f, 0.82f, 1.0f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);

		// Support for transparencies
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	/**
	 * Checks if a key is pressed.
	 *
	 * @param keyCode The keycode to look for.
	 * @return True if the key is pressed, false if not.
	 */
	public boolean isKeyPressed(int keyCode) {
		return GLFW.glfwGetKey(this.windowHandle, keyCode) == GLFW.GLFW_PRESS;
	}

	/**
	 * Sets the clear value for fixed-point and floating-point color buffers in
	 * RGBA mode.
	 *
	 * @param r The red component.
	 * @param g The green component.
	 * @param b The blue component.
	 * @param alpha The alpha component.
	 */
	public void setClearColor(float r, float g, float b, float alpha) {
		GL11.glClearColor(r, g, b, alpha);
	}

	/**
	 * Swap buffers (render) and poll for events.
	 */
	public void update() {
		GLFW.glfwSwapBuffers(this.windowHandle);
		GLFW.glfwPollEvents();
	}

	/**
	 * Checks if the window should close.
	 *
	 * @return True if the window should close, false if not.
	 */
	public boolean windowShouldClose() {
		return GLFW.glfwWindowShouldClose(this.windowHandle);
	}
}
