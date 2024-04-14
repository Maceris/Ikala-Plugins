package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.base.RequestHandler;

import io.netty.channel.Channel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.net.SocketAddress;

/**
 * A connection on a different computer, or at least one that uses the network to transmit requests.
 */
@RequiredArgsConstructor
public class RemoteConnection implements Connection {

    /** The active channel. */
    private final Channel channel;

    /** This end of the connection, that handles request sent by the remote. */
    private final RequestHandler local;

    /**
     * The other end of the connection, which we will send requests to over a network connection.
     */
    private final RequestHandler remote;

    /** The address of the remote connection. */
    private final SocketAddress remoteAddress;

    /**
     * The direction that a packet is traveling towards when sending to remote. For example, if this
     * is {@link RequestDirection#SERVER_BOUND}, then this connection exists on a client and the
     * remote end is a server.
     */
    private final RequestDirection remoteDirection;

    @Override
    public void connect() {}

    @Override
    public void disconnect() {}

    @Override
    public boolean isLocal() {
        return false;
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
