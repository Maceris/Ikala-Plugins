package com.ikalagaming.graphics;

import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.Callable;

/**
 * Provides convenience methods for an OpenGL window.
 */
@Slf4j
@Getter
public class Window {

	/**
	 * Options for the window.
	 */
	public static class WindowOptions {
		/**
		 * Whether anti-aliasing is enabled.
		 */
		public boolean antiAliasing;
		/**
		 * Whether we want to use a compatible profile instead of the core one.
		 */
		public boolean compatibleProfile;
		/**
		 * The target frames per second, which relates to vsync.
		 */
		public int fps;
		/**
		 * The height of the window in pixels.
		 */
		public int height;
		/**
		 * The target updates per second.
		 */
		public int ups = GraphicsManager.TARGET_FPS;
		/**
		 * The width of the window in pixels.
		 */
		public int width;
	}

	/**
	 * The OpenGL window handle.
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
	/**
	 * Mouse input handler.
	 */
	private MouseInput mouseInput;
	/**
	 * The resize function to call.
	 */
	private Callable<Void> resizeFunc;

	/**
	 * Create a new window.
	 *
	 * @param title The title of the window to display.
	 * @param opts Options for the window setup.
	 * @param resizeFunc The funciton to call upon the window resizing.
	 */
	public Window(String title, WindowOptions opts, Callable<Void> resizeFunc) {
		this.resizeFunc = resizeFunc;

		if (!GLFW.glfwInit()) {
			Window.log.error(SafeResourceLoader.getString("GLFW_INIT_FAIL",
				GraphicsPlugin.getResourceBundle()));
			throw new IllegalStateException("Unable to initialize GLFW");
		}

		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE);

		if (opts.antiAliasing) {
			GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, 4);
		}
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
		if (opts.compatibleProfile) {
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,
				GLFW.GLFW_OPENGL_COMPAT_PROFILE);
		}
		else {
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,
				GLFW.GLFW_OPENGL_CORE_PROFILE);
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11.GL_TRUE);
		}

		if (opts.width > 0 && opts.height > 0) {
			this.width = opts.width;
			this.height = opts.height;
		}
		else {
			GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_TRUE);
			GLFWVidMode vidMode =
				GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
			this.width = vidMode.width();
			this.height = vidMode.height();
		}

		this.windowHandle = GLFW.glfwCreateWindow(this.width, this.height,
			title, MemoryUtil.NULL, MemoryUtil.NULL);
		if (this.windowHandle == MemoryUtil.NULL) {
			throw new RuntimeException("Failed to create the GLFW window");
		}

		GLFW.glfwSetFramebufferSizeCallback(this.windowHandle,
			(window, w, h) -> this.resized(w, h));

		GLFW.glfwSetErrorCallback((int errorCode, long msgPtr) -> Window.log
			.error("Error code [{}], msg [{]]", errorCode,
				MemoryUtil.memUTF8(msgPtr)));

		GLFW.glfwSetKeyCallback(this.windowHandle,
			(window, key, scancode, action, mods) -> {
				if (key == GLFW.GLFW_KEY_ESCAPE
					&& action == GLFW.GLFW_RELEASE) {
					// We will detect this in the rendering loop
					GLFW.glfwSetWindowShouldClose(window, true);
				}
			});

		GLFW.glfwMakeContextCurrent(this.windowHandle);

		if (opts.fps > 0) {
			GLFW.glfwSwapInterval(0);
		}
		else {
			GLFW.glfwSwapInterval(1);
		}

		GLFW.glfwShowWindow(this.windowHandle);

		int[] arrWidth = new int[1];
		int[] arrHeight = new int[1];
		GLFW.glfwGetFramebufferSize(this.windowHandle, arrWidth, arrHeight);
		this.width = arrWidth[0];
		this.height = arrHeight[0];

		this.mouseInput = new MouseInput(this.windowHandle);
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
		GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
		if (callback != null) {
			callback.free();
		}
		this.windowHandle = MemoryUtil.NULL;
		Window.log.debug(SafeResourceLoader.getString("WINDOW_DESTROYED",
			GraphicsPlugin.getResourceBundle()));
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
	 * Poll for events and process input.
	 */
	public void pollEvents() {
		GLFW.glfwPollEvents();
		this.mouseInput.input();
	}

	/**
	 * A callback for resizing the window.
	 *
	 * @param width The new width of the window.
	 * @param height The new height of the window.
	 */
	@SuppressWarnings("hiding")
	protected void resized(int width, int height) {
		this.width = width;
		this.height = height;
		try {
			this.resizeFunc.call();
		}
		catch (Exception excp) {
			Window.log.error("Error calling resize callback", excp);
		}
	}

	/**
	 * Render the window.
	 */
	public void update() {
		GLFW.glfwSwapBuffers(this.windowHandle);
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
