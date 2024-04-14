package com.ikalagaming.factory.networking.base;

import lombok.NonNull;

/** A connection that allows requests to be sent back and forth. */
public interface Connection {

    /** Initiate a connection with the remote. */
    void connect();

    /** Close a connection from the remote. */
    void disconnect();

    /**
     * Whether this is a local connection.
     *
     * @return True if the remote is in the same process, false if it is over a network.
     */
    boolean isLocal();

    /**
     * Forward a request to the local request handler on this end of the connection.
     *
     * @param request The request that was received from the remote.
     */
    void receive(@NonNull Request request);

    /**
     * Send a request to the remote end of the connection.
     *
     * @param request The request to send to the remote.
     */
    void send(@NonNull Request request);
}
