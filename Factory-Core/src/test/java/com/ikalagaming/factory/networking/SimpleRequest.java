package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.NonNull;

/** A request with no contents used to test serialization. */
public class SimpleRequest implements Request {
    @Override
    public void sendUsing(@NonNull Connection connection) {}
}
