package com.ikalagaming.gui.console;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * The plugin interface for the simple console.
 * 
 * @author Ches Burks
 *
 */
@CustomLog(topic = ConsolePlugin.PLUGIN_NAME)
public class ConsolePlugin extends Plugin {

	/**
	 * The resource bundle for localizing this console.
	 *
	 * @param The resource bundle for the console.
	 * @return The resource bundle for the console.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	@Setter
	private ResourceBundle resourceBundle;

	/**
	 * The console we interact with.
	 */
	private Console console;

	/**
	 * Constructs a new Console Plugin.
	 */
	public ConsolePlugin() {
		this.console = new Console();
		this.console.setParent(this);
	}

	/**
	 * Should match the plugin.yml, but here as a constant for convenience.
	 */
	public static final String PLUGIN_NAME = "Ikala-Console";

	private HashSet<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners = new HashSet<>();
			this.listeners.add(new ConsoleListener(this, console));
			this.listeners.add(new PMEventListener(this));
		}
		return this.listeners;
	}

	@Override
	public boolean onDisable() {
		// TODO clean this up
		console.frame.setVisible(false);
		console.frame.dispose();
		return true;
	}

	@Override
	public boolean onEnable() {
		// TODO clean this up
		console.init();
		console.appendIndicatorChar();
		console.appendMessage(SafeResourceLoader.getString("missed_logs",
			this.getResourceBundle()));
		return true;
	}

	@Override
	public boolean onLoad() {
		try {
			this.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.gui.console.resources.Console",
				Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			log.warning("Locale not found for Console in onLoad()");
		}
		console.setWindowTitle(
			SafeResourceLoader.getString("title", this.getResourceBundle()));
		return true;
	}

	@Override
	public boolean onUnload() {
		// TODO clean this up
		if (console.frame != null) {
			console.frame.setVisible(false);
			console.frame.dispose();
			console.frame = null;
		}
		this.setResourceBundle(null);
		console.history = null;
		this.console.setParent(null);
		if (this.listeners != null) {
			this.listeners.clear();
			this.listeners = null;
		}
		return true;
	}

}
