package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.server.ServerRequestHandler;
import com.ikalagaming.factory.networking.request.server.UpdateTagRegistry;

import lombok.NonNull;

public class ServerRequestHandlerImpl implements ServerRequestHandler {

    @Override
    public void process(@NonNull Request request) {
        request.handleUsing(this);
    }

    @Override
    public void handle(@NonNull UpdateTagRegistry request) {}
}
