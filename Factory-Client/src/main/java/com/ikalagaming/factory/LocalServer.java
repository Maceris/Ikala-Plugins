package com.ikalagaming.factory;

import com.ikalagaming.plugins.PluginManager;

/**
 * Starts a server in this process, for single player. Factory-Server is only a soft dependency, so
 * the client can run without it, as it does in the asset editor; this is the one place that touches
 * server classes, and only after checking the plugin is there.
 */
public final class LocalServer {

    /** The server plugin's name, which must match its plugin.yml. */
    public static final String SERVER_PLUGIN = "Factory-Server";

    /**
     * Whether a local server can be started.
     *
     * @return True if the server plugin is enabled.
     */
    public static boolean isAvailable() {
        return PluginManager.getInstance().isEnabled(SERVER_PLUGIN);
    }

    /**
     * Start the local server.
     *
     * @return True if it was started, false if the server plugin isn't enabled.
     */
    public static boolean start() {
        if (!isAvailable()) {
            return false;
        }
        Starter.start();
        return true;
    }

    /**
     * Holds the reference to server classes, so they only load once the plugin is known to be
     * there.
     */
    private static final class Starter {
        /** Start the server. */
        static void start() {
            FactoryServerPlugin.getServer().start();
        }

        private Starter() {}
    }

    private LocalServer() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
