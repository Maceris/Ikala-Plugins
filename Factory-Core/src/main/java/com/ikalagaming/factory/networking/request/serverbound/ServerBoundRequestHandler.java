package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.RequestHandler;

/**
 * The receiving end of a connection, handles the client requests that are sent. The requests that
 * are handled are from the client, but this would be implemented on a server.
 */
public interface ServerBoundRequestHandler extends RequestHandler {}
