package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.server.Server;
import com.ikalagaming.factory.server.ServerListener;
import com.ikalagaming.launcher.AppPaths;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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

    /** The server that we will use, which starts out not running. */
    @Getter private static final Server server = new Server();

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (null == this.listeners) {
            this.listeners = Collections.synchronizedSet(new HashSet<>());
            this.listeners.add(new ServerListener(server));
        }
        return this.listeners;
    }

    @Override
    public String getName() {
        return FactoryServerPlugin.PLUGIN_NAME;
    }

    /**
     * Host the instance's save, if the game was started with one ({@code bin/runServer}), as a
     * dedicated server that opens its port.
     *
     * @return True.
     */
    @Override
    public boolean onEnable() {
        AppPaths.instance()
                .ifPresent(
                        instance -> {
                            log.info("Hosting {} as a dedicated server", instance);
                            server.open(instance);
                        });
        return true;
    }

    @Override
    public boolean onDisable() {
        server.stop();
        return true;
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
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
        setResourceBundle(null);
        return true;
    }
}
