package com.ikalagaming.rpg;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.event.Order;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.plugins.events.PluginEnabled;
import com.ikalagaming.rpg.world.events.LevelLoaded;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Handle events for the GUI.
 *
 * @author Ches Burks
 */
@Slf4j
public class GUIEventListener implements Listener {

    /** The GUI instance. */
    @Getter private GUIControls gui;

    /** Attach the GUI to the scene. */
    public void attachGUI() {
        gui = new GUIControls(GraphicsManager.getScene());
        GraphicsManager.setGUI(gui);
        log.debug(SafeResourceLoader.getString("GUI_ATTACHED", GUIPlugin.getResourceBundle()));
    }

    /**
     * Update scene controls when a level is loaded.
     *
     * @param event The event.
     */
    @EventHandler(order = Order.LATE)
    public void onLevelLoaded(LevelLoaded event) {
        log.debug(SafeResourceLoader.getString("SCENE_CONTROLS", GUIPlugin.getResourceBundle()));
        gui.windowSceneControls.setup(GraphicsManager.getScene());
        gui.windowInventory.setup(GraphicsManager.getScene());
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
