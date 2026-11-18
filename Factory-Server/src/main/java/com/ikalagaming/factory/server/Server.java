package com.ikalagaming.factory.server;

import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;
import com.ikalagaming.factory.registry.Registries;
import com.ikalagaming.factory.registry.events.LoadingTags;
import com.ikalagaming.factory.server.events.ServerStopping;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Server {
    private World loadedWorld;
    /**
     * The definitions loaded for this run. Replaced on each start; read by event handlers on the
     * event thread.
     */
    @Getter private volatile Registries registries;
    private final AtomicBoolean running;
    private final List<ServerBoundRequestHandler> activeConnections;

    public Server() {
        running = new AtomicBoolean(false);
        activeConnections = Collections.synchronizedList(new ArrayList<>());
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STARTING", FactoryServerPlugin.getResourceBundle()));
            registries = new Registries();
            new LoadingTags().fire();
        } else {
            log.warn(
                    SafeResourceLoader.getString(
                            "SERVER_ALREADY_RUNNING", FactoryServerPlugin.getResourceBundle()));
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info(
                    SafeResourceLoader.getString(
                            "SERVER_STOPPING", FactoryServerPlugin.getResourceBundle()));
            new ServerStopping().fire();
        }
    }
}
