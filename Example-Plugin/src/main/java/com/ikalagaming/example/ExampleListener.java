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
     * Logs the fact that a plugin was loaded.
     *
     * @param event The event we are handling.
     */
    @EventHandler
    public void onPluginEnabled(PluginEnabled event) {
        log.info("The plugin " + event.getPlugin() + " was loaded!");
    }
}
