package com.ikalagaming.rpg.world;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A plugin that handles the game world state.
 *
 * @author Ches Burks
 */
public class WorldPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "RPG-World";

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (null == listeners) {
            listeners = Collections.synchronizedSet(new HashSet<>());
        }
        return listeners;
    }

    @Override
    public String getName() {
        return WorldPlugin.PLUGIN_NAME;
    }
}
