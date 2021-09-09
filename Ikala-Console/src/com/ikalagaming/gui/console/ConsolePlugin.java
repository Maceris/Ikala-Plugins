package com.ikalagaming.gui.console;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class ConsolePlugin extends Plugin {

	/**
	 * Should match the plugin.yml, but here as a constant for convenience.
	 */
	public static final String PLUGIN_NAME = "Ikala-Console";

	/**
	 * The resource bundle for localizing this console.
	 *
	 * @param resourceBundle The resource bundle for the console.
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

	private HashSet<Listener> listeners;

	/**
	 * Constructs a new Console Plugin.
	 */
	public ConsolePlugin() {

	}

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners = new HashSet<>();
			this.listeners.add(new ConsoleListener(this, this.console));
			this.listeners.add(new PMEventListener(this));
		}
		return this.listeners;
	}

	@Override
	public boolean onDisable() {
		// TODO clean this up
		this.console.frame.setVisible(false);
		this.console.frame.dispose();
		return true;
	}

	@Override
	public boolean onEnable() {
		// TODO clean this up
		this.console.init();
		this.console.setWindowTitle(
			SafeResourceLoader.getString("title", this.getResourceBundle()));
		this.console.appendIndicatorChar();
		this.console.appendMessage(SafeResourceLoader.getString("missed_logs",
			this.getResourceBundle()));
		return true;
	}

	@Override
	public boolean onLoad() {
		this.console = new Console();
		this.console.setParent(this);

		try {
			this.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.gui.console.resources.Console",
				Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			ConsolePlugin.log.warn("Locale not found for Console in onLoad()");
		}
		return true;
	}

	@Override
	public boolean onUnload() {
		// TODO clean this up
		if (this.console.frame != null) {
			this.console.frame.setVisible(false);
			this.console.frame.dispose();
			this.console.frame = null;
		}
		this.setResourceBundle(null);
		this.console.history = null;
		this.console.setParent(null);
		if (this.listeners != null) {
			this.listeners.clear();
			this.listeners = null;
		}
		this.console = null;
		return true;
	}

}
