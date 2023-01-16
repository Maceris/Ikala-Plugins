package com.ikalagaming.graphics;

import com.ikalagaming.event.Listener;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.world.WorldManager;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

/**
 * The plugin for handling graphics.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsPlugin extends Plugin {

	/**
	 * The name of the plugin.
	 */
	public static final String PLUGIN_NAME = "Ikala-Graphics";
	/**
	 * The resource bundle for the Graphics plugin.
	 *
	 * @return The bundle.
	 * @param resourceBundle The new bundle to use.
	 */
	@Getter
	@Setter
	private static ResourceBundle resourceBundle;

	private Set<Listener> listeners;

	@Override
	public Set<Listener> getListeners() {
		if (this.listeners == null) {
			this.listeners =
				Collections.synchronizedSet(new HashSet<Listener>());
			this.listeners.add(new GraphicsListener());
		}

		return this.listeners;
	}

	@Override
	public boolean onDisable() {
		GraphicsManager.getShutdownFlag().set(true);
		Launcher.removeMainThreadStage(GraphicsManager.getTickStageID());
		return true;
	}

	@Override
	public boolean onEnable() {
		GraphicsManager.createWindow();
		UUID stageID = Launcher.addMainThreadStage(GraphicsManager::tick);
		GraphicsManager.setTickStageID(stageID);
		WorldManager.getInstance().loadLevel("default");
		return true;
	}

	@Override
	public boolean onLoad() {
		try {
			GraphicsPlugin.setResourceBundle(ResourceBundle.getBundle(
				"com.ikalagaming.graphics.strings", Localization.getLocale()));
		}
		catch (MissingResourceException missingResource) {
			// don't localize this since it would fail anyways
			GraphicsPlugin.log
				.warn("Locale not found for Ikala-Graphics in onLoad()");
		}
		return true;
	}

	@Override
	public boolean onUnload() {
		GraphicsPlugin.setResourceBundle(null);
		return true;
	}

}
