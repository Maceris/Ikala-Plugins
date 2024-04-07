package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Connection;

/**
 * The receiving end of a connection, handles the client requests that are sent. The requests that
 * are handled are from the client, but this would be implemented on a server.
 */
public interface ServerBoundConnection extends Connection {}
