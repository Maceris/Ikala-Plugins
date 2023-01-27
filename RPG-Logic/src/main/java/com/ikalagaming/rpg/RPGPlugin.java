package com.ikalagaming.rpg;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.scripting.Engine;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners =
				Collections.synchronizedSet(new HashSet<Listener>());
			this.listeners.add(new GeneralListener());
		}

		return this.listeners;
	}

	@Override
	public boolean onEnable() {
		Engine.registerClass(FlagManager.class);
		return true;
	}

	@Override
	public boolean onDisable() {
		Engine.unregisterClass(FlagManager.class);
		return true;
	}
}
