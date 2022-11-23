package com.ikalagaming.rpg;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.plugins.events.PluginEnabled;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
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
	@Getter
	private GUIControls gui;

	/**
	 * Attach the GUI to the scene.
	 */
	public void attachGUI() {
		this.gui = new GUIControls(GraphicsManager.getScene());
		GraphicsManager.setGUI(this.gui);
		// GraphicsManager.refreshRenderData();
		GUIEventListener.log.debug(SafeResourceLoader.getString("GUI_ATTACHED",
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
		this.attachGUI();
	}
}
