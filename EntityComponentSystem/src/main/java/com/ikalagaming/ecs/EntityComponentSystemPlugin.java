package com.ikalagaming.ecs;

import com.ikalagaming.plugins.Plugin;

/**
 * Handles the lifecycle for the Entity Component System plugin.
 *
 * @author Ches Burks
 *
 */
public class EntityComponentSystemPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	private static final String PLUGIN_NAME = "EntityComponentSystem";

	@Override
	public String getName() {
		return EntityComponentSystemPlugin.PLUGIN_NAME;
	}
}
