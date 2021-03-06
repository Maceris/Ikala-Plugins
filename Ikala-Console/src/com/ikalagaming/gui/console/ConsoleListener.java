package com.ikalagaming.gui.console;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.gui.console.events.ConsoleMessage;
import com.ikalagaming.gui.console.events.ReportUnknownCommand;
import com.ikalagaming.logging.events.Log;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.events.PluginCommandSent;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AllArgsConstructor;

/**
 * The listener for the console gui. This handles events for the console.
 *
 * @author Ches Burks
 *
 */
@AllArgsConstructor
class ConsoleListener implements Listener {

	/**
	 * The parent plugin we are working with.
	 * 
	 * @param parent The parent plugin.
	 */
	@SuppressWarnings("javadoc")
	private ConsolePlugin parent;

	/**
	 * The console we are interacting with.
	 * 
	 * @param console The console to interact with.
	 */
	@SuppressWarnings("javadoc")
	private Console console;

	/**
	 * Called when a command event is sent.
	 *
	 * @param event the command sent
	 */
	@EventHandler
	public void onCommand(PluginCommandSent event) {

		// TODO do something with commands
	}

	/**
	 * When a console message is sent, append it to the console.
	 *
	 * @param event the event that was received
	 */
	@EventHandler
	public void onConsoleMessage(ConsoleMessage event) {
		if (!PluginManager.getInstance().isEnabled(this.parent)) {
			System.err.println("Console is disabled! Cannot print message '"
				+ event.getMessage() + "'");
			return;// Don't try to log things if it is disabled
		}
		this.console.appendMessage(event.getMessage());
	}

	/**
	 * Displays messages created by the logger.
	 *
	 * @param event logs and errors received
	 */
	@EventHandler
	public void onDisplayLog(Log event) {
		if (!PluginManager.getInstance().isEnabled(this.parent)) {
			System.err.println("Console is disabled! Cannot print message '"
				+ event.getMessage() + "'");
			return;// Don't try to log things if it is disabled
		}
		this.console.appendMessage(event.getMessage());
	}

	/**
	 * Appends a message stating the last command was incorrect and a help
	 * message informing the user of the help command.
	 *
	 * @param event the command that was reported as unknown
	 */
	@EventHandler
	public void onReportUnknownCommand(ReportUnknownCommand event) {
		String message = SafeResourceLoader.getString("unknown_command",
			this.parent.getResourceBundle())
			+ " '" + event.getCommand() + "'. "
			+ SafeResourceLoader.getString("try_cmd",
				this.parent.getResourceBundle())
			+ " '" + SafeResourceLoader.getString("COMMAND_HELP",
				"com.ikalagaming.plugins.resources.PluginManager")
			+ "'";
		if (!PluginManager.getInstance().isEnabled(this.parent)) {
			System.err.println(
				"Console is disabled! Cannot print message '" + message + "'");
			return;// Don't try to log things if it is disabled
		}
		this.console.appendMessage(message);
	}
}
