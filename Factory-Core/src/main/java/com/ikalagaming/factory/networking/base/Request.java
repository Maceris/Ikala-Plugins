package com.ikalagaming.factory.networking.base;

import com.ikalagaming.factory.networking.request.clientbound.ClientBoundRequestHandler;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;

import lombok.NonNull;

/** Information or requests that is sent to a client or server. */
public interface Request {
    /**
     * Handle the request using a server request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ServerBoundRequestHandler)
     */
    default void handleUsing(@NonNull ClientBoundRequestHandler handler) {}

    /**
     * Handle the request using a client request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ClientBoundRequestHandler)
     */
    default void handleUsing(@NonNull ServerBoundRequestHandler handler) {}

    /**
     * Whether the request must arrive, in order with the other reliable requests. Unreliable
     * requests may be lost or arrive out of order, and only make sense when a newer one replaces an
     * older one, like the player's state each command frame.
     *
     * @return True if the request must arrive, which is the default.
     */
    default boolean isReliable() {
        return true;
    }
}
