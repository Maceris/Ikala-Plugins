package com.ikalagaming.factory.server;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.event.Order;
import com.ikalagaming.factory.registry.ContentFolders;
import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.registry.events.*;
import com.ikalagaming.factory.server.events.ServerLoaded;
import com.ikalagaming.factory.server.events.ServerStopping;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServerListener implements Listener {
    private final Server server;

    @EventHandler(order = Order.EARLY)
    public void handleLoadingBlocks(LoadingBlocks event) {
        var registries = server.getRegistries();
        DefinitionLoader.loadBlocks(
                ContentFolders.installed(),
                registries.getBlockRegistry(),
                registries.getItemRegistry());
        new LoadingBlocksCompleted().fire();
        new LoadingItems().fire();
    }

    @EventHandler(order = Order.EARLY)
    public void handleLoadingItems(LoadingItems event) {
        DefinitionLoader.loadItems(
                ContentFolders.installed(), server.getRegistries().getItemRegistry());
        new LoadingItemsCompleted().fire();
        new ServerLoaded().fire();
    }

    @EventHandler(order = Order.EARLY)
    public void handleLoadingMaterials(LoadingMaterials event) {
        DefinitionLoader.loadMaterials(
                ContentFolders.installed(), server.getRegistries().getMaterialRegistry());
        new LoadingMaterialsCompleted().fire();
        new LoadingBlocks().fire();
    }

    @EventHandler(order = Order.EARLY)
    public void handleLoadingTags(LoadingTags event) {
        DefinitionLoader.loadTags(
                ContentFolders.installed(), server.getRegistries().getTagRegistry());
        new LoadingTagsCompleted().fire();
        new LoadingMaterials().fire();
    }

    @EventHandler(order = Order.LATEST)
    public void handleStopping(ServerStopping event) {}

    @EventHandler(order = Order.LATEST)
    public void handleLoaded(ServerLoaded event) {
        server.markLoaded();
    }
}
