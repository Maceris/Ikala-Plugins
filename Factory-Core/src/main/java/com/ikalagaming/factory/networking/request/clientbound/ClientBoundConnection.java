package com.ikalagaming.factory.networking.request.clientbound;

import com.ikalagaming.factory.networking.base.Connection;

import lombok.NonNull;

/**
 * Handles server and shared events. Specifies all the events that would need to be sent by a
 * server, or handled by a client.
 */
public interface ClientBoundConnection extends Connection {
    void handle(@NonNull UpdateTagRegistry request);
}
