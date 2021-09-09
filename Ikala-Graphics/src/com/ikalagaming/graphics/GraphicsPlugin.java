package com.ikalagaming.graphics;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class GraphicsPlugin extends Plugin {

	/**
	 * The name of the plugin.
	 */
	static final String NAME = "Ikala-Graphics";
	private ControlListener ctrl;
	private Set<Listener> listeners;
	/**
	 * The resource bundle for the Graphics plugin.
	 * 
	 * @return The bundle.
	 * @param resourceBundle The new bundle to use.
	 */
	@Getter
	@Setter
	private ResourceBundle resourceBundle;

	private ControlListener getCtrl() {
		if (this.ctrl == null) {
			this.ctrl = new ControlListener();
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
		GraphicsManager.destroyAllWindows();
		GraphicsManager.terminate();
		return true;
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
			log.warn("Locale not found for Ikala-Graphics in onLoad()");
		}
		GraphicsManager.setPlugin(this);
		return true;
	}

	@Override
	public boolean onUnload() {
		GraphicsManager.setPlugin(null);
		this.ctrl = null;
		this.setResourceBundle(null);
		return true;
	}

}
