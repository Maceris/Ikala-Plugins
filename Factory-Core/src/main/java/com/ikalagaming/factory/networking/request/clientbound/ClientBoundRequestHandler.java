package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.RequestHandler;

import lombok.NonNull;

/**
 * Handles server and shared events. Specifies all the events that would need to be sent by a
 * server, or handled by a client.
 */
public interface ClientBoundRequestHandler extends RequestHandler {
    void handle(@NonNull UpdateTagRegistry request);

    /**
     * The server accepted the join.
     *
     * @param request The request.
     */
    void handle(@NonNull Joined request);

    /**
     * A chunk arrived.
     *
     * @param request The request.
     */
    void handle(@NonNull ChunkData request);

    /**
     * A chunk can be dropped.
     *
     * @param request The request.
     */
    void handle(@NonNull ChunkUnload request);
}
