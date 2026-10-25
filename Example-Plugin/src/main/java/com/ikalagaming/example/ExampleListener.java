package com.ikalagaming.example;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.events.PluginEnabled;

import lombok.extern.slf4j.Slf4j;

/**
 * An example of event listener setup.
 *
 * @author Ches Burks
 */
@Slf4j
public class ExampleListener implements Listener {

    /**
     * Logs the fact that a plugin was enabled. Listeners are only registered while their plugin is
     * enabled, so this is called for this plugin and the ones enabled after it.
     *
     * @param event The event we are handling.
     */
    @EventHandler
    public void onPluginEnabled(PluginEnabled event) {
        log.info("The plugin {} was enabled!", event.getPlugin());
    }
}
