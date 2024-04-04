package com.ikalagaming.rpg.inventory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * An inventory plugin.
 *
 * @author Ches Burks
 */
@Slf4j
public class InventoryPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "RPG-Inventory";

    /**
     * The resource bundle for the plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

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
        return InventoryPlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onLoad() {
        try {
            InventoryPlugin.setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.rpg.inventory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for RPG-Inventory in onLoad()");
        }
        return true;
    }
}
