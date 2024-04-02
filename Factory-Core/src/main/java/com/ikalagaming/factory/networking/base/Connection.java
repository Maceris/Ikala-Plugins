package com.ikalagaming.factory.networking.base;

import lombok.NonNull;

/** A connection to either a local or remote client/server, used to transmit data. */
public abstract class Connection {
    /**
     * Send a request to the remote.
     *
     * @param request The data to send.
     */
    public abstract void send(@NonNull Request request);
}
