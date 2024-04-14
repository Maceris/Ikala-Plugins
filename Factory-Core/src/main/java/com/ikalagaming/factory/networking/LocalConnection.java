package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestHandler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A connection within the same process, that transparently passes requests directly to the handler
 * without serializing anything.
 */
@RequiredArgsConstructor
public class LocalConnection implements Connection {

    /** This end of the connection, that handles request sent by the remote. */
    private final RequestHandler local;

    /**
     * The other end of the connection, which we will directly send requests to, bypassing a network
     * connection.
     */
    private final RequestHandler remote;

    @Override
    public void connect() {}

    @Override
    public void disconnect() {}

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public void receive(@NonNull Request request) {
        local.process(request);
    }

    @Override
    public void send(@NonNull Request request) {
        remote.process(request);
    }
}
