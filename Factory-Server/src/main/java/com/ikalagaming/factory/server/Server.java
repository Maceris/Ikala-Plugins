package com.ikalagaming.factory.server;

import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.events.registry.LoadingTags;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Server {
    private World loadedWorld;
    @Getter private Registries registries;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void start() {
        if (running.compareAndExchange(false, true)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STARTING", FactoryServerPlugin.getResourceBundle()));
            new LoadingTags().fire();
        }
    }

    public void stop() {
        if (running.compareAndExchange(true, false)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STOPPING", FactoryServerPlugin.getResourceBundle()));
        }
    }
}
