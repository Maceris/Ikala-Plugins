package com.ikalagaming.factory.networking.request.server;

import com.ikalagaming.factory.networking.base.Connection;

import lombok.NonNull;

/**
 * Handles server and shared events. Specifies all the events that would need to be sent by a
 * server, or handled by a client.
 */
public interface ServerConnection extends Connection {
    void handle(@NonNull UpdateTagRegistry request);
}
