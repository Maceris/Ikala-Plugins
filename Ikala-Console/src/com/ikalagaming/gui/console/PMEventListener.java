package com.ikalagaming.gui.console;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.gui.console.events.ConsoleCommandEntered;
import com.ikalagaming.gui.console.events.ConsoleMessage;
import com.ikalagaming.gui.console.events.ReportUnknownCommand;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.events.PluginCommandSent;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;

import java.util.Optional;

/**
 * The event listener for the plugin management system.
 *
 * @author Ches Burks
 *
 */
@CustomLog(topic = ConsolePlugin.PLUGIN_NAME)
class PMEventListener implements Listener {

	private ConsolePlugin parent;
	private PluginManager manager;

	/**
	 * Constructs a listener for the default plugin manager.
	 *
	 * @param parent the parent console
	 */
	public PMEventListener(ConsolePlugin parent) {
		this.parent = parent;
		this.manager = PluginManager.getInstance();
	}

	/**
	 * Prints the version of the plugin specified, if it exists. If no plugin
	 * exists, alerts the user to this fact.
	 *
	 * @param args The arguments to a command, first of which should be the
	 *            plugin name
	 *
	 */
	private void cbPrintVersion(String[] args) {
		if (args.length < 1) {
			return;
		}
		String pluginName = args[0];
		Optional<Plugin> pack = this.manager.getPlugin(pluginName);

		if (!pack.isPresent()) {
			this.reportNotLoaded(pluginName);
			return;
		}
		String tmp = SafeResourceLoader.getString("PLUGIN_VERSION",
			this.manager.getResourceBundle());
		Optional<PluginInfo> info = this.manager.getInfo(pluginName);
		if (info.isPresent()) {
			tmp += info.get().getVersion();
		}
		else {
			tmp += "?";
		}

		new ConsoleMessage(tmp).fire();
	}

	/**
	 * Log an alert about a plugin lifecycle, where plugin name and version are
	 * automatically replaced.
	 *
	 * @param whichAlert The string to read from the resource bundle
	 * @param plugin The plugin that the alert is about
	 */
	void logAlert(String whichAlert, String plugin) {
		PluginInfo pInfo = this.manager.getInfo(plugin).get();

		String message = SafeResourceLoader
			.getString(whichAlert, this.manager.getResourceBundle())
			.replaceFirst("\\$PLUGIN", pInfo.getName())
			.replaceFirst("\\$VERSION", pInfo.getVersion());
		log.fine(message);
	}

	/**
	 * Check to see if the command is registered, and handle it or report it as
	 * unregistered.
	 *
	 * @param event the command sent by the console
	 */
	@EventHandler
	public void onConsoleCommandEntered(ConsoleCommandEntered event) {
		String firstWord = event.getCommand().trim().split("\\s+")[0];
		if (!this.manager.containsCommand(firstWord)) {
			new ReportUnknownCommand(firstWord).fire();
		}

		new PluginCommandSent(event.getCommand()).fire();
	}

	/**
	 * Report that a plugin is not loaded.
	 *
	 * @param pluginName The name of the missing pluginF
	 */
	void reportNotLoaded(String pluginName) {
		ConsoleMessage message = new ConsoleMessage(SafeResourceLoader
			.getString("PLUGIN_NOT_LOADED", this.manager.getResourceBundle())
			.replaceFirst("\\$PLUGIN", pluginName));
		message.fire();
	}

	/**
	 * Load the commands from the resource files.
	 */
	void setCommands() {
		this.manager.registerCommand(
			SafeResourceLoader.getString("COMMAND_VERSION",
				this.parent.getResourceBundle()),
			this::cbPrintVersion, ConsolePlugin.PLUGIN_NAME);
	}

}
