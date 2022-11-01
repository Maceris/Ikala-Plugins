package com.ikalagaming.graphics.events;

import com.ikalagaming.event.Event;

/**
 * Fired off when a new GLFWwindow has been created.
 *
 * @author Ches Burks
 *
 */
public class WindowCreated extends Event {

	private final long windowID;

	/**
	 * Constructs a new window created event.
	 *
	 * @param id The window handle.
	 */
	public WindowCreated(final long id) {
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
