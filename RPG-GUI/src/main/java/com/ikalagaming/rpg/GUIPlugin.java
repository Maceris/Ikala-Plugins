package com.ikalagaming.rpg;

import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsPlugin;
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
 * A plugin that handles GUI.
 * 
 * @author Ches Burks
 *
 */
@Slf4j
public class GUIPlugin extends Plugin {
	/**
	 * The name of the plugin.
	 */
	public static final String PLUGIN_NAME = "RPG-GUI";
	/**
	 * The resource bundle for the Graphics plugin.
	 *
	 * @return The bundle.
	 * @param resourceBundle The new bundle to use.
	 */
	@Getter
	@Setter
	private static ResourceBundle resourceBundle;

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners =
				Collections.synchronizedSet(new HashSet<Listener>());
			this.listeners.add(new GUIEventListener());
		}
		return this.listeners;
	}

	@Override
	public boolean onLoad() {
		try {
			GUIPlugin.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.rpg.strings", Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			log.warn("Locale not found for RPG-GUI in onLoad()");
		}
		return true;
	}

	@Override
	public boolean onUnload() {
		GraphicsPlugin.setResourceBundle(null);
		return true;
	}

	/**
	 * You shouldn't be calling this manually.
	 */
	public GUIPlugin() {}
}
