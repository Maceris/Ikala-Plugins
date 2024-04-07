package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.ClientBoundConnection;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;

import lombok.NonNull;

/** Handles requests from the server. */
public class ClientBoundConnectionHandler implements ClientBoundConnection {

    @Override
    public void dispatch(@NonNull Request request) {
        request.handleUsing(this);
    }

    @Override
    public void handle(@NonNull UpdateTagRegistry request) {}
}
