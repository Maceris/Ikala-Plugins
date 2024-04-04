package com.ikalagaming.factory.networking.request.server;

import com.ikalagaming.factory.networking.base.RequestHandler;

import lombok.NonNull;

/**
 * The receiving end of a connection, handles the server requests that are sent. The requests that
 * are handled are from the server, but this would be implemented on a client.
 */
public interface ServerRequestHandler extends RequestHandler {
    void handle(@NonNull UpdateTagRegistry request);
}
