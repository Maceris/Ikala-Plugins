package com.ikalagaming.window;

import com.ikalagaming.event.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Input device was released above a window.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public class EvtUp extends Event {

	/**
	 * The window that was clicked/touched.
	 *
	 * @param window The window that was clicked/touched.
	 * @return the window that was clicked/touched.
	 */
	private final Window window;

}
