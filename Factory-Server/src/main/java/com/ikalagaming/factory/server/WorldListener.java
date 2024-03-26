package com.ikalagaming.factory.server;

import com.ikalagaming.event.EventHandler;
import com.ikalagaming.event.Listener;
import com.ikalagaming.event.Order;

import java.util.*;

public class WorldListener implements Listener {
    @EventHandler(order = Order.LATEST)
    public void handleLoadingBlocks() {}

    @EventHandler(order = Order.LATEST)
    public void handleLoadingItems() {}

    @EventHandler(order = Order.LATEST)
    public void handleLoadingMaterials() {}

    @EventHandler(order = Order.LATEST)
    public void handleLoadingTags() {}
}
