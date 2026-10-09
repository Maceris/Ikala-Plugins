package com.ikalagaming.graphics;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.events.PluginUnloaded;

/** Releases graphics resources that belong to plugins as they unload. */
public class GraphicsPluginListener implements Listener {

    /**
     * Release everything an unloaded plugin owned. Our own resources are cleaned up when the
     * renderer shuts down instead.
     *
     * @param event The event.
     */
    @EventHandler
    public void onPluginUnloaded(PluginUnloaded event) {
        if (GraphicsPlugin.PLUGIN_NAME.equals(event.getPlugin())) {
            return;
        }
        GraphicsManager.pluginUnloaded(event.getPlugin());
    }
}
