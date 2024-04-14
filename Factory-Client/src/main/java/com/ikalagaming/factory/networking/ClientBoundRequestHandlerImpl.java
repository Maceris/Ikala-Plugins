package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.ClientBoundRequestHandler;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;

import lombok.NonNull;

/** Handles requests from the server. */
public class ClientBoundRequestHandlerImpl implements ClientBoundRequestHandler {

    @Override
    public void handle(@NonNull UpdateTagRegistry request) {}

    @Override
    public void process(@NonNull Request request) {
        request.handleUsing(this);
    }
}
