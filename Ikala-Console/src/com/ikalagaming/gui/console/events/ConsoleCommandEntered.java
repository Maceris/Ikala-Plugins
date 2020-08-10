package com.ikalagaming.gui.console.events;

import com.ikalagaming.event.Event;

/**
 * A message command was entered on the console. Informs the command registry
 * that the command was typed.
 *
 * @author Ches Burks
 *
 */
public class ConsoleCommandEntered extends Event {

	/**
	 * The command and parameters.
	 */
	private final String message;

	/**
	 * Informs the command registry that a command was typed into the console.
	 *
	 * @param cmd the command that was not known
	 */
	public ConsoleCommandEntered(String cmd) {
		this.message = cmd;
	}

	/**
	 * Returns the command transmitted.
	 *
	 * @return the command
	 */
	public String getCommand() {
		return this.message;
	}

}
