package com.ikalagaming.rpg;

import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.scripting.Engine;

/**
 * An example plugin to demonstrate how plugins are set up.
 *
 * @author Ches Burks
 *
 */
public class RPGPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "RPG-Logic";

	@Override
	public boolean onEnable() {
		Engine.registerHooks(FlagManager.class);
		return true;
	}

	@Override
	public boolean onDisable() {
		Engine.unregisterHooks(FlagManager.class);
		return true;
	}
}
