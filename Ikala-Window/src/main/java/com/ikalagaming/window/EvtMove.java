package com.ikalagaming.window;

import com.ikalagaming.event.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Input device was moved while down and above a window.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public class EvtMove extends Event {

	/**
	 * The window that was clicked/touched.
	 *
	 * @param window The window that was clicked/touched.
	 * @return the window that was clicked/touched.
	 */
	private final Window window;

}
