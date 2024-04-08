package com.ikalagaming.factory.networking.serialization;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Used to map requests to and from KVT data. */
@Slf4j
public class RequestKVTMapper {
    /**
     * Convert the request to KVT data, based on the type of the object.
     *
     * @param object The object to serialize.
     * @return The KVT data corresponding to the request contents.
     * @throws IllegalArgumentException If the request type is not supported.
     */
    public static KVT toKVT(@NonNull Request object) {
        if (object instanceof UpdateTagRegistry mapped) {
            return UpdateTagRegistry.toKVT(mapped);
        }

        var message =
                SafeResourceLoader.getStringFormatted(
                        "UNSUPPORTED_REQUEST_TYPE",
                        FactoryPlugin.getResourceBundle(),
                        object.getClass().getSimpleName());
        log.error(message);
        throw new IllegalArgumentException(message);
    }

    /**
     * Convert the KVT data to a request object, based on the type of the object.
     *
     * @param kvt The KVT data we are converting.
     * @param type The type of request that we are building.
     * @return The corresponding request.
     * @throws IllegalArgumentException If the request type is not supported.
     */
    public static Request fromKVT(@NonNull KVT kvt, @NonNull Class<? extends Request> type) {
        if (UpdateTagRegistry.class.isAssignableFrom(type)) {
            return UpdateTagRegistry.fromKVT(kvt);
        }

        var message =
                SafeResourceLoader.getStringFormatted(
                        "UNSUPPORTED_REQUEST_TYPE",
                        FactoryPlugin.getResourceBundle(),
                        type.getSimpleName());
        log.error(message);
        throw new IllegalArgumentException(message);
    }

    /** Private constructor so that this class is not instantiated. */
    private RequestKVTMapper() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
