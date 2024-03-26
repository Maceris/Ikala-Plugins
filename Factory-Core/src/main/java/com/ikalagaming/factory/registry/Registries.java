package com.ikalagaming.factory.registry;

import lombok.Getter;

/** All the definitions used for the game, like items, blocks, tags. */
@Getter
public class Registries {
    /** Stores all the tags. */
    private final TagRegistry tagRegistry;

    /** Stores all the materials. */
    private final MaterialRegistry materialRegistry;

    /** Stores all the item definitions. */
    private final ItemRegistry itemRegistry;

    /** Stores all the block definitions. */
    private final BlockRegistry blockRegistry;

    public Registries() {
        tagRegistry = new TagRegistry();
        materialRegistry = new MaterialRegistry(tagRegistry);
        itemRegistry = new ItemRegistry(tagRegistry, materialRegistry);
        blockRegistry = new BlockRegistry(tagRegistry, materialRegistry);
    }
}
