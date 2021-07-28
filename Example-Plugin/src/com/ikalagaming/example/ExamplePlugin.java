package com.ikalagaming.example;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import lombok.CustomLog;

import java.util.HashSet;
import java.util.Set;

/**
 * An example plugin to demonstrate how plugins are set up.
 * 
 * @author Ches Burks
 *
 */
@CustomLog(topic = ExamplePlugin.PLUGIN_NAME)
public class ExamplePlugin extends Plugin {
	/**
	 * The name of the plugin in Java for convenience, should match the name in
	 * plugin.yml.
	 */
	public static final String PLUGIN_NAME = "Example-Plugin";

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (null == listeners) {
			listeners = new HashSet<>();
			listeners.add(new ExampleListener());
		}
		return listeners;
	}

	@Override
	public boolean onEnable() {
		log.info("Hello world! The example plugin is loaded.");
		return true;
	}
}
