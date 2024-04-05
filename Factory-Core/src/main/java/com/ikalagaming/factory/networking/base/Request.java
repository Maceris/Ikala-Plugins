package com.ikalagaming.factory.networking.base;

import com.ikalagaming.factory.networking.request.client.ClientConnection;
import com.ikalagaming.factory.networking.request.server.ServerConnection;

import lombok.NonNull;

/** Information or requests that is sent to a client or server. */
public interface Request {
    /**
     * Handle the request using a client request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ServerConnection)
     */
    default void handleUsing(@NonNull ClientConnection handler) {}

    /**
     * Handle the request using a server request handler. It is expected that any given request
     * implement exactly 1 of these methods, based on which kind of event it is (client, server).
     *
     * @param handler The handler to use handle this with.
     * @see #handleUsing(ClientConnection)
     */
    default void handleUsing(@NonNull ServerConnection handler) {}

    /**
     * Send the request using a connection.
     *
     * @param connection The connection to send a request over.
     */
    void sendUsing(@NonNull Connection connection);
}
