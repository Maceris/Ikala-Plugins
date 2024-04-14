package com.ikalagaming.factory.networking.base;

import lombok.NonNull;

/** One end of a connection, handles the requests that are sent or received. */
public interface RequestHandler {
    /**
     * Process a request from a client or server.
     *
     * @param request The data to send or that was received.
     */
    void process(@NonNull Request request);
}
