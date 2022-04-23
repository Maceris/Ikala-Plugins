package com.ikalagaming.gui.console.events;

import com.ikalagaming.event.Event;

/**
 * A message needs to be sent to the console.
 *
 * @author Ches Burks
 *
 */
public class ConsoleMessage extends Event {

	/**
	 * The command and parameters.
	 */
	private final String theMessage;

	/**
	 * Output the given message to the console. This will output to a new line.
	 *
	 * @param message the message to print
	 */
	public ConsoleMessage(String message) {
		this.theMessage = message;
	}

	/**
	 * Returns the message transmitted.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return this.theMessage;
	}

}
