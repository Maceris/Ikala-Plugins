package com.ikalagaming.factory.server;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.event.Order;
import com.ikalagaming.factory.events.registry.LoadingBlocks;
import com.ikalagaming.factory.events.registry.LoadingItems;
import com.ikalagaming.factory.events.registry.LoadingMaterials;
import com.ikalagaming.factory.events.registry.LoadingTags;

import com.ikalagaming.factory.events.server.ServerLoaded;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServerListener implements Listener {
    private final Server server;

    @EventHandler(order = Order.LATEST)
    public void handleLoadingBlocks(LoadingBlocks event) {
        if (!event.isCanceled()) {
            var registries = server.getRegistries();
            DefinitionLoader.loadBlocks(
                    registries.getBlockRegistry(), registries.getItemRegistry());
        }
        new LoadingItems().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingItems(LoadingItems event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadItems(server.getRegistries().getItemRegistry());
        }
        new ServerLoaded().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingMaterials(LoadingMaterials event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadMaterials(server.getRegistries().getMaterialRegistry());
        }
        new LoadingBlocks().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingTags(LoadingTags event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadTags(server.getRegistries().getTagRegistry());
        }
        new LoadingMaterials().fire();
    }
}
