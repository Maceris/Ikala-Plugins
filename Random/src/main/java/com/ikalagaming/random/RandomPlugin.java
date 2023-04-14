package com.ikalagaming.random;

import com.ikalagaming.plugins.Plugin;

/**
 * An example plugin to demonstrate how plugins are set up.
 *
 * @author Ches Burks
 *
 */
public class RandomPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Random";

	@Override
	public String getName() {
		return RandomPlugin.PLUGIN_NAME;
	}
}
