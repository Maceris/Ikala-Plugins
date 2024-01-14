package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import java.util.HashSet;
import java.util.Set;

/**
 * The core of a factory game.
 *
 * @author Ches Burks
 *
 */
public class FactoryPlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Factory-Core";

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
		return FactoryPlugin.PLUGIN_NAME;
	}

}
