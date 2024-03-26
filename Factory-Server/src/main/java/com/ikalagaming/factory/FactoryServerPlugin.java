package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Handles the server-side part of Lotomation.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryServerPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Server";

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
        if (null == this.listeners) {
            this.listeners = new HashSet<>();
        }
        return this.listeners;
    }

    @Override
    public String getName() {
        return FactoryServerPlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onLoad() {
        try {
            FactoryPlugin.setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Factory-Server in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        FactoryPlugin.setResourceBundle(null);
        return true;
    }
}
