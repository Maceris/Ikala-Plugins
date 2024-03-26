package com.ikalagaming.factory.server;

import com.ikalagaming.factory.events.registry.LoadingTags;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.world.World;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private World loadedWorld;
    @Getter private Registries registries;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void start() {
        if (running.compareAndExchange(false, true)) {
            new LoadingTags().fire();
            // TODO(ches) log
        }
    }

    public void stop() {
        if (running.compareAndExchange(true, false)) {
            // TODO(ches) shut down
        }
    }
}
