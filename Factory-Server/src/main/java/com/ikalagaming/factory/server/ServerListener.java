package com.ikalagaming.factory.server;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.event.Order;
import com.ikalagaming.factory.events.registry.*;
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
        new LoadingBlocksCompleted().fire();
        new LoadingItems().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingItems(LoadingItems event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadItems(server.getRegistries().getItemRegistry());
        }
        new LoadingItemsCompleted().fire();
        new ServerLoaded().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingMaterials(LoadingMaterials event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadMaterials(server.getRegistries().getMaterialRegistry());
        }
        new LoadingMaterialsCompleted().fire();
        new LoadingBlocks().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleLoadingTags(LoadingTags event) {
        if (!event.isCanceled()) {
            DefinitionLoader.loadTags(server.getRegistries().getTagRegistry());
        }
        new LoadingTagsCompleted().fire();
        new LoadingMaterials().fire();
    }
}
