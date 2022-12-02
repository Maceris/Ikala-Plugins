package com.ikalagaming.world;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import java.util.HashSet;
import java.util.Set;

/**
 * A plugin that handles the game world state.
 *
 * @author Ches Burks
 *
 */
public class WorldPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Ikala-World";

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (null == this.listeners) {
			this.listeners = new HashSet<>();
		}
		return this.listeners;
	}
}
