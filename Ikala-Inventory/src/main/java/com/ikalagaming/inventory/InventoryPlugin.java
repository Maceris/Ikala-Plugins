package com.ikalagaming.inventory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import java.util.HashSet;
import java.util.Set;

/**
 * An inventory plugin.
 *
 * @author Ches Burks
 *
 */
public class InventoryPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-Inventory";

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (null == this.listeners) {
			this.listeners = new HashSet<>();
		}
		return this.listeners;
	}

}
