package com.ikalagaming.factory;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.RequestHandler;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.plugins.PluginManager;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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
     * Open a save on the local server, starting the server if needed. The same save already open is
     * reused, and another open save is closed first.
     *
     * @param saveFolder The save's folder.
     * @return Completes with where new players start once the world is open, or fails if it can't
     *     be opened or the server plugin isn't enabled.
     */
    public static CompletableFuture<SpawnFinder.Spawn> open(@NonNull Path saveFolder) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(SERVER_PLUGIN + " isn't enabled"));
        }
        return Starter.open(saveFolder);
    }

    /**
     * Connect a client to the world open on the local server. Requests pass as objects, never
     * serialized, but only through the returned connection, so a remote server would work the same
     * way.
     *
     * @param client Handles the requests the server sends.
     * @return The connection that sends requests to the server.
     * @throws IllegalStateException If the server plugin isn't enabled or no world is open.
     */
    public static Connection connect(@NonNull RequestHandler client) {
        if (!isAvailable()) {
            throw new IllegalStateException(SERVER_PLUGIN + " isn't enabled");
        }
        return Starter.connect(client);
    }

    /**
     * Holds the reference to server classes, so they only load once the plugin is known to be
     * there.
     */
    private static final class Starter {
        /**
         * Open a save.
         *
         * @param saveFolder The save's folder.
         * @return Completes with the world's spawn.
         */
        static CompletableFuture<SpawnFinder.Spawn> open(@NonNull Path saveFolder) {
            return FactoryServerPlugin.getServer()
                    .open(saveFolder)
                    .thenApply(com.ikalagaming.factory.server.ServerWorld::getSpawn);
        }

        /**
         * Connect a client.
         *
         * @param client Handles the requests the server sends.
         * @return The client's connection.
         */
        static Connection connect(@NonNull RequestHandler client) {
            return FactoryServerPlugin.getServer().connect(client);
        }

        private Starter() {}
    }

    private LocalServer() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
