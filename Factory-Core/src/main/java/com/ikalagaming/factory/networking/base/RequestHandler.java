package com.ikalagaming.factory.networking.base;

import lombok.NonNull;

/** The receiving end of a connection, handles the requests that are sent. */
public interface RequestHandler {
    /**
     * Process a request from a client or server.
     *
     * @param request The data to send.
     */
    void process(@NonNull Request request);
}
