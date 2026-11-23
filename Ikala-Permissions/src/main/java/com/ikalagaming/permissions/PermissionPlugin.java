package com.ikalagaming.permissions;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;

import java.util.Set;

/**
 * Provides permissions to other plugins: the shared {@link PermissionRegistry} where they declare
 * their nodes. Games keep their own {@link PermissionStore} per save and check with {@link
 * Permissions}.
 */
public class PermissionPlugin extends Plugin {

    /** The plugin's name, as in plugin.yml. */
    public static final String PLUGIN_NAME = "Ikala-Permissions";

    /**
     * The nodes every plugin declared.
     *
     * @return The registry.
     */
    @Getter private static final PermissionRegistry registry = new PermissionRegistry();

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Set<Listener> getListeners() {
        return Set.of();
    }
}
