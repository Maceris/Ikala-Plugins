package com.ikalagaming.gui.console;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.gui.console.events.ConsoleCommandEntered;
import com.ikalagaming.gui.console.events.ConsoleMessage;
import com.ikalagaming.gui.console.events.ReportUnknownCommand;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginCommand;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.events.PluginCommandSent;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

	private void cbPrintHelp(@SuppressWarnings("unused") String[] args) {
		ArrayList<PluginCommand> commands;
		commands = this.manager.getCommands();
		String tmp;
		ConsoleMessage message;
		for (PluginCommand cmd : commands) {
			tmp = cmd.getCommand();

			message = new ConsoleMessage(tmp);
			this.manager.fireEvent(message);
		}
	}

	private void cbPrintPlugins(@SuppressWarnings("unused") String[] args) {
		String tmp;
		ConsoleMessage message;
		HashMap<String, Plugin> loadedPlugins = this.manager.getLoadedPlugins();
		ArrayList<String> names = new ArrayList<>();
		names.addAll(loadedPlugins.keySet());
		Collections.sort(names);

		for (String name : names) {
			tmp = "";
			tmp += this.manager.getInfo(loadedPlugins.get(name)).get()
				.getFullName();
			if (this.manager.isEnabled(loadedPlugins.get(name))) {
				tmp +=
					" " + "(" + SafeResourceLoader.getString("ENABLED_STATUS",
						this.manager.getResourceBundle()) + ")";
			}
			else {
				tmp +=
					" " + "(" + SafeResourceLoader.getString("DISABLED_STATUS",
						this.manager.getResourceBundle()) + ")";
			}
			message = new ConsoleMessage(tmp);
			this.manager.fireEvent(message);
		}
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
			this.manager.getResourceBundle())
			+ this.manager.getInfo(pack.get()).get().getVersion();

		this.manager.fireEvent(new ConsoleMessage(tmp));
	}

	/**
	 * Log an alert about a plugin lifecycle, where plugin name and version are
	 * automatically replaced.
	 *
	 * @param whichAlert The string to read from the resource bundle
	 * @param plugin The plugin that the alert is about
	 */
	void logAlert(String whichAlert, Plugin plugin) {
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
			ReportUnknownCommand report = new ReportUnknownCommand(firstWord);
			this.manager.fireEvent(report);
		}

		this.manager.fireEvent(new PluginCommandSent(event.getCommand()));
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
		this.manager.fireEvent(message);
	}

	/**
	 * Load the commands from the resource files.
	 */
	void setCommands() {
		this.manager.registerCommand(SafeResourceLoader
			.getString("COMMAND_HELP", this.parent.getResourceBundle()),
			this::cbPrintHelp);
		this.manager.registerCommand(SafeResourceLoader
			.getString("COMMAND_LIST_PLUGINS", this.parent.getResourceBundle()),
			this::cbPrintPlugins);
		this.manager.registerCommand(SafeResourceLoader
			.getString("COMMAND_VERSION", this.parent.getResourceBundle()),
			this::cbPrintVersion);
	}

}
