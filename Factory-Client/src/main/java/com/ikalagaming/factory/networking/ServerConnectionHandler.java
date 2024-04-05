package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.server.ServerConnection;
import com.ikalagaming.factory.networking.request.server.UpdateTagRegistry;

import lombok.NonNull;

/** Handles requests from the server. */
public class ServerConnectionHandler implements ServerConnection {

    @Override
    public void dispatch(@NonNull Request request) {
        request.handleUsing(this);
    }

    @Override
    public void handle(@NonNull UpdateTagRegistry request) {}
}
