package com.ikalagaming.graphics.events;

import com.ikalagaming.event.Event;

/**
 * Fired off when a new GLFWwindow has been destroyed.
 *
 * @author Ches Burks
 *
 */
public class WindowDestroyed extends Event {

	private final long windowID;

	/**
	 * Constructs a new window destroyed event.
	 *
	 * @param id The window handle.
	 */
	public WindowDestroyed(final long id) {
		this.windowID = id;
	}

	/**
	 * Returns the window handle.
	 *
	 * @return the GLFW window ID.
	 */
	public long getID() {
		return this.windowID;
	}

}
