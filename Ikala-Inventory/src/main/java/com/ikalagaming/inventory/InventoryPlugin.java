package com.ikalagaming.inventory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * An inventory plugin.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class InventoryPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-Inventory";

	/**
	 * The resource bundle for the plugin.
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
		if (null == this.listeners) {
			this.listeners = new HashSet<>();
		}
		return this.listeners;
	}

	@Override
	public String getName() {
		return InventoryPlugin.PLUGIN_NAME;
	}

	@Override
	public boolean onLoad() {
		try {
			InventoryPlugin.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.inventory.strings", Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			InventoryPlugin.log
				.warn("Locale not found for Ikala-Inventory in onLoad()");
		}
		return true;
	}
}
