package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * The core of a factory game.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Core";

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
        return FactoryPlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onLoad() {
        try {
            FactoryPlugin.setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Factory-Core in onLoad()");
        }

        try {
            RequestRegistry.registerDefaults();
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onUnload() {
        RequestRegistry.purge();
        FactoryPlugin.setResourceBundle(null);
        return true;
    }
}
