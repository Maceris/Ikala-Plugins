package com.ikalagaming.gui.console.events;

import com.ikalagaming.event.Event;

/**
 * A message that a command was unknown.
 *
 * @author Ches Burks
 *
 */
public class ReportUnknownCommand extends Event {

	/**
	 * The command and parameters.
	 */
	private final String message;

	/**
	 * Prints a help message to the console stating the given command was not
	 * recognized.
	 *
	 * @param cmd the command that was not known
	 */
	public ReportUnknownCommand(String cmd) {
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
