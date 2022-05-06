package com.ikalagaming.item;

import com.ikalagaming.plugins.Plugin;

/**
 * Handles the lifecycle for the item plugin.
 *
 * @author Ches Burks
 *
 */
public class ItemPlugin extends Plugin {
	/**
	 * Should be called by the plugin management system.
	 */
	public ItemPlugin() {}

	@Override
	public boolean onEnable() {
		return true;
	}
}
