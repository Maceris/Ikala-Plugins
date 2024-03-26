package com.ikalagaming.factory.world;

import com.ikalagaming.factory.world.registry.*;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

/**
 * Tracks the state of the world.
 *
 * @author Ches Burks
 */
@Slf4j
public class World {
    /** The number of blocks per side of a chunk. */
    public static final int CHUNK_WIDTH = 16;

    /**
     * The minimum y level of the world. Blocks can exist on this level, but nothing can be below
     * it.
     */
    public static final int WORLD_HEIGHT_MIN = -512;

    /**
     * The maximum y level of the world. Blocks can exist on this level, but nothing can be above
     * it.
     */
    public static final int WORLD_HEIGHT_MAX = 512;

    /** The total height of the world in blocks. */
    public static final int WORLD_HEIGHT_TOTAL = WORLD_HEIGHT_MAX - WORLD_HEIGHT_MIN;

    /** Stores all the tags. */
    @Getter private final TagRegistry tagRegistry;

    /** Stores all the materials. */
    @Getter private final MaterialRegistry materialRegistry;

    /** Stores all the item definitions. */
    @Getter private final ItemRegistry itemRegistry;

    /** Stores all the block definitions. */
    @Getter private final BlockRegistry blockRegistry;

    public World() {
        tagRegistry = new TagRegistry();
        materialRegistry = new MaterialRegistry(tagRegistry);
        itemRegistry = new ItemRegistry(tagRegistry, materialRegistry);
        blockRegistry = new BlockRegistry(tagRegistry, materialRegistry);
    }
}
