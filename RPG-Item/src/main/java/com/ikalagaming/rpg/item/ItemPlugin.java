package com.ikalagaming.rpg.item;

import com.ikalagaming.plugins.Plugin;

/**
 * Handles the lifecycle for the item plugin.
 *
 * @author Ches Burks
 *
 */
public class ItemPlugin extends Plugin {
	/**
	 * The name of this plugin.
	 */
	public static final String PLUGIN_NAME = "RPG-Item";

	/**
	 * Should be called by the plugin management system.
	 */
	public ItemPlugin() {}

	@Override
	public String getName() {
		return ItemPlugin.PLUGIN_NAME;
	}

}
