package com.ikalagaming.graphics;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.CustomLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * The plugin for handling graphics.
 *
 * @author Ches Burks
 *
 */
@CustomLog(topic = GraphicsPlugin.NAME)
public class GraphicsPlugin extends Plugin {

	/**
	 * The name of the plugin.
	 */
	static final String NAME = "Ikala-Graphics";
	private GraphicsState state;
	private ControlListener ctrl;
	private Set<Listener> listeners;
	private ResourceBundle resourceBundle;

	private ControlListener getCtrl() {
		if (this.ctrl == null) {
			this.ctrl = new ControlListener(this);
		}
		return this.ctrl;
	}

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners =
				Collections.synchronizedSet(new HashSet<Listener>());
			this.listeners.add(this.getCtrl());
		}

		return this.listeners;
	}

	@Override
	public boolean onDisable() {
		this.state = null;
		if (this.ctrl != null) {
			this.ctrl.shutdown();
		}
		return true;
	}

	@Override
	public boolean onEnable() {
		this.state = new GraphicsState();
		this.getCtrl().setState(this.state);
		return true;
	}

	/**
	 * Returns the resource bundle for this console
	 *
	 * @return the resource bundle
	 */
	ResourceBundle getResourceBundle() {
		return this.resourceBundle;
	}

	private void setResourceBundle(ResourceBundle newBundle) {
		this.resourceBundle = newBundle;
	}

	@Override
	public boolean onLoad() {
		try {
			this.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.graphics.resources.strings",
				Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			log.warning("Locale not found for Ikala-Graphics in onLoad()");
		}
		return true;
	}

	@Override
	public boolean onUnload() {
		this.ctrl = null;
		this.setResourceBundle(null);
		return true;
	}

}
