package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A connection within the same process, that transparently passes requests directly to the handler
 * without serializing anything.
 */
@RequiredArgsConstructor
public class LocalConnection implements Connection {

    /**
     * The other end of the connection, which we will directly send requests to, bypassing a network
     * connection.
     */
    private final Connection remote;

    @Override
    public void dispatch(@NonNull Request request) {
        remote.dispatch(request);
    }
}
