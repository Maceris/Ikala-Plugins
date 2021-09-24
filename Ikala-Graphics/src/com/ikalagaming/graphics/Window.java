package com.ikalagaming.graphics;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Provides convenience methods for an OpenGL window.
 *
 * @author Ches Burks
 *
 */
public class Window {

	private final long id;

	/**
	 * Create a new window for the given window handle.
	 *
	 * @param id The window handle this object tracks.
	 */
	public Window(long id) {
		this.id = id;
	}

	/**
	 * Centers the window on the screen.
	 */
	public void centerWindow() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);

			GLFW.glfwGetWindowSize(this.id, width, height);

			GLFWVidMode vidmode =
				GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());

			GLFW.glfwSetWindowPos(this.id, (vidmode.width() - width.get(0)) / 2,
				(vidmode.height() - height.get(0)) / 2);
		}
	}

	/**
	 * Fetch the height of the window.
	 *
	 * @return The height of the window.
	 */
	public int getHeight() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);

			GLFW.glfwGetWindowSize(this.id, width, height);

			return height.get(0);
		}
	}

	/**
	 * Fetch the width of the window.
	 *
	 * @return The width of the window.
	 */
	public int getWidth() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);

			GLFW.glfwGetWindowSize(this.id, width, height);

			return width.get(0);
		}
	}

}
