package com.ikalagaming.rpg;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.plugins.events.PluginEnabled;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;

/**
 * Handle events for the GUI.
 * 
 * @author Ches Burks
 *
 */
@Slf4j
public class GUIEventListener implements Listener {

	/**
	 * The GUI instance.
	 */
	private GUIControls gui;

	/**
	 * Attach the GUI to the scene.
	 */
	public void attachGUI() {
		gui = new GUIControls(GraphicsManager.getScene());
		GraphicsManager.setGUI(gui);
		GraphicsManager.refreshRenderData();
		log.debug(SafeResourceLoader.getString("GUI_ATTACHED",
			GUIPlugin.getResourceBundle()));
	}

	/**
	 * Attach the GUI once the graphics plugin is loaded.
	 * 
	 * @param event The event.
	 */
	@EventHandler
	public void onPluginEnabled(PluginEnabled event) {
		if (!GraphicsPlugin.PLUGIN_NAME.equals(event.getPlugin())) {
			return;
		}
		attachGUI();
	}
}
