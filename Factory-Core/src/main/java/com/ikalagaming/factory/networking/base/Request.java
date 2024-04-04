package com.ikalagaming.factory.networking.base;

import com.ikalagaming.factory.networking.request.client.ClientRequestHandler;
import com.ikalagaming.factory.networking.request.server.ServerRequestHandler;

import lombok.NonNull;

/** Information or requests that is sent to a client or server. */
public interface Request {
    /**
     * Handle the request using a client request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ServerRequestHandler)
     */
    default void handleUsing(@NonNull ClientRequestHandler handler) {}

    /**
     * Handle the request using a server request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ClientRequestHandler)
     */
    default void handleUsing(@NonNull ServerRequestHandler handler) {}
}
