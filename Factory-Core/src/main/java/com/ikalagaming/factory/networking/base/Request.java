package com.ikalagaming.factory.networking.base;

import com.ikalagaming.factory.networking.request.clientbound.ClientBoundConnection;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundConnection;

import lombok.NonNull;

/** Information or requests that is sent to a client or server. */
public interface Request {
    /**
     * Handle the request using a client request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ClientBoundConnection)
     */
    default void handleUsing(@NonNull ServerBoundConnection handler) {}

    /**
     * Handle the request using a server request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ServerBoundConnection)
     */
    default void handleUsing(@NonNull ClientBoundConnection handler) {}

    /**
     * Send the request using a connection.
     *
     * @param connection The connection to send a request over.
     */
    void sendUsing(@NonNull Connection connection);
}
