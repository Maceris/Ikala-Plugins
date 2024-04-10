package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;
import com.ikalagaming.util.SafeResourceLoader;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/** Registers the different types of packets and assigns them unique IDs, at least per direction. */
@Slf4j
public class RequestRegistry {
    /** Requests headed towards the client. */
    private static final BiMap<Integer, Class<? extends Request>> clientBound = HashBiMap.create();

    /** Requests headed towards the server. */
    private static final BiMap<Integer, Class<? extends Request>> serverBound = HashBiMap.create();

    /** Whether we have set up the registry. */
    @Getter private static boolean setUp;

    /**
     * Register all the default packets. Should only ever be called once, and internally by this
     * plugin.
     *
     * @throws IllegalArgumentException If there was a problem registering defaults.
     */
    @Synchronized
    public static void registerDefaults() {
        if (setUp) {
            return;
        }
        setUp = true;
        registerClientBound(UpdateTagRegistry.class);
    }

    /** Clean out all definitions. Only really applicable for testing or reloading plugins. */
    @Synchronized
    public static void purge() {
        setUp = false;
        clientBound.clear();
        serverBound.clear();
    }

    /**
     * Register a request that is headed towards the client.
     *
     * @param type The type of request to register.
     * @throws IllegalArgumentException If the request is already added to the collection.
     */
    private static void registerClientBound(@NonNull Class<? extends Request> type) {
        if (clientBound.containsValue(type)) {
            logDuplicateException(type);
        }
        clientBound.put(clientBound.size(), type);
    }

    /**
     * Register a request that is headed towards the server.
     *
     * @param type The type of request to register.
     * @throws IllegalArgumentException If the request is already added to the collection.
     */
    private static void registerServerBound(@NonNull Class<? extends Request> type) {
        if (serverBound.containsValue(type)) {
            logDuplicateException(type);
        }
        serverBound.put(serverBound.size(), type);
    }

    /**
     * Register a request that is headed towards both the server and client.
     *
     * @param type The type of request to register.
     * @throws IllegalArgumentException If the request is already added to the collection.
     */
    private static void registerShared(@NonNull Class<? extends Request> type) {
        if (clientBound.containsValue(type) || serverBound.containsValue(type)) {
            logDuplicateException(type);
        }
        clientBound.put(clientBound.size(), type);
        serverBound.put(serverBound.size(), type);
    }

    /**
     * Log the fact the request is already registered before an exception is thrown.
     *
     * @param type The class that we were trying to register.
     */
    private static void logDuplicateException(@NonNull Class<? extends Request> type) {
        log.warn(
                SafeResourceLoader.getStringFormatted(
                        "REQUEST_ALREADY_REGISTERED",
                        FactoryPlugin.getResourceBundle(),
                        type.getSimpleName()));
    }

    /**
     * Fetch the ID for the specified type.
     *
     * @param direction The direction we are interested in.
     * @param type The type we want the ID of.
     * @return The ID of the packet in that direction. -1 if it is not found.
     */
    public static int getID(
            @NonNull RequestDirection direction, @NonNull Class<? extends Request> type) {
        var map =
                switch (direction) {
                    case CLIENT_BOUND -> clientBound;
                    case SERVER_BOUND -> serverBound;
                };

        return Optional.ofNullable(map.inverse().get(type)).orElse(-1);
    }

    /**
     * Fetch the class that corresponds to the specified ID.
     *
     * @param direction The direction we are interested in.
     * @param id The ID we want the class of.
     * @return The class of the packet with that ID in that direction, or null if not found.
     */
    public static Class<? extends Request> getType(@NonNull RequestDirection direction, int id) {
        var map =
                switch (direction) {
                    case CLIENT_BOUND -> clientBound;
                    case SERVER_BOUND -> serverBound;
                };

        return map.get(id);
    }
}
